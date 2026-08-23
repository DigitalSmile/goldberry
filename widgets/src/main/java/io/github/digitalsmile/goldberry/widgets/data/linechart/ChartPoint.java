package io.github.digitalsmile.goldberry.widgets.data.linechart;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.markup.Markup;
import io.github.digitalsmile.goldberry.widgets.markup.Wiring;
import java.util.List;

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
