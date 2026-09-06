package io.github.digitalsmile.goldberry.widgets.form.timepicker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.bind.Property;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.event.TextEvent;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.input.key.Key;
import io.github.digitalsmile.goldberry.input.key.Mod;
import io.github.digitalsmile.goldberry.input.key.Modifiers;
import io.github.digitalsmile.goldberry.kdl.KdlParser;
import io.github.digitalsmile.goldberry.render.model.LogicalPoint;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.render.model.LogicalSize;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widget.style.Styled;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.TestHost;
import io.github.digitalsmile.goldberry.widgets.Widgets;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import io.github.digitalsmile.goldberry.widgets.form.parts.PickerField;

/// §4's typed time field, driven the way a user drives it.
///
/// The wheels are [TimeColumnsTest]'s and the parsing is [TimeFormatTest]'s. What
/// is here is the seam: that the field is the source of truth, that `min`, `max`
/// and the predicate gate it, and what a document can write.
///
/// **A 24-hour formatter throughout**, because asserting a locale's short form
/// would be asserting a `java.time` table rather than anything this widget
/// decides.
class TimePickerTest {

    private static final TimeFormat HH_MM = TimeFormat.of(DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT));

    private static final LogicalRect FIELD_BOUNDS = new LogicalRect(new LogicalPoint(12, 40), new LogicalSize(360, 32));

    private final TestHost host = new TestHost();

    private static TimePicker picker(@Nullable Consumer<@Nullable LocalTime> onChange) {
        return new TimePicker(onChange).format(HH_MM);
    }

    private ElementTree mounted(TimePicker picker) {
        var tree = new ElementTree(picker, host);
        render(tree);
        return tree;
    }

    private void render(ElementTree tree) {
        tree.flush();
        new WidgetRenderer(List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load()), TestFont.get()).render(tree);
    }

    private PickerField box(ElementTree tree) {
        return (PickerField) tree.root().children().getFirst().widget();
    }

    /// The `text-input` inside, found by the name a stylesheet would use —
    /// `TextField` is package-private, which is ADR-0065's rule working.
    private Handles field(ElementTree tree) {
        var found = firstStyled(tree.root(), "text-input");
        if (found == null) {
            throw new AssertionError("the picker built no text-input");
        }
        return (Handles) found;
    }

    private static @Nullable Styled firstStyled(Element element, String cssType) {
        if (element.widget() instanceof Styled styled && styled.cssType().equals(cssType)) {
            return styled;
        }
        for (var child : element.children()) {
            var found = firstStyled(child, cssType);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private TimePickerState state(ElementTree tree) {
        return (TimePickerState) tree.root().state().orElseThrow();
    }

    private void type(ElementTree tree, String text) {
        field(tree).onText(new TextEvent(text, null));
        render(tree);
    }

    private void retype(ElementTree tree, String text) {
        key(tree, Key.A, Modifiers.of(Mod.CTRL));
        type(tree, text);
    }

    private void clear(ElementTree tree) {
        key(tree, Key.A, Modifiers.of(Mod.CTRL));
        key(tree, Key.BACKSPACE, Modifiers.NONE);
    }

    private void key(ElementTree tree, Key key, Modifiers modifiers) {
        field(tree).onKey(new KeyEvent(KeyEvent.Kind.PRESSED, key, modifiers, false, null));
        render(tree);
    }

    private String shown(ElementTree tree) {
        return state(tree).fieldText();
    }

    @Nested
    @DisplayName("the field is the source of truth")
    class Typing {

        @Test
        @DisplayName("a time typed into it is reported as soon as it parses")
        void typed() {
            var chosen = new ArrayList<@Nullable LocalTime>();
            var tree = mounted(picker(chosen::add));

            type(tree, "09:30");

            assertEquals(List.of(LocalTime.of(9, 30)), chosen);
        }

        @Test
        @DisplayName("a half-typed time reports nothing and keeps what was typed")
        void halfTyped() {
            var chosen = new ArrayList<@Nullable LocalTime>();
            var tree = mounted(picker(chosen::add));

            type(tree, "09:");

            assertTrue(chosen.isEmpty());
            assertEquals("09:", shown(tree));
        }

        /// §4's "an unreachable date cannot be typed either", for times. The text
        /// stays, because deleting what somebody typed loses a keystroke they
        /// were halfway through.
        @Test
        @DisplayName("a time outside the bounds is left in the field and never reported")
        void outsideBounds() {
            var chosen = new ArrayList<@Nullable LocalTime>();
            var tree = mounted(picker(chosen::add).between(LocalTime.of(9, 0), LocalTime.of(17, 0)));

            type(tree, "08:00");

            assertTrue(chosen.isEmpty());
            assertEquals("08:00", shown(tree));

            retype(tree, "09:00");
            assertEquals(List.of(LocalTime.of(9, 0)), chosen);
        }

        @Test
        @DisplayName("and the predicate gates the field as well as the wheels")
        void predicate() {
            var chosen = new ArrayList<@Nullable LocalTime>();
            var tree = mounted(picker(chosen::add).disabledTimes(time -> time.getMinute() % 15 != 0));

            type(tree, "09:07");
            assertTrue(chosen.isEmpty());

            retype(tree, "09:15");
            assertEquals(List.of(LocalTime.of(9, 15)), chosen);
        }

        /// Blank and rubbish are different answers: an empty field clears the
        /// value and a half-typed one leaves the last good one alone.
        @Test
        @DisplayName("clearing it reports null rather than nothing")
        void cleared() {
            var chosen = new ArrayList<@Nullable LocalTime>();
            var tree = mounted(picker(chosen::add));

            type(tree, "09:30");
            clear(tree);

            assertEquals(2, chosen.size());
            assertNull(chosen.getLast());
        }
    }

    @Nested
    @DisplayName("Esc reverts")
    class Reverting {

        @Test
        @DisplayName("the last value that parsed goes back into the field")
        void reverts() {
            var tree = mounted(picker(null));
            type(tree, "09:30");
            retype(tree, "09:3");

            var event = new KeyEvent(KeyEvent.Kind.PRESSED, Key.ESCAPE, Modifiers.NONE, false, null);
            box(tree).onKey(event);
            render(tree);

            assertTrue(event.isConsumed());
            assertEquals("09:30", shown(tree));
        }

        @Test
        @DisplayName("and an untouched picker does not take the key")
        void nothingToRevert() {
            var tree = mounted(picker(null));

            var event = new KeyEvent(KeyEvent.Kind.PRESSED, Key.ESCAPE, Modifiers.NONE, false, null);
            box(tree).onKey(event);

            assertFalse(event.isConsumed());
        }
    }

    @Nested
    @DisplayName("the wheels")
    class Popover {

        /// `date-picker`'s bug, asserted for this one too: a dropdown asks for at
        /// least its field's width because a list's rows stretch, and a column set
        /// does not.
        @Test
        @DisplayName("ask for no minimum width, because three wheels cannot stretch")
        void noMinimumWidth() {
            var tree = mounted(picker(null));
            box(tree).located(FIELD_BOUNDS, FIELD_BOUNDS);

            box(tree).picker().toggle();
            render(tree);

            assertEquals(1, host.opened.size());
            assertEquals(0f, host.opened.getLast().minimumWidth());
        }

        @Test
        @DisplayName("cannot open without a window, and say so by staying closed")
        void noHost() {
            var tree = mounted(picker(null));

            box(tree).picker().toggle();
            render(tree);

            assertFalse(box(tree).open());
        }
    }

    @Nested
    @DisplayName("a bound value")
    class Binding {

        @Test
        @DisplayName("a LocalTime in the model is formatted into the field")
        void localTime() {
            var time = Property.of(LocalTime.of(9, 30));
            var tree = mounted(picker(null).bound(time));

            assertEquals("09:30", shown(tree));
        }

        /// The picker shows hours and minutes, so a model holding seconds must
        /// not put a time in the field that no column can change.
        @Test
        @DisplayName("and is truncated to what the columns can express")
        void truncated() {
            var time = Property.of(LocalTime.of(9, 30, 45));
            var tree = mounted(picker(null).bound(time));

            assertEquals("09:30", shown(tree));
        }

        @Test
        @DisplayName("a value the application changes takes the field")
        void override() {
            var time = Property.of(LocalTime.of(9, 30));
            var tree = mounted(picker(null).bound(time));

            time.set(LocalTime.of(17, 0));
            render(tree);

            assertEquals("17:00", shown(tree));
        }
    }

    @Nested
    @DisplayName("precision")
    class Precision {

        /// The format follows the precision because it is null until somebody
        /// sets it, not because the wither reaches over and rewrites it — see
        /// `TimePicker.precision`.
        @Test
        @DisplayName("moves the default format with it, and a supplied one stays put")
        void formatFollows() {
            var seconds = new TimePicker().precision(TimePrecision.SECONDS);
            assertEquals("09:30:45", seconds.resolvedFormat().format(LocalTime.of(9, 30, 45)));

            var pinned = new TimePicker().format(HH_MM).precision(TimePrecision.SECONDS);
            assertEquals("09:30", pinned.resolvedFormat().format(LocalTime.of(9, 30, 45)));
        }

        @Test
        @DisplayName("decides how many wheels there are")
        void columns() {
            assertEquals(1, TimePrecision.HOURS.columns());
            assertEquals(2, TimePrecision.MINUTES.columns());
            assertEquals(3, TimePrecision.SECONDS.columns());
        }
    }

    @Nested
    @DisplayName("what a document writes")
    class FromMarkup {

        private TimePicker inflated(String kdl) {
            return (TimePicker)
                    Widgets.inflater().inflateAll(KdlParser.parse(kdl)).getFirst();
        }

        @Test
        @DisplayName("precision, min, max and fallback")
        void properties() {
            var picker = inflated("time-picker precision=\"seconds\" min=\"09:00\" max=\"17:00\" fallback=\"12:00\"");

            assertEquals(TimePrecision.SECONDS, picker.precision());
            assertEquals(LocalTime.of(9, 0), picker.min());
            assertEquals(LocalTime.of(17, 0), picker.max());
            assertEquals(LocalTime.NOON, picker.fallback());
        }

        @Test
        @DisplayName("a bare time-picker is hours and minutes from midnight")
        void defaults() {
            var picker = inflated("time-picker");

            assertEquals(TimePrecision.MINUTES, picker.precision());
            assertEquals(LocalTime.MIDNIGHT, picker.fallback());
        }

        /// A typo already visible in the markup — `text-input`'s answer to an
        /// unknown `filter=`, unchanged.
        @Test
        @DisplayName("an unknown precision and a bad time are logged and ignored")
        void refused() {
            assertEquals(
                    TimePrecision.MINUTES,
                    inflated("time-picker precision=\"nanos\"").precision());
            assertNull(inflated("time-picker min=\"9am\"").min());
        }
    }

    @Nested
    @DisplayName("what it refuses to be built as")
    class Bounds {

        /// A shift from 22:00 to 06:00 is two ranges, and a picker that let a
        /// bound wrap would have no way to say which of the two a time at 03:00
        /// was in.
        @Test
        @DisplayName("a max before its min, because a range that wraps is two ranges")
        void wrappingBounds() {
            org.junit.jupiter.api.Assertions.assertThrows(
                    IllegalArgumentException.class,
                    () -> new TimePicker().between(LocalTime.of(22, 0), LocalTime.of(6, 0)));
        }
    }
}
