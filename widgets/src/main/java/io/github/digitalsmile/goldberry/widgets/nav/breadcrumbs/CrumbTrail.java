package io.github.digitalsmile.goldberry.widgets.nav.breadcrumbs;

import java.util.List;
import java.util.Set;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.semantics.Role;
import io.github.digitalsmile.goldberry.widget.semantics.Semantics;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// What a [Breadcrumbs] draws: the row itself.
///
/// `breadcrumbs` as a **CSS type** is this node and not the stateful one above
/// it, which is [io.github.digitalsmile.goldberry.widgets.panel.tabs.TabStrip]'s
/// arrangement and its reason — two `breadcrumbs` nodes nested in the cascade
/// would take every rule twice (ADR-0109).
///
/// The attributes are the trail's own, carried down so that `#path` and
/// `.compact` land on the node a stylesheet can see. The id in particular has to
/// be here: an id on a node nothing paints would be an anchor that
/// [io.github.digitalsmile.goldberry.Host#anchor] never finds.
///
/// @param children   the crumbs, separators and overflow, already interleaved
/// @param attributes the trail's, verbatim
record CrumbTrail(List<Widget> children, Attributes attributes) implements Widget.Leaf, Styled, Paints, Semantics {

    CrumbTrail {
        children = List.copyOf(children == null ? List.of() : children);
        attributes = attributes == null ? Attributes.NONE : attributes;
    }

    @Override
    public String cssType() {
        return "breadcrumbs";
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
        return Box.of().style(style).children(boxes.toArray(Box[]::new));
    }

    /// [Role#GROUP] — "a region with a boundary and no better word".
    ///
    /// §6 asks for a *navigation landmark*, and [Role] has none. `GROUP` is the
    /// honest approximation rather than a new constant nothing consumes: the
    /// landmark half of that sentence needs the AccessKit bridge, and inventing
    /// the role now would make a gap look closed. `book/src/TODO.md` carries it.
    @Override
    public Role role() {
        return Role.GROUP;
    }
}
