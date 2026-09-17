package io.github.digitalsmile.goldberry.widgets.nav.wizard;

import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.semantics.Role;
import io.github.digitalsmile.goldberry.widget.semantics.Semantics;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// What a [Wizard] draws: the indicator, the content and the bar, in a column.
///
/// `wizard` as a **CSS type** is this node and not the stateful one above it
/// (ADR-0109). The attributes are the wizard's own, carried down so `#signup`
/// lands on the node a stylesheet can see.
///
/// @param children   the indicator, the content area and the action bar
/// @param name       the accessible name — the current page and the position
/// @param attributes the wizard's, verbatim
record WizardPanel(List<Widget> children, String name, Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Semantics {

    @Override
    public String cssType() {
        return "wizard";
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

    /// [Role#GROUP] "with the step count and position" — §6's semantics line,
    /// and the position is the name.
    @Override
    public Role role() {
        return Role.GROUP;
    }

    @Override
    public String accessibleName() {
        return name;
    }
}
