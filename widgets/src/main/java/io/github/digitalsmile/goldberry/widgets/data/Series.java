package io.github.digitalsmile.goldberry.widgets.data;

import java.util.List;
import java.util.Objects;

/// One line on a chart: what it is called, and what it did.
///
/// **A name, not a colour.** Which colour a series takes is decided by its
/// *position* — slot 1, slot 2 — because the order is what keeps adjacent series
/// distinguishable under colour-vision deficiency
/// ([ADR-0194](../../../../../../../book/src/adr/0194-a-series-colour-is-derived-from-nord-not-taken-from-it.md)),
/// and a caller that could pass a colour would be a caller that could pass two
/// that collide. An application that genuinely needs a particular colour writes
/// a rule: `#revenue { --gb-chart-1: … }`, which is the cascade doing it
/// ([ADR-0195](../../../../../../../book/src/adr/0195-a-painter-reads-the-theme-through-a-custom-property.md)).
///
/// The x is the **index**. A time axis is `content-widgets.md` §3.1's
/// `java.time`-driven stepping and is not built; until it is, a chart says where
/// the points are evenly spaced and the caller labels them.
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
        values = List.copyOf(values == null ? List.of() : values);
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
    public double min() {
        return values.stream().mapToDouble(Double::doubleValue).min().orElse(Double.POSITIVE_INFINITY);
    }

    /// The largest, or [Double#NEGATIVE_INFINITY] when there are none.
    public double max() {
        return values.stream().mapToDouble(Double::doubleValue).max().orElse(Double.NEGATIVE_INFINITY);
    }
}
