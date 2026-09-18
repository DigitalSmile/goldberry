package io.github.digitalsmile.goldberry.widgets.data;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;

/// What the three axis charts have in common, so one state can serve all of
/// them.
///
/// `line-chart`, `area-chart` and `bar-chart` are three widgets and one chart: a
/// list of series, a list of category labels, a mode, and the attributes any
/// widget has. They differ in what they draw and in what they refuse — a bar may
/// not zoom its baseline, an area is always stacked — and in nothing else.
///
/// This interface exists because **isolation is state that spans both halves of
/// a chart**. Clicking a legend entry has to change what the *plot* draws, and
/// the plot and the legend are siblings, so the thing that remembers which
/// series is isolated must sit above both. Three copies of that state, one per
/// chart, would be three chances for one of them to grow a rule the others do
/// not have — the same argument that put the legend rule in [ChartParts].
///
/// ## The knobs are here too, for the same reason
///
/// Every wither below used to be written out three times, once per chart, with the
/// same body and the same prose above it — two hundred lines of it. They drifted:
/// `bar-chart`'s copy of [#markers] promised a dot at each reading and a bar chart
/// draws none, while its copy of [#curve] said in as many words that a bar ignores
/// it. One statement per knob, made once, is what stops a chart claiming something
/// the painter does not do.
///
/// Each says which charts draw it. **A knob a chart ignores is not an omission**:
/// the three are one chart with three shapes, so `chart.markers(ALWAYS)` compiles
/// and does nothing wherever the shape has no markers, rather than a caller having
/// to know which of three types it is holding. `ChartCurveTest`, `ChartLogTest` and
/// `TimeAxisTest` assert exactly that — the same pixels either way.
///
/// The one knob that is **not** here is `fill`: `line-chart` fades the region under
/// its line and `area-chart` fades each band within its own extent, which is two
/// different sentences about two different pictures, and `bar-chart` has no such
/// region at all and so has no wither for one.
///
/// [io.github.digitalsmile.goldberry.widgets.data.donutchart.DonutChart] does
/// **not** implement it. A donut has no axes, no crosshair, and isolating one
/// slice of a part-to-whole chart leaves a chart that no longer shows a whole;
/// it is a different widget rather than a fourth mode.
///
/// @param <C> the implementing chart's own type, so a chain of withers keeps it —
///        `new LineChart(…).logY().threshold(warn)` is a `LineChart`
public interface ChartSpec<C extends ChartSpec<C>> extends Widget {

    /// The series, in order — which is also their colour order (ADR-0194).
    List<Series> series();

    /// A label per point, or empty for no x labels.
    List<String> categories();

    /// Which shape the plot draws.
    ChartParts.Mode mode();

    /// This chart's CSS type — `line-chart`, `area-chart` or `bar-chart`.
    ///
    /// Not `Styled#cssType()`: a chart is a [Widget.Stateful] now, so it has no
    /// box of its own and the cascade never asks it. What it names here is the
    /// type of the box its [ChartView] draws, which is the box every rule in the
    /// stylesheet is already written against.
    String chartType();

    /// `id` and `class`, which the view carries so a rule that names the chart
    /// still lands on the chart's own box.
    Attributes attributes();

    /// Everything about this chart that is not its numbers — its state, its
    /// null policy, its limits and what its x means.
    ///
    /// One accessor rather than one per knob, for [ChartOptions]'s reason: each
    /// of them was threaded by hand through four places and three charts.
    ChartOptions options();

    /// This chart with `value` as everything that is not its numbers.
    ///
    /// The one line a chart has to write, because only it knows its own
    /// components — [io.github.digitalsmile.goldberry.widget.attr.Attributed]'s
    /// arrangement, for its reason. Every wither below goes through it.
    C options(ChartOptions value);

    /// This chart with an axis that reaches **at least** `min…max`, and further
    /// if the data does.
    ///
    /// What stops a flat series rendering as noise: an uptime between 99.91 and
    /// 99.99 auto-scaled is a mountain range made of eight hundredths of a
    /// percent, and `softAxis(99, 100)` draws it as the flat line near the top
    /// that it is — while still showing an outage, because a reading of 40 pushes
    /// the axis down to meet it ([Bounds]).
    default C softAxis(double min, double max) {
        return options(options().bounds(Bounds.soft(min, max)));
    }

