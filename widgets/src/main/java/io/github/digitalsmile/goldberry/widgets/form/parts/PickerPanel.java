package io.github.digitalsmile.goldberry.widgets.form.parts;

import java.util.List;
import java.util.Set;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// The surface a picker's popover opens on — `picker-panel`, a **part**, and
/// §2's "popup radius 12, padding 8".
///
/// A node of its own rather than styling whatever is inside the popup, because
/// the things that go in one draw no surface: a `calendar` "sits on whatever it
/// was put on", which is `panel`'s rule and `chart`'s, so that one on a card is
/// not a card inside a card. Something has to be the surface when it is floating
/// over a window, and this is it — `select-list`'s job, in the shape
/// `select-list` already has.
///
/// Shared by all three of §4's pickers for [PickerToggle]'s reason: §2 gives
/// `date-picker` and `time-picker` the same popup metrics on the same row, and
/// three identical rules is how one of them drifts.
///
/// @param content what to put on it
public record PickerPanel(Widget content) implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "picker-panel";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public List<Widget> children() {
        return List.of(content);
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).children(children.toArray(Box[]::new));
    }
}
