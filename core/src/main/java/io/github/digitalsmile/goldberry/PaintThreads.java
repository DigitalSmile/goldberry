package io.github.digitalsmile.goldberry;

import io.github.digitalsmile.goldberry.backend.PhysicalSize;
import io.github.digitalsmile.goldberry.natives.log.Logs;
import org.slf4j.Logger;

/// How many Blend2D workers a frame is painted with.
///
/// Blend2D can rasterize a frame across threads by splitting it into horizontal
/// bands (ADR-0002). ADR-0031 parked the knob as "only matters if paint ever
/// becomes the bottleneck"; ADR-0037 measured a frame in which it had, with paint
/// at 5.10 ms of a 7.86 ms total. This is where the number is decided, and the
/// numbers behind it are in ADR-0042.
///
/// Three things shape the policy, and all three are measured rather than
/// assumed — `PaintBenchmark` sweeps seven worker counts across six surface
/// sizes, and ADR-0042 has the table:
///
/// 1. **One worker is never better than none.** It pays for the command queue
///    and the hand-off and gets no parallelism back — at 640×480 it measured
///    *slower* than synchronous, 0.499 ms against 0.478. So the count is zero or
///    at least two, and never one.
/// 2. **More workers than bands is waste.** The speed-up flattens at four on
///    every size measured and gets worse at eight, and each extra worker is one
///    more thread waking sixty times a second on a laptop battery.
/// 3. **A small surface has nothing to divide.** At 240×120 threading saved
///    46 µs — real, but inside the run-to-run spread and 0.3% of a frame budget.
///    Below [#MIN_THREADED_PIXELS] a frame paints synchronously.
///
/// `-Dgoldberry.paint.threads=N` overrides all three, and `0` restores the
/// synchronous behaviour of everything before ADR-0042.
public final class PaintThreads {

    /// Sets the worker count explicitly. `0` paints synchronously.
    public static final String PROPERTY = "goldberry.paint.threads";

    /// Under this many physical pixels, threading a frame is not worth waking
    /// the pool for. 400×300 — above a tooltip, below every window.
    static final int MIN_THREADED_PIXELS = 400 * 300;

    /// More than this buys nothing on any size measured, and costs a thread.
    static final int MAX_THREADS = 4;

    /// A single worker is the one count that loses, so a machine that can only
    /// offer one gets none.
    static final int MIN_THREADS = 2;

    private static final Logger LOG = Logs.of(PaintThreads.class);

    /// Resolved once. The property is read at class-initialization time
    /// deliberately: a value that changed between frames would make two frames
    /// of the same window incomparable, and there is no use for that.
    private static final int CONFIGURED = configured();

    private PaintThreads() {
    }

    /// The worker count for a surface of `size`.
    public static int forSurface(PhysicalSize size) {
        return resolve(
                (long) size.width() * size.height(),
                CONFIGURED,
                Runtime.getRuntime().availableProcessors());
    }

    /// The whole policy, as a function of its three inputs.
    ///
    /// Split out from [#forSurface] so it is testable without a system property
    /// and without running on a machine of each core count — the same reason
    /// `NativePlatform.of` takes the strings rather than reading them.
    ///
    /// @param pixels     the surface area in physical pixels
    /// @param configured the property's value, or negative if it is not set
    /// @param processors what the machine reports
    static int resolve(long pixels, int configured, int processors) {
        // An explicit request wins outright, small surface or not. Someone who
        // sets this is measuring something, and a policy that quietly overruled
        // them would make the measurement a lie.
        if (configured >= 0) {
            return configured;
        }
        if (pixels < MIN_THREADED_PIXELS) {
            return 0;
        }
        return automatic(processors);
    }

    /// What the machine suggests, with no surface in the question.
    ///
    /// One worker per core, minus the one the UI thread is already using, capped
    /// at [#MAX_THREADS] — and rounded *down to zero* rather than up when that
    /// leaves a single worker, because one is the count that measured slower
    /// than none. A one- or two-core machine therefore paints synchronously.
    static int automatic(int processors) {
        var available = Math.clamp(processors - 1L, 0, MAX_THREADS);
        return available < MIN_THREADS ? 0 : available;
    }

    /// What this machine suggests.
    static int automatic() {
        return automatic(Runtime.getRuntime().availableProcessors());
    }

    /// The property's value, or `-1` for "not set".
    ///
    /// A value that is not a number is a mistake worth reporting rather than
    /// swallowing — but not worth refusing to start over, so it logs and falls
    /// back to the automatic count.
    private static int configured() {
        var raw = System.getProperty(PROPERTY);
        if (raw == null || raw.isBlank()) {
            return -1;
        }
        try {
            var value = Integer.parseInt(raw.trim());
            if (value < 0) {
                LOG.warn("{}={} is negative; using the automatic worker count", PROPERTY, raw);
                return -1;
            }
            return value;
        } catch (NumberFormatException e) {
            LOG.warn("{}=\"{}\" is not a number; using the automatic worker count", PROPERTY, raw);
            return -1;
        }
    }
}
