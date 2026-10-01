package dev.goldberry.widgets.core.image;

import java.util.List;
import java.util.Set;
import java.util.function.DoubleConsumer;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.icon.Icon;
import dev.goldberry.input.handler.Measured;
import dev.goldberry.input.hit.Extent;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// The styled node of a **decorative** [ImageView] — a **part**, CSS type
/// `image`, and nothing a reader is told about.
///
/// A separate record from [ImageFigure] rather than a flag, because whether a
/// node has semantics is a type question here: `Semantics` is implemented or it
/// is not, and a decorative picture that answered `FIGURE` with no name would be
/// announced as "figure" and nothing else.
///
/// @param load          where the load stands
/// @param alt           the alt text, shown in the box when the load failed
/// @param fit           how the pixels fill the box
/// @param measuredWidth the width the last frame laid the box out at, or 0
/// @param onMeasured    told a new width, or null when nothing depends on it
/// @param errorIcon     the icon a failed load shows, or null
/// @param attributes    the view's `id` and classes
record ImageBox(
        ImageLoad load,
        String alt,
        Fit fit,
        double measuredWidth,
        @Nullable DoubleConsumer onMeasured,
        @Nullable Icon errorIcon,
        Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Measured {

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
        return ImageView.classes(load, attributes);
    }

    @Override
    public List<Widget> children() {
        return ImageView.failureParts(load, alt, errorIcon);
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return ImagePaint.render(load, fit, measuredWidth, style, children);
    }

    @Override
    public void measured(Extent bounds, Extent part) {
        if (onMeasured != null) {
            onMeasured.accept(bounds.width());
        }
    }
}
