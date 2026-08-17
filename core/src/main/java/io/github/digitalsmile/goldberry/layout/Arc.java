package io.github.digitalsmile.goldberry.layout;

import io.github.digitalsmile.goldberry.natives.blend2d.BlendPath;

/// A circular arc, as a Blend2D path.
///
/// The sibling of [RoundRect] and it exists for the same reason, which
/// [ADR-0064](../../../../../../book/src/adr/0064-a-rounded-rectangle-is-four-cubics.md)
/// argues in full: Blend2D has an arc call, using it would put a symbol on the
/// export list, and that list has caught the same class of local-symbol bug three
/// times — each answered only by a CI run across four targets. `bl_path_cubic_to`
/// is already exported and already stroked by 1544 Lucide icons, so an arc built
/// from cubics arrives on every platform at once.
///
/// A rounded rectangle needed four quarter-circles at fixed angles. A spinner
/// needs three quarters starting anywhere, so this is the general form: the same
/// KAPPA, applied per segment to whatever is left of the sweep.
final class Arc {

    /// `4 * (sqrt(2) - 1) / 3` — the control-point distance that best fits a
    /// **quarter** circle, and the reason the sweep below is cut into quarters
    /// rather than drawn as one curve. The approximation is good to about one
    /// part in 10,000 of the radius over 90° and an order of magnitude worse over
    /// 180°, which is the difference between invisible and visible on a 16px ring.
    private static final double KAPPA = 0.5522847498307933;

    private Arc() {
    }

    /// Appends an arc of `radius` about `(cx, cy)`, running `sweep` radians from
    /// `start`, to `path`.
    ///
    /// Angles are in radians, clockwise, with zero pointing right — Blend2D's
    /// coordinates have y downwards, so this is the direction a clock's hands go
    /// on screen.
    static void addTo(BlendPath path, double cx, double cy, double radius,
            double start, double sweep) {

        if (radius <= 0 || sweep == 0) {
            return;
        }
        path.moveTo(cx + radius * Math.cos(start), cy + radius * Math.sin(start));

        // Whole quarters, then whatever is left. A segment is never more than 90°,
        // which is what keeps the cubic within a ten-thousandth of the circle.
        var remaining = sweep;
        var angle = start;
        var direction = Math.signum(sweep);
        while (Math.abs(remaining) > 1e-9) {
            var segment = direction * Math.min(Math.abs(remaining), Math.PI / 2);
            addSegment(path, cx, cy, radius, angle, segment);
            angle += segment;
            remaining -= segment;
        }
    }

    /// One segment of at most a quarter circle.
    ///
    /// The control points sit on the tangents at each end, `k` of the radius
    /// along them — where `k` is KAPPA scaled to the segment, because KAPPA is the
    /// answer for 90° and a shorter arc needs a proportionally shorter handle.
    private static void addSegment(BlendPath path, double cx, double cy, double radius,
            double start, double sweep) {

        var end = start + sweep;
        var k = KAPPA * radius * (sweep / (Math.PI / 2));

        var x1 = cx + radius * Math.cos(start);
        var y1 = cy + radius * Math.sin(start);
        var x2 = cx + radius * Math.cos(end);
        var y2 = cy + radius * Math.sin(end);

        path.cubicTo(
                x1 - k * Math.sin(start), y1 + k * Math.cos(start),
                x2 + k * Math.sin(end), y2 - k * Math.cos(end),
                x2, y2);
    }
}
