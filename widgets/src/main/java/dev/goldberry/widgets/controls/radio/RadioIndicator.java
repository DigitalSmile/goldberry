package dev.goldberry.widgets.controls.radio;

import java.util.List;
import java.util.Set;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// The 16px circle with the dot in it — a **part** of [Radio], styleable as
/// `radio-indicator` and not a node a document can write.
///
/// A part for the same two reasons as `CheckIndicator`. A radio has two
/// surfaces a theme must style separately — the row, which is 32 tall and holds
/// the label, and the glyph, which is 16 square and is what fills with the
/// accent — and a [ComputedStyle] carries one background, one border and one
/// radius. And a `radio-indicator` outside a `radio` is a circle that means
/// nothing, so registering the KDL node would let a document create exactly
/// that.
///
/// The circle is the radius, not a new shape: `border-radius: 8px` on a 16px
/// box is a circle, drawn by the same rounded rectangle every box is. Nothing
/// here draws a circle, which is why a theme can make a radio square-ish
/// without a Java change.
///
/// The dot itself is a **second** node, [RadioDot], and not a mark on this box:
/// it scales from 0.6 to 1 while the ring stays put, and a mark cannot move
/// independently of the box it is drawn onto.
///
/// @param selected whether to draw the dot, which is also what `:checked` is
///                 mirrored from
/// @param disabled inherited from the radio, so `radio-indicator:disabled` is
///                 selectable without a descendant combinator
record RadioIndicator(boolean selected, boolean disabled) implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "radio-indicator";
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
    public boolean isChecked() {
        return selected;
    }

    /// The dot, always — see [RadioDot] for why it is a node rather than a mark
    /// on this box, and why it is built in both states.
    @Override
    public List<Widget> children() {
        return List.of(new RadioDot(disabled));
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).children(children.toArray(Box[]::new));
    }
}
