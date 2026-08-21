package io.github.digitalsmile.goldberry.widgets.panel.accordion;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.natives.yoga.FlexDirection;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Set;

/// **This is the `column` a stylesheet selects.**
///
/// An accordion *is* a column — the flag §5 puts on `column` says something about
/// how its children behave, not about what it is — so this reports `column` as
/// its CSS type and adds an `accordion` class beside whatever the document wrote.
/// A rule written for `column` therefore still applies, which is the point: an
/// author who turns a column into an accordion has not changed its appearance and
/// should not have to restate its padding ([ADR-0166]).
///
/// The direction is applied after the style for
/// [io.github.digitalsmile.goldberry.widgets.core.Column]'s reason: a `column` a
/// stylesheet could turn into a row would be a name that lies.
record AccordionColumn(List<Widget> children, Attributes attributes)
        implements Widget.Leaf, Styled, Paints {

    AccordionColumn {
        children = List.copyOf(children == null ? List.of() : children);
        attributes = attributes == null ? Attributes.NONE : attributes;
    }

    @Override
    public String cssType() {
        return "column";
    }

    @Override
    public String id() {
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
        return Box.of().style(style)
                .direction(FlexDirection.COLUMN)
                .children(boxes.toArray(Box[]::new));
    }
}
