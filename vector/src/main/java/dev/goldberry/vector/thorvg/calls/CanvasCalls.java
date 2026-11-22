package dev.goldberry.vector.thorvg.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.util.List;

import dev.goldberry.vector.thorvg.Downcalls;

/// ThorVG's CPU canvas: one per draw, over pixels Java owns. Every function but
/// `tvg_swcanvas_create` answers a `Tvg_Result`.
public record CanvasCalls(Create create, SetTarget setTarget, Add add, Draw draw, Sync sync, Destroy destroy) {

    /// The symbols this record binds, in the order of its components.
    public static final List<String> SYMBOLS = List.of(
            "tvg_swcanvas_create",
            "tvg_swcanvas_set_target",
            "tvg_canvas_add",
            "tvg_canvas_draw",
            "tvg_canvas_sync",
            "tvg_canvas_destroy");

    /// Binds every function above.
    ///
    /// @param lookup the loaded library
    public static CanvasCalls bind(SymbolLookup lookup) {
        return new CanvasCalls(
                new Create(lookup),
                new SetTarget(lookup),
                new Add(lookup),
                new Draw(lookup),
                new Sync(lookup),
                new Destroy(lookup));
    }

    /// `Tvg_Canvas tvg_swcanvas_create(Tvg_Engine_Option op)`
    ///
    /// NULL when the engine is not started.
    public static final class Create {

        private static final MethodHandle FD_tvg_swcanvas_create =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, JAVA_INT));

        private final MemorySegment address;

        Create(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "tvg_swcanvas_create");
        }

        public MemorySegment call(int option) {
            try {
                return (MemorySegment) FD_tvg_swcanvas_create.invokeExact(address, option);
            } catch (Throwable t) {
                throw Downcalls.failure("tvg_swcanvas_create", t);
            }
        }
    }

    /// `Tvg_Result tvg_swcanvas_set_target(Tvg_Canvas canvas, uint32_t *buffer, uint32_t stride, uint32_t w,
    /// uint32_t h, Tvg_Colorspace cs)`
    ///
    /// `stride` is in pixels, not bytes.
    public static final class SetTarget {

        private static final MethodHandle FD_tvg_swcanvas_set_target = Downcalls.link(
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT));

        private final MemorySegment address;

        SetTarget(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "tvg_swcanvas_set_target");
        }

        public int call(MemorySegment canvas, MemorySegment buffer, int stride, int width, int height, int colorspace) {
            try {
                return (int) FD_tvg_swcanvas_set_target.invokeExact(
                        address, canvas, buffer, stride, width, height, colorspace);
            } catch (Throwable t) {
                throw Downcalls.failure("tvg_swcanvas_set_target", t);
            }
        }
    }

    /// `Tvg_Result tvg_canvas_add(Tvg_Canvas canvas, Tvg_Paint paint)`
    ///
    /// The canvas takes the paint, and releases it when it is destroyed.
    public static final class Add {

        private static final MethodHandle FD_tvg_canvas_add =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

        private final MemorySegment address;

        Add(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "tvg_canvas_add");
        }

        public int call(MemorySegment canvas, MemorySegment paint) {
            try {
                return (int) FD_tvg_canvas_add.invokeExact(address, canvas, paint);
            } catch (Throwable t) {
                throw Downcalls.failure("tvg_canvas_add", t);
            }
        }
    }

    /// `Tvg_Result tvg_canvas_draw(Tvg_Canvas canvas, bool clear)`
    ///
    /// Updates what changed first.
    public static final class Draw {

        private static final MethodHandle FD_tvg_canvas_draw =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_BOOLEAN));

        private final MemorySegment address;

        Draw(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "tvg_canvas_draw");
        }

        public int call(MemorySegment canvas, boolean clear) {
            try {
                return (int) FD_tvg_canvas_draw.invokeExact(address, canvas, clear);
            } catch (Throwable t) {
                throw Downcalls.failure("tvg_canvas_draw", t);
            }
        }
    }

    /// `Tvg_Result tvg_canvas_sync(Tvg_Canvas canvas)`
    ///
    /// Returns once the pixels are written.
    public static final class Sync {

        private static final MethodHandle FD_tvg_canvas_sync = Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        Sync(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "tvg_canvas_sync");
        }

        public int call(MemorySegment canvas) {
            try {
                return (int) FD_tvg_canvas_sync.invokeExact(address, canvas);
            } catch (Throwable t) {
                throw Downcalls.failure("tvg_canvas_sync", t);
            }
        }
    }

    /// `Tvg_Result tvg_canvas_destroy(Tvg_Canvas canvas)`
    ///
    /// Releases the canvas and every paint it holds.
    public static final class Destroy {

        private static final MethodHandle FD_tvg_canvas_destroy =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        Destroy(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "tvg_canvas_destroy");
        }

        public int call(MemorySegment canvas) {
            try {
                return (int) FD_tvg_canvas_destroy.invokeExact(address, canvas);
            } catch (Throwable t) {
                throw Downcalls.failure("tvg_canvas_destroy", t);
            }
        }
    }
}
