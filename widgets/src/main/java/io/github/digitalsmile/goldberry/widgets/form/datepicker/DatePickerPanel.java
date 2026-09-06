package io.github.digitalsmile.goldberry.widgets.form.datepicker;

import java.util.List;
import java.util.Set;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// The surface the grid opens on — `date-picker-panel`, a **part**, and §2's
/// "popup radius 12, padding 8".
///
/// A node of its own rather than styling the `calendar` inside the popup,
/// because a `calendar` is §10's widget and draws no surface: it sits on whatever
/// it was put on, exactly as `panel` and `chart` do, so that one on a card is not
/// a card inside a card. Something has to be that surface when the calendar is
/// floating over a window, and this is it — `select-list`'s job for `select`, in
/// the shape `select-list` already has.
///
/// @param calendar the grid to put on it
record DatePickerPanel(Widget calendar) implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "date-picker-panel";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public List<Widget> children() {
        return List.of(calendar);
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).children(children.toArray(Box[]::new));
    }
}
