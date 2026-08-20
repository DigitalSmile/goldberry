package io.github.digitalsmile.goldberry;

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
final class FrameRing implements FrameStats {

    /// How many frames are kept. See the class note.
    static final int CAPACITY = 60;

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
    void stages(long buildNanos, long styleNanos, long layoutNanos, long rasterNanos) {
        pendingBuilt = Math.max(0L, buildNanos);
        pendingStyled = Math.max(0L, styleNanos);
        pendingLaid = Math.max(0L, layoutNanos);
        pendingRastered = Math.max(0L, rasterNanos);
    }

    /// Where the next frame goes.
    private int next;

    /// How many slots are filled, up to [#CAPACITY].
    private int size;

    /// Every frame since the window opened, which is the one number that is not
    /// a mean and the one thing this ring cannot forget.
    private long count;

    /// What the display does, as the backend last reported it — see
    /// [FrameStats#displayHertz].
    private double displayHertz;

    void displayHertz(double hertz) {
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
    void record(long startNanos, long paintNanos) {
        finished[next] = paintNanos;
        painted[next] = Math.max(0L, paintNanos - startNanos);
        built[next] = pendingBuilt;
        styled[next] = pendingStyled;
        laid[next] = pendingLaid;
        rastered[next] = pendingRastered;
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

    @Override
    public String toString() {
        return "FrameRing[%d frames, %.1f fps, paint %.2fms]".formatted(count, fps(), paintMillis());
    }
}
