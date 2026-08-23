package io.github.digitalsmile.goldberry.widgets.data;

import java.util.ArrayList;
import java.util.List;

/// Where the labels on an axis go — `content-widgets.md` §3.1's "Wilkinson's
/// extended tick-labeling algorithm".
///
/// **Nice numbers are not a rounding problem.** The naive answer — divide the
/// range by the tick count and round the step up to something tidy — gets
/// `0…97` wrong in a way everybody has seen: it either labels `0, 20, 40, 60,
/// 80` and leaves a quarter of the axis unlabelled, or labels `0, 12.5, 25 …`
/// and asks the reader to do arithmetic to place a point. The two failures pull
/// in opposite directions, which is why one number cannot decide it.
///
/// Talbot, Lin and Hanrahan's extension of Wilkinson's algorithm (2010) scores
/// candidate labellings on four axes and takes the best:
///
/// - **simplicity** — is the step one of the numbers people read easily
///   (1, 5, 2, 2.5, 4, 3, in that order of preference), and does the axis
///   include zero;
/// - **coverage** — how much of the data's own range the labels span, penalising
///   labels that stop well short of the extremes *and* ones that run far past
///   them;
/// - **density** — how close the label count is to the number asked for;
/// - **legibility** — whether the labels can be drawn without colliding, which
///   is where a renderer would weigh in.
///
/// Legibility is **1** here. The paper's version weighs font size, orientation
/// and overlap, all of which need the text stack and a decided axis width; this
/// one has neither at the point it runs. Stated rather than silently dropped: it
/// is the term to add when an axis first has labels that collide, and until then
/// weighing a constant would be arithmetic with no effect.
///
/// The search is exhaustive over a bounded space and runs in microseconds. It
/// terminates because every loop has a score bound that can only fall — a
/// candidate whose *best possible* score is already below the best found is
/// abandoned along with everything after it.
public final class Ticks {

    /// The steps people read without thinking, most preferred first. The order
    /// is the simplicity score: `1` is better than `5` is better than `2`.
    private static final double[] NICE = {1, 5, 2, 2.5, 4, 3};

    /// The paper's weights: simplicity, coverage, density, legibility.
    private static final double SIMPLICITY = 0.25;
    private static final double COVERAGE = 0.2;
    private static final double DENSITY = 0.5;
    private static final double LEGIBILITY = 0.05;

    /// How many powers of the nice steps to consider. `j` is the paper's "skip
    /// amount" — labelling every second or third nice number — and beyond three
    /// the labels are so sparse that density has already ruled them out.
    private static final int MAX_SKIP = 3;

    /// The most labels any axis is asked to consider. A guard rather than a
    /// parameter: the search is bounded by its own score anyway, and this is what
    /// keeps a caller who asks for a thousand ticks from finding out slowly.
    private static final int MAX_COUNT = 60;

    private Ticks() {
    }

    /// A labelling: where it starts, where it ends, and the step between labels.
    ///
    /// The range is the **axis's**, not the data's, and the difference is the
    /// point: `0…97` is labelled `0, 25, 50, 75, 100`, so the axis runs to 100
    /// and the data stops short of it.
    ///
    /// @param min   the first label
    /// @param max   the last label
    /// @param step  the distance between them
    /// @param count how many labels there are
    public record Labelling(double min, double max, double step, int count) {

        /// The label positions themselves.
        ///
        /// Computed as `min + i * step` rather than by accumulating, because
        /// adding 0.1 to itself ten times is 0.9999999999999999 and that is a
        /// label somebody has to read.
        public List<Double> values() {
            var out = new ArrayList<Double>(count);
            for (var i = 0; i < count; i++) {
                out.add(min + i * step);
            }
            return List.copyOf(out);
        }
    }

