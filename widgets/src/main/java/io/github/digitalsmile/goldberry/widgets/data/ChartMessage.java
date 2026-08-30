package io.github.digitalsmile.goldberry.widgets.data;

import java.util.List;
import java.util.Set;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// What a chart shows instead of a picture — a **part**, so it is CSS-selectable
/// and not constructible.
///
/// `charts.md` §3.1: "a chart with no data draws a themed message, never an empty
/// grid". An empty grid is not a neutral thing to draw: gridlines and axis labels
/// are an assertion about a scale, and a chart that draws `0, 5, 10, 15, 20` over
/// no data has invented every one of those numbers.
///
/// ## Widgets, not paint
///
/// The other direction from the hover readout, which is painted (ADR-0198), and
/// the rule that decides it is the same one: **does it participate in layout?** A
/// readout is placed in plot coordinates and must not affect the box. A message
/// is centred in the box, wraps when the box is narrow, and *is* the content — so
/// it is a node, a stylesheet reaches it, and the shaping cache serves it like
/// any other sentence.
///
/// ## No spinner
///
/// A loading chart says "Loading…" and does not spin. §1.7 keeps the frame loop
/// idle when nothing is animating, and a spinner would wake it for every chart on
/// a dashboard that is waiting — which, on a dashboard, is all of them at once.
/// An application that wants one puts it beside the chart, where it is that
/// application's frame budget being spent (ADR-0081).
///
/// @param text  the sentence, already decided by [ChartStatus]
/// @param kind  `empty`, `loading` or `failed` — the class a stylesheet selects
///              it by
record ChartMessage(String text, String kind) implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "chart-message";
    }

    @Override
    public Set<String> classes() {
        return kind == null ? Set.of() : Set.of(kind);
    }

    @Override
    public List<Widget> children() {
        return List.of(new Text(text));
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).children(children.toArray(Box[]::new));
    }
}
