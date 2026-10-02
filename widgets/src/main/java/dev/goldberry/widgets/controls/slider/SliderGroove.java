package dev.goldberry.widgets.controls.slider;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// The 4px channel a slider's thumb runs along — a part of [Slider], styled as
/// `slider-groove`.
///
/// A part because it needs a background of its own: the control is 32 tall so
/// there is a hit target, the groove is 4, and one [ComputedStyle] carries one
/// background. It is a different box from [SliderTrack] because the value label
/// made the two differ — the track is the rectangle the value is *measured*
/// along, and the groove is the one you can see.
///
/// Read more: [Values and progress](https://goldberry.dev/docs/components/values.html#slider).
///
/// ## How the thumb is placed, which is the interesting part
///
/// Its three children are a **fill**, the **thumb**, and a spacer — and they are
/// positioned by `flex-grow` rather than by a transform:
///
/// ```
/// [ fill grow=f ][ thumb 16 ][ rest grow=1-f ]
/// ```
///
/// Layout hands free space out in proportion to the grow factors, so the thumb
/// lands exactly `f` of the way along whatever width the groove turned out to be —
/// and **nothing in Java ever learns that width.** That matters because the
/// obvious implementation is `transform: translate`, and a transform cannot
/// express it: CSS percentages in `translate` are a proportion of *the moving
/// box*, so `translate(50%)` moves the thumb by half a thumb, not to the middle
/// of the groove.
///
/// It also produces the filled portion for free, as a box the cascade can reach.
///
/// The slider's [Slider#spans()] come first, as [SliderSpan]s placed out of flow,
/// so they take no part in the ratio and are painted under the fill and the
/// thumb.
///
/// @param fraction where the thumb sits, `0..1`
/// @param disabled inherited from the slider, so a part is selectable without a
///                 descendant combinator
/// @param spans    stretches to mark, as fractions of the groove
/// @param vertical whether it runs bottom to top, which the spans' insets follow
record SliderGroove(double fraction, boolean disabled, List<Slider.Span> spans, boolean vertical)
        implements Widget.Leaf, Styled, Paints {

    SliderGroove {
        spans = List.copyOf(spans);
    }

    @Override
    public String cssType() {
        return "slider-groove";
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
        var children = new ArrayList<Widget>(spans.size() + 3);
        for (var span : spans) {
            children.add(new SliderSpan(span.from(), span.to(), vertical, disabled));
        }
        children.add(new SliderFill(fraction, disabled));
        children.add(new SliderThumb(disabled));
        children.add(new SliderRest(1 - fraction));
        return List.copyOf(children);
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).children(children.toArray(Box[]::new));
    }
}
