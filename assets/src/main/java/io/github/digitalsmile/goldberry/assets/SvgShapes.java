package io.github.digitalsmile.goldberry.assets;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/// Converts SVG's basic shapes into path data.
///
/// SVG has seven ways to describe an outline and Blend2D has one: a path. The
/// conversions below are the ones the SVG specification itself defines in its
/// "basic shapes" chapter — this is transcription, not interpretation, which is
/// why each is a few lines and why they can be checked against numbers.
///
/// It handles exactly what Lucide uses. Lucide is uniform by construction: a
/// 24×24 viewBox of 2px round strokes, no transforms, no fills, no gradients, no
/// nested groups. An icon set that used any of those would need a real SVG
/// renderer, and [IconCompiler] refuses rather than emitting an icon that is
/// almost right.
public final class SvgShapes {

    private SvgShapes() {
    }

    /// `<line x1 y1 x2 y2>` — a single segment.
    public static String line(double x1, double y1, double x2, double y2) {
        return "M" + n(x1) + " " + n(y1) + "L" + n(x2) + " " + n(y2);
    }

    /// `<polyline points>` — an open run of segments.
    ///
    /// An odd trailing coordinate is dropped, which is what the SVG
    /// specification says to do with a malformed point list.
    public static String polyline(String points) {
        return fromPoints(points, false);
    }

    /// `<polygon points>` — the same, closed.
    public static String polygon(String points) {
        return fromPoints(points, true);
    }

    /// `<circle cx cy r>`.
    public static String circle(double cx, double cy, double r) {
        return ellipse(cx, cy, r, r);
    }

    /// `<ellipse cx cy rx ry>`.
    ///
    /// Two half-arcs, not one. A single arc from a point back to the same point
    /// is a no-op in SVG — the arc command draws nothing when its endpoints
    /// coincide — so a closed ellipse has to be drawn in two halves. Getting this
    /// wrong produces an icon that is silently missing all its circles.
    public static String ellipse(double cx, double cy, double rx, double ry) {
        if (rx <= 0 || ry <= 0) {
            // SVG says a zero or negative radius disables rendering of the shape.
            return "";
        }
        var left = n(cx - rx);
        var right = n(cx + rx);
        var middle = n(cy);
        var radii = n(rx) + " " + n(ry);
        return "M" + left + " " + middle
                + "A" + radii + " 0 1 0 " + right + " " + middle
                + "A" + radii + " 0 1 0 " + left + " " + middle
                + "Z";
    }

    /// `<rect x y width height rx ry>`, square-cornered or rounded.
    ///
    /// SVG's rule for the radii is that an omitted one mirrors the other, and
    /// that each is clamped to half the corresponding side — a radius larger than
    /// the rectangle is legal input and produces a stadium, not an error.
    ///
    /// @param rx horizontal corner radius, or a negative number for "not given"
    /// @param ry vertical corner radius, or a negative number for "not given"
    public static String rect(double x, double y, double width, double height, double rx, double ry) {
        if (width <= 0 || height <= 0) {
            return "";
        }

        var resolvedX = rx < 0 ? ry : rx;
        var resolvedY = ry < 0 ? rx : ry;
        if (resolvedX < 0) {
            resolvedX = 0;
        }
        if (resolvedY < 0) {
            resolvedY = 0;
        }
        resolvedX = Math.min(resolvedX, width / 2);
        resolvedY = Math.min(resolvedY, height / 2);

        if (resolvedX == 0 || resolvedY == 0) {
            return "M" + n(x) + " " + n(y)
                    + "H" + n(x + width)
                    + "V" + n(y + height)
                    + "H" + n(x)
                    + "Z";
        }

        var radii = n(resolvedX) + " " + n(resolvedY);
        return "M" + n(x + resolvedX) + " " + n(y)
                + "H" + n(x + width - resolvedX)
                + "A" + radii + " 0 0 1 " + n(x + width) + " " + n(y + resolvedY)
                + "V" + n(y + height - resolvedY)
                + "A" + radii + " 0 0 1 " + n(x + width - resolvedX) + " " + n(y + height)
                + "H" + n(x + resolvedX)
                + "A" + radii + " 0 0 1 " + n(x) + " " + n(y + height - resolvedY)
                + "V" + n(y + resolvedY)
                + "A" + radii + " 0 0 1 " + n(x + resolvedX) + " " + n(y)
                + "Z";
    }

    /// Parses an SVG point list — numbers separated by whitespace, commas, or
    /// both, in any combination.
    public static List<double[]> points(String points) {
        var numbers = new ArrayList<Double>();
        for (var token : points.trim().split("[\\s,]+")) {
            if (!token.isEmpty()) {
                numbers.add(Double.parseDouble(token));
            }
        }
        var pairs = new ArrayList<double[]>(numbers.size() / 2);
        for (var i = 0; i + 1 < numbers.size(); i += 2) {
            pairs.add(new double[] {numbers.get(i), numbers.get(i + 1)});
        }
        return pairs;
    }

    private static String fromPoints(String points, boolean close) {
        var pairs = points(points);
        if (pairs.isEmpty()) {
            return "";
        }
        var out = new StringBuilder();
        for (var i = 0; i < pairs.size(); i++) {
            out.append(i == 0 ? "M" : "L")
                    .append(n(pairs.get(i)[0]))
                    .append(' ')
                    .append(n(pairs.get(i)[1]));
        }
        return close ? out.append('Z').toString() : out.toString();
    }

    /// Formats a coordinate as short as it can be without losing it.
    ///
    /// Whole numbers lose their `.0`, which matters more than it looks: the
    /// icon table is 1544 lines of coordinates, and Lucide's are nearly all
    /// integers. `Locale.ROOT` because a decimal comma would turn one coordinate
    /// into two.
    static String n(double value) {
        if (value == Math.rint(value) && !Double.isInfinite(value)) {
            return Long.toString((long) value);
        }
        return String.format(Locale.ROOT, "%s", value);
    }
}
