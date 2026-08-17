package io.github.digitalsmile.goldberry.widgets;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Set;

/// The number beside the groove — a **part** of [Slider], and the thirteenth.
/// `docs/core-widgets.md` §3's "optional value label".
///
/// A part rather than a `text` child, for the reason every part in this catalog
/// is one: it needs a width of its own. A label that sized itself to its content
/// would **resize the track as the digits change** — dragging from 9 to 10 would
/// take three pixels off the track under the finger, which moves the value under
/// the pointer that is setting it, at the moment it is being set. The width is
/// the stylesheet's (`slider-value { width: … }`), and the drift is gone because
/// there is nothing left to drift
/// ([ADR-0080](../../../../../../book/src/adr/0080-a-value-is-measured-along-a-part.md)).
///
/// It draws text directly rather than holding a [io.github.digitalsmile.goldberry.widget.Widgets.Text]
/// child, so `slider-value` is one node with one [ComputedStyle] — the type
/// selector is the whole of what an author needs to restyle it, and a `text`
/// inside it would be a second node for `slider-value text` to have to reach.
///
/// @param text     what the slider's format produced, already a string
/// @param disabled inherited from the slider, so a part is selectable without a
///                 descendant combinator
record SliderValue(String text, boolean disabled) implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "slider-value";
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
        // A measured leaf, exactly as `text` is: the paragraph reports how tall
        // it came out at the width Yoga proposed (ADR-0036). The width is the
        // stylesheet's, so what is measured here is only the height.
        return Box.text(context.paragraph(style, text == null ? "" : text), style.color())
                .style(style);
    }
}
