package io.github.digitalsmile.goldberry.widgets.panel.calendar;

import java.util.List;
import java.util.Set;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// The month, and the two ways to leave it — `calendar-header`, a **part**.
///
/// ## An addition, and it is written down as one
///
/// §10 gives this widget a keyboard and no pointer affordance for changing month:
/// "`PgUp`/`PgDn` a month, `Shift+PgUp`/`PgDn` a year". §2's metrics row names a
/// "header row `caption`", and that is the **weekday** row — `calendar-weekdays`.
/// So a calendar built to the letter of both would be one a mouse cannot page,
/// which is not a calendar. This is the addition that fixes it, and
/// `docs/design-system.md` §2 gained a row for it in the same change rather than
/// this being an undocumented part (ADR-0274).
///
/// ## Neither arrow is focusable
///
/// §10: "The grid is one Tab stop with a roving day". Two focusable arrows would
/// make it three stops, and a `date-picker`'s popover four — `TabClose`'s
/// argument, unchanged. The keyboard already reaches both, by the keys §10 names.
///
/// Neither arrow *overrides* `isFocusable` either, and that is the same decision
/// said the way the sweeps read it: the default is already false, and an override
/// is what makes a type owe a role it has no honest answer for.
///
/// @param label      the month and year, in the application's locale
/// @param onPrevious what to ask for the month before
/// @param onNext     what to ask for the month after
record CalendarHeader(String label, Runnable onPrevious, Runnable onNext) implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "calendar-header";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public List<Widget> children() {
        return List.of(new CalendarNav(true, onPrevious), new CalendarTitle(label), new CalendarNav(false, onNext));
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).children(children.toArray(Box[]::new));
    }

    /// The month and year — `calendar-title`, a part, so a stylesheet can size it
    /// without reaching through the header.
    record CalendarTitle(String label) implements Widget.Leaf, Styled, Paints {

        @Override
        public String cssType() {
            return "calendar-title";
        }

        @Override
        public Set<String> classes() {
            return Set.of();
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of().style(style).children(Box.text(context.paragraph(style, label), style.color()));
        }
    }

    /// One of the two arrows — `calendar-nav`, a part, `.previous` or `.next`.
    ///
    /// **The same mark for both**, turned half round by the stylesheet for the
    /// previous one: `carousel`'s trade, and its reason unchanged — there is no
    /// `CHEVRON_START`, and adding one would be a second kind that has to stay
    /// the mirror of the first forever, where a `transform` is one declaration
    /// and cannot drift.
    record CalendarNav(boolean previous, Runnable onPress) implements Widget.Leaf, Styled, Paints, Handles {

        @Override
        public String cssType() {
            return "calendar-nav";
        }

        @Override
        public Set<String> classes() {
            return Set.of(previous ? "previous" : "next");
        }

        @Override
        public void onPointer(PointerEvent event) {
            if (event.kind() == PointerEvent.Kind.CLICKED) {
                onPress.run();
                event.consume();
            }
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of().style(style).mark(new Box.Mark(Box.Mark.Kind.CHEVRON_END, style.color(), 1.5));
        }
    }
}
