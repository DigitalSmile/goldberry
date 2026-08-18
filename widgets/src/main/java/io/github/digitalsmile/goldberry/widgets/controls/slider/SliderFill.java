package io.github.digitalsmile.goldberry.widgets.controls.slider;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Set;

/// The part of the groove behind the thumb, drawn in the accent — a **part** of
/// [Slider], and the eighth.
///
/// It exists because it is where the value *is* rather than as decoration: a
/// slider with no fill reads as a groove with a dot on it, and the fill is what
/// says "this much". It costs nothing extra, being the flex child that positions
/// the thumb anyway (see [SliderTrack]).
///
/// @param fraction its share of the free space — the slider's value, `0..1`
record SliderFill(double fraction, boolean disabled) implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "slider-fill";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public boolean isDisabled() {
        return disabled;
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        // The one value the stylesheet cannot supply, because it is the slider's
        // *value* and not its appearance. Applied after `style`, so a theme still
        // owns the colour, the height and the radius of what it fills.
        return Box.of().style(style).grow(fraction);
    }
}
