package dev.goldberry.widgets.core.icon;

import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.icon.Icon;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// The styled node of a **decorative** [IconView]: a **part**, CSS type
/// `icon`, and nothing a reader is told about.
///
/// A separate record from [IconFigure] rather than a flag, because whether a
/// node has semantics is a type question: an icon that answered `FIGURE`
/// with no name would be announced as "figure" and nothing else.
///
/// @param icon       what to draw, or null for nothing
/// @param attributes the view's `id` and classes
record IconBox(@Nullable Icon icon, Attributes attributes) implements Widget.Leaf, Styled, Paints {

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
}
