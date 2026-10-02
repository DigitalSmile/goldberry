package dev.goldberry.widgets.controls.slider;

import java.util.List;
import java.util.Set;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// The 16px disc a slider is dragged by — a part of [Slider], styled as
/// `slider-thumb`.
///
/// Unlike `ToggleThumb` it carries **no transform at all**, and that is the whole
/// difference between the two controls: a switch has two positions and a
/// stylesheet can name both, while a slider has a continuum and no stylesheet can
/// name a number that came from a model. It is placed by the flex ratio around it
/// instead ([SliderGroove]).
///
/// Which also means it does not animate on drag: the drag is one to one. A
/// thumb that eased toward the pointer would lag the finger, which is the one
/// thing a direct-manipulation control must not do.
///
/// Read more: [Values and progress](https://goldberry.dev/docs/components/values.html#slider).
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