    /// This chart with an axis that is **exactly** `min…max`, whatever the data
    /// does.
    ///
    /// For a range that is a definition rather than an observation — a percentage
    /// of a whole, a gauge with a physical stop. Data outside it is drawn outside
    /// the plot and clipped, which is the correct rendering of a promise that was
    /// wrong.
    default C axis(double min, double max) {
        return options(options().bounds(Bounds.hard(min, max)));
    }

    /// This chart sharing its crosshair with every other chart in `group`.
    ///
    /// Pointing at Tuesday here puts the crosshair on Tuesday on all of them,
    /// which is how a reader asks what the other panel was doing at the same
    /// moment. Only the chart under the pointer draws the readout
    /// ([CrosshairGroup]).
    default C crosshair(CrosshairGroup group) {
        return options(options().crosshair(group));
    }

    /// This chart with a different marker rule — a dot at each reading, or not.
    ///
    /// **`line-chart` only.** A band's shape is its thickness and a bar's is its
    /// length, so neither has a place to put a dot that would mean anything; both
    /// ignore this and draw what they drew. The crosshair's own markers are not
    /// this — a hover puts one on every series whatever this says, because that is
    /// the reading it is pointing at.
    default C markers(Markers value) {
        return options(options().markers(value));
    }

    /// This chart with a **logarithmic** value axis.
    ///
    /// For a series that spends its life at 3 and spikes to 30 000: on a linear
    /// axis every reading anybody cares about is in the bottom pixel. A log axis
    /// gives each decade the same room.
    ///
    /// **It costs the zeroes.** `log10(0)` is negative infinity, so a
    /// non-positive reading has no position and becomes a hole — the line breaks
    /// there rather than sliding off the bottom (ADR-0205).
    ///
    /// **`line-chart` only.** A bar and a band are lengths from zero, and zero is
    /// not on the axis at all — it is infinitely far down — so a chart that drew
    /// one anyway would have to pick a bottom, and every choice is a number nobody
    /// gave it. Both ignore this.
    default C logY() {
        return options(options().logY(true));
    }

    /// This chart with a different interpolation — how the line gets from one
    /// point to the next.
    ///
    /// The default is [Curve#LINEAR], which makes the weakest claim about what
    /// happened in between. **`line-chart` and `area-chart`**: a band takes the
    /// curve on both of its edges, so the fill between them stays the difference
    /// its numbers say. A `bar-chart` ignores it — a bar is a length rather than a
    /// path.
    default C curve(Curve value) {
        return options(options().curve(value));
    }

    /// This chart with a **time axis**: one instant per point, so the x is when
    /// rather than which.
    ///
    /// A gap in the sampling becomes a gap on the axis, and the labels step
    /// across second, minute, hour, day, month and year boundaries ([TimeAxis]).
    ///
    /// **`line-chart` and `area-chart`.** A `bar-chart`'s x is a set of categories
    /// with a bar standing on each, and bars at their true instants would be a
    /// row of slivers with the gaps between readings drawn as empty plot; it
    /// ignores this and labels its categories.
    default C times(List<Instant> value) {
        return options(options().time(TimeAxis.of(value)));
    }

    /// The same, in a zone the application chooses — a server's clock, or `UTC`
    /// for a test.
    default C times(List<Instant> value, ZoneId zone) {
        return options(options().time(TimeAxis.of(value).in(zone)));
    }

    /// This chart with `limit` drawn across it — a line or a shaded region, in
    /// one of the four semantic hues.
    ///
    /// Additive, so several limits read as several calls:
    /// `chart.threshold(warn).threshold(fail)`. A threshold is part of the
    /// domain, so one you have not crossed yet is still on screen ([Threshold]).
    default C threshold(Threshold limit) {
        return options(options().threshold(limit));
    }

    /// This chart with exactly these limits, replacing whatever it had.
    default C thresholds(List<Threshold> limits) {
        return options(options().thresholds(limits));
    }

    /// This chart, told what to do where a series has no value.
    ///
    /// The default is [NullPolicy#GAP], which is the only one of the three that
    /// invents nothing.
    default C nulls(NullPolicy value) {
        return options(options().nulls(value));
    }

    /// This chart, waiting for its data — it keeps its box and says so.
    ///
    /// The box is the point: a panel whose charts vanished while their queries
    /// resolved would reflow twice per chart ([ChartStatus]).
    default C loading() {
        return status(ChartStatus.loading());
    }

    /// This chart, in the application's own words about why there is nothing.
    default C failed(String message) {
        return status(ChartStatus.failed(message));
    }

    /// This chart with `value` as its state — see [ChartStatus].
    default C status(ChartStatus value) {
        return options(options().status(value));
    }
}
