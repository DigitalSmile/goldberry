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
    /// [io.github.digitalsmile.goldberry.widgets.panel.accordion.Accordion], which
    /// reports `column` as its own CSS type and adds an `accordion` class. A
    /// document writes what §5 says, a stylesheet still sees a column, and an
    /// ordinary column pays nothing ([ADR-0166]).
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        if (node.booleanProperty("accordion")) {
            return new io.github.digitalsmile.goldberry.widgets.panel.accordion.Accordion(
                    io.github.digitalsmile.goldberry.widgets.panel.accordion.Accordion.NONE,
                    null, children, Attributes.of(node));
        }
        return new Column(children, Attributes.of(node));
    }
}
