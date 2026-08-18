package io.github.digitalsmile.goldberry.widgets.controls.toggle;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Set;

/// The 36×20 pill the thumb slides in — a **part** of [Toggle], and the fifth.
///
/// Same argument as `CheckIndicator`, made a third time: a toggle has two
/// surfaces a theme must style separately — the row that holds the label and is
/// the hit target, and the pill that turns accent-coloured when on — and one
/// [ComputedStyle] carries one background. It is also CSS-selectable and
/// deliberately **not** KDL-constructible, because a `toggle-track` outside a
/// `toggle` is a pill that means nothing
/// ([ADR-0065](../../../../../../../../book/src/adr/0065-a-part-is-styleable-and-not-constructible.md)).
///
/// It is the node `:checked` is mirrored onto rather than [Toggle] alone, which
/// is what lets the stylesheet write `toggle-track:checked toggle-thumb` and move
/// the thumb without Java deciding where it goes. `toggle:checked` matches too —
/// the control mirrors its own state as well — so a theme can reach either.
///
/// @param on       whether the switch is on, which is also what `:checked` is
///                 mirrored from
/// @param disabled inherited from the toggle, so a part is selectable without a
///                 descendant combinator
record ToggleTrack(boolean on, boolean disabled) implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "toggle-track";
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
        return on;
    }

    /// The thumb, always — there is no state in which a switch has none, which is
    /// why this needs none of `CheckMark`'s argument about a node that appears
    /// with the value and would snap.
    @Override
    public List<Widget> children() {
        return List.of(new ToggleThumb(disabled));
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).children(children.toArray(Box[]::new));
    }
}
