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

    /// Written out so that the parameters taking null for a default can say so (ADR-0497).
    public Column(@Nullable List<Widget> children, Attributes attributes) {
        children = List.copyOf(children == null ? List.of() : children);
        this.children = children;
        this.attributes = attributes;
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
        return Box.of().children(boxes.toArray(Box[]::new)).style(style).direction(FlexDirection.COLUMN);
    }

    /// Builds a `column` from markup.
    ///
    /// **`accordion=#true` builds something else.** §5 puts that flag here and is
    /// right to — "one section open at a time" is a rule about *siblings*, which
    /// no section can enforce about the others — but honouring it needs state, and
    /// a `column` is the most-used container in the toolkit. Making this record
    /// stateful would give every column in every document a `State` object it
    /// never uses.
    ///
    /// So the flag inflates to an
    /// [dev.goldberry.widgets.panel.accordion.Accordion], which
    /// reports `column` as its own CSS type and adds an `accordion` class. A
    /// document writes what §5 says, a stylesheet still sees a column, and an
    /// ordinary column pays nothing ([ADR-0166]).
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        if (node.booleanProperty("accordion")) {
            return new dev.goldberry.widgets.panel.accordion.Accordion(
                    dev.goldberry.widgets.panel.accordion.Accordion.NONE, null, children, Attributes.of(node));
        }
        return new Column(children, Attributes.of(node));
    }
}
