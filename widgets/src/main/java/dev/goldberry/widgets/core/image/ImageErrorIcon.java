package dev.goldberry.widgets.core.image;

import java.util.List;
import java.util.Set;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.icon.Icon;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// Lucide's `image-off` in a failed image — a **part**, `image-icon`, drawn in
/// the node's colour.
///
/// @param icon the icon, owned by the view's state
record ImageErrorIcon(Icon icon) implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "image-icon";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.icon(icon, style.color());
    }
}
