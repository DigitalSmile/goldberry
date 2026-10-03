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

/// Children laid out top to bottom.
///
/// ```kdl
/// column id="confirm" { text "Delete this file?"; row { spacer; button press="delete" "Delete" } }
/// ```
///
/// `new Column(Widget...)` takes the children; `new Column(List<Widget>, Attributes)`
/// takes them with an id and classes.
///
/// Everything about a column except its direction is the stylesheet's: it sets
/// no colour, no padding and no gap. The direction is the widget's, and the one
/// thing a rule cannot take: `flex-direction: column` is applied after the
/// computed style, because a `column` a stylesheet could turn into a row would
/// be a name that lies.
///
/// `accordion=#true` in markup builds an accordion instead: one `collapse`
/// child open at a time, reporting `column` as its CSS type.
///
/// Read more: [Row and column](https://goldberry.dev/docs/layout/row-and-column.html#column).
@Markup("column")
public record Column(List<Widget> children, Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Attributed<Column> {

    public Column(Widget... kids) {
        this(List.of(kids), Attributes.NONE);
    }

    /// Written out so that the parameters that take null for a default can say so.
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
    /// `accordion=#true` builds an
    /// [dev.goldberry.widgets.panel.accordion.Accordion] instead. "One section
    /// open at a time" is a rule about siblings that needs state to enforce, and
    /// a column is the most-used container in the toolkit, so the state lives on
    /// the accordion rather than on every column. The accordion reports `column`
    /// as its CSS type and adds an `accordion` class, so a stylesheet still sees
    /// a column.
    @SuppressWarnings("unused")
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        if (node.booleanProperty("accordion")) {
            return new dev.goldberry.widgets.panel.accordion.Accordion(
                    dev.goldberry.widgets.panel.accordion.Accordion.NONE, null, children, Attributes.of(node));
        }
        return new Column(children, Attributes.of(node));
    }
}
