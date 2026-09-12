package io.github.digitalsmile.goldberry.paint.geom;

import io.github.digitalsmile.goldberry.paint.Path;

/// Turns a [Path]'s curves into straight segments.
///
/// ## Why the toolkit needs this at all
///
/// Blend2D flattens curves itself, far better than this does, and nothing in the
/// paint pipeline ever asked for a flattened path — until dashing did. A dash is
/// a statement about **arc length**, and the arc length of a cubic has no closed
/// form: the only way to walk four pixels along a curve is to walk a polyline
/// that approximates it. So [Dasher] flattens first, and this is that step,
/// separated because it is worth testing on its own (ADR-0278).
///
/// ## The tolerance
///
/// [#TOLERANCE] is the greatest distance a segment is allowed to stray from the
/// curve it replaces, in logical pixels. At a tenth of a pixel the difference is
/// below the rasterizer's own antialiasing on a 1× display and a quarter of that
/// at 2×, which is why the number is not a parameter on the drawing path: a
/// caller choosing it would be choosing between two invisible options and one
/// slow one.
///
/// The segment count comes from a bound on the curve's second derivative rather
/// than from its control-polygon length. Subdividing into `n` pieces leaves an
/// error of at most `max|B''| / (8n²)`, so `n` is the square root of that over
/// the tolerance — which spends segments where a curve actually bends, and gives
/// a nearly-straight cubic the one segment it deserves.
public final class Flattener {

    /// The greatest distance, in logical pixels, a flattened segment may stray
    /// from the curve it replaces.
    public static final double TOLERANCE = 0.1;

    /// A ceiling on the segments one curve may become.
    ///
    /// Reached only by something already pathological — a cubic whose controls
    /// are thousands of pixels out — and it is here so that a NaN-free but absurd
    /// path costs a bounded amount of work instead of hanging the UI thread.
    private static final int MAX_SEGMENTS = 1000;

    private Flattener() {}

    /// `path` with every curve replaced by straight segments, at [#TOLERANCE].
    public static Path flatten(Path path) {
        return flatten(path, TOLERANCE);
    }

    /// `path` with every curve replaced by straight segments.
    ///
    /// Moves, lines and closes come through untouched, so a path that was
    /// already straight comes back equal to itself.
    ///
    /// @param tolerance the greatest distance a segment may stray from the curve
    ///        it replaces, in logical pixels
    /// @throws IllegalArgumentException if `tolerance` is not finite and positive
    public static Path flatten(Path path, double tolerance) {
        if (!Double.isFinite(tolerance) || tolerance <= 0) {
            throw new IllegalArgumentException("a flattening tolerance must be finite and positive, not " + tolerance);
        }

        var builder = Path.builder();
        var x = 0d;
        var y = 0d;
        var startX = 0d;
        var startY = 0d;

        for (var segment : path.segments()) {
            switch (segment) {
                case Path.Segment.MoveTo move -> {
                    builder.moveTo(move.x(), move.y());
                    x = move.x();
                    y = move.y();
                    startX = x;
                    startY = y;
                }
                case Path.Segment.LineTo line -> {
                    builder.lineTo(line.x(), line.y());
                    x = line.x();
                    y = line.y();
                }
                case Path.Segment.QuadTo quad -> {
                    quadTo(builder, x, y, quad, tolerance);
                    x = quad.x();
                    y = quad.y();
                }
                case Path.Segment.CubicTo cubic -> {
                    cubicTo(builder, x, y, cubic, tolerance);
                    x = cubic.x();
                    y = cubic.y();
                }
                case Path.Segment.ArcTo arc -> {
                    arcTo(builder, x, y, arc, tolerance);
                    x = arc.x();
                    y = arc.y();
                }
                case Path.Segment.Close ignored -> {
                    builder.close();
                    x = startX;
                    y = startY;
                }
            }
        }
        return builder.build();
    }

    /// A quadratic, whose second derivative is the constant `2(p0 - 2c + p1)`.
    private static void quadTo(Path.Builder builder, double x0, double y0, Path.Segment.QuadTo quad, double tolerance) {

        var ddx = 2 * (x0 - 2 * quad.cx() + quad.x());
        var ddy = 2 * (y0 - 2 * quad.cy() + quad.y());
        var count = segmentsFor(Math.hypot(ddx, ddy), tolerance);

        for (var step = 1; step <= count; step++) {
            var t = (double) step / count;
            var u = 1 - t;
            builder.lineTo(
                    u * u * x0 + 2 * u * t * quad.cx() + t * t * quad.x(),
                    u * u * y0 + 2 * u * t * quad.cy() + t * t * quad.y());
        }
    }

