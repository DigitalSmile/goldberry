package dev.goldberry.paint.geom;

import java.util.Objects;

import dev.goldberry.paint.Path;

/// Whether a point is inside a path's fill, by the rule the fill uses.
///
/// The path is flattened first ([Flattener], at its tolerance of a hundredth
/// of a unit), and every sub-path is closed back to its start, as a fill is.
/// Then a ray from the point to the right counts the edges it crosses: an odd
/// count is inside under `evenodd`, and a non-zero sum of their directions is
/// inside under `nonzero`.
///
/// A point exactly on an edge counts by the half-open rule the rasteriser
/// uses: a vertex at the ray's height belongs to the edge going up from it.
public final class PathHitTest {

    private PathHitTest() {}

    /// Whether `(x, y)` is inside `path`'s fill.
    ///
    /// @param evenOdd whether the fill is `evenodd`, rather than `nonzero`
    public static boolean contains(Path path, double x, double y, boolean evenOdd) {
        Objects.requireNonNull(path, "path");
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            return false;
        }
        var counter = new Counter(x, y);
        double startX = 0;
        double startY = 0;
        double lastX = 0;
        double lastY = 0;
        var open = false;
        for (var segment : Flattener.flatten(path).segments()) {
            switch (segment) {
                case Path.Segment.MoveTo move -> {
                    if (open) {
                        counter.edge(lastX, lastY, startX, startY);
                    }
                    startX = move.x();
                    startY = move.y();
                    lastX = startX;
                    lastY = startY;
                    open = true;
                }
                case Path.Segment.LineTo line -> {
                    counter.edge(lastX, lastY, line.x(), line.y());
                    lastX = line.x();
                    lastY = line.y();
                }
                case Path.Segment.Close _ -> {
                    counter.edge(lastX, lastY, startX, startY);
                    lastX = startX;
                    lastY = startY;
                }
                // A flattened path has only the three above.
                case Path.Segment.QuadTo _, Path.Segment.CubicTo _, Path.Segment.ArcTo _ ->
                    throw new IllegalStateException("a flattened path has a curve: " + segment);
            }
        }
        if (open) {
            counter.edge(lastX, lastY, startX, startY);
        }
        return evenOdd ? (counter.crossings & 1) == 1 : counter.winding != 0;
    }

    /// The crossings and the winding of a ray from one point to the right.
    private static final class Counter {

        private final double x;
        private final double y;
        private int crossings;
        private int winding;

        Counter(double x, double y) {
            this.x = x;
            this.y = y;
        }

        void edge(double x0, double y0, double x1, double y1) {
            var upward = y0 <= y && y1 > y;
            var downward = y1 <= y && y0 > y;
            if (!upward && !downward) {
                return;
            }
            var crossX = x0 + (y - y0) * (x1 - x0) / (y1 - y0);
            if (crossX > x) {
                crossings++;
                winding += upward ? 1 : -1;
            }
        }
    }
}
