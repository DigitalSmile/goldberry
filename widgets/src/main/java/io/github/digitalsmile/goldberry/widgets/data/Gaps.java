package io.github.digitalsmile.goldberry.widgets.data;

import java.util.ArrayList;
import java.util.List;

/// A series with its holes resolved — the one place a [NullPolicy] is applied.
///
/// Every mode of every chart needs the same two things out of a series with
/// missing values: **what to draw** and **where to stop**. So the policy is
/// applied once, here, and produces both: a value list with the substitutions
/// made, and the runs of consecutive indices that are actually drawable. A
/// polyline, a band and a bar then all read the same answer, and there is no mode
/// that quietly disagrees about where a hole is.
public final class Gaps {

    private Gaps() {
    }

    /// `values` with `policy` applied.
    ///
    /// @param values the substituted values — the input for [NullPolicy#GAP],
    ///               zeroes for [NullPolicy#ZERO], interpolated for
    ///               [NullPolicy#CONNECT]
    /// @param runs   the stretches of consecutive drawable indices, in order;
    ///               empty when nothing is drawable, and one run covering
    ///               everything for a series with no holes
    public record Resolved(List<Double> values, List<int[]> runs) {

        /// Whether the value at `index` is there at all.
        public boolean has(int index) {
            return index >= 0 && index < values.size() && isValue(values.get(index));
        }

        /// The value at `index`, or `NaN` — the substituted one, so a `ZERO`
        /// chart reads zero here.
        public double at(int index) {
            return index >= 0 && index < values.size() ? values.get(index) : Double.NaN;
        }

        /// Whether this series has a hole in it at all — the fast path every
        /// chart in practice takes.
        public boolean isWhole() {
            return runs.size() == 1 && runs.getFirst()[0] == 0
                    && runs.getFirst()[1] == values.size();
        }
    }

    /// Whether `value` is a value rather than a hole.
    ///
    /// Infinity counts as a hole too. It is not a missing reading, but there is
    /// no position on an axis for it and a scale that included one would collapse
    /// every real point onto a single pixel — which is a worse answer than
    /// leaving it out and is the same answer the axis already gives a `NaN`.
    public static boolean isValue(Double value) {
        return value != null && Double.isFinite(value);
    }

    /// `values` under `policy`.
    public static Resolved resolve(List<Double> values, NullPolicy policy) {
        var mode = policy == null ? NullPolicy.GAP : policy;
        if (values == null || values.isEmpty()) {
            return new Resolved(List.of(), List.of());
        }
        var resolved = switch (mode) {
            case GAP -> values;
            case ZERO -> substituted(values);
            case CONNECT -> interpolated(values);
        };
        return new Resolved(resolved, runsOf(resolved));
    }

    /// Every hole as a zero.
    private static List<Double> substituted(List<Double> values) {
        var out = new ArrayList<Double>(values.size());
        for (var value : values) {
            out.add(isValue(value) ? value : 0.0);
        }
        return List.copyOf(out);
    }

    /// Every *interior* hole filled in linearly between its neighbours.
    ///
    /// Interior, because connecting needs two ends: a series that starts late did
    /// not have a value before it started, and inventing one by extending the
    /// first reading backwards would add data rather than join it.
    private static List<Double> interpolated(List<Double> values) {
        var out = new ArrayList<Double>(values);
        var previous = -1;
        for (var i = 0; i < out.size(); i++) {
            if (!isValue(out.get(i))) {
                continue;
            }
            if (previous >= 0 && i - previous > 1) {
                var from = out.get(previous);
                var to = out.get(i);
                var steps = i - previous;
                for (var k = previous + 1; k < i; k++) {
                    out.set(k, from + (to - from) * (k - previous) / steps);
                }
            }
            previous = i;
        }
        return List.copyOf(out);
    }

    /// The stretches of consecutive real values in `values`.
    private static List<int[]> runsOf(List<Double> values) {
        var runs = new ArrayList<int[]>();
        var start = -1;
        for (var i = 0; i < values.size(); i++) {
            if (isValue(values.get(i))) {
                if (start < 0) {
                    start = i;
                }
            } else if (start >= 0) {
                runs.add(new int[] {start, i});
                start = -1;
            }
        }
        if (start >= 0) {
            runs.add(new int[] {start, values.size()});
        }
        return List.copyOf(runs);
    }

    /// The runs a **stack** can be drawn over — the indices where every one of
    /// `series` has a value.
    ///
    /// A stacked band's y is a running total, so an index where one component is
    /// missing is an index where the total is **unknown** — and drawing the bands
    /// above it as though the missing one were zero would put them at the wrong
    /// height rather than leaving a hole. So a hole in any series is a hole in the
    /// whole stack, which is the only rendering that does not move a band to a
    /// value nobody reported.
    public static List<int[]> stackRuns(List<Resolved> series, int length) {
        var whole = new ArrayList<Double>(length);
        for (var i = 0; i < length; i++) {
            var complete = true;
            for (var one : series) {
                if (!one.has(i)) {
                    complete = false;
                    break;
                }
            }
            whole.add(complete ? 0.0 : Double.NaN);
        }
        return runsOf(whole);
    }
}