    /// A cubic, whose second derivative is largest at one end or the other.
    private static void cubicTo(
            Path.Builder builder, double x0, double y0, Path.Segment.CubicTo cubic, double tolerance) {

        var startX = 6 * (x0 - 2 * cubic.c1x() + cubic.c2x());
        var startY = 6 * (y0 - 2 * cubic.c1y() + cubic.c2y());
        var endX = 6 * (cubic.c1x() - 2 * cubic.c2x() + cubic.x());
        var endY = 6 * (cubic.c1y() - 2 * cubic.c2y() + cubic.y());
        var bound = Math.max(Math.hypot(startX, startY), Math.hypot(endX, endY));
        var count = segmentsFor(bound, tolerance);

        for (var step = 1; step <= count; step++) {
            var t = (double) step / count;
            var u = 1 - t;
            builder.lineTo(
                    u * u * u * x0 + 3 * u * u * t * cubic.c1x() + 3 * u * t * t * cubic.c2x() + t * t * t * cubic.x(),
                    u * u * u * y0 + 3 * u * u * t * cubic.c1y() + 3 * u * t * t * cubic.c2y() + t * t * t * cubic.y());
        }
    }

    /// An SVG elliptic arc, through the endpoint-to-centre conversion.
    ///
    /// The arithmetic is SVG 1.1's implementation notes, F.6.5 and F.6.6, and it
    /// is here rather than in [Path] because a path *stores* an arc and only a
    /// flattener has to know where its middle is. Both of SVG's degenerate cases
    /// are a straight line: a zero radius, and coincident endpoints.
    private static void arcTo(Path.Builder builder, double x0, double y0, Path.Segment.ArcTo arc, double tolerance) {

        var rx = Math.abs(arc.rx());
        var ry = Math.abs(arc.ry());
        if (rx == 0 || ry == 0 || (x0 == arc.x() && y0 == arc.y())) {
            builder.lineTo(arc.x(), arc.y());
            return;
        }

        var cos = Math.cos(arc.rotation());
        var sin = Math.sin(arc.rotation());
        var dx = (x0 - arc.x()) / 2;
        var dy = (y0 - arc.y()) / 2;
        var x1 = cos * dx + sin * dy;
        var y1 = -sin * dx + cos * dy;

        // F.6.6: radii too small to reach between the endpoints are scaled up
        // until they exactly do, rather than being refused.
        var lambda = (x1 * x1) / (rx * rx) + (y1 * y1) / (ry * ry);
        if (lambda > 1) {
            var scale = Math.sqrt(lambda);
            rx *= scale;
            ry *= scale;
        }

        var numerator = rx * rx * ry * ry - rx * rx * y1 * y1 - ry * ry * x1 * x1;
        var denominator = rx * rx * y1 * y1 + ry * ry * x1 * x1;
        // Clamped at zero: with the radii fitted above this is non-negative in
        // exact arithmetic, and a hair below it in floating point.
        var factor = Math.sqrt(Math.max(0, numerator / denominator));
        if (arc.largeArc() == arc.sweep()) {
            factor = -factor;
        }
        var cx1 = factor * rx * y1 / ry;
        var cy1 = -factor * ry * x1 / rx;
        var cx = cos * cx1 - sin * cy1 + (x0 + arc.x()) / 2;
        var cy = sin * cx1 + cos * cy1 + (y0 + arc.y()) / 2;

        var startAngle = Math.atan2((y1 - cy1) / ry, (x1 - cx1) / rx);
        var endAngle = Math.atan2((-y1 - cy1) / ry, (-x1 - cx1) / rx);
        var sweep = endAngle - startAngle;
        if (arc.sweep() && sweep < 0) {
            sweep += 2 * Math.PI;
        } else if (!arc.sweep() && sweep > 0) {
            sweep -= 2 * Math.PI;
        }

        // The sagitta of one step: a chord subtending `a` on a circle of radius
        // `r` strays `r(1 - cos(a/2))` from it, so the step that just meets the
        // tolerance is `2 acos(1 - tol/r)`. The larger radius is the one that
        // bounds the error on an ellipse.
        var radius = Math.max(rx, ry);
        var step = tolerance >= radius ? Math.PI : 2 * Math.acos(1 - tolerance / radius);
        var count = Math.clamp((int) Math.ceil(Math.abs(sweep) / step), 1, MAX_SEGMENTS);

        for (var index = 1; index <= count; index++) {
            var angle = startAngle + sweep * index / count;
            var ax = rx * Math.cos(angle);
            var ay = ry * Math.sin(angle);
            builder.lineTo(cx + cos * ax - sin * ay, cy + sin * ax + cos * ay);
        }
    }

    /// How many straight pieces a curve bounded by `secondDerivative` needs.
    ///
    /// Subdividing into `n` leaves an error of at most `max|B''| / (8n²)`, so
    /// this is that inequality solved for `n`.
    private static int segmentsFor(double secondDerivative, double tolerance) {
        if (!(secondDerivative > 0)) {
            // A curve whose controls are collinear with its ends is a line, and
            // one segment draws it exactly.
            return 1;
        }
        var count = (int) Math.ceil(Math.sqrt(secondDerivative / (8 * tolerance)));
        return Math.clamp(count, 1, MAX_SEGMENTS);
    }
}
