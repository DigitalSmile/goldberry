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

/// Children laid out along the cross axis — `docs/core-widgets.md` §1's `column`.
///
/// ```kdl
/// column gap=8 { text "Name"; text-input }
/// ```
///
/// Everything about it except its direction is the stylesheet's: it sets no
/// colour, no padding and no gap. **The direction is the widget's**, and that is
/// the one thing a rule cannot take — a `column` a stylesheet could turn into a
/// row would be a name that lies, and `flex-direction` is therefore applied
/// after the style rather than read from it.
@Markup("column")
public record Column(List<Widget> children, Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Attributed<Column> {

    public Column(Widget... kids) {
        this(List.of(kids), Attributes.NONE);
    }

    public Column {
        children = List.copyOf(children == null ? List.of() : children);
    }

    @Override
    public Column withAttributes(Attributes attributes) {
        return new Column(children, attributes);
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
                .direction(FlexDirection.COLUMN);
    }

    /// Builds a `column` from markup.
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        return new Column(children, Attributes.of(node));
    }
}
