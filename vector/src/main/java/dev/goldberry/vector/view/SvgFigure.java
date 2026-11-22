package dev.goldberry.vector.view;

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
import dev.goldberry.widget.semantics.Role;
import dev.goldberry.widget.semantics.Semantics;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;
import dev.goldberry.widgets.core.image.Fit;

/// The styled node of an [SvgView] that means something: CSS type `svg-view`,
/// announced as a figure named by its alt text, as `image` is.
///
/// @param load          where the load stands
/// @param alt           the alt text, shown in the box when the load failed
/// @param fit           how the document fills the box
/// @param measuredWidth the width the last frame laid the box out at, or 0
/// @param onMeasured    told a new width, or null when nothing depends on it
/// @param errorIcon     the icon a failed load shows, or null
/// @param attributes    the view's `id` and classes
record SvgFigure(
        SvgLoad load,
        String alt,
        Fit fit,
        double measuredWidth,
        @Nullable DoubleConsumer onMeasured,
        @Nullable Icon errorIcon,
        Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Measured, Semantics {

    @Override
    public String cssType() {
        return SvgView.CSS_TYPE;
    }

    @Override
    public @Nullable String id() {
        return attributes.id();
    }

    @Override
    public Set<String> classes() {
        return SvgView.classes(load, attributes);
    }

    @Override
    public List<Widget> children() {
        return SvgView.failureParts(load, alt, errorIcon);
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return SvgPaint.render(load, fit, measuredWidth, style, children);
    }

    @Override
    public void measured(Extent bounds, Extent part) {
        if (onMeasured != null) {
            onMeasured.accept(bounds.width());
        }
    }

    @Override
    public Role role() {
        return Role.FIGURE;
    }

    @Override
    public String accessibleName() {
        return alt;
    }
}
