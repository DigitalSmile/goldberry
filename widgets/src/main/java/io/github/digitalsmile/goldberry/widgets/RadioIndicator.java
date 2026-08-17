package io.github.digitalsmile.goldberry.widgets;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Set;

/// The 16px circle with the dot in it — a **part** of [Radio], and the second
/// part in the toolkit.
///
/// ## Why this is a part and not a widget, again
///
/// [CheckIndicator] made the argument and [ADR-0065] asked that it be made again
/// rather than assumed: a part is not a general escape hatch, and the second one
/// is where a pattern either holds or turns out to have been a special case. It
/// holds, for the same two reasons and no new ones. A radio has two surfaces a
/// theme must style separately — the row, which is 32 tall and holds the label,
/// and the glyph, which is 16 square and is what fills with the accent — and a
/// [ComputedStyle] carries one background, one border and one radius. And a
/// `radio-indicator` outside a `radio` is a circle that means nothing, so
/// registering the KDL node would let a document create exactly that.
///
/// ## The circle is the radius, not a new shape
///
/// `border-radius: 8px` on a 16px box is a circle, drawn by the four cubics
/// [ADR-0064] already ships. Nothing here draws a circle: this part is a square
/// box that the stylesheet rounds, which is why a theme can make a radio
/// square-ish without a Java change, and why no native symbol was needed.
///
/// The dot itself is a **second** node, [RadioDot], and not a mark on this box:
/// §3.1 asks it to scale 0.6→1 while the ring stays put, and a mark cannot move
/// independently of the box it is drawn onto.
///
/// @param selected whether to draw the dot, which is also what `:checked` is
///                 mirrored from
/// @param disabled inherited from the radio, so `radio-indicator:disabled` is
///                 selectable without a descendant combinator
record RadioIndicator(boolean selected, boolean disabled)
        implements Widget.Leaf, Styled, Paints {

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
