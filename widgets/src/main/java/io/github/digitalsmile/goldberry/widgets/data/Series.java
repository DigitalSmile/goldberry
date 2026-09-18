package io.github.digitalsmile.goldberry.widgets.data;

import java.util.List;
import java.util.Objects;

/// One line on a chart: what it is called, and what it did.
///
/// **A name, not a colour.** Which colour a series takes is decided by its
/// *position* — slot 1, slot 2 — because the order is what keeps adjacent series
/// distinguishable under colour-vision deficiency
/// (ADR-0194),
/// and a caller that could pass a colour would be a caller that could pass two
/// that collide. An application that genuinely needs a particular colour writes
/// a rule: `#revenue { --gb-chart-1: … }`, which is the cascade doing it
/// (ADR-0195).
///
/// **The x is not in here.** A series is values in order, and what they are
/// plotted against is the chart's: by default the point **index**, evenly
/// spaced and labelled by the caller, or `content-widgets.md` §3.1's
/// `java.time` axis when the chart is given one `Instant` per point — at which
/// point an unscraped stretch is as wide as it was long
/// (ADR-0203, [TimeAxis]).
///
/// @param name   what the legend calls it — required, because a series nobody
///               can name is a line nobody can read
/// @param values the points, in order
public record Series(String name, List<Double> values) {

    public Series {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("a series needs a name for its legend entry");
        }
        values = normalized(values);
    }

    /// `values` with every hole spelled the same way.
    ///
    /// **A missing value is `NaN`**, and a `null` is read as one rather than
    /// refused. `List.copyOf` rejects nulls, so before this a series read out of a
    /// nullable database column had to be converted by its caller — and the
    /// obvious conversion is `orElse(0)`, which is the one answer that silently
    /// destroys the difference this toolkit has a whole enum about
    /// ([NullPolicy]). Taking the null and calling it a hole is the friendlier
    /// half of refusing to guess.
    private static List<Double> normalized(List<Double> values) {
        if (values == null) {
            return List.of();
        }
        var out = new java.util.ArrayList<Double>(values.size());
        for (var value : values) {
            out.add(value == null ? Double.NaN : value);
        }
        return List.copyOf(out);
    }

    /// A series from bare numbers.
    public static Series of(String name, double... values) {
        var boxed = new java.util.ArrayList<Double>(values.length);
        for (var value : values) {
            boxed.add(value);
        }
        return new Series(name, boxed);
    }

    /// The smallest value, or [Double#POSITIVE_INFINITY] when there are none.
    ///
    /// **Holes are skipped.** `Math.min` propagates `NaN`, so a series with one
    /// missing reading used to answer `NaN` here — and the axis, finding its
    /// domain was not finite, fell back to `0..0` and collapsed the whole chart
    /// onto one line. A hole is an absence of a value, not a value smaller than
    /// every other.
    public double min() {
        return values.stream()
                .mapToDouble(Double::doubleValue)
                .filter(Double::isFinite)
                .min()
                .orElse(Double.POSITIVE_INFINITY);
    }

    /// The largest, or [Double#NEGATIVE_INFINITY] when there are none — holes
    /// skipped, for [#min]'s reason.
    public double max() {
        return values.stream()
                .mapToDouble(Double::doubleValue)
                .filter(Double::isFinite)
                .max()
                .orElse(Double.NEGATIVE_INFINITY);
    }

    /// How many of this series' points are actually there.
    ///
    /// A series of nothing but holes is as empty as a series of no points, and it
    /// is the shape a query returning rows of nulls produces.
    public int valueCount() {
        return (int) values.stream().filter(Gaps::isValue).count();
    }
}
