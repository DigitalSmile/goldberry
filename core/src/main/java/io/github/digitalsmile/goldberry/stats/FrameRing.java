package io.github.digitalsmile.goldberry.stats;

import io.github.digitalsmile.goldberry.Window;

/// The last [#CAPACITY] frames, and nothing older.
///
/// A [Window]'s own record of its frame loop. Two `long`s per frame in two fixed
/// arrays: this is written on the frame path, so it allocates nothing, and it is
/// read by a HUD that is itself being drawn inside one of these frames.
///
/// ## Why the window is frames and not seconds
///
/// A duration window has to be pruned, which means the answer changes when nobody
/// asked it anything — and a HUD reading it twice in one frame could get two
/// numbers. A fixed count is a mean over a fixed sample, computed on demand from
/// data nothing but [#record] touches.
///
/// 60 of them: a second at the rate most displays run, so the number a HUD shows
/// settles within a second of a change and still steadies out the one frame in
/// twenty that the compositor makes late.
public final class FrameRing implements FrameStats {

    /// An empty ring, holding no frames yet.
    ///
    /// Public along with the class: a [io.github.digitalsmile.goldberry.Window]
    /// owns one and lives in another package now
    /// (ADR-0172).
    /// Nothing is lost by an application making its own -- a ring nobody feeds
    /// reads as zero, and [FixedFrameStats] is the better way to fake one.
    public FrameRing() {}

    /// How many frames are kept. See the class note.
    public static final int CAPACITY = 60;

    /// When each retained frame finished, in `System.nanoTime` units.
    private final long[] finished = new long[CAPACITY];

    /// How long each retained frame spent being painted.
    private final long[] painted = new long[CAPACITY];

    /// The four stages of each retained frame, in the same slots.
    ///
    /// One array per stage rather than one array of records: a ring of 60 frames
    /// times four `long`s is 1.9 KiB of primitives that never move, where 60
    /// records would be 60 allocations per second for a diagnostic that must not
    /// cost anything to leave on (ADR-0146).
    private final long[] built = new long[CAPACITY];
    private final long[] styled = new long[CAPACITY];
    private final long[] laid = new long[CAPACITY];
    private final long[] rastered = new long[CAPACITY];

    /// The stages of the frame being painted **now**, waiting for [#record].
    ///
    /// Handed in during the painter and consumed when it returns, because the
    /// thing that can time the stages is inside the painter and the thing that
    /// closes the frame is outside it. Cleared on every record, so a painter that
    /// reported no stages leaves zeroes rather than the previous frame's.
    private long pendingBuilt;
    private long pendingStyled;
    private long pendingLaid;
    private long pendingRastered;

    /// What the frame currently being painted spent in each stage, in nanos.
    public void stages(long buildNanos, long styleNanos, long layoutNanos, long rasterNanos) {
        pendingBuilt = Math.max(0L, buildNanos);
        pendingStyled = Math.max(0L, styleNanos);
        pendingLaid = Math.max(0L, layoutNanos);
        pendingRastered = Math.max(0L, rasterNanos);
    }

    /// Frames that were wanted and never seen, per slot — see [#lateFrames()].
    private final long[] late = new long[CAPACITY];

    /// What [#record] will bank as this frame's lateness, waiting for it.
    private long pendingLate;

    /// How many refreshes went by with a frame wanted and undelivered **before**
    /// the frame now being painted.
    ///
    /// Handed in by [io.github.digitalsmile.goldberry.Window] from the two things
    /// that know: the backend's pacer, which counts the refreshes a frame that
    /// was asked for did not arrive in time for, and the window itself, which
    /// counts the frames it painted and the platform then refused ([ADR-0271]).
    ///
    /// Banked **with the frame that follows the gap**, because that is the frame
    /// whose interval contains it: a gap has to be attached to something in the
    /// ring, or it ages out on a different schedule from the frames it belongs
    /// between.
    public void late(long refreshes) {
        pendingLate = Math.max(0L, refreshes);
    }

    /// Where the next frame goes.
    private int next;

    /// How many slots are filled, up to [#CAPACITY].
    private int size;

    /// Every frame since the window opened, which is the one number that is not
    /// a mean and the one thing this ring cannot forget.
    private long count;

    /// The run's totals, kept beside the ring rather than in it: what
    /// [#summary] reports once the window has aged the frames out. Three
    /// `long`s written on the frame path, which is the whole of their cost
    /// ([ADR-0342]).
    private long lateTotal;

    private long paintedTotal;
    private long paintedWorst;

    /// What the display does, as the backend last reported it — see
    /// [FrameStats#displayHertz].
    private double displayHertz;

    public void displayHertz(double hertz) {
        this.displayHertz = hertz;
    }

