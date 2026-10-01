package dev.goldberry.widgets.data.linechart;

import java.util.List;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.kdl.KdlNode;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;
import dev.goldberry.widgets.markup.Markup;
import dev.goldberry.widgets.markup.Wiring;

/// One `point` of a [ChartSeries] — the leaf of §3.2's inline data.
///
/// It exists so the node is **registered**: the inflater refuses a node it does
/// not know, and it builds depth-first, so `point` has to be inflatable before
/// `series` is handed anything. [ChartSeries] reads the values off the raw KDL
/// rather than off these, because a point is two arguments and re-deriving them
/// from a widget would be the same parse written twice.
@Markup("point")
public record ChartPoint() implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "point";
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style);
    }

    /// Builds a `point`. See the class note on why it carries nothing.
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        return new ChartPoint();
    }
}
