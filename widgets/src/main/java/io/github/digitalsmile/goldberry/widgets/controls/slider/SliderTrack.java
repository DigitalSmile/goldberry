package io.github.digitalsmile.goldberry.widgets.controls.slider;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/// The full-height box a slider's value is measured along — a **part** of
/// [Slider], and the seventh.
///
/// It paints nothing. What it is, is a **rectangle**: the groove is 4px tall and
/// the control is 32, and between them the thing the pointer is mapped along has
/// to be one specific box. Until §3's value label there was no difference — the
/// track was the control — and a label at the end of the row is exactly what
/// makes them different, by its own width
/// ([ADR-0080](../../../../../../../../book/src/adr/0080-a-value-is-measured-along-a-part.md)).
/// [Slider#localPart()] names this part, and the router measures against it.
///
/// It is also what gives the groove and the tick marks somewhere to be *stacked*:
/// the slider's own axis is taken by the value, and a scale under a groove is the
/// cross axis of a control that has no cross axis left.
///
/// @param fraction where the thumb sits, `0..1`, passed to the groove
/// @param ticks    how many marks to draw under it; `0` for none
/// @param disabled inherited from the slider, so a part is selectable without a
///                 descendant combinator
record SliderTrack(double fraction, int ticks, boolean disabled)
        implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "slider-track";
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
    public List<Widget> children() {
        var children = new ArrayList<Widget>(2);
        children.add(new SliderGroove(fraction, disabled));
        if (ticks >= 2) {
            children.add(new SliderTicks(ticks, disabled));
        }
        return List.copyOf(children);
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).children(children.toArray(Box[]::new));
    }
}
