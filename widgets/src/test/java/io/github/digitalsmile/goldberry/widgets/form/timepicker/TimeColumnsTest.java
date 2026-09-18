package io.github.digitalsmile.goldberry.widgets.form.timepicker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.key.Key;
import io.github.digitalsmile.goldberry.input.key.Modifiers;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.TestHost;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;

/// §4's "hour/minute/second column set", driven the way a user drives it.
///
/// The wheel is the part of this widget with rules in it: what a row shows, what
/// wraps, which arrows mean what, and what a refused time does to a turn.
class TimeColumnsTest {

    private final TestHost host = new TestHost();

    private final List<LocalTime> committed = new ArrayList<>();

    private ElementTree mounted(LocalTime value, TimePrecision precision, Predicate<LocalTime> allows) {
        var tree = new ElementTree(new TimeColumns(value, LocalTime.MIDNIGHT, precision, allows, committed::add), host);
        render(tree);
        return tree;
    }

    private ElementTree mounted(LocalTime value) {
        return mounted(value, TimePrecision.MINUTES, time -> true);
    }

    private void render(ElementTree tree) {
        tree.flush();
        new WidgetRenderer(List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load()), TestFont.get()).render(tree);
    }

    private TimeColumnsBox box(ElementTree tree) {
        return (TimeColumnsBox) tree.root().children().getFirst().widget();
    }

    private List<TimeCell> cells(ElementTree tree, int column) {
        var wheel = (TimeColumn) box(tree).columns().get(column);
        return wheel.cells().stream().map(TimeCell.class::cast).toList();
    }

    private String selected(ElementTree tree, int column) {
        return cells(tree, column).stream()
                .filter(TimeCell::selected)
                .map(TimeCell::label)
                .findFirst()
                .orElseThrow(() -> new AssertionError("column " + column + " shows no value"));
    }

    private void key(ElementTree tree, Key key) {
        box(tree).onKey(new KeyEvent(KeyEvent.Kind.PRESSED, key, Modifiers.NONE, false, null));
        render(tree);
    }

    @Nested
    @DisplayName("the wheel")
    class Wheel {

        @Test
        @DisplayName("shows five rows with the value in the middle")
        void fiveRows() {
            var tree = mounted(LocalTime.of(9, 30));
            var hours = cells(tree, 0);

            assertEquals(5, hours.size());
            assertEquals(
                    List.of("07", "08", "09", "10", "11"),
                    hours.stream().map(TimeCell::label).toList());
            assertTrue(hours.get(2).selected());
        }

        /// Two digits always, because a column whose rows were "9" and "10" would
        /// jump about as it turned.
        @Test
        @DisplayName("draws two digits, always")
        void twoDigits() {
            assertEquals("00", selected(mounted(LocalTime.MIDNIGHT), 0));
        }

        /// The wheel formatted its digits with `String.format("%02d", …)` and no
        /// locale, so the *default* locale's numbering system chose the glyphs: a
        /// machine set to `hi-IN-u-nu-deva` drew `०९` where CI drew `09`, and the
        /// golden taken on it was a pixel diff nobody could reproduce
        /// (`docs/testing.md` §0). `Slider#text()` had already been told this.
        @Test
        @DisplayName("in the same digits wherever the machine is set")
        void latinDigitsWhateverTheLocale() {
            var was = Locale.getDefault(Locale.Category.FORMAT);
            try {
                Locale.setDefault(Locale.Category.FORMAT, Locale.forLanguageTag("hi-IN-u-nu-deva"));

                var tree = mounted(LocalTime.of(9, 30));

                assertEquals(
                        List.of("07", "08", "09", "10", "11"),
                        cells(tree, 0).stream().map(TimeCell::label).toList());
                assertEquals("30", selected(tree, 1));
            } finally {
                Locale.setDefault(Locale.Category.FORMAT, was);
            }
        }

        /// `58 59 00 01 02` is telling the truth about what comes next, where a
        /// list clamped at 59 would stop.
        @Test
        @DisplayName("wraps at both ends, so the neighbours are honest")
        void wraps() {
            var tree = mounted(LocalTime.of(23, 59));

            assertEquals(
                    List.of("21", "22", "23", "00", "01"),
                    cells(tree, 0).stream().map(TimeCell::label).toList());
            assertEquals(
                    List.of("57", "58", "59", "00", "01"),
                    cells(tree, 1).stream().map(TimeCell::label).toList());
        }

        @Test
        @DisplayName("has as many columns as the precision says")
        void columnCount() {
            assertEquals(
                    1,
                    box(mounted(LocalTime.NOON, TimePrecision.HOURS, t -> true))
                            .columns()
                            .size());
            assertEquals(
                    3,
                    box(mounted(LocalTime.NOON, TimePrecision.SECONDS, t -> true))
                            .columns()
                            .size());
        }
    }

    @Nested
    @DisplayName("the keyboard")
    class Keys {

        /// A column set is a row of independent wheels, so `Up`/`Down` change a
        /// value and `Left`/`Right` change which wheel — where a calendar's four
        /// arrows all move a cell.
        @Test
        @DisplayName("Up and Down turn the roving wheel")
        void turns() {
            var tree = mounted(LocalTime.of(9, 30));

            key(tree, Key.DOWN);
            assertEquals("10", selected(tree, 0));
            key(tree, Key.UP);
            key(tree, Key.UP);
            assertEquals("08", selected(tree, 0));
        }

        @Test
        @DisplayName("Left and Right move between wheels")
        void movesColumn() {
            var tree = mounted(LocalTime.of(9, 30));

            key(tree, Key.RIGHT);
            key(tree, Key.DOWN);

            assertEquals("09", selected(tree, 0));
            assertEquals("31", selected(tree, 1));
        }

        /// The ends of the **column**, not of the row: a row has three items and
        /// reaching its ends is what `Left` and `Right` already do in one press.
        @Test
        @DisplayName("Home and End are the ends of the wheel")
        void columnEdges() {
            var tree = mounted(LocalTime.of(9, 30));

            key(tree, Key.END);
            assertEquals("23", selected(tree, 0));
            key(tree, Key.HOME);
            assertEquals("00", selected(tree, 0));
        }

        /// A popover with the keyboard owns its arrows, or `Left` would walk the
        /// focus scope of the window underneath it.
        @Test
        @DisplayName("every key it understands is consumed")
        void consumes() {
            var tree = mounted(LocalTime.of(9, 30));

            for (var key : List.of(Key.UP, Key.DOWN, Key.LEFT, Key.RIGHT, Key.HOME, Key.END, Key.ENTER)) {
                var event = new KeyEvent(KeyEvent.Kind.PRESSED, key, Modifiers.NONE, false, null);
                box(tree).onKey(event);
                render(tree);
                assertTrue(event.isConsumed(), key + " was not consumed");
            }
        }

        /// It belongs to the popup, which the launcher dismisses before anything
        /// here is reached.
        @Test
        @DisplayName("Escape is not one of them")
        void escape() {
            var tree = mounted(LocalTime.of(9, 30));

            var event = new KeyEvent(KeyEvent.Kind.PRESSED, Key.ESCAPE, Modifiers.NONE, false, null);
            box(tree).onKey(event);

            assertFalse(event.isConsumed());
        }
    }

    @Nested
    @DisplayName("what it reports")
    class Reporting {

        /// Unlike a calendar's roving day, which reports nothing until `Enter`.
        /// There is no "not yet" state for an hour: the columns always show some
        /// time, and turning one changes the value you can see.
        @Test
        @DisplayName("every turn, because a wheel has no unchosen state")
        void everyTurn() {
            var tree = mounted(LocalTime.of(9, 30));

            key(tree, Key.DOWN);
            key(tree, Key.RIGHT);
            key(tree, Key.DOWN);

            assertEquals(List.of(LocalTime.of(10, 30), LocalTime.of(10, 31)), committed);
        }

        /// Not a no-op: it is what closes the popover, and a control that offered
        /// no way to say "done" from the keyboard would be one a keyboard cannot
        /// finish with.
        @Test
        @DisplayName("and again on Enter, which is how a keyboard finishes")
        void enter() {
            var tree = mounted(LocalTime.of(9, 30));

            key(tree, Key.ENTER);

            assertEquals(List.of(LocalTime.of(9, 30)), committed);
        }

        @Test
        @DisplayName("a press on a row chooses it")
        void press() {
            var tree = mounted(LocalTime.of(9, 30));
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

            cells(tree, 0).getFirst().onPointer(event);
            render(tree);

            assertEquals(List.of(LocalTime.of(7, 30)), committed);
        }

        /// A wheel that turned onto a time the picker would then refuse would be
        /// a control arguing with itself — §4 asks the gates to hold for the
        /// popover as well as the field.
        @Test
        @DisplayName("a refused time is neither shown nor reported")
        void refused() {
            var tree = mounted(LocalTime.of(9, 30), TimePrecision.MINUTES, time -> time.getHour() < 12);

            key(tree, Key.END);

            assertTrue(committed.isEmpty());
            assertEquals("09", selected(tree, 0));
        }
    }
}
