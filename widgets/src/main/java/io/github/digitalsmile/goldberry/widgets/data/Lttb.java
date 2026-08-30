package io.github.digitalsmile.goldberry.widgets.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// Largest-Triangle-Three-Buckets — `content-widgets.md` §3.1's downsampling.
///
/// **The problem it solves is not speed, it is truth.** A hundred thousand points
/// drawn into a two-hundred-pixel sparkline is five hundred points per pixel, and
/// whichever one the loop happens to draw last is the one on screen. Taking every
/// *n*th point is worse than slow: it is a picture that omits the spike, because a
/// spike is one sample and the stride steps over it.
///
/// LTTB keeps the point in each bucket that forms the **largest triangle** with
/// the point already chosen and the next bucket's average — which is the point
/// that changes the polyline's shape most, and so the one whose absence would be
/// visible. Steve Fowler's undersampling thesis is where it comes from, and it is
/// the algorithm every serious plotting library ends up with.
///
/// First and last are always kept: a series whose ends moved would be a series
/// drawn over a different range than it has.
public final class Lttb {

    private Lttb() {}

    /// `values` reduced to at most `threshold` points, keeping the shape.
    ///
    /// Returned as **indices into the input**, not as values: a caller drawing a
    /// sparkline needs the x it came from, and an index is that x. A caller that
    /// wants the values maps over them.
    ///
    /// Returns every index when there is nothing to gain — fewer points than the
    /// threshold, or a threshold below three, where "keep the ends and pick the
    /// most important middle" has no middle to pick from.
    ///
    /// @param values    the series, in order
    /// @param threshold the most points to keep
    public static int[] indices(List<Double> values, int threshold) {
        Objects.requireNonNull(values, "values");
        var n = values.size();
        if (threshold >= n || threshold < 3) {
            var all = new int[n];
            for (var i = 0; i < n; i++) {
                all[i] = i;
            }
            return all;
        }

        var kept = new int[threshold];
        kept[0] = 0;
        kept[threshold - 1] = n - 1;

        // Every bucket but the first and last point, which are already spoken
        // for. A double rather than an int stride: with 100k points and a
        // threshold of 1000 an integer bucket size would drift, and the drift
        // lands entirely in the last bucket.
        var bucket = (double) (n - 2) / (threshold - 2);
        var previous = 0;

        for (var i = 0; i < threshold - 2; i++) {
            // The average of the *next* bucket is the triangle's third corner.
            // It is what makes this look ahead rather than only behind, and it
            // is why the result is stable when the data is noisy.
            var nextStart = (int) Math.floor((i + 1) * bucket) + 1;
            var nextEnd = Math.min((int) Math.floor((i + 2) * bucket) + 1, n);
            var count = nextEnd - nextStart;
            var averageX = 0.0;
            var averageY = 0.0;
            for (var j = nextStart; j < nextEnd; j++) {
                averageX += j;
                averageY += values.get(j);
            }
            if (count > 0) {
                averageX /= count;
                averageY /= count;
            } else {
                // The last bucket can be empty when the arithmetic lands exactly
                // on the end. The final point stands in for it, which is the
                // point that is about to be kept anyway.
                averageX = n - 1;
                averageY = values.get(n - 1);
            }

            var start = (int) Math.floor(i * bucket) + 1;
            var end = Math.min((int) Math.floor((i + 1) * bucket) + 1, n - 1);
            var previousX = previous;
            var previousY = values.get(previous);

            var bestArea = -1.0;
            var best = start;
            for (var j = start; j < end; j++) {
                // Twice the triangle's area, which is enough to compare by: the
                // factor is the same for every candidate and the square root a
                // real area would need is not.
                var area = Math.abs((previousX - averageX) * (values.get(j) - previousY)
                        - (previousX - j) * (averageY - previousY));
                if (area > bestArea) {
                    bestArea = area;
                    best = j;
                }
            }
            kept[i + 1] = best;
            previous = best;
        }
        return kept;
    }

    /// [#indices] as the values themselves, for a caller that does not need the x.
    public static List<Double> downsample(List<Double> values, int threshold) {
        var kept = indices(values, threshold);
        var out = new ArrayList<Double>(kept.length);
        for (var index : kept) {
            out.add(values.get(index));
        }
        return List.copyOf(out);
    }
}
