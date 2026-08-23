package io.github.digitalsmile.goldberry.natives.blend2d.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import io.github.digitalsmile.goldberry.natives.Downcalls;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

/// Blend2D’s `BLPath` — a recorded sequence of drawing commands.
///
/// One holder per function: its handle, its address, and a `call` whose
/// parameters are the C prototype’s. See [Downcalls] for why the handle is a
/// `static final` constant and why these live in a package of their own.
public record PathCalls(
        PathInit pathInit,
        PathDestroy pathDestroy,
        PathReset pathReset,
        PathGetSize pathGetSize,
        PathMoveTo pathMoveTo,
        PathLineTo pathLineTo,
        PathQuadTo pathQuadTo,
        PathCubicTo pathCubicTo,
        PathSmoothQuadTo pathSmoothQuadTo,
        PathSmoothCubicTo pathSmoothCubicTo,
        PathEllipticArcTo pathEllipticArcTo,
        PathClose pathClose) {

    /// Binds every function above, failing if the library exports none of them.
    ///
    /// @param lookup the loaded `libgoldberry`
    public static PathCalls bind(SymbolLookup lookup) {
        return new PathCalls(
                new PathInit(lookup),
                new PathDestroy(lookup),
                new PathReset(lookup),
                new PathGetSize(lookup),
                new PathMoveTo(lookup),
                new PathLineTo(lookup),
                new PathQuadTo(lookup),
                new PathCubicTo(lookup),
                new PathSmoothQuadTo(lookup),
                new PathSmoothCubicTo(lookup),
                new PathEllipticArcTo(lookup),
                new PathClose(lookup));
    }

    /// Initialises an empty path.
    ///
    /// `int bl_path_init(void*)`
    ///
    /// @param path an uninitialised `BLPathCore` to take over
    public static final class PathInit {

        private static final MethodHandle FD_bl_path_init =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        PathInit(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_path_init");
        }

        public int call(MemorySegment path) {
            try {
                return (int) FD_bl_path_init.invokeExact(address, path);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_path_init", t);
            }
        }
    }

    /// Releases the path.
    ///
    /// `int bl_path_destroy(void*)`
    ///
    /// @param path the path to release
    public static final class PathDestroy {

        private static final MethodHandle FD_bl_path_destroy =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        PathDestroy(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_path_destroy");
        }

        public int call(MemorySegment path) {
            try {
                return (int) FD_bl_path_destroy.invokeExact(address, path);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_path_destroy", t);
            }
        }
    }

    /// Drops every command, keeping the allocation — which is what makes a path
    /// reusable across frames without allocating.
    ///
    /// `int bl_path_reset(void*)`
    ///
    /// @param path the path to empty
    public static final class PathReset {

        private static final MethodHandle FD_bl_path_reset =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        PathReset(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_path_reset");
        }

        public int call(MemorySegment path) {
            try {
                return (int) FD_bl_path_reset.invokeExact(address, path);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_path_reset", t);
            }
        }
    }

    /// How many commands the path holds.
    ///
    /// Returns `size_t`, not `BLResult`: the one path call that is not an
    /// operation and so cannot fail.
    ///
    /// `int64_t bl_path_get_size(void*)`
    ///
    /// @param path the path to measure
    /// @return the command count
    public static final class PathGetSize {

        private static final MethodHandle FD_bl_path_get_size =
                Downcalls.link(FunctionDescriptor.of(JAVA_LONG, ADDRESS));

        private final MemorySegment address;

        PathGetSize(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_path_get_size");
        }

        public long call(MemorySegment path) {
            try {
                return (long) FD_bl_path_get_size.invokeExact(address, path);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_path_get_size", t);
            }
        }
    }

    /// Starts a new subpath at `(x, y)` — SVG’s `M`.
    ///
    /// `int bl_path_move_to(void*, double, double)`
    ///
    /// @param path the path to append to
    public static final class PathMoveTo {

        private static final MethodHandle FD_bl_path_move_to =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_DOUBLE, JAVA_DOUBLE));

        private final MemorySegment address;

        PathMoveTo(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_path_move_to");
        }

        public int call(MemorySegment path, double x, double y) {
            try {
                return (int) FD_bl_path_move_to.invokeExact(address, path, x, y);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_path_move_to", t);
            }
        }
    }

    /// Draws a straight line to `(x, y)` — SVG’s `L`.
    ///
    /// `int bl_path_line_to(void*, double, double)`
    ///
    /// @param path the path to append to
    public static final class PathLineTo {

        private static final MethodHandle FD_bl_path_line_to =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_DOUBLE, JAVA_DOUBLE));

        private final MemorySegment address;

        PathLineTo(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_path_line_to");
        }

        public int call(MemorySegment path, double x, double y) {
            try {
                return (int) FD_bl_path_line_to.invokeExact(address, path, x, y);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_path_line_to", t);
            }
        }
    }

    /// Draws a quadratic Bézier — SVG’s `Q`.
    ///
    /// `int bl_path_quad_to(void*, double, double, double, double)`
    ///
    /// @param path the path to append to
    /// @param x the end point
    /// @param y the end point
    public static final class PathQuadTo {

        private static final MethodHandle FD_bl_path_quad_to =
                Downcalls.link(FunctionDescriptor.of(
                        JAVA_INT, ADDRESS, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE));

        private final MemorySegment address;

        PathQuadTo(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_path_quad_to");
        }

        public int call(MemorySegment path, double controlX, double controlY, double x, double y) {
            try {
                return (int) FD_bl_path_quad_to.invokeExact(
                        address, path, controlX, controlY, x, y);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_path_quad_to", t);
            }
        }
    }

    /// Draws a cubic Bézier — SVG’s `C`.
    ///
    /// `int bl_path_cubic_to(void*, double, double, double, double, double, double)`
    ///
    /// @param path the path to append to
    /// @param x the end point
    /// @param y the end point
    public static final class PathCubicTo {

        private static final MethodHandle FD_bl_path_cubic_to =
                Downcalls.link(FunctionDescriptor.of(
                        JAVA_INT, ADDRESS, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE,
                        JAVA_DOUBLE, JAVA_DOUBLE));

        private final MemorySegment address;

        PathCubicTo(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_path_cubic_to");
        }

        public int call(
                MemorySegment path, double control1X, double control1Y, double control2X,
                double control2Y, double x, double y) {
            try {
                return (int) FD_bl_path_cubic_to.invokeExact(
                        address, path, control1X, control1Y, control2X, control2Y, x, y);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_path_cubic_to", t);
            }
        }
    }

    /// Draws a quadratic Bézier whose control point is the reflection of the
    /// previous one — SVG’s `T`.
    ///
    /// Blend2D does the reflection itself, against the command it actually
    /// recorded — which is the definition SVG gives, and not the one a caller
    /// tracking "the last control point" in Java would arrive at after a `Z` or a
    /// bare `M`.
    ///
    /// `int bl_path_smooth_quad_to(void*, double, double)`
    ///
    /// @param path the path to append to
    /// @param x the end point
    /// @param y the end point
    public static final class PathSmoothQuadTo {

        private static final MethodHandle FD_bl_path_smooth_quad_to =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_DOUBLE, JAVA_DOUBLE));

        private final MemorySegment address;

        PathSmoothQuadTo(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_path_smooth_quad_to");
        }

        public int call(MemorySegment path, double x, double y) {
            try {
                return (int) FD_bl_path_smooth_quad_to.invokeExact(address, path, x, y);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_path_smooth_quad_to", t);
            }
        }
    }

    /// Draws a cubic Bézier whose first control point is the reflection of the
    /// previous one — SVG’s `S`. See [PathCalls.PathSmoothQuadTo] for why the
    /// reflection is Blend2D’s to do.
    ///
    /// `int bl_path_smooth_cubic_to(void*, double, double, double, double)`
    ///
    /// @param path the path to append to
    /// @param x the end point
    /// @param y the end point
    public static final class PathSmoothCubicTo {

        private static final MethodHandle FD_bl_path_smooth_cubic_to =
                Downcalls.link(FunctionDescriptor.of(
                        JAVA_INT, ADDRESS, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE));

        private final MemorySegment address;

        PathSmoothCubicTo(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_path_smooth_cubic_to");
        }

        public int call(
                MemorySegment path, double control2X, double control2Y, double x, double y) {
            try {
                return (int) FD_bl_path_smooth_cubic_to.invokeExact(
                        address, path, control2X, control2Y, x, y);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_path_smooth_cubic_to", t);
            }
        }
    }

    /// Draws an elliptical arc — SVG’s `A`, argument for argument and flag for flag.
    ///
    /// The two flags are C `_Bool`, one byte: `JAVA_BOOLEAN` and not `JAVA_INT`,
    /// which would put four bytes where the ABI expects one and shift every
    /// argument after them.
    ///
    /// `int bl_path_elliptic_arc_to(void*, double, double, double, _Bool, _Bool, double, double)`
    ///
    /// @param path the path to append to
    /// @param xAxisRotation in **radians**, where SVG writes degrees
    /// @param largeArc SVG’s large-arc flag
    /// @param sweep SVG’s sweep flag
    /// @param x the end point
    /// @param y the end point
    public static final class PathEllipticArcTo {

        private static final MethodHandle FD_bl_path_elliptic_arc_to =
                Downcalls.link(FunctionDescriptor.of(
                        JAVA_INT, ADDRESS, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_BOOLEAN,
                        JAVA_BOOLEAN, JAVA_DOUBLE, JAVA_DOUBLE));

        private final MemorySegment address;

        PathEllipticArcTo(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_path_elliptic_arc_to");
        }

        public int call(
                MemorySegment path, double radiusX, double radiusY, double xAxisRotation,
                boolean largeArc, boolean sweep, double x, double y) {
            try {
                return (int) FD_bl_path_elliptic_arc_to.invokeExact(
                        address, path, radiusX, radiusY, xAxisRotation, largeArc, sweep, x, y);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_path_elliptic_arc_to", t);
            }
        }
    }

    /// Closes the current subpath — SVG’s `Z`.
    ///
    /// `int bl_path_close(void*)`
    ///
    /// @param path the path to close
    public static final class PathClose {

        private static final MethodHandle FD_bl_path_close =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        PathClose(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_path_close");
        }

        public int call(MemorySegment path) {
            try {
                return (int) FD_bl_path_close.invokeExact(address, path);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_path_close", t);
            }
        }
    }
}
