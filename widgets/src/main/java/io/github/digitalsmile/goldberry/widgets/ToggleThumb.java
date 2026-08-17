package io.github.digitalsmile.goldberry.widgets;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Set;

/// The 16px disc that slides — a **part** of [Toggle], and the sixth part.
///
/// It is a node of its own for the reason
/// [ADR-0073](../../../../../../book/src/adr/0073-a-composite-is-one-tab-stop.md)
/// established for `check-mark`: **two things must move independently, and the
/// unit of independent movement is a cascade node.** `docs/design-system.md` §3.1
/// asks for "thumb `translate` base; track color base", which is a transform on
/// one box and a background on another — and a `transform` applies down its
/// subtree, so a thumb drawn onto the track would slide the track with it.
///
/// It carries no state of its own. Whether it is at rest or travelled is
/// `toggle-track:checked toggle-thumb { transform: … }` in the stylesheet — the
/// state lives on the track, which is the node `:checked` is mirrored onto, and
/// the thumb is positioned by a descendant selector rather than by a flag passed
/// down here. A theme can therefore change where the thumb travels to, or stop it
/// travelling, without a Java change.
///
/// @param disabled inherited from the toggle, so `toggle-thumb:disabled` is
///                 selectable without a descendant combinator — the same reason
///                 [CheckMark] takes it
record ToggleThumb(boolean disabled) implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "toggle-thumb";
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
        return Box.of().style(style);
    }
}
