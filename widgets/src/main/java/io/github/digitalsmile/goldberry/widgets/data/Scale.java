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
/// (or, worse, a `NaN` that silently draws nothing).
/// [io.github.digitalsmile.goldberry.widgets.data.sparkline.Sparkline] states the
/// same rule for the same reason; it is here so the next four charts inherit it rather
/// than each rediscovering it.
///
/// ## Logarithmic is the same arithmetic on the logarithm
///
/// [#log] maps `log10(value)` linearly, which is all a log axis is. It is a flag
/// on this record rather than a second type because every caller wants *a scale*
/// and none of them wants to know which kind: a painter asks where a value goes
/// and a pointer asks what is at a pixel, and both questions have one answer
/// either way.
///
/// **A log scale has no room for zero or for a negative**, and that is a fact
/// about logarithms rather than a decision this file could make differently:
/// `log10(0)` is negative infinity and `log10(-1)` is not a number. So the domain
/// must be positive, and a chart that wants one filters its data first — see
/// [Gaps#positiveOnly].
///
/// @param domainMin the value at [#rangeMin]
/// @param domainMax the value at [#rangeMax]
/// @param rangeMin  the coordinate `domainMin` maps to
/// @param rangeMax  the coordinate `domainMax` maps to
/// @param logarithmic whether the mapping is on the logarithm of the value
public record Scale(double domainMin, double domainMax, double rangeMin, double rangeMax, boolean logarithmic) {

    public Scale {
        if (!Double.isFinite(domainMin)
                || !Double.isFinite(domainMax)
                || !Double.isFinite(rangeMin)
                || !Double.isFinite(rangeMax)) {
            throw new IllegalArgumentException("a scale needs finite bounds: " + domainMin + "…" + domainMax + " onto "
                    + rangeMin + "…" + rangeMax);
        }
        if (logarithmic && (domainMin <= 0 || domainMax <= 0)) {
            throw new IllegalArgumentException("a logarithmic scale needs a positive domain, and " + domainMin + "…"
                    + domainMax + " is not: log10(0) is negative infinity and"
                    + " log10 of a negative number is not a number. Filter the"
                    + " non-positive values out first (Gaps.positiveOnly).");
        }
    }

    /// The linear form, which is every scale that is not a log one.
    public Scale(double domainMin, double domainMax, double rangeMin, double rangeMax) {
        this(domainMin, domainMax, rangeMin, rangeMax, false);
    }

    /// A linear mapping of `domainMin…domainMax` onto `rangeMin…rangeMax`.
    public static Scale linear(double domainMin, double domainMax, double rangeMin, double rangeMax) {
        return new Scale(domainMin, domainMax, rangeMin, rangeMax, false);
    }

    /// A mapping of the **logarithm** of `domainMin…domainMax` onto
    /// `rangeMin…rangeMax`.
    ///
    /// Which is what makes a log axis worth having: a series that spends most of
    /// its life at 3 and spikes to 30 000 is, on a linear axis, a flat line at
    /// the bottom with one spike — every reading anybody cares about is in the
    /// first pixel. On a log axis each decade gets the same room, so the quiet
    /// data has as much of the chart as the loud data.
    ///
    /// @throws IllegalArgumentException if either end of the domain is not
    ///         positive
    public static Scale log(double domainMin, double domainMax, double rangeMin, double rangeMax) {
        return new Scale(domainMin, domainMax, rangeMin, rangeMax, true);
    }

    /// A scale over the values themselves, with the domain widened to round
    /// numbers — the axis a labelled chart wants.
    ///
    /// The labelling and the scale have to agree or the gridlines land off the
    /// labels, so they come from one call rather than two that could disagree.
    ///
    /// @param target how many labels are wanted; see [Ticks#extended]
    public static Scale nice(double domainMin, double domainMax, double rangeMin, double rangeMax, int target) {

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
        if (logarithmic) {
            if (!(value > 0)) {
                // No position exists. `NaN` rather than an edge, so a caller that
                // forgot to filter draws nothing instead of drawing a reading at
                // the bottom of the axis that never happened.
                return Double.NaN;
            }
            return on(Math.log10(value), Math.log10(domainMin), Math.log10(domainMax));
        }
        return on(value, domainMin, domainMax);
    }

    /// `value` placed between `low` and `high`, in the range's coordinates.
    private double on(double value, double low, double high) {
        var span = high - low;
        if (span == 0) {
            // See the class note: the middle, not an edge and not a NaN.
            return (rangeMin + rangeMax) / 2;
        }
        return rangeMin + (value - low) / span * (rangeMax - rangeMin);
    }

    /// The value at `coordinate` — [#at] backwards.
    ///
    /// What a crosshair needs: the pointer is at a pixel and the tooltip has to
    /// say which x that is.
    public double from(double coordinate) {
        var span = rangeMax - rangeMin;
        if (span == 0) {
            return logarithmic ? Math.sqrt(domainMin * domainMax) : (domainMin + domainMax) / 2;
        }
        var fraction = (coordinate - rangeMin) / span;
        if (logarithmic) {
            var low = Math.log10(domainMin);
            return Math.pow(10, low + fraction * (Math.log10(domainMax) - low));
        }
        return domainMin + fraction * (domainMax - domainMin);
    }

    /// This scale's labels, at about `target` of them.
    public Ticks.Labelling labels(int target) {
        return Ticks.extended(domainMin, domainMax, target);
    }
}
