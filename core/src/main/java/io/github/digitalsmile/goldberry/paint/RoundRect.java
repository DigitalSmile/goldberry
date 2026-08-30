package io.github.digitalsmile.goldberry.paint;

import io.github.digitalsmile.goldberry.css.Corners;
import io.github.digitalsmile.goldberry.natives.blend2d.BlendPath;

/// A rounded rectangle, as a Blend2D path.
///
/// ## Why this is built from cubics rather than bound
///
/// Blend2D has a round-rect geometry call, and using it would mean adding a
/// symbol to the export list. That list has caught the same class of bug three
/// times — `--exclude-libs`, then Blend2D's `BL_STATIC`, then HarfBuzz's bare
/// `HB_EXTERN`, each of which linked a symbol in and left it *local* — and each
/// time the answer only arrived from a CI run on all four targets. A rounded
/// rectangle is four arcs, `bl_path_cubic_to` is already exported and already
/// tested by 1544 Lucide icons, and a curve drawn from a control point costs
/// nothing measurable next to filling the area it encloses. So: no new symbols,
/// no new export branch, and the corner arrives on every platform at once.
///
/// ## The magic number
///
/// A circular arc cannot be written exactly as a cubic Bézier, and 0.5522847498
/// is the ratio that minimises the error of the standard four-segment
/// approximation — about one part in 10,000 of the radius, which at a 12px corner
/// is a thousandth of a pixel and well inside the golden images' tolerance
/// ([ADR-0050]).
///
/// ## Why it is public
///
/// It was package-private while the only caller was [BoxPainter], and stopped
/// being so when a `canvas` painter needed one: a chart's readout is a rounded
/// rectangle drawn by a widget, and the alternative was a second derivation of
/// the four cubics above in `:widgets` — two implementations of a corner that
/// must agree, which is the whole thing this class exists to prevent. Public
/// takes nothing new across the native boundary: it is the already-exported
/// `bl_path_cubic_to` and arithmetic.
public final class RoundRect {

    /// `4 * (sqrt(2) - 1) / 3` — the control-point distance that best fits a
    /// quarter circle.
    private static final double KAPPA = 0.5522847498307933;

    private RoundRect() {}

    /// Appends the outline of `(x, y, width, height)` with corner radius `radius`
    /// to `path`, clockwise from the top-left corner's end.
    ///
    /// The radius is clamped to half the shorter side, which is what makes
    /// `border-radius: 9999px` a pill rather than a rendering error — CSS's own
    /// rule, and the one the design system's `full` radius relies on.
    public static void addTo(BlendPath path, double x, double y, double width, double height, double radius) {

        addTo(path, x, y, width, height, Corners.all(radius));
    }

    /// The same, with **a radius per corner** — CSS's `border-radius: 7px 7px 0 0`.
    ///
    /// One drawing for both, rather than a rounded path and a square one: the
    /// uniform case emits exactly the point sequence the single-radius version
    /// always did, which is what says the four corners did not move
    /// (ADR-0216). A square corner is a `lineTo` into the corner point and no
    /// cubic at all — a degenerate zero-length curve would be handed to the
    /// rasterizer on every square box otherwise.
    ///
    /// The corners are fitted to the box first, so a pair that together overrun
    /// an edge is scaled down in proportion rather than crossing over —
    /// [Corners#fittedTo].
    public static void addTo(BlendPath path, double x, double y, double width, double height, Corners corners) {

        var fitted = corners.fittedTo(width, height);
        if (fitted.isSquare()) {
            path.moveTo(x, y);
            path.lineTo(x + width, y);
            path.lineTo(x + width, y + height);
            path.lineTo(x, y + height);
            path.closeSubPath();
            return;
        }

        var right = x + width;
        var bottom = y + height;
        var topLeft = fitted.topLeft();
        var topRight = fitted.topRight();
        var bottomRight = fitted.bottomRight();
        var bottomLeft = fitted.bottomLeft();

        path.moveTo(x + topLeft, y);
        path.lineTo(right - topRight, y);
        if (topRight > 0) {
            var c = topRight * KAPPA;
            path.cubicTo(right - topRight + c, y, right, y + topRight - c, right, y + topRight);
        }
        path.lineTo(right, bottom - bottomRight);
        if (bottomRight > 0) {
            var c = bottomRight * KAPPA;
            path.cubicTo(right, bottom - bottomRight + c, right - bottomRight + c, bottom, right - bottomRight, bottom);
        }
        path.lineTo(x + bottomLeft, bottom);
        if (bottomLeft > 0) {
            var c = bottomLeft * KAPPA;
            path.cubicTo(x + bottomLeft - c, bottom, x, bottom - bottomLeft + c, x, bottom - bottomLeft);
        }
        path.lineTo(x, y + topLeft);
        if (topLeft > 0) {
            var c = topLeft * KAPPA;
            path.cubicTo(x, y + topLeft - c, x + topLeft - c, y, x + topLeft, y);
        }
        path.closeSubPath();
    }
}
