package dev.goldberry.widgets.nav.breadcrumbs;

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

/// What a [Breadcrumbs] draws: the row itself.
///
/// `breadcrumbs` as a **CSS type** is this node and not the stateful one above
/// it, which is [dev.goldberry.widgets.panel.tabs.TabStrip]'s
/// arrangement and its reason — two `breadcrumbs` nodes nested in the cascade
/// would take every rule twice.
///
/// The attributes are the trail's own, carried down so that `#path` and
/// `.compact` land on the node a stylesheet can see. The id in particular has to
/// be here: an id on a node nothing paints would be an anchor that
/// [dev.goldberry.Host#anchor] never finds.
///
/// @param children   the crumbs, separators and overflow, already interleaved
/// @param attributes the trail's, verbatim
record CrumbTrail(List<Widget> children, Attributes attributes) implements Widget.Leaf, Styled, Paints, Semantics {

    /// Written out so that the parameters taking null for a default can say so.
    CrumbTrail(@Nullable List<Widget> children, @Nullable Attributes attributes) {
        children = List.copyOf(children == null ? List.of() : children);
        attributes = attributes == null ? Attributes.NONE : attributes;
        this.children = children;
        this.attributes = attributes;
    }

    @Override
    public String cssType() {
        return "breadcrumbs";
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
        return Box.of().style(style).children(boxes.toArray(Box[]::new));
    }

    /// [Role#GROUP] — "a region with a boundary and no better word".
    ///
    /// A trail is a *navigation landmark*, and [Role] has none. `GROUP` is the
    /// honest approximation rather than a new constant nothing consumes: the
    /// landmark needs an accessibility bridge to mean anything, and inventing
    /// the role now would make a gap look closed.
    @Override
    public Role role() {
        return Role.GROUP;
    }
}
