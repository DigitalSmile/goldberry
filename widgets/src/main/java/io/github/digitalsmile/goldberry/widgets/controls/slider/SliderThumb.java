package io.github.digitalsmile.goldberry.widgets.controls.slider;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Set;

/// The 16px disc a slider is dragged by — a **part** of [Slider], and the tenth.
///
/// Unlike `ToggleThumb` it carries **no transform at all**, and that is the whole
/// difference between the two controls: a switch has two positions and a
/// stylesheet can name both, while a slider has a continuum and no stylesheet can
/// name a number that came from a model. It is placed by the flex ratio around it
/// instead ([SliderTrack], [ADR-0079]).
///
/// Which also means it does not animate on drag, and §3.1 asks for exactly that:
/// "drag: **1:1, no animation**". A thumb that eased toward the pointer would lag
/// the finger, which is the one thing a direct-manipulation control must not do.
record SliderThumb(boolean disabled) implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "slider-thumb";
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
        return Box.of().style(style);
    }
}
