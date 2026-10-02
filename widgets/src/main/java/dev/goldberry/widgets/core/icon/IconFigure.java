package dev.goldberry.widgets.core.icon;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.icon.Icon;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.semantics.Role;
import dev.goldberry.widget.semantics.Semantics;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// The styled node of an [IconView] that means something: a **part**, CSS
/// type `icon`, announced as a figure with the view's `name=`.
///
/// [Role#FIGURE], which `image` and `qr-code` answer too: the role set has no
/// picture of its own.
///
/// @param icon       what to draw, or null for nothing
/// @param attributes the view's `id`, classes and name
record IconFigure(@Nullable Icon icon, Attributes attributes) implements Widget.Leaf, Styled, Paints, Semantics {

    @Override
    public String cssType() {
        return IconView.CSS_TYPE;
    }

    @Override
    public @Nullable String id() {
        return attributes.id();
    }

    @Override
    public Set<String> classes() {
        return IconView.classes(icon, attributes);
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return IconPaint.render(icon, style);
    }

    @Override
    public Role role() {
        return Role.FIGURE;
    }

    /// The view's `name=`, which is why this part was chosen.
    @Override
    public String accessibleName() {
        return Objects.requireNonNullElse(attributes.name(), "");
    }
}