    /// The best labelling of `dmin…dmax` at about `target` labels.
    ///
    /// @param dmin   the data's minimum
    /// @param dmax   the data's maximum
    /// @param target how many labels are wanted — a preference, not a promise:
    ///               the whole point is that a nice five is better than an exact
    ///               six
    public static Labelling extended(double dmin, double dmax, int target) {
        if (!Double.isFinite(dmin) || !Double.isFinite(dmax)) {
            throw new IllegalArgumentException(
                    "an axis needs finite bounds, and " + dmin + "…" + dmax + " are not");
        }
        if (target < 2) {
            throw new IllegalArgumentException(
                    "an axis with fewer than two labels is not an axis, and " + target
                            + " was asked for");
        }
        if (dmin > dmax) {
            var swap = dmin;
            dmin = dmax;
            dmax = swap;
        }
        if (dmin == dmax) {
            // A flat series still has an axis. One label at the value is the
            // honest answer -- inventing a range around it would draw a spread
            // the data does not have.
            return new Labelling(dmin, dmin, 0, 1);
        }

        var best = (Labelling) null;
        var bestScore = -2.0;

        for (var skip = 1; skip <= MAX_SKIP; skip++) {
            for (var index = 0; index < NICE.length; index++) {
                var q = NICE[index];
                var simplicityMax = 1 - (double) index / (NICE.length - 1) - skip + 1;
                if (SIMPLICITY * simplicityMax + COVERAGE + DENSITY + LEGIBILITY < bestScore) {
                    // Every later q is simpler-scored no better, and every later
                    // skip worse still. Nothing after this can win.
                    break;
                }

                for (var count = 2; count <= MAX_COUNT; count++) {
                    var densityMax = count >= target
                            ? 2 - (double) (count - 1) / (target - 1)
                            : 1;
                    if (SIMPLICITY * simplicityMax + COVERAGE + DENSITY * densityMax + LEGIBILITY
                            < bestScore) {
                        break;
                    }

                    var delta = (dmax - dmin) / (count + 1) / skip / q;
                    var exponent = (int) Math.ceil(log10(delta));
                    // Bounded rather than `while (true)`: the score check below
                    // ends it in practice, and this ends it in the case where a
                    // degenerate range makes the arithmetic wander.
                    for (var tries = 0; tries < 64; tries++, exponent++) {
                        var step = skip * q * Math.pow(10, exponent);
                        var coverageMax = coverageMax(dmin, dmax, step * (count - 1));
                        if (SIMPLICITY * simplicityMax + COVERAGE * coverageMax
                                + DENSITY * densityMax + LEGIBILITY < bestScore) {
                            break;
                        }

                        var minStart = (int) (Math.floor(dmax / step) * skip - (count - 1) * skip);
                        var maxStart = (int) (Math.ceil(dmin / step) * skip);
                        if (minStart > maxStart) {
                            continue;
                        }
                        for (var start = minStart; start <= maxStart; start++) {
                            var min = start * step / skip;
                            var max = min + step * (count - 1);
                            var score = SIMPLICITY * simplicity(index, skip, min, max, step)
                                    + COVERAGE * coverage(dmin, dmax, min, max)
                                    + DENSITY * density(count, target, dmin, dmax, min, max)
                                    + LEGIBILITY;
                            if (score > bestScore) {
                                bestScore = score;
                                best = new Labelling(min, max, step, count);
                            }
                        }
                    }
                }
            }
        }

        // Only reachable for a range so extreme that every candidate scored
        // below the floor -- 1e-300 to 1e300, say. The naive answer is the right
        // fallback precisely because it is unconditional.
        return best != null ? best : naive(dmin, dmax, target);
    }

    /// Whether the step is a preferred number, less the skip, plus a point for
    /// including zero — which is the label a reader looks for first.
    private static double simplicity(int index, int skip, double min, double max, double step) {
        var eps = 1e-10;
        var remainder = min % step;
        var includesZero = (remainder < eps || step - remainder < eps) && min <= 0 && max >= 0;
        return 1 - (double) index / (NICE.length - 1) - skip + (includesZero ? 1 : 0);
    }

    /// How well the labels span the data, penalising both ends.
    ///
    /// Squared, so an axis that stops just short of the maximum is barely
    /// punished and one that stops halfway is ruled out.
    private static double coverage(double dmin, double dmax, double min, double max) {
        var span = dmax - dmin;
        return 1 - 0.5 * (sq(dmax - max) + sq(dmin - min)) / sq(0.1 * span);
    }

    /// The best coverage any labelling of this width could reach, for pruning.
    private static double coverageMax(double dmin, double dmax, double span) {
        var range = dmax - dmin;
        if (span <= range) {
            return 1;
        }
        var half = (span - range) / 2;
        return 1 - 0.5 * (sq(half) + sq(half)) / sq(0.1 * range);
    }

    /// How near the label count is to the one asked for, in both directions.
    private static double density(
            int count, int target, double dmin, double dmax, double min, double max) {

        var have = (count - 1) / (max - min);
        var want = (target - 1) / (Math.max(max, dmax) - Math.min(min, dmin));
        return 2 - Math.max(have / want, want / have);
    }

    /// The answer when the search finds nothing — an even split, rounded to a
    /// power of ten.
    private static Labelling naive(double dmin, double dmax, int target) {
        var raw = (dmax - dmin) / (target - 1);
        var step = Math.pow(10, Math.ceil(log10(raw)));
        var min = Math.floor(dmin / step) * step;
        var count = (int) Math.ceil((dmax - min) / step) + 1;
        return new Labelling(min, min + step * (count - 1), step, count);
    }

    private static double log10(double value) {
        return Math.log10(Math.abs(value) < Double.MIN_NORMAL ? Double.MIN_NORMAL : value);
    }

    private static double sq(double value) {
        return value * value;
    }
}
