package io.github.digitalsmile.goldberry.layout;

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
final class RoundRect {

    /// `4 * (sqrt(2) - 1) / 3` — the control-point distance that best fits a
    /// quarter circle.
    private static final double KAPPA = 0.5522847498307933;

    private RoundRect() {
    }

    /// Appends the outline of `(x, y, width, height)` with corner radius `radius`
    /// to `path`, clockwise from the top-left corner's end.
    ///
    /// The radius is clamped to half the shorter side, which is what makes
    /// `border-radius: 9999px` a pill rather than a rendering error — CSS's own
    /// rule, and the one the design system's `full` radius relies on.
    static void addTo(BlendPath path, double x, double y, double width, double height,
            double radius) {

        var r = Math.min(radius, Math.min(width, height) / 2);
        if (r <= 0) {
            path.moveTo(x, y);
            path.lineTo(x + width, y);
            path.lineTo(x + width, y + height);
            path.lineTo(x, y + height);
            path.closeSubPath();
            return;
        }

        var right = x + width;
        var bottom = y + height;
        var c = r * KAPPA;

        path.moveTo(x + r, y);
        path.lineTo(right - r, y);
        path.cubicTo(right - r + c, y, right, y + r - c, right, y + r);
        path.lineTo(right, bottom - r);
        path.cubicTo(right, bottom - r + c, right - r + c, bottom, right - r, bottom);
        path.lineTo(x + r, bottom);
        path.cubicTo(x + r - c, bottom, x, bottom - r + c, x, bottom - r);
        path.lineTo(x, y + r);
        path.cubicTo(x, y + r - c, x + r - c, y, x + r, y);
        path.closeSubPath();
    }
}
