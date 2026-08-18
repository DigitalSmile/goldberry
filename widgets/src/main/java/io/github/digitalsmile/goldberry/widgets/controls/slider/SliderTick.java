package io.github.digitalsmile.goldberry.widgets.controls.slider;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Set;

/// One mark on a slider's scale — a **part** of [Slider], and the twelfth.
///
/// It carries no position: [SliderTicks] places it, and it is the same box
/// wherever it lands. That is deliberate — a mark that knew its own index would
/// be a mark a stylesheet could style differently at the ends, and a scale whose
/// first mark is not like its last is a scale with a bug in it.
///
/// @param disabled inherited from the slider, so a part is selectable without a
///                 descendant combinator
record SliderTick(boolean disabled) implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "slider-tick";
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
