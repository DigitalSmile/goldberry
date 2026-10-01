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

    /// Written out so that the parameters taking null for a default can say so (ADR-0497).
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
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        return new Row(children, Attributes.of(node));
    }
}
