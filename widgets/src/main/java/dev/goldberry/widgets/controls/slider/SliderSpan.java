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

/// One of a slider's [Slider#spans()], drawn in the groove under the fill and
/// the thumb: a stretch of the range the application marks, such as what a
/// media player has buffered.
///
/// **Placed by percentage insets, out of flow.** The groove's own children place
/// the thumb by flex ratio ([SliderGroove]), and a span must not take part in
/// that. An absolute box against the groove, inset by the span's fractions,
/// lands on its stretch whatever width the groove turned out to be, and nothing
/// in Java learns the width, which is the property the thumb's placement keeps
/// too. A span is measured along the whole groove where the thumb's centre is
/// measured along its travel, so the two agree in the middle and differ by at
/// most half a thumb at the ends.
///
/// Its height is the groove's: the insets across the axis are zero, so a theme
/// sets only its colour and radius.
///
/// @param from     where it starts along the groove, `0..1`
/// @param to       where it ends, `from..1`
/// @param vertical whether the groove runs bottom to top (`slider.vertical`)
/// @param disabled inherited from the slider, so a part is selectable without a
///                 descendant combinator
record SliderSpan(double from, double to, boolean vertical, boolean disabled) implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "slider-span";
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
        var start = Length.percent((float) (from * 100));
        var end = Length.percent((float) ((1 - to) * 100));
        var inset = vertical ? new Insets(end, zero, start, zero) : new Insets(zero, end, zero, start);
        return Box.of().style(style).position(Position.ABSOLUTE).inset(inset);
    }
}
