package io.github.digitalsmile.goldberry.widgets;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Set;

/// The 4px channel a slider's thumb runs along — a **part** of [Slider], and the
/// eighth.
///
/// ADR-0065's argument a fifth time and unchanged: the control is 32 tall because
/// §1.3 wants a hit target, the groove is 4 because §3 says so, and one
/// [ComputedStyle] carries one background. It was called `slider-track` until the
/// value label arrived and the two boxes stopped being the same one — the track
/// is the rectangle the value is *measured* along, and the groove is the one you
/// can see ([ADR-0080], [SliderTrack]).
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
/// Yoga hands free space out in proportion to the grow factors, so the thumb
/// lands exactly `f` of the way along whatever width the groove turned out to be —
/// and **nothing in Java ever learns that width.** That matters because the
/// obvious implementation is `transform: translate`, and a transform cannot
/// express it: CSS percentages in `translate` are a proportion of *the moving
/// box*, so `translate(50%)` moves the thumb by half a thumb, not to the middle
/// of the groove ([ADR-0079]).
///
/// It also produces the filled portion for free, as a box the cascade can reach.
///
/// @param fraction where the thumb sits, `0..1`
/// @param disabled inherited from the slider, so a part is selectable without a
///                 descendant combinator
record SliderGroove(double fraction, boolean disabled) implements Widget.Leaf, Styled, Paints {

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
        return List.of(
                new SliderFill(fraction, disabled),
                new SliderThumb(disabled),
                new SliderRest(1 - fraction));
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).children(children.toArray(Box[]::new));
    }
}
