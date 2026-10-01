package dev.goldberry.widgets.controls.slider;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// The full-height box a slider's value is measured along — a **part** of
/// [Slider], and the seventh.
///
/// It paints nothing. What it is, is a **rectangle**: the groove is 4px tall and
/// the control is 32, and between them the thing the pointer is mapped along has
/// to be one specific box. Until §3's value label there was no difference — the
/// track was the control — and a label at the end of the row is exactly what
/// makes them different, by its own width
/// (ADR-0080).
/// [SliderControl#localPart()] names this part, and the router measures against
/// it.
///
/// It is also what gives the groove and the tick marks somewhere to be *stacked*:
/// the slider's own axis is taken by the value, and a scale under a groove is the
/// cross axis of a control that has no cross axis left.
///
/// @param fraction where the thumb sits, `0..1`, passed to the groove
/// @param ticks    how many marks to draw under it; `0` for none
/// @param disabled inherited from the slider, so a part is selectable without a
///                 descendant combinator
/// @param spans    the slider's spans as fractions of the groove, passed to it
/// @param vertical whether the groove runs bottom to top
record SliderTrack(double fraction, int ticks, boolean disabled, List<Slider.Span> spans, boolean vertical)
        implements Widget.Leaf, Styled, Paints {

    SliderTrack {
        spans = List.copyOf(spans);
    }

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
        children.add(new SliderGroove(fraction, disabled, spans, vertical));
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
