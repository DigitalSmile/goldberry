package io.github.digitalsmile.goldberry.natives.blend2d;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;

import io.github.digitalsmile.goldberry.natives.NativeLibrary;
import io.github.digitalsmile.goldberry.natives.blend2d.calls.PathCalls;
import io.github.digitalsmile.goldberry.natives.blend2d.error.BlendException;

/// Blend2D's path calls, behind [BlendPath] (ADR-0043).
///
/// Every command is `(BLPathCore*, doubles...)` returning `BLResult`, which is
/// what makes this a long list of near-identical methods rather than a design.
final class Blend2dPath {

    private static final class Holder {
        private static final Blend2dPath INSTANCE =
                new Blend2dPath(NativeLibrary.get().lookup());
    }

    private final PathCalls calls;

    private Blend2dPath(SymbolLookup lookup) {
        this.calls = PathCalls.bind(lookup);
    }

    static Blend2dPath get() {
        return Holder.INSTANCE;
    }

    void pathInit(MemorySegment path) {
        check("bl_path_init", calls.pathInit().call(path));
    }

    void pathDestroy(MemorySegment path) {
        check("bl_path_destroy", calls.pathDestroy().call(path));
    }

    void pathReset(MemorySegment path) {
        check("bl_path_reset", calls.pathReset().call(path));
    }

    /// How many vertices the path holds. Used by the tests, which is how "the
    /// parser really issued the commands" becomes a number rather than a claim.
    /// `size_t`, not `BLResult` — the one path call that is not an operation.
    long pathSize(MemorySegment path) {
        return calls.pathGetSize().call(path);
    }

    void pathMoveTo(MemorySegment path, double x, double y) {
        int result;
        result = calls.pathMoveTo().call(path, x, y);
        check("bl_path_move_to", result);
    }

    void pathLineTo(MemorySegment path, double x, double y) {
        int result;
        result = calls.pathLineTo().call(path, x, y);
        check("bl_path_line_to", result);
    }

    void pathQuadTo(MemorySegment path, double x1, double y1, double x2, double y2) {
        int result;
        result = calls.pathQuadTo().call(path, x1, y1, x2, y2);
        check("bl_path_quad_to", result);
    }

    void pathCubicTo(MemorySegment path, double x1, double y1, double x2, double y2, double x3, double y3) {
        int result;
        result = calls.pathCubicTo().call(path, x1, y1, x2, y2, x3, y3);
        check("bl_path_cubic_to", result);
    }

    /// SVG's `S` and `T`: the first control point is the reflection of the
    /// previous one. Blend2D does that reflection itself, against the command it
    /// actually recorded — which is the definition SVG gives, and not the one a
    /// caller tracking "the last control point" in Java would arrive at after a
    /// `Z` or a bare `M`.
    void pathSmoothQuadTo(MemorySegment path, double x2, double y2) {
        int result;
        result = calls.pathSmoothQuadTo().call(path, x2, y2);
        check("bl_path_smooth_quad_to", result);
    }

    void pathSmoothCubicTo(MemorySegment path, double x2, double y2, double x3, double y3) {
        int result;
        result = calls.pathSmoothCubicTo().call(path, x2, y2, x3, y3);
        check("bl_path_smooth_cubic_to", result);
    }

    /// `BLResult bl_path_elliptic_arc_to(BLPathCore*, double rx, double ry,`
    /// `double x_axis_rotation, bool large_arc, bool sweep, double x1, double y1)`
    ///
    /// SVG's `A` command, argument for argument and flag for flag. The two
    /// `bool`s are C `_Bool`, one byte — `JAVA_BOOLEAN`, not `JAVA_INT`, which
    /// would put four bytes where the ABI expects one and shift every argument
    /// after them.
    void pathEllipticArcTo(
            MemorySegment path,
            double rx,
            double ry,
            double rotation,
            boolean largeArc,
            boolean sweep,
            double x,
            double y) {
        check("bl_path_elliptic_arc_to", calls.pathEllipticArcTo().call(path, rx, ry, rotation, largeArc, sweep, x, y));
    }

    void pathClose(MemorySegment path) {
        check("bl_path_close", calls.pathClose().call(path));
    }

    /// A `BLResult` that is not `BL_SUCCESS` is the call reporting a problem, not
    /// the crossing failing -- so it is raised as a [BlendException] naming the
    /// operation, and not as the [IllegalStateException] a holder raises.
    private static void check(String operation, int result) {
        if (result != 0) {
            throw new BlendException(operation, result);
        }
    }
}
