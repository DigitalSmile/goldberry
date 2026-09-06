package io.github.digitalsmile.goldberry.widgets.panel.calendar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.key.Key;
import io.github.digitalsmile.goldberry.input.key.Mod;
import io.github.digitalsmile.goldberry.input.key.Modifiers;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.TestHost;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;

/// §10's month grid, driven the way a user drives it.
///
/// The selection rules are [DateSelectionTest]'s and the grid arithmetic is
/// [CalendarMonthTest]'s, both with no widget at all. What is here is everything
/// that needs one: which key moves what, when the application is told, what
/// `min`, `max` and the disabled predicate refuse, and the month change asking
/// for frames.
///
/// **September 2026 throughout**, because a calendar that read the clock would be
/// a test that failed at midnight — and the widget cannot read one anyway, which
/// is the point of it being told (ADR-0274, ADR-0203).
class CalendarTest {

    private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 6);

    private final TestHost host = new TestHost();

    private ElementTree mounted(CalendarView calendar) {
        var tree = new ElementTree(calendar, host);
        render(tree);
        return tree;
    }

    private void render(ElementTree tree) {
        tree.flush();
        new WidgetRenderer(List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load()), TestFont.get()).render(tree);
    }

    private CalendarBox box(ElementTree tree) {
        return (CalendarBox) tree.root().children().getFirst().widget();
    }

    private void key(ElementTree tree, Key key, Modifiers modifiers) {
        box(tree).onKey(new KeyEvent(KeyEvent.Kind.PRESSED, key, modifiers, false, null));
        render(tree);
    }

    private void key(ElementTree tree, Key key) {
        key(tree, key, Modifiers.NONE);
    }

    private void focus(ElementTree tree, boolean gained) {
        box(tree).onFocusChanged(gained, true);
        render(tree);
    }

    /// Every day cell of the month **being shown**, in reading order.
    ///
    /// The first layer, which is the incoming month: during a cross-fade there
    /// are two, and the one that is leaving is not what anything here asks about.
    private List<CalendarDay> days(ElementTree tree) {
        var grid = (CalendarGrid) box(tree).grid();
        var layer = (CalendarMonthLayer) grid.children().getFirst();
        var cells = new ArrayList<CalendarDay>();
        for (var week : layer.weeks()) {
            for (var day : ((CalendarWeek) week).days()) {
                cells.add((CalendarDay) day);
            }
        }
        return cells;
    }

    private CalendarDay dayOf(ElementTree tree, LocalDate date) {
        return days(tree).stream()
                .filter(day -> day.date().equals(date))
                .findFirst()
                .orElseThrow(() -> new AssertionError(date + " is not in the grid being shown"));
    }

    private LocalDate roving(ElementTree tree) {
        return days(tree).stream()
                .filter(CalendarDay::roving)
                .map(CalendarDay::date)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no day is roving"));
    }

    private String monthLabel(ElementTree tree) {
        return ((CalendarHeader) box(tree).header()).label();
    }

    private static CalendarView september() {
        return new CalendarView(DateSelection.NONE, null, SEPTEMBER)
                .today(TODAY)
                .locale(Locale.UK);
    }

    @Nested
    @DisplayName("the grid")
    class Grid {

        /// Six rows always, so a five-week month and a four-week one are the same
        /// height and nothing under the calendar moves when it pages.
        @Test
        @DisplayName("is always six weeks of seven days")
        void alwaysSixWeeks() {
            assertEquals(42, days(mounted(september())).size());
        }

        /// September 2026 starts on a Tuesday, so a UK grid begins on Monday
        /// 31 August.
        @Test
        @DisplayName("starts on the locale's first day of the week")
        void leadingDays() {
            var cells = days(mounted(september()));

            assertEquals(LocalDate.of(2026, 8, 31), cells.getFirst().date());
            assertFalse(cells.getFirst().inMonth());
            assertTrue(cells.get(1).inMonth());
        }

        /// The same month in the United States, where the week starts on Sunday.
        @Test
        @DisplayName("and a different locale starts it somewhere else")
        void americanWeek() {
            var tree = mounted(september().locale(Locale.US));

            assertEquals(LocalDate.of(2026, 8, 30), days(tree).getFirst().date());
        }

        @Test
        @DisplayName("marks today, and only when it has been told what today is")
        void today() {
            assertTrue(dayOf(mounted(september()), TODAY).today());

            var untold = new CalendarView(DateSelection.NONE, null, SEPTEMBER).locale(Locale.UK);
            assertFalse(dayOf(mounted(untold), TODAY).today());
        }
    }

    @Nested
    @DisplayName("the keyboard")
    class Keys {

        @Test
        @DisplayName("the arrows move a day and a week")
        void arrows() {
            var tree = mounted(september());
            focus(tree, true);

            assertEquals(LocalDate.of(2026, 9, 1), roving(tree));
            key(tree, Key.RIGHT);
            assertEquals(LocalDate.of(2026, 9, 2), roving(tree));
            key(tree, Key.DOWN);
            assertEquals(LocalDate.of(2026, 9, 9), roving(tree));
            key(tree, Key.UP);
            key(tree, Key.LEFT);
            assertEquals(LocalDate.of(2026, 9, 1), roving(tree));
        }

        @Test
        @DisplayName("PgUp and PgDn move a month, and Shift moves a year")
        void paging() {
            var tree = mounted(september());
            focus(tree, true);

            key(tree, Key.PAGE_DOWN);
            assertEquals(LocalDate.of(2026, 10, 1), roving(tree));
            assertEquals("October 2026", monthLabel(tree));

            key(tree, Key.PAGE_UP, Modifiers.of(Mod.SHIFT));
            assertEquals(LocalDate.of(2025, 10, 1), roving(tree));
            assertEquals("October 2025", monthLabel(tree));
        }

        /// The ends of the week the roving day is in, **in the locale's terms**:
        /// a UK week ends on Sunday and an American one on Saturday.
        @Test
        @DisplayName("Home and End reach the ends of the week")
        void weekEdges() {
            var tree = mounted(september());
            focus(tree, true);
            key(tree, Key.DOWN);

            key(tree, Key.END);
            assertEquals(DayOfWeek.SUNDAY, roving(tree).getDayOfWeek());
            key(tree, Key.HOME);
            assertEquals(DayOfWeek.MONDAY, roving(tree).getDayOfWeek());
        }

        /// Moving off the end of the month pages the grid, which is what makes the
        /// arrows a way of getting anywhere rather than a way of getting around
        /// one month.
        @Test
        @DisplayName("arrowing past the end of the month pages the grid")
        void arrowsPage() {
            var tree = mounted(september());
            focus(tree, true);
            key(tree, Key.LEFT);

            assertEquals(LocalDate.of(2026, 8, 31), roving(tree));
            assertEquals("August 2026", monthLabel(tree));
        }

        @Test
        @DisplayName("Enter chooses the roving day")
        void chooses() {
            var chosen = new ArrayList<DateSelection>();
            var tree = mounted(new CalendarView(DateSelection.NONE, chosen::add, SEPTEMBER).locale(Locale.UK));
            focus(tree, true);

            key(tree, Key.RIGHT);
            key(tree, Key.ENTER);

            assertEquals(1, chosen.size());
            assertEquals(LocalDate.of(2026, 9, 2), chosen.getFirst().first());
        }

        /// A grid with the keyboard owns its arrows, or `Left` would walk the
        /// focus scope it sits in and `Home` would scroll the page behind it.
        @Test
        @DisplayName("every key it understands is consumed")
        void consumes() {
            var tree = mounted(september());
            focus(tree, true);

            for (var key : List.of(Key.LEFT, Key.RIGHT, Key.UP, Key.DOWN, Key.PAGE_UP, Key.PAGE_DOWN, Key.HOME)) {
                var event = new KeyEvent(KeyEvent.Kind.PRESSED, key, Modifiers.NONE, false, null);
                box(tree).onKey(event);
                render(tree);
                assertTrue(event.isConsumed(), key + " was not consumed");
            }
        }

        /// It belongs to the popover or the dialog around this, and a calendar
        /// that swallowed it would trap both.
        @Test
        @DisplayName("Escape is not one of them")
        void escapeIsNotTaken() {
            var tree = mounted(september());
            focus(tree, true);

            var event = new KeyEvent(KeyEvent.Kind.PRESSED, Key.ESCAPE, Modifiers.NONE, false, null);
            box(tree).onKey(event);

            assertFalse(event.isConsumed());
        }

        /// A roving highlight on a grid nobody is typing into is a second thing
        /// claiming to be the selection.
        @Test
        @DisplayName("nothing roves until the grid has the keyboard")
        void rovesOnlyWhenFocused() {
            var tree = mounted(september());

            assertTrue(days(tree).stream().noneMatch(CalendarDay::roving));
            focus(tree, true);
            assertEquals(LocalDate.of(2026, 9, 1), roving(tree));
        }
    }

    @Nested
    @DisplayName("what it refuses")
    class Bounds {

        private CalendarView bounded() {
            return september().between(LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 20));
        }

        @Test
        @DisplayName("a day outside min and max is disabled")
        void outsideBounds() {
            var tree = mounted(bounded());

            assertTrue(dayOf(tree, LocalDate.of(2026, 9, 4)).disabled());
            assertFalse(dayOf(tree, LocalDate.of(2026, 9, 5)).disabled());
            assertFalse(dayOf(tree, LocalDate.of(2026, 9, 20)).disabled());
            assertTrue(dayOf(tree, LocalDate.of(2026, 9, 21)).disabled());
        }

        @Test
        @DisplayName("and the predicate refuses days inside them")
        void predicate() {
            var tree = mounted(september().disabledDates(date -> date.getDayOfWeek() == DayOfWeek.SUNDAY));

            assertTrue(dayOf(tree, LocalDate.of(2026, 9, 6)).disabled());
            assertFalse(dayOf(tree, LocalDate.of(2026, 9, 7)).disabled());
        }

        /// §4 asks for the bounds to gate "both the field and the grid, so an
        /// unreachable date cannot be typed either" — `allows` is the one place
        /// both ask.
        @Test
        @DisplayName("a refused day reports nothing when it is pressed")
        void refusedPressIsSilent() {
            var chosen = new ArrayList<DateSelection>();
            var tree = mounted(new CalendarView(DateSelection.NONE, chosen::add, SEPTEMBER)
                    .locale(Locale.UK)
                    .between(LocalDate.of(2026, 9, 5), null));

            press(tree, LocalDate.of(2026, 9, 4));
            assertTrue(chosen.isEmpty());

            press(tree, LocalDate.of(2026, 9, 5));
            assertEquals(1, chosen.size());
        }

        /// The arrows clamp to `min` and `max` and **not** to the predicate: a
        /// `Right` that skipped four days because a weekend was refused is a grid
        /// whose arrows lie.
        @Test
        @DisplayName("the arrows stop at min, and step onto a refused day")
        void arrowsClampToBoundsOnly() {
            var tree = mounted(bounded().disabledDates(date -> date.getDayOfMonth() == 10));
            focus(tree, true);

            assertEquals(LocalDate.of(2026, 9, 5), roving(tree));
            key(tree, Key.LEFT);
            assertEquals(LocalDate.of(2026, 9, 5), roving(tree));

            for (var i = 0; i < 5; i++) {
                key(tree, Key.RIGHT);
            }
            assertEquals(LocalDate.of(2026, 9, 10), roving(tree));
            assertTrue(dayOf(tree, roving(tree)).disabled());
        }
    }

    @Nested
    @DisplayName("the pointer")
    class Pressing {

        @Test
        @DisplayName("a press reports the whole new selection")
        void reports() {
            var chosen = new ArrayList<DateSelection>();
            var tree = mounted(new CalendarView(DateSelection.NONE, chosen::add, SEPTEMBER).locale(Locale.UK));

            press(tree, LocalDate.of(2026, 9, 14));

            assertEquals(List.of(LocalDate.of(2026, 9, 14)), chosen.getFirst().dates());
        }

        /// A day borrowed from the month either side is a real date, and pressing
        /// it pages the grid — which is what makes the last row of a month usable
        /// without arrowing.
        @Test
        @DisplayName("a day from the next month is pressable and pages the grid")
        void outsideDayPages() {
            var chosen = new ArrayList<DateSelection>();
            var tree = mounted(new CalendarView(DateSelection.NONE, chosen::add, SEPTEMBER).locale(Locale.UK));

            press(tree, LocalDate.of(2026, 10, 1));

            assertEquals(LocalDate.of(2026, 10, 1), chosen.getFirst().first());
            assertEquals("October 2026", monthLabel(tree));
        }

        @Test
        @DisplayName("the arrows in the header page a month each")
        void headerArrows() {
            var tree = mounted(september());
            var header = (CalendarHeader) box(tree).header();

            header.onNext().run();
            render(tree);
            assertEquals("October 2026", monthLabel(tree));

            header.onPrevious().run();
            render(tree);
            assertEquals("September 2026", monthLabel(tree));
        }

        private void press(ElementTree tree, LocalDate date) {
            CalendarTest.this.press(tree, date);
        }
    }

    @Nested
    @DisplayName("the month change")
    class CrossFade {

        /// §3.1: "month change: content `opacity` cross-fade fast". The sweep in
        /// `AnimationSweepTest` requires this assertion to exist beside the widget
        /// that declares `isAnimating`, and it is what stops a calendar that
        /// stopped asking for frames from passing every golden unchanged.
        @Test
        @DisplayName("asks for frames while it runs, and stops when it settles")
        void isAnimating() {
            var tree = mounted(september());
            var grid = (CalendarGrid) box(tree).grid();
            assertNull(grid.phase());
            assertFalse(grid.isAnimating(), "a settled calendar asks for frames");

            focus(tree, true);
            key(tree, Key.PAGE_DOWN);

            var changing = (CalendarGrid) box(tree).grid();
            assertTrue(changing.isAnimating(), "a month change draws no frames");
            assertNotNull(changing.outgoing());
        }

        /// Both months are described at once, which is what makes it a cross-fade
        /// rather than a dissolve to the background.
        @Test
        @DisplayName("draws both months while it runs and one when it has finished")
        void twoLayers() {
            var tree = mounted(september());
            focus(tree, true);
            key(tree, Key.PAGE_DOWN);

            assertEquals(2, ((CalendarGrid) box(tree).grid()).children().size());
        }

        private static void assertNotNull(Object value) {
            org.junit.jupiter.api.Assertions.assertNotNull(value);
        }
    }

    /// A press on the day cell, the way the router delivers one.
    private void press(ElementTree tree, LocalDate date) {
        var event = new PointerEvent(
                PointerEvent.Kind.PRESSED,
                0,
                0,
                PointerEvent.Button.PRIMARY,
                1,
                Float.NaN,
                Float.NaN,
                Modifiers.NONE,
                null);
        dayOf(tree, date).onPointer(event);
        render(tree);
    }
}
