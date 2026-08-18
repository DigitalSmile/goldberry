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

    /// Where the next frame goes.
    private int next;

    /// How many slots are filled, up to [#CAPACITY].
    private int size;

    /// Every frame since the window opened, which is the one number that is not
    /// a mean and the one thing this ring cannot forget.
    private long count;

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
        if (size == 0) {
            return 0;
        }
        var total = 0L;
        for (var i = 0; i < size; i++) {
            total += painted[(next - 1 - i + CAPACITY) % CAPACITY];
        }
        return total / 1_000_000.0 / size;
    }

    @Override
    public String toString() {
        return "FrameRing[%d frames, %.1f fps, paint %.2fms]".formatted(count, fps(), paintMillis());
    }
}
