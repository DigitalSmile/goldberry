package io.github.digitalsmile.goldberry.widgets.data;

import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;

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
/// [io.github.digitalsmile.goldberry.widgets.data.donutchart.DonutChart] does
/// **not** implement it. A donut has no axes, no crosshair, and isolating one
/// slice of a part-to-whole chart leaves a chart that no longer shows a whole;
/// it is a different widget rather than a fourth mode.
public interface ChartSpec extends Widget {

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

    /// What this chart does where a series has no value.
    ///
    /// One convention per picture, for [NullPolicy]'s reason: two series in one
    /// chart treating their holes differently is a chart a reader cannot
    /// interpret without being told which line is which kind.
    NullPolicy nulls();

    /// Whether this chart has its data, is waiting for it, or could not get it.
    ///
    /// On the interface for isolation's reason turned round: every axis chart
    /// answers it the same way, and the widget that draws the sentence instead
    /// of the picture is the same one for all three
    /// ([ChartMessage]).
    ChartStatus status();
}
