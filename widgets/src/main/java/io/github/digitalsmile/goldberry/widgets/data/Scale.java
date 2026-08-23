package io.github.digitalsmile.goldberry.widgets.data;

/// A value's place on an axis — the arithmetic every chart does and none of them
/// should write twice.
///
/// ```java
/// var y = Scale.linear(0, 100, size.height(), 0);   // note the order
/// frame.fillRect(x, y.at(value), 2, 2, ink);
/// ```
///
/// ## The y axis is built by swapping the range, not by negating
///
/// A frame's y grows **downward** and a chart's values grow **upward**, and every
/// bug in this area comes from remembering that in one place and forgetting it in
/// another. So there is no "inverted" flag: a y scale is
/// `linear(min, max, height, 0)` — the bottom of the box for the smallest value —
/// and the arithmetic below never knows which axis it is. One direction, stated
/// once, at the only place that knows the answer.
///
/// ## A flat domain maps to the middle
///
/// A series that did not change has `min == max`, and the honest picture of it is
/// a line down the middle of the box rather than a division by zero at the top
/// (or, worse, a `NaN` that silently draws nothing). [Sparkline] states the same
/// rule for the same reason; it is here so the next four charts inherit it rather
/// than each rediscovering it.
///
/// @param domainMin the value at [#rangeMin]
/// @param domainMax the value at [#rangeMax]
/// @param rangeMin  the coordinate `domainMin` maps to
/// @param rangeMax  the coordinate `domainMax` maps to
public record Scale(double domainMin, double domainMax, double rangeMin, double rangeMax) {

    public Scale {
        if (!Double.isFinite(domainMin) || !Double.isFinite(domainMax)
                || !Double.isFinite(rangeMin) || !Double.isFinite(rangeMax)) {
            throw new IllegalArgumentException(
                    "a scale needs finite bounds: " + domainMin + "…" + domainMax + " onto "
                            + rangeMin + "…" + rangeMax);
        }
    }

    /// A linear mapping of `domainMin…domainMax` onto `rangeMin…rangeMax`.
    public static Scale linear(
            double domainMin, double domainMax, double rangeMin, double rangeMax) {
        return new Scale(domainMin, domainMax, rangeMin, rangeMax);
    }

    /// A scale over the values themselves, with the domain widened to round
    /// numbers — the axis a labelled chart wants.
    ///
    /// The labelling and the scale have to agree or the gridlines land off the
    /// labels, so they come from one call rather than two that could disagree.
    ///
    /// @param target how many labels are wanted; see [Ticks#extended]
    public static Scale nice(
            double domainMin, double domainMax, double rangeMin, double rangeMax, int target) {

        var labels = Ticks.extended(domainMin, domainMax, target);
        return labels.count() < 2
                ? new Scale(domainMin, domainMax, rangeMin, rangeMax)
                : new Scale(labels.min(), labels.max(), rangeMin, rangeMax);
    }

    /// Where `value` sits, in the range's coordinates.
    ///
    /// Not clamped: a chart that wants a point outside its axis clipped says so
    /// with a clip, and one that wants it dropped filters. Silently pinning it to
    /// the edge would draw a line to a place the data never went.
    public double at(double value) {
        var span = domainMax - domainMin;
        if (span == 0) {
            // See the class note: the middle, not an edge and not a NaN.
            return (rangeMin + rangeMax) / 2;
        }
        return rangeMin + (value - domainMin) / span * (rangeMax - rangeMin);
    }

    /// The value at `coordinate` — [#at] backwards.
    ///
    /// What a crosshair needs: the pointer is at a pixel and the tooltip has to
    /// say which x that is.
    public double from(double coordinate) {
        var span = rangeMax - rangeMin;
        if (span == 0) {
            return (domainMin + domainMax) / 2;
        }
        return domainMin + (coordinate - rangeMin) / span * (domainMax - domainMin);
    }

    /// This scale's labels, at about `target` of them.
    public Ticks.Labelling labels(int target) {
        return Ticks.extended(domainMin, domainMax, target);
    }
}
