package dev.goldberry.image.lottie;

import java.util.List;

import org.jspecify.annotations.Nullable;

/// A piece of geometry's outline at a moment, as contours: where each kind of
/// shape starts and which way it runs, as After Effects draws them.
///
/// The start and the direction are invisible to a fill and decide everything
/// for a trim, which is why a rectangle starts at its top right corner and runs
/// clockwise rather than wherever was convenient.
final class Shapes {

    private Shapes() {}

    /// `geometry`'s contours at `frame`.
    static List<Contour> outline(Shape.Geometry geometry, double frame) {
        var contour = switch (geometry) {
            case Shape.Rect rect -> rect(rect, frame);
            case Shape.Ellipse ellipse -> ellipse(ellipse, frame);
            case Shape.Star star -> star(star, frame);
            case Shape.PathShape(var path, var _) -> {
                var bezier = path.at(frame);
                yield bezier.size() == 0 ? null : Contour.of(bezier);
            }
        };
        return contour == null ? List.of() : List.of(contour);
    }

    private static Contour rect(Shape.Rect rect, double frame) {
        var p = rect.position().at(frame);
        var s = rect.size().at(frame);
        var cx = p.length > 0 ? p[0] : 0;
        var cy = p.length > 1 ? p[1] : 0;
        var w = Math.abs(s.length > 0 ? s[0] : 0);
        var h = Math.abs(s.length > 1 ? s[1] : 0);
        var left = cx - w / 2;
        var right = cx + w / 2;
        var top = cy - h / 2;
        var bottom = cy + h / 2;
        var r = Math.min(Math.max(0, rect.roundness().scalar(frame)), Math.min(w, h) / 2);
        Contour contour;
        if (r <= 0) {
            contour = Contour.at(right, top)
                    .lineTo(right, bottom)
                    .lineTo(left, bottom)
                    .lineTo(left, top)
                    .lineTo(right, top)
                    .close();
        } else {
            var k = r * Contour.KAPPA;
            contour = Contour.at(right, top + r)
                    .lineTo(right, bottom - r)
                    .cubicTo(right, bottom - r + k, right - r + k, bottom, right - r, bottom)
                    .lineTo(left + r, bottom)
                    .cubicTo(left + r - k, bottom, left, bottom - r + k, left, bottom - r)
                    .lineTo(left, top + r)
                    .cubicTo(left, top + r - k, left + r - k, top, left + r, top)
                    .lineTo(right - r, top)
                    .cubicTo(right - r + k, top, right, top + r - k, right, top + r)
                    .close();
        }
        return rect.reversed() ? contour.reversed() : contour;
    }

    private static Contour ellipse(Shape.Ellipse ellipse, double frame) {
        var p = ellipse.position().at(frame);
        var s = ellipse.size().at(frame);
        var cx = p.length > 0 ? p[0] : 0;
        var cy = p.length > 1 ? p[1] : 0;
        var rx = Math.abs(s.length > 0 ? s[0] : 0) / 2;
        var ry = Math.abs(s.length > 1 ? s[1] : 0) / 2;
        var kx = rx * Contour.KAPPA;
        var ky = ry * Contour.KAPPA;
        // From the top, clockwise.
        var contour = Contour.at(cx, cy - ry)
                .cubicTo(cx + kx, cy - ry, cx + rx, cy - ky, cx + rx, cy)
                .cubicTo(cx + rx, cy + ky, cx + kx, cy + ry, cx, cy + ry)
                .cubicTo(cx - kx, cy + ry, cx - rx, cy + ky, cx - rx, cy)
                .cubicTo(cx - rx, cy - ky, cx - kx, cy - ry, cx, cy - ry)
                .close();
        return ellipse.reversed() ? contour.reversed() : contour;
    }

    /// A star alternates outer and inner points; a polygon has only outer ones.
    /// A point's roundness pulls its tangents along the circle it sits on.
    private static @Nullable Contour star(Shape.Star star, double frame) {
        var points = (int) Math.floor(star.points().scalar(frame));
        if (points < 2) {
            return null;
        }
        var count = star.star() ? points * 2 : points;
        var p = star.position().at(frame);
        var cx = p.length > 0 ? p[0] : 0;
        var cy = p.length > 1 ? p[1] : 0;
        var outer = star.outerRadius().scalar(frame);
        var inner = star.innerRadius().scalar(frame);
        var outerRound = star.outerRoundness().scalar(frame) / 100;
        var innerRound = star.innerRoundness().scalar(frame) / 100;
        var direction = star.reversed() ? -1 : 1;
        var step = 2 * Math.PI / count * direction;
        var angle = -Math.PI / 2 + Math.toRadians(star.rotation().scalar(frame));
        // The length a point's tangent is scaled from: the arc between two
        // neighbouring points, halved again for a polygon's wider spacing.
        var outerSegment = 2 * Math.PI * outer / (star.star() ? count * 2 : count * 4);
        var innerSegment = 2 * Math.PI * inner / (count * 2);
        var xs = new double[count];
        var ys = new double[count];
        var ins = new double[count * 2];
        var outs = new double[count * 2];
        for (var i = 0; i < count; i++) {
            var isOuter = !star.star() || i % 2 == 0;
            var radius = isOuter ? outer : inner;
            var round = isOuter ? outerRound : innerRound;
            var segment = isOuter ? outerSegment : innerSegment;
            var x = radius * Math.cos(angle);
            var y = radius * Math.sin(angle);
            var length = Math.hypot(x, y);
            var tx = length == 0 ? 0 : y / length;
            var ty = length == 0 ? 0 : -x / length;
            xs[i] = cx + x;
            ys[i] = cy + y;
            var reach = segment * round * direction;
            outs[i * 2] = xs[i] - tx * reach;
            outs[i * 2 + 1] = ys[i] - ty * reach;
            ins[i * 2] = xs[i] + tx * reach;
            ins[i * 2 + 1] = ys[i] + ty * reach;
            angle += step;
        }
        var contour = Contour.at(xs[0], ys[0]);
        for (var i = 0; i < count; i++) {
            var next = (i + 1) % count;
            contour.cubicTo(outs[i * 2], outs[i * 2 + 1], ins[next * 2], ins[next * 2 + 1], xs[next], ys[next]);
        }
        return contour.close();
    }
}
