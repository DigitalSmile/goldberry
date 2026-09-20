package io.github.digitalsmile.goldberry.widgets.panel.calendar;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.semantics.Role;
import io.github.digitalsmile.goldberry.widget.semantics.Semantics;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// The node a stylesheet calls `calendar`, and everything that needs a frame.
///
/// [CalendarView] is stateful and styles nothing, so this carries the CSS type,
/// the `id` and the classes — the arrangement every stateful widget in this
/// catalog uses, and for its reason.
///
/// ## What it is made of
///
/// ```
/// calendar               this node. Takes the focus and every key
/// ├── calendar-header    the month, and the two arrows
/// ├── calendar-weekdays  the seven names, outside the cross-fade
/// └── calendar-grid      the months, one fading into the other
///     └── calendar-month
///         └── calendar-week ×6
///             └── calendar-day ×7
/// ```
///
/// ## One Tab stop, and every key is this node's
///
/// §10: "The grid is one Tab stop with a roving day (§2.2)". A [FocusScope] would
/// have been the other way to say that and is the wrong one: a scope roves focus
/// between *focusable* children, and forty-two focusable cells is forty-two Tab
/// stops from anywhere the scope does not reach — a screen reader's, a popup's,
/// and `Home`'s. So the cells are parts, this is the one focusable node, and the
/// roving day is a class on a cell rather than a focus.
///
/// It also means the arrows are unambiguous. §7.2 warns that a `BOTH` scope
/// quietly moves focus when a widget declines a key; here nothing declines
/// anything, because a grid has a meaning for all four.
///
/// @param header     the month row
/// @param weekdays   the column headings
/// @param grid       the months
/// @param disabled   whether it refuses everything and matches `:disabled`
/// @param attributes the `id` and classes the application wrote
/// @param keys       what to tell about a key
record CalendarBox(
        Widget header, Widget weekdays, Widget grid, boolean disabled, Attributes attributes, CalendarKeys keys)
        implements Widget.Leaf, Styled, Paints, Handles, Semantics {

    /// §2's "day cell 32 (28) square", as the fallback behind
    /// `--gb-calendar-day`. Here rather than in the part that draws one because
    /// two things read it and neither is the cell: the stylesheet's token
    /// default, and nothing else — the cross-fade stopped needing it when the
    /// outgoing month became one node ([CalendarMonthLayer]).
    static final double DAY_SIZE_FALLBACK = 32;

    @Override
    public String cssType() {
        return "calendar";
    }

    @Override
    public @Nullable String id() {
        return attributes.id();
    }

    @Override
    public Set<String> classes() {
        return attributes.classes();
    }

    @Override
    public @Nullable Object key() {
        return attributes.key();
    }

    @Override
    public boolean isFocusable() {
        return !disabled;
    }

    @Override
    public boolean isDisabled() {
        return disabled;
    }

    @Override
    public void onFocusChanged(boolean gained, boolean fromKeyboard) {
        keys.focusChanged(gained);
    }

    @Override
    public List<Widget> children() {
        return List.of(header, weekdays, grid);
    }

    /// §10's keyboard, in full: "arrows move a day, `PgUp`/`PgDn` a month,
    /// `Shift+PgUp`/`PgDn` a year, `Home`/`End` the week".
    ///
    /// **Every one of them is consumed**, including a move that ran into `min` or
    /// `max` and did nothing — a grid with the keyboard owns its arrows, or
    /// `Left` would walk the focus scope it sits in and `Home` would scroll the
    /// page behind it. `text-input` makes the same argument for the same keys.
    ///
    /// `Enter` and `Space` choose the roving day, which §10 does not say and every
    /// grid does: a roving highlight nothing can commit is a highlight that only
    /// a mouse can act on. `Escape` is deliberately absent — it belongs to the
    /// `date-picker`'s popover or to the dialog around this, and a calendar that
    /// swallowed it would trap both.
    @Override
    public void onKey(KeyEvent event) {
        if (disabled || event.kind() != KeyEvent.Kind.PRESSED) {
            return;
        }
        var modifiers = event.modifiers();
        if (modifiers.control() || modifiers.alt()) {
            return;
        }
        var shift = modifiers.shift();
        var handled =
                switch (event.key()) {
                    case LEFT -> keys.moveDays(-1);
                    case RIGHT -> keys.moveDays(1);
                    case UP -> keys.moveDays(-CalendarMonth.DAYS_IN_WEEK);
                    case DOWN -> keys.moveDays(CalendarMonth.DAYS_IN_WEEK);
                    case PAGE_UP -> shift ? keys.moveYears(-1) : keys.moveMonths(-1);
                    case PAGE_DOWN -> shift ? keys.moveYears(1) : keys.moveMonths(1);
                    case HOME -> keys.moveToWeekEdge(true);
                    case END -> keys.moveToWeekEdge(false);
                    case ENTER, SPACE -> keys.choose();
                    default -> false;
                };
        if (handled) {
            event.consume();
        }
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        var boxes = new ArrayList<>(children);
        return Box.of().style(style).children(boxes.toArray(Box[]::new));
    }

    /// §10 asks for "grid with each cell's full date as its name", and the second
    /// half has nowhere to go: [Semantics] is a role, a name and a liveness, with
    /// no per-cell channel for any widget. The role is honest today and the names
    /// would arrive with the AccessKit bridge — the entry `code-input` opened,
    /// which this is the second widget to want. That bridge is **on hold and
    /// owned by no milestone** ([ADR-0440]), so the names are not coming.
    @Override
    public Role role() {
        return Role.GRID;
    }

    /// No name of its own: a `field` or the `date-picker` around it supplies one.
    @Override
    public @Nullable String accessibleName() {
        return null;
    }

    /// What a key means, answered by the state that holds the roving day.
    ///
    /// Each method reports **whether it did anything**, which is what decides
    /// whether the event is consumed — see [#onKey] for why nearly everything is
    /// consumed anyway.
    interface CalendarKeys {

        /// Move the roving day by `days`, clamped by `min` and `max`.
        boolean moveDays(int days);

        /// Move it by `months`, keeping the day of the month where the target
        /// month is long enough.
        boolean moveMonths(int months);

        boolean moveYears(int years);

        /// `Home` and `End` — the first and last day of the roving week.
        boolean moveToWeekEdge(boolean start);

        /// `Enter` and `Space` on the roving day.
        boolean choose();

        void focusChanged(boolean focused);
    }
}
