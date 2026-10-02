package dev.goldberry.widgets.panel.calendar;

import java.util.List;
import java.util.Set;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.input.event.PointerEvent;
import dev.goldberry.input.handler.Handles;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// The month, and the two ways to leave it — `calendar-header`, a **part**.
///
/// ## Why there is a header
///
/// The keyboard pages the calendar on its own: `PgUp`/`PgDn` a month,
/// `Shift+PgUp`/`PgDn` a year. A mouse has no key, and a calendar a mouse cannot
/// page is not a calendar, so this row carries the month's name and an arrow
/// each way. The design system's component metrics have a row for it, so it is
/// a documented part with its own tokens, not a private one.
///
/// ## Neither arrow is focusable
///
/// The grid is one Tab stop with a roving day. Two focusable arrows would make
/// it three stops, and a `date-picker`'s popover four — `TabClose`'s argument,
/// unchanged. The keyboard already reaches both months by the paging keys.
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
