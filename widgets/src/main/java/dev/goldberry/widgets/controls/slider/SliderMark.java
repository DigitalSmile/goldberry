package dev.goldberry.widgets.controls.slider;

import java.util.List;
import java.util.Set;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.layout.Insets;
import dev.goldberry.layout.Length;
import dev.goldberry.layout.Position;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// One of a slider's [Slider#marks()], notched across the groove over the fill
/// and under the thumb: a point of the range the application marks, such as
/// where a media player's chapter starts.
///
/// **Placed as a [SliderSpan] is**, out of flow and by percentage insets, so
/// it lands on its point whatever width the groove turned out to be and takes
/// no part in the ratio that places the thumb. Both insets along the axis meet
/// at the point, which leaves a box of no width there; a theme's negative
/// `margin` on both sides is what widens it, by the same amount each way, so
/// it stays centred on the point. Its height is the groove's.
///
/// @param at       where it sits along the groove, `0..1`
/// @param vertical whether the groove runs bottom to top (`slider.vertical`)
/// @param disabled inherited from the slider, so a part is selectable without a
///                 descendant combinator
record SliderMark(double at, boolean vertical, boolean disabled) implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "slider-mark";
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
        var zero = Length.points(0);
        var start = Length.percent((float) (at * 100));
        var end = Length.percent((float) ((1 - at) * 100));
        var inset = vertical ? new Insets(end, zero, start, zero) : new Insets(zero, end, zero, start);
        return Box.of().style(style).position(Position.ABSOLUTE).inset(inset);
    }
}
