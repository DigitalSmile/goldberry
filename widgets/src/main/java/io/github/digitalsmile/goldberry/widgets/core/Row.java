package io.github.digitalsmile.goldberry.widgets.core;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.natives.yoga.FlexDirection;
import io.github.digitalsmile.goldberry.widget.Attributed;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Set;
import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.widgets.Wiring;
import io.github.digitalsmile.goldberry.widgets.Markup;

/// Children laid out along the main axis — `docs/core-widgets.md` §1's `row`.
///
/// ```kdl
/// row gap=8 class="toolbar" { icon name="search"; spacer; button "New" }
/// ```
///
/// Everything about it except its direction is the stylesheet's: it sets no
/// colour, no padding and no gap. **The direction is the widget's**, and that is
/// the one thing a rule cannot take — a `row` a stylesheet could turn into a
/// column would be a name that lies, and `flex-direction` is therefore applied
/// after the style rather than read from it.
@Markup("row")
public record Row(List<Widget> children, Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Attributed<Row> {

    public Row(Widget... kids) {
        this(List.of(kids), Attributes.NONE);
    }

    public Row {
        children = List.copyOf(children == null ? List.of() : children);
    }

    @Override
    public Row withAttributes(Attributes attributes) {
        return new Row(children, attributes);
    }

    @Override
    public List<Widget> children() {
        return children;
    }

    @Override
    public String id() {
        return attributes.id();
    }

    @Override
    public Set<String> classes() {
        return attributes.classes();
    }

    @Override
    public Object key() {
        return attributes.key();
    }

    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        return Box.of().children(boxes.toArray(Box[]::new)).style(style)
                .direction(FlexDirection.ROW);
    }

    /// Builds a `row` from markup.
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        return new Row(children, Attributes.of(node));
    }
}
