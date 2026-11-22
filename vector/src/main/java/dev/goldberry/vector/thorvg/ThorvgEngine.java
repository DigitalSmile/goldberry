package dev.goldberry.vector.thorvg;

import java.lang.ref.Cleaner;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/// The one owner of ThorVG's engine: every call into the library goes through
/// [#call], one at a time.
///
/// ThorVG is built without its task scheduler, and with it go its locks: the
/// loader cache and the CPU rasterizer's memory pool are process-wide and
/// unguarded. So every parse, draw and release in the process holds one lock.
/// The engine is started on the first call and never stopped, because a
/// document can be drawn for as long as the process lives, and a stop would
/// have to pair with the start on the same thread.
///
/// Whether parallel canvases are safe without the lock is a question for a
/// benchmark, not an assumption; until one answers it, they wait.
public final class ThorvgEngine {

    /// `TVG_ENGINE_OPTION_DEFAULT`, for `tvg_swcanvas_create`.
    public static final int ENGINE_OPTION_DEFAULT = 1;

    /// `TVG_COLORSPACE_ARGB8888`: premultiplied `0xAARRGGBB` per `uint32_t`,
    /// which on every target the toolkit builds is premultiplied BGRA in memory.
    public static final int COLORSPACE_ARGB8888 = 1;

    private static final ReentrantLock LOCK = new ReentrantLock();

    /// Releases pictures nobody closed. Its thread takes [#LOCK] like any other.
    private static final Cleaner CLEANER = Cleaner.create();

    /// Guarded by [#LOCK].
    private static boolean started;

    private ThorvgEngine() {}

    /// Runs `work` with the library, holding the engine's lock and starting the
    /// engine first if nothing has yet.
    ///
    /// @throws IllegalStateException when the library is not available, naming
    ///                               why, or when the engine would not start
    public static <T> T call(Function<VectorLibrary, T> work) {
        Objects.requireNonNull(work, "work");
        var library = VectorNatives.get();
        LOCK.lock();
        try {
            if (!started) {
                var result = library.engine().init().call(0);
                if (result != TvgResult.SUCCESS.code()) {
                    throw new IllegalStateException("ThorVG's engine would not start: " + TvgResult.describe(result));
                }
                started = true;
            }
            return work.apply(library);
        } finally {
            LOCK.unlock();
        }
    }

    /// Whether the current thread holds the engine's lock: what a method that
    /// must run inside [#call] asserts.
    public static boolean isHeldByCurrentThread() {
        return LOCK.isHeldByCurrentThread();
    }

    /// Registers `action` to run when `owner` becomes unreachable.
    ///
    /// The action must not refer to `owner`, or it never will.
    public static Cleaner.Cleanable whenUnreachable(Object owner, Runnable action) {
        return CLEANER.register(owner, action);
    }
}
