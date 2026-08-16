package io.github.digitalsmile.goldberry.natives.blend2d;

import io.github.digitalsmile.goldberry.natives.layout.Layouts;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/// A Blend2D path — a sequence of move, line, curve and close commands.
///
/// The command set is SVG's, one for one, because the thing Goldberry builds
/// paths out of is SVG path data: Lucide's 1544 icons are strings of exactly
/// these commands in a 24×24 box (ADR-0043). Two of them are worth naming:
///
/// - [#ellipticArcTo] is SVG's `A`, argument for argument, flags included.
///   Converting an elliptic arc to cubics is a page of arithmetic that Blend2D
///   already has and gets right at the degenerate cases — a zero radius, a
///   rotation, an arc whose endpoints coincide.
/// - [#smoothCubicTo] and [#smoothQuadTo] are `S` and `T`. Blend2D reflects the
///   previous control point itself, against the command it actually recorded.
///   A caller tracking "the last control point" in Java gets the same answer
///   until a `Z` or a bare `M` intervenes, and then quietly does not.
///
/// A path holds no reference to a context and can be built once and drawn many
/// times, at many origins, which is the whole reason an icon costs one parse.
///
/// Confined to the thread that created it, and must be closed.
public final class BlendPath implements AutoCloseable {

    private final Blend2D blend2d = Blend2D.get();
    private final Arena arena;
    private final MemorySegment path;
    private final Thread owner = Thread.currentThread();

    private boolean closed;

    private BlendPath() {
        this.arena = Arena.ofConfined();
        try {
            this.path = arena.allocate(Layouts.BL_PATH_CORE.layout());
            blend2d.pathInit(path);
        } catch (RuntimeException | Error e) {
            arena.close();
            throw e;
        }
    }

    /// An empty path.
    public static BlendPath create() {
        return new BlendPath();
    }

    /// Starts a new sub-path at `(x, y)` — SVG's `M`.
    public void moveTo(double x, double y) {
        requireUsable();
        blend2d.pathMoveTo(path, x, y);
    }

    /// A straight segment to `(x, y)` — SVG's `L`.
    public void lineTo(double x, double y) {
        requireUsable();
        blend2d.pathLineTo(path, x, y);
    }

    /// A quadratic curve through control `(x1, y1)` to `(x2, y2)` — SVG's `Q`.
    public void quadTo(double x1, double y1, double x2, double y2) {
        requireUsable();
        blend2d.pathQuadTo(path, x1, y1, x2, y2);
    }

    /// A cubic curve through two controls to `(x3, y3)` — SVG's `C`.
    public void cubicTo(double x1, double y1, double x2, double y2, double x3, double y3) {
        requireUsable();
        blend2d.pathCubicTo(path, x1, y1, x2, y2, x3, y3);
    }

    /// A quadratic whose control is the reflection of the previous one — SVG's
    /// `T`.
    public void smoothQuadTo(double x2, double y2) {
        requireUsable();
        blend2d.pathSmoothQuadTo(path, x2, y2);
    }

    /// A cubic whose first control is the reflection of the previous second
    /// one — SVG's `S`.
    public void smoothCubicTo(double x2, double y2, double x3, double y3) {
        requireUsable();
        blend2d.pathSmoothCubicTo(path, x2, y2, x3, y3);
    }

    /// An elliptic arc to `(x, y)` — SVG's `A`.
    ///
    /// @param rx       the ellipse's horizontal radius
    /// @param ry       its vertical radius
    /// @param rotation the x-axis rotation, **in radians**
    /// @param largeArc SVG's large-arc-flag: take the longer of the two arcs
    /// @param sweep    SVG's sweep-flag: go clockwise in a y-down space
    ///
    /// SVG writes the rotation in degrees and Blend2D takes radians. The
    /// conversion is the caller's, and it is stated here because a path drawn
    /// with 45 where 0.785 was meant produces an icon that is subtly wrong
    /// rather than absent.
    public void ellipticArcTo(
            double rx, double ry, double rotation, boolean largeArc, boolean sweep,
            double x, double y) {
        requireUsable();
        blend2d.pathEllipticArcTo(path, rx, ry, rotation, largeArc, sweep, x, y);
    }

    /// Closes the current sub-path — SVG's `Z`.
    ///
    /// **Not** [#close()]. A path has two different "closes" — finish this
    /// figure, and give the memory back — and giving them the same name would
    /// mean `try-with-resources` silently drawing a closing segment.
    public void closeSubPath() {
        requireUsable();
        blend2d.pathClose(path);
    }

    /// Discards every command, keeping the path usable.
    public void reset() {
        requireUsable();
        blend2d.pathReset(path);
    }

    /// How many vertices the path holds.
    ///
    /// Not the command count: a cubic contributes three. It is here so a test
    /// can assert that a parse produced geometry, and so an empty path is
    /// distinguishable from one that silently swallowed its input.
    public long vertexCount() {
        requireUsable();
        return blend2d.pathSize(path);
    }

    /// Whether the path holds no commands at all.
    public boolean isEmpty() {
        return vertexCount() == 0;
    }

    /// Whether this path has been released.
    public boolean isReleased() {
        return closed;
    }

    /// Releases Blend2D's side of the path. Idempotent.
    ///
    /// SVG's `Z` is [#closeSubPath()], not this.
    @Override
    public void close() {
        if (closed) {
            return;
        }
        requireOwner();
        closed = true;
        try {
            blend2d.pathDestroy(path);
        } finally {
            arena.close();
        }
    }

    MemorySegment pointer() {
        requireUsable();
        return path;
    }

    private void requireUsable() {
        requireOwner();
        if (closed) {
            throw new IllegalStateException("this BlendPath has been released");
        }
    }

    private void requireOwner() {
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException(
                    "a BlendPath belongs to the thread that created it, and this is not it");
        }
    }

    @Override
    public String toString() {
        return "BlendPath[" + (closed ? "released" : vertexCount() + " vertices") + "]";
    }
}
