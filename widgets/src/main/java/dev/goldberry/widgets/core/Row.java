package dev.goldberry.widgets.core;

import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.kdl.KdlNode;
import dev.goldberry.layout.FlexDirection;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributed;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;
import dev.goldberry.widgets.markup.Markup;
import dev.goldberry.widgets.markup.Wiring;

/// Children laid out left to right.
///
/// ```kdl
/// row id="toolbar" { button press="find" "Find"; spacer; button press="create" "New" }
/// ```
///
/// `new Row(Widget...)` takes the children; `new Row(List<Widget>, Attributes)`
/// takes them with an id and classes.
///
/// Everything about a row except its direction is the stylesheet's: it sets no
/// colour, no padding and no gap, so `gap` is written as `#toolbar { gap: 8px }`
/// and not as an attribute. The direction is the widget's, and the one thing a
/// rule cannot take: `flex-direction: row` is applied after the computed style,
/// because a `row` a stylesheet could turn into a column would be a name that
/// lies.
///
/// Read more: [Row and column](https://goldberry.dev/docs/layout/row-and-column.html#row).
@Markup("row")
public record Row(List<Widget> children, Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Attributed<Row> {

    public Row(Widget... kids) {
        this(List.of(kids), Attributes.NONE);
    }

    /// Written out so that the parameters that take null for a default can say so.
    public Row(@Nullable List<Widget> children, Attributes attributes) {
        children = List.copyOf(children == null ? List.of() : children);
        this.children = children;
        this.attributes = attributes;
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
    public @Nullable String id() {
        return attributes.id();
    }

    @Override
    public Set<String> classes() {
        return attributes.classes();
    }

    @Override
    public @Nullable Object key() {
        return attributes.key();
    }

    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        return Box.of().children(boxes.toArray(Box[]::new)).style(style).direction(FlexDirection.ROW);
    }

    /// Builds a `row` from markup.
    @SuppressWarnings("unused")
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        return new Row(children, Attributes.of(node));
    }
}
