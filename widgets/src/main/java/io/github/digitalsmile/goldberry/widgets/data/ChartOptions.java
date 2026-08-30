package io.github.digitalsmile.goldberry.widgets.data;

import java.util.List;
import java.util.Objects;

/// Everything about a chart that is not its numbers.
///
/// A chart is `series`, `categories`, **this**, and `attributes`. Before it there
/// were six components on each of the three axis charts and the next feature made
/// seven, with every one of them threaded by hand through `ChartParts`,
/// `ChartPlot` and `ChartSurface` — which is four places to forget something and
/// three charts to forget it in.
/// [ADR-0202](../../../../../../../book/src/adr/0202-a-limit-is-not-a-series.md)
/// said the next one should bundle them, and the time axis is the next one.
///
/// **A subject rather than a bag.** What is in here is what a chart does with
/// data it has been given: whether it has any yet, what to do where a value is
/// missing, what limits to draw across it, and what its x means. The data itself
/// stays where it is, because that is the other subject and it is the one an
/// application changes every frame.
///
/// Applications rarely name this type. The charts keep their withers —
/// `chart.loading()`, `chart.nulls(ZERO)`, `chart.threshold(…)` — and each of
/// them is one line that rebuilds this record.
///
/// @param status     whether the chart has its data, is waiting, or gave up
/// @param nulls      what to do where a series has no value
/// @param thresholds the limits drawn across it, in the semantic hues
/// @param time       what the x axis means, or null for the point index
/// @param curve      how a line gets from one point to the next
/// @param logY       whether the value axis is logarithmic
/// @param bounds     what the value axis has to reach
/// @param markers    whether a dot is drawn at each reading
/// @param crosshair  the group whose crosshair this chart shares, or null
/// @param fill       what is under a band or a line
public record ChartOptions(
        ChartStatus status,
        NullPolicy nulls,
        List<Threshold> thresholds,
        TimeAxis time,
        Curve curve,
        boolean logY,
        Bounds bounds,
        Markers markers,
        CrosshairGroup crosshair,
        Fill fill) {

    /// What a chart that has been told nothing does: it has its data, a hole is
    /// a hole, there are no limits, and x is the point index.
    public static final ChartOptions DEFAULTS = new ChartOptions(
            ChartStatus.READY,
            NullPolicy.GAP,
            List.of(),
            null,
            Curve.LINEAR,
            false,
            Bounds.NONE,
            Markers.AUTO,
            null,
            Fill.NONE);

    public ChartOptions {
        status = status == null ? ChartStatus.READY : status;
        nulls = nulls == null ? NullPolicy.GAP : nulls;
        thresholds = List.copyOf(thresholds == null ? List.of() : thresholds);
        curve = curve == null ? Curve.LINEAR : curve;
        bounds = bounds == null ? Bounds.NONE : bounds;
        markers = markers == null ? Markers.AUTO : markers;
        // NONE rather than SOLID, so a chart that has been told nothing keeps
        // the picture it had: a line chart draws no fill and an area chart reads
        // it as SOLID, because a band with no fill is not a band (Fill).
        fill = fill == null ? Fill.NONE : fill;
    }

    /// These options with a different state.
    public ChartOptions status(ChartStatus value) {
        return new ChartOptions(value, nulls, thresholds, time, curve, logY, bounds, markers, crosshair, fill);
    }

    /// These options with a different null policy.
    public ChartOptions nulls(NullPolicy value) {
        return new ChartOptions(status, value, thresholds, time, curve, logY, bounds, markers, crosshair, fill);
    }

    /// These options with one more limit.
    public ChartOptions threshold(Threshold limit) {
        var next = new java.util.ArrayList<>(thresholds);
        next.add(Objects.requireNonNull(limit, "limit"));
        return new ChartOptions(status, nulls, List.copyOf(next), time, curve, logY, bounds, markers, crosshair, fill);
    }

    /// These options with exactly these limits.
    public ChartOptions thresholds(List<Threshold> limits) {
        return new ChartOptions(status, nulls, limits, time, curve, logY, bounds, markers, crosshair, fill);
    }

    /// These options with a different interpolation.
    public ChartOptions curve(Curve value) {
        return new ChartOptions(status, nulls, thresholds, time, value, logY, bounds, markers, crosshair, fill);
    }

    /// These options with a logarithmic value axis, or a linear one.
    public ChartOptions logY(boolean value) {
        return new ChartOptions(status, nulls, thresholds, time, curve, value, bounds, markers, crosshair, fill);
    }

    /// These options with what the value axis has to reach.
    public ChartOptions bounds(Bounds value) {
        return new ChartOptions(status, nulls, thresholds, time, curve, logY, value, markers, crosshair, fill);
    }

    /// These options with a different marker rule.
    public ChartOptions markers(Markers value) {
        return new ChartOptions(status, nulls, thresholds, time, curve, logY, bounds, value, crosshair, fill);
    }

    /// These options with a shared crosshair, or null for a chart that keeps its
    /// own.
    public ChartOptions crosshair(CrosshairGroup value) {
        return new ChartOptions(status, nulls, thresholds, time, curve, logY, bounds, markers, value, fill);
    }

    /// These options with a different fill under the data.
    public ChartOptions fill(Fill value) {
        return new ChartOptions(status, nulls, thresholds, time, curve, logY, bounds, markers, crosshair, value);
    }

    /// These options with a time axis, or null for the point index.
    public ChartOptions time(TimeAxis value) {
        return new ChartOptions(status, nulls, thresholds, value, curve, logY, bounds, markers, crosshair, fill);
    }
}
