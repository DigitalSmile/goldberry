package dev.goldberry.paint.geom;

import java.util.Arrays;
import java.util.Objects;

import dev.goldberry.paint.Path;

/// The intersection of two convex polygons — what a fill rule needs when one
/// shape has to be cut out of another it may not lie inside.
///
/// The rasterizer clips to rectangles and to nothing else, so a hole that
/// pokes out of the shape it is cut from cannot be clipped back into it at
/// fill time. Under the even-odd rule the part of the hole outside the shape
/// would be filled instead of cut. Intersecting the hole with the shape first
/// leaves a hole that lies inside, which even-odd then cuts cleanly.
///
/// A rounded rectangle is convex, which is the only shape this is asked about:
/// an inner shadow's hole against the box's padding box. Sutherland–Hodgman
/// over the flattened outlines is exact for convex shapes and costs a few
/// dozen points per call.
///
/// Polygons are flat arrays of `x, y` pairs, closed implicitly.
///
/// Read more: [Styling](https://goldberry.dev/docs/guide/styling.html#border-outline-and-shadow).
public final class ConvexClip {

    private static final double EPSILON = 1e-12;

    private ConvexClip() {}

    /// The vertices of the first sub-path of `path`, flattened to `tolerance`.
    ///
    /// A point equal to the one before it is dropped, so a closed outline does
    /// not repeat its first point at the end.
    public static double[] polygon(Path path, double tolerance) {
        Objects.requireNonNull(path, "path");
        var points = new double[16];
        var count = 0;
        var started = false;
        for (var segment : Flattener.flatten(path, tolerance).segments()) {
            double x;
            double y;
            switch (segment) {
                case Path.Segment.MoveTo move -> {
                    if (started) {
                        return open(points, count);
                    }
                    started = true;
                    x = move.x();
                    y = move.y();
                }
                case Path.Segment.LineTo line -> {
                    x = line.x();
                    y = line.y();
                }
                case Path.Segment.Close _ -> {
                    continue;
                }
                case Path.Segment.QuadTo quad -> throw unflattened(quad);
                case Path.Segment.CubicTo cubic -> throw unflattened(cubic);
                case Path.Segment.ArcTo arc -> throw unflattened(arc);
            }
            if (count >= 2 && points[count - 2] == x && points[count - 1] == y) {
                continue;
            }
            if (count == points.length) {
                points = Arrays.copyOf(points, count * 2);
            }
            points[count++] = x;
            points[count++] = y;
        }
        return open(points, count);
    }

    /// The points without a last one that repeats the first: a polygon is
    /// closed implicitly, and the repeat would be an edge of no length.
    private static double[] open(double[] points, int count) {
        if (count >= 4 && points[count - 2] == points[0] && points[count - 1] == points[1]) {
            count -= 2;
        }
        return trimmed(points, count);
    }

    /// `subject` clipped to `clip`, both convex; empty when they do not meet.
    ///
    /// Either winding is accepted for `clip`: which side of an edge is inside
    /// is read off the polygon's own signed area.
    public static double[] intersect(double[] subject, double[] clip) {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(clip, "clip");
        var orientation = Math.signum(signedArea(clip));
        if (orientation == 0 || subject.length < 6) {
            return new double[0];
        }
        var output = subject;
        var edges = clip.length / 2;
        for (var e = 0; e < edges && output.length >= 6; e++) {
            var ax = clip[e * 2];
            var ay = clip[e * 2 + 1];
            var bx = clip[((e + 1) % edges) * 2];
            var by = clip[((e + 1) % edges) * 2 + 1];
            if (Math.abs(bx - ax) < EPSILON && Math.abs(by - ay) < EPSILON) {
                continue;
            }
            output = clipAgainst(output, ax, ay, bx, by, orientation);
        }
        return output.length >= 6 ? output : new double[0];
    }

    /// A closed path through `polygon`'s points, or [Path#EMPTY] for fewer
    /// than three.
    public static Path toPath(double[] polygon) {
        Objects.requireNonNull(polygon, "polygon");
        if (polygon.length < 6) {
            return Path.EMPTY;
        }
        var builder = Path.builder().moveTo(polygon[0], polygon[1]);
        for (var i = 2; i < polygon.length; i += 2) {
            builder.lineTo(polygon[i], polygon[i + 1]);
        }
        return builder.close().build();
    }

    /// Twice the polygon's signed area; positive when it winds clockwise on a
    /// screen whose y axis points down.
    static double signedArea(double[] polygon) {
        var area = 0.0;
        var n = polygon.length / 2;
        for (var i = 0; i < n; i++) {
            var j = (i + 1) % n;
            area += polygon[i * 2] * polygon[j * 2 + 1] - polygon[j * 2] * polygon[i * 2 + 1];
        }
        return area;
    }

    /// One Sutherland–Hodgman pass: `input` cut by the line through `a` and
    /// `b`, keeping the side `orientation` says is inside.
    private static double[] clipAgainst(
            double[] input, double ax, double ay, double bx, double by, double orientation) {
        var out = new double[input.length + 4];
        var count = 0;
        var n = input.length / 2;
        var px = input[(n - 1) * 2];
        var py = input[(n - 1) * 2 + 1];
        var pSide = side(ax, ay, bx, by, px, py, orientation);
        for (var i = 0; i < n; i++) {
            var qx = input[i * 2];
            var qy = input[i * 2 + 1];
            var qSide = side(ax, ay, bx, by, qx, qy, orientation);
            if ((pSide >= 0) != (qSide >= 0)) {
                // The edge crosses the line: keep where.
                var t = pSide / (pSide - qSide);
                if (count + 2 > out.length) {
                    out = Arrays.copyOf(out, out.length * 2);
                }
                out[count++] = px + (qx - px) * t;
                out[count++] = py + (qy - py) * t;
            }
            if (qSide >= 0) {
                if (count + 2 > out.length) {
                    out = Arrays.copyOf(out, out.length * 2);
                }
                out[count++] = qx;
                out[count++] = qy;
            }
            px = qx;
            py = qy;
            pSide = qSide;
        }
        return trimmed(out, count);
    }

    /// How far inside the edge from `a` to `b` the point is, scaled: positive
    /// inside, negative outside, zero on it.
    private static double side(double ax, double ay, double bx, double by, double px, double py, double orientation) {
        return orientation * ((bx - ax) * (py - ay) - (by - ay) * (px - ax));
    }

    private static double[] trimmed(double[] points, int count) {
        return count == points.length ? points : Arrays.copyOf(points, count);
    }

    private static IllegalStateException unflattened(Path.Segment segment) {
        return new IllegalStateException("a flattened path holds only moves, lines and closes, and this is a "
                + segment.getClass().getSimpleName());
    }
}
