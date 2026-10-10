package dev.goldberry.widgets.core.image;

import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// The styled node of a **decorative** [AnimationView] — a **part**, CSS type
/// `image`, and nothing a reader is told about; [ImageBox]'s counterpart, a
/// separate record for its reason.
///
/// @param paint      the animation and where it has got to
/// @param attributes the view's `id` and classes
record AnimationBox(AnimationPaint paint, Attributes attributes) implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return ImageView.CSS_TYPE;
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
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return paint.render(style, children, context);
    }

    @Override
    public boolean isAnimating(ComputedStyle style, Context context) {
        return paint.isAnimating(context);
    }
}