    @Override
    public double displayHertz() {
        return displayHertz;
    }

    /// Adds a frame.
    ///
    /// Both timestamps are `System.nanoTime` readings taken by [Window#paint]
    /// around the painter, so `paintNanos - startNanos` is the toolkit's own work
    /// and the difference between two consecutive `paintNanos` is the interval
    /// the loop actually achieved — including everything the platform did in
    /// between, which is the half a toolkit cannot see and a rate must include.
    ///
    /// @param startNanos when the frame began
    /// @param paintNanos when the painter returned
    public void record(long startNanos, long paintNanos) {
        finished[next] = paintNanos;
        painted[next] = Math.max(0L, paintNanos - startNanos);
        paintedTotal += painted[next];
        paintedWorst = Math.max(paintedWorst, painted[next]);
        lateTotal += pendingLate;
        built[next] = pendingBuilt;
        styled[next] = pendingStyled;
        laid[next] = pendingLaid;
        rastered[next] = pendingRastered;
        late[next] = pendingLate;
        pendingLate = 0;
        pendingBuilt = 0;
        pendingStyled = 0;
        pendingLaid = 0;
        pendingRastered = 0;
        next = (next + 1) % CAPACITY;
        if (size < CAPACITY) {
            size++;
        }
        count++;
    }

    @Override
    public int capacity() {
        return CAPACITY;
    }

    @Override
    public long count() {
        return count;
    }

    @Override
    public double fps() {
        var interval = frameMillis();
        return interval > 0 ? 1_000.0 / interval : 0;
    }

    @Override
    public double frameMillis() {
        if (size < 2) {
            // One frame is not an interval. Reporting anything here would be
            // reporting the time since an origin this class does not have.
            return 0;
        }
        var newest = finished[(next - 1 + CAPACITY) % CAPACITY];
        var oldest = finished[(next - size + CAPACITY) % CAPACITY];
        var span = newest - oldest;
        return span > 0 ? span / 1_000_000.0 / (size - 1) : 0;
    }

    @Override
    public long lateFrames() {
        if (size == 0) {
            return 0;
        }
        var total = 0L;
        for (var i = 0; i < size; i++) {
            total += late[(next - 1 - i + CAPACITY) % CAPACITY];
        }
        return total;
    }

    @Override
    public double paintMillis() {
        return meanOf(painted);
    }

    @Override
    public double buildMillis() {
        return meanOf(built);
    }

    @Override
    public double styleMillis() {
        return meanOf(styled);
    }

    @Override
    public double layoutMillis() {
        return meanOf(laid);
    }

    @Override
    public double rasterMillis() {
        return meanOf(rastered);
    }

    @Override
    public Span paint() {
        return spanOf(painted);
    }

    @Override
    public Span build() {
        return spanOf(built);
    }

    @Override
    public Span style() {
        return spanOf(styled);
    }

    @Override
    public Span layout() {
        return spanOf(laid);
    }

    @Override
    public Span raster() {
        return spanOf(rastered);
    }

    /// The cheapest, the mean and the dearest of one stage's ring.
    ///
    /// One pass over at most sixty `long`s, which is what makes it safe to ask
    /// inside a `build` that runs every frame — the same promise [#paintMillis]
    /// already made (ADR-0154).
    private Span spanOf(long[] ring) {
        if (size == 0) {
            return Span.NONE;
        }
        var total = 0L;
        var min = Long.MAX_VALUE;
        var max = 0L;
        for (var i = 0; i < size; i++) {
            var value = ring[(next - 1 - i + CAPACITY) % CAPACITY];
            total += value;
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        return new Span(min / 1_000_000.0, total / 1_000_000.0 / size, max / 1_000_000.0);
    }

    /// The mean of one stage's ring, in milliseconds.
    private double meanOf(long[] ring) {
        if (size == 0) {
            return 0;
        }
        var total = 0L;
        for (var i = 0; i < size; i++) {
            total += ring[(next - 1 - i + CAPACITY) % CAPACITY];
        }
        return total / 1_000_000.0 / size;
    }

    /// The run so far, from the totals rather than the window — so a run of
    /// three hundred frames reports three hundred, and every refresh it missed,
    /// not the last sixty of either.
    @Override
    public FrameSummary summary() {
        if (count == 0) {
            return FrameSummary.NONE;
        }
        return new FrameSummary(
                count, lateTotal, paintedTotal / 1_000_000.0 / count, paintedWorst / 1_000_000.0, displayHertz);
    }

    @Override
    public String toString() {
        return "FrameRing[%d frames, %.1f fps, paint %.2fms, %d late]"
                .formatted(count, fps(), paintMillis(), lateFrames());
    }
}
