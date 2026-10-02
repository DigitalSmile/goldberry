package dev.goldberry.widgets.nav.steps;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.semantics.Role;
import dev.goldberry.widget.semantics.Semantics;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// What a [Steps] draws: the row, or the column.
///
/// `steps` as a **CSS type** is this node and not the composition above it, so
/// a stylesheet's rules apply once rather than to two nested `steps` nodes.
/// A vertical list carries the class `vertical`, which is the whole
/// of how the stylesheet turns it — the same spelling `slider.vertical` uses.
///
/// The attributes are the list's own, carried down so that `#progress` and
/// `.compact` land on the node a stylesheet can see.
///
/// @param children   the steps and connectors, already interleaved
/// @param direction  which way this runs
/// @param attributes the list's, verbatim
record StepList(List<Widget> children, Steps.Direction direction, Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Semantics {

    /// Written out so that the parameters taking null for a default can say so.
    StepList(@Nullable List<Widget> children, Steps.Direction direction, @Nullable Attributes attributes) {
        children = List.copyOf(children == null ? List.of() : children);
        attributes = attributes == null ? Attributes.NONE : attributes;
        this.children = children;
        this.direction = direction;
        this.attributes = attributes;
    }

    @Override
    public String cssType() {
        return "steps";
    }

    @Override
    public @Nullable String id() {
        return attributes.id();
    }

    @Override
    public Set<String> classes() {
        if (direction != Steps.Direction.VERTICAL) {
            return attributes.classes();
        }
        var classes = new HashSet<>(attributes.classes());
        classes.add(Steps.Direction.VERTICAL_CLASS);
        return Set.copyOf(classes);
    }

    @Override
    public @Nullable Object key() {
        return attributes.key();
    }

    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        return Box.of().style(style).children(boxes.toArray(Box[]::new));
    }

    /// [Role#GROUP] — a list with the current item marked is what this is, and
    /// [Role] has no list. The items answer [Role#ROW] and the current one is
    /// `:checked`, so a bridge that reads the tree reads the list; the word for
    /// the container waits for an accessibility bridge, as `breadcrumbs`' does.
    @Override
    public Role role() {
        return Role.GROUP;
    }
}
