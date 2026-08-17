package io.github.digitalsmile.goldberry.widgets;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Set;

/// The groove *ahead* of the thumb — a **part** of [Slider], and the ninth.
///
/// A node rather than nothing, because the thumb is placed by the ratio between
/// what is behind it and what is ahead of it ([SliderTrack]), and "what is ahead"
/// has to be a box for Yoga to give space to. Having it also lets a theme style
/// the unfilled groove separately from the filled one, which several design
/// systems do.
///
/// It carries no `disabled`: it paints nothing by default, and a part that is
/// invisible has no disabled appearance to select.
///
/// @param fraction its share of the free space — `1 - value`
record SliderRest(double fraction) implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "slider-rest";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).grow(fraction);
    }
}
