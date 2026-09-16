package io.github.digitalsmile.goldberry.widgets.form.textarea;

import java.util.List;
import java.util.Set;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// The column the line numbers sit in — `text-area-gutter`, a **part**, so it is
/// CSS-selectable and not constructible
/// (ADR-0065).
///
/// It draws nothing itself beyond whatever the stylesheet gives it: a fill, and
/// usually a rule down its right-hand edge. It exists as a node rather than as a
/// rectangle the box paints because that is what makes the column themeable at
/// all — `text-area-gutter { background: … ; border-right: … }` is a rule an
/// author can write, and a hard-coded fill is not (`docs/gaps.md` G37,
/// [ADR-0331]).
///
/// **It is not what is numbered.** The numbers are drawn by [TextAreaBox] itself
/// as one paragraph, because where a hard line ended up is a fact about the wrap
/// and a column of number *nodes* would be a frame behind the text on every
/// keystroke that changed the line structure — see that class. Their ink is
/// `--gb-gutter-color`, declared on `text-area`; this is only the strip behind
/// them, stretched from the top of the control to the bottom so it does not end
/// where the text does.
record TextAreaGutter() implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "text-area-gutter";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style);
    }
}
