package io.github.digitalsmile.goldberry.widgets.data.linechart;

import java.util.ArrayList;
import java.util.List;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.kdl.KdlValue;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;
import io.github.digitalsmile.goldberry.widgets.data.Series;
import io.github.digitalsmile.goldberry.widgets.markup.Markup;
import io.github.digitalsmile.goldberry.widgets.markup.Wiring;

/// `content-widgets.md` §3.2's inline data, as a node — the `series` in
///
/// ```kdl
/// line-chart {
///     series name="downloads" { point "0.1" 1200; point "0.2" 3400 }
/// }
/// ```
///
/// **A widget that draws nothing**, which is `option`'s pattern and is here for
/// `option`'s reason: the KDL inflater builds a document depth-first and hands a
/// factory children that are already widgets, so data written as child nodes has
/// to *be* widgets. `select`'s options solved this first; a chart's series is the
/// same shape — content a parent reads rather than a box the layout places.
///
/// It renders an empty box and its parent never puts it in the tree, so it costs
/// a node in the element tree and nothing on screen. The alternative — an
/// inflater that can be told "my children are data" — is a change to the one
/// mechanism every widget in the catalog goes through, for a case two widgets
/// have.
@Markup("series")
public record ChartSeries(String name, List<Double> values, List<String> labels)
        implements Widget.Leaf, Styled, Paints {

    public ChartSeries {
        values = List.copyOf(values == null ? List.of() : values);
        labels = List.copyOf(labels == null ? List.of() : labels);
    }

    /// This node as the value a chart draws.
    public Series toSeries(int position) {
        return new Series(name == null || name.isBlank() ? "series " + (position + 1) : name, values);
    }

    @Override
    public String cssType() {
        return "series";
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style);
    }

    /// Reads `point` children — `point "0.1" 1200`, a label then a number.
    ///
    /// The label is optional and the number is not: a point with no value is not
    /// a point, and one with no name is a point on an axis nobody labelled.
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        var values = new ArrayList<Double>();
        var labels = new ArrayList<String>();
        for (var point : node.childrenNamed("point")) {
            String label = null;
            Double value = null;
            for (var argument : point.arguments()) {
                if (argument instanceof KdlValue.Num number && value == null) {
                    value = number.value();
                } else if (argument instanceof KdlValue.Str text && label == null) {
                    label = text.value();
                }
            }
            if (value == null) {
                continue;
            }
            values.add(value);
            labels.add(label == null ? "" : label);
        }
        return new ChartSeries(node.stringProperty("name"), values, labels);
    }
}
