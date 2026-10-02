package dev.goldberry.widgets.panel.accordion;

import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.layout.FlexDirection;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// The `column` a stylesheet selects when a document writes an accordion.
///
/// An accordion *is* a column: the `accordion` flag says how its children
/// behave, not what the container is. So this reports `column` as its CSS type
/// and adds an `accordion` class beside whatever the document wrote. A rule
/// written for `column` still applies, which is the point: an author who turns
/// a column into an accordion has not changed its appearance and should not
/// have to restate its padding.
///
/// The direction is applied after the style for
/// [dev.goldberry.widgets.core.Column]'s reason: a `column` a
/// stylesheet could turn into a row would be a name that lies.
record AccordionColumn(List<Widget> children, Attributes attributes) implements Widget.Leaf, Styled, Paints {

    /// Written out so that the parameters taking null for a default can say so.
    AccordionColumn(@Nullable List<Widget> children, @Nullable Attributes attributes) {
        children = List.copyOf(children == null ? List.of() : children);
        attributes = attributes == null ? Attributes.NONE : attributes;
        this.children = children;
        this.attributes = attributes;
    }

    @Override
    public String cssType() {
        return "column";
    }

    @Override
    public @Nullable String id() {
        return attributes.id();
    }

    @Override
    public Set<String> classes() {
        var all = new java.util.LinkedHashSet<>(attributes.classes());
        all.add("accordion");
        return Set.copyOf(all);
    }

    @Override
    public List<Widget> children() {
        return children;
    }

    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        return Box.of().style(style).direction(FlexDirection.COLUMN).children(boxes.toArray(Box[]::new));
    }
}
