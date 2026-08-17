package io.github.digitalsmile.goldberry.widgets;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/// The tick or the dash inside a [CheckIndicator] — a **part**, and [RadioDot]'s
/// twin.
///
/// `docs/design-system.md` §3.1 gives `checkbox` and `radio` **one row**:
/// "check/dot: scale 0.6→1 + `opacity`, base · color fast". Both halves of that
/// row need the mark to move without its glyph moving, and a [Box.Mark] drawn
/// onto the indicator's own box cannot — scaling the box scales the 16px square
/// with it. So the mark becomes a node, exactly as the dot does, and the row is
/// satisfied for both controls by one mechanism rather than two.
///
/// This closes the "the check mark still does not scale" entry that had been open
/// since [ADR-0067](../../../../../../../book/src/adr/0067-motion-is-an-overlay-on-a-frame-clock.md)
/// shipped the opacity half without it.
///
/// ## Which shape, and why it is drawn even when nothing is checked
///
/// A node that appears only while checked cannot transition — the first frame of
/// a newly built element deliberately starts nothing — so the mark is built in
/// every state and faded by the stylesheet. `UNCHECKED` therefore has to draw
/// *some* shape at zero opacity, and it draws the tick: unchecked → checked is
/// the overwhelmingly common transition, and the one where a shape swap would be
/// visible if it happened at the wrong moment. Going to `MIXED` swaps to the dash
/// instantly and then fades it in, which is correct — the kind of mark is not a
/// property the whitelist animates, and a tick that morphed into a dash is not
/// what §3.1 asks for.
///
/// @param state     which shape to draw; also what the parent's `:checked` and
///                  `:indeterminate` come from
/// @param disabled  inherited down from the checkbox
/// @param thickness the stroke width in logical pixels — the tick and the dash
///                  are stroked, unlike the radio's filled dot
record CheckMark(Checkbox.Value state, boolean disabled, double thickness)
        implements Widget.Leaf, Styled, Paints {

    CheckMark {
        Objects.requireNonNull(state, "state");
    }

    @Override
    public String cssType() {
        return "check-mark";
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
        var kind = state == Checkbox.Value.MIXED ? Box.Mark.Kind.DASH : Box.Mark.Kind.CHECK;
        return Box.of().style(style).mark(new Box.Mark(kind, style.color(), thickness));
    }
}
