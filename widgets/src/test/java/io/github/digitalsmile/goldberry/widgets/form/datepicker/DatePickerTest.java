package io.github.digitalsmile.goldberry.widgets.form.datepicker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widget.style.Styled;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.TestHost;
import io.github.digitalsmile.goldberry.widgets.Widgets;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import io.github.digitalsmile.goldberry.widgets.form.parts.PickerField;
import io.github.digitalsmile.goldberry.widgets.panel.calendar.DateSelection;

/// §4's typed date field, driven the way a user drives it.
///
/// The parsing is [DateFormatTest]'s and the grid is `CalendarTest`'s, both with
/// no widget. What is here is the seam between them: that the **field is the
/// source of truth**, that the grid writes text into it rather than around it,
/// that `min`, `max` and the predicate gate both, and what `Alt+Down` and `Esc`
/// do.
///
/// **ISO dates and a fixed month**, because a picker cannot read a clock
/// (ADR-0274) and asserting a locale's short form would be asserting a
/// `java.time` table.
class DatePickerTest {

    private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);
    private static final DateFormat ISO = DateFormat.of(DateTimeFormatter.ISO_LOCAL_DATE);

    /// Where a frame might have put the control — wide, so that a popup which
    /// took its width from the field would be visibly wrong.
    private static final io.github.digitalsmile.goldberry.render.model.LogicalRect FIELD_BOUNDS =
            new io.github.digitalsmile.goldberry.render.model.LogicalRect(
                    new io.github.digitalsmile.goldberry.render.model.LogicalPoint(12, 40),
                    new io.github.digitalsmile.goldberry.render.model.LogicalSize(360, 32));

    private final TestHost host = new TestHost();

    private static DatePicker picker() {
        return new DatePicker(SEPTEMBER).format(ISO).locale(Locale.UK);
    }

    private ElementTree mounted(DatePicker picker) {
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

    /// The `text-input` node inside, found by the name a **stylesheet** would use
    /// rather than by its class.
    ///
    /// `TextField` is package-private, which is ADR-0065's rule working: a part is
    /// styleable and not constructible, and that goes for a neighbouring widget's
    /// test as much as for an application. What is reachable from here is what is
    /// reachable from anywhere — the CSS type and the handler interfaces — and
    /// driving the picker through those is a better test anyway, because it is
    /// what the router does.
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

    /// The picker's own state, which is what holds the text — §4's source of
    /// truth, asked directly.
    private DatePickerState state(ElementTree tree) {
        return (DatePickerState) tree.root().state().orElseThrow();
    }

    /// Types `text` at the caret, which is what committed text does.
    private void type(ElementTree tree, String text) {
        field(tree).onText(new TextEvent(text, null));
        render(tree);
    }

    /// Selects everything and types over it — what a user does to a field that
    /// already holds a date, and the reason [#type] on its own would append.
    private void retype(ElementTree tree, String text) {
        key(tree, Key.A, Modifiers.of(Mod.CTRL));
        type(tree, text);
    }

    /// Empties the field. Not `type("")`, which a field ignores: committed text
    /// of no characters is not a keystroke.
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
        @DisplayName("a date typed into it is reported as soon as it parses")
        void typed() {
            var chosen = new ArrayList<DateSelection>();
            var tree =
                    mounted(new DatePicker(SEPTEMBER, chosen::add).format(ISO).locale(Locale.UK));

            type(tree, "2026-09-14");

            assertEquals(1, chosen.size());
            assertEquals(LocalDate.of(2026, 9, 14), chosen.getFirst().first());
        }

        /// Nothing is reported while the date is half typed, and nothing is taken
        /// out of the field either.
        @Test
        @DisplayName("a half-typed date reports nothing and keeps what was typed")
        void halfTyped() {
            var chosen = new ArrayList<DateSelection>();
            var tree =
                    mounted(new DatePicker(SEPTEMBER, chosen::add).format(ISO).locale(Locale.UK));

            type(tree, "2026-09");

            assertTrue(chosen.isEmpty());
            assertEquals("2026-09", shown(tree));
        }

        /// §4: "an unreachable date cannot be typed either". The text stays,
        /// because deleting what somebody typed is how a field loses a keystroke
        /// they were halfway through.
        @Test
        @DisplayName("a date outside the bounds is left in the field and never reported")
        void outsideBounds() {
            var chosen = new ArrayList<DateSelection>();
            var tree = mounted(new DatePicker(SEPTEMBER, chosen::add)
                    .format(ISO)
                    .locale(Locale.UK)
                    .between(LocalDate.of(2026, 9, 10), null));

            type(tree, "2026-09-04");

            assertTrue(chosen.isEmpty());
            assertEquals("2026-09-04", shown(tree));
        }

        @Test
        @DisplayName("and the predicate gates the field as well as the grid")
        void predicateGatesTheField() {
            var chosen = new ArrayList<DateSelection>();
            var tree = mounted(new DatePicker(SEPTEMBER, chosen::add)
                    .format(ISO)
                    .locale(Locale.UK)
                    .disabledDates(date -> date.getDayOfWeek() == DayOfWeek.SUNDAY));

            // 6 September 2026 is a Sunday; the 7th is not.
            type(tree, "2026-09-06");
            assertTrue(chosen.isEmpty());

            retype(tree, "2026-09-07");
            assertEquals(1, chosen.size());
        }

        @Test
        @DisplayName("clearing it reports an empty value rather than nothing")
        void cleared() {
            var chosen = new ArrayList<DateSelection>();
            var tree =
                    mounted(new DatePicker(SEPTEMBER, chosen::add).format(ISO).locale(Locale.UK));

            type(tree, "2026-09-14");
            clear(tree);

            assertEquals(2, chosen.size());
            assertTrue(chosen.getLast().isEmpty());
        }
    }

    @Nested
    @DisplayName("Esc reverts")
    class Reverting {

        /// §4's word, and `select`'s rule: the control holds a value, typing is a
        /// way of reaching one, and abandoning the attempt must not throw away
        /// something nobody asked to lose.
        @Test
        @DisplayName("the last value that parsed goes back into the field")
        void reverts() {
            var tree = mounted(picker());
            type(tree, "2026-09-14");
            retype(tree, "2026-09-1");

            var event = new KeyEvent(KeyEvent.Kind.PRESSED, Key.ESCAPE, Modifiers.NONE, false, null);
            box(tree).onKey(event);
            render(tree);

            assertTrue(event.isConsumed());
            assertEquals("2026-09-14", shown(tree));
        }

        /// So a dialog around an untouched picker still closes on `Escape`.
        @Test
        @DisplayName("and an untouched picker does not take the key")
        void nothingToRevert() {
            var tree = mounted(picker());

            var event = new KeyEvent(KeyEvent.Kind.PRESSED, Key.ESCAPE, Modifiers.NONE, false, null);
            box(tree).onKey(event);

            assertFalse(event.isConsumed());
        }
    }

    @Nested
    @DisplayName("the grid")
    class Popover {

        /// A `TestHost` has no popup windows, which is the same answer a golden
        /// image and a layout preview get: the control stays closed rather than
        /// throwing (ADR-0140).
        @Test
        @DisplayName("cannot open without a window, and says so by staying closed")
        void noHost() {
            var tree = mounted(picker());

            box(tree).picker().toggle();
            render(tree);

            assertFalse(box(tree).open());
        }

        /// The bug this asserts against: the first version passed the **field's**
        /// width as a minimum, which is `select`'s rule (ADR-0145) and is wrong
        /// here. A dropdown's rows stretch to fill whatever they are given; a
        /// month grid is seven cells of `--gb-calendar-day` and cannot be any
        /// other width, so a floor produced a panel as wide as the field with the
        /// grid stranded at one end of it.
        @Test
        @DisplayName("asks for no minimum width, because a grid cannot stretch")
        void noMinimumWidth() {
            var tree = mounted(picker());
            // The router does this after a paint; a test driving the node has to
            // say where the frame put it.
            box(tree).located(FIELD_BOUNDS, FIELD_BOUNDS);

            box(tree).picker().toggle();
            render(tree);

            assertEquals(1, host.opened.size());
            assertEquals(0f, host.opened.getLast().minimumWidth());
            assertEquals(FIELD_BOUNDS, host.opened.getLast().anchor());
        }

        /// The capture pass, because `text-input` reads a plain `Down` as "go to
        /// the end of the line" and does not ask about the modifier.
        @Test
        @DisplayName("Alt+Down is taken before the field sees it")
        void altDownIsCaptured() {
            var tree = mounted(picker());

            var event = new KeyEvent(KeyEvent.Kind.PRESSED, Key.DOWN, Modifiers.of(Mod.ALT), false, null);
            box(tree).onKeyCapture(event);

            // Nothing opened, because there is no window — but the key was offered
            // to the picker before the field, which is what this asserts.
            assertFalse(event.isConsumed());
        }
    }

    @Nested
    @DisplayName("a bound value")
    class Binding {

        /// §4 gives this control a `LocalDate` value, and a model that holds one
        /// is the common case. It is *formatted* rather than stringified, so the
        /// field shows the application's own spelling.
        @Test
        @DisplayName("a LocalDate in the model is formatted into the field")
        void localDate() {
            var date = Property.of(LocalDate.of(2026, 9, 14));
            var tree = mounted(picker().bound(date));

            assertEquals("2026-09-14", shown(tree));
        }

        /// A form that has not parsed its values yet keeps them as text, and that
        /// is meant too.
        @Test
        @DisplayName("a string in the model is taken as the text it typed")
        void text() {
            var typed = Property.of("2026-09-14");
            var tree = mounted(picker().bound(typed));

            assertEquals("2026-09-14", shown(tree));
        }

        @Test
        @DisplayName("a value the application changes takes the field")
        void override() {
            var date = Property.of(LocalDate.of(2026, 9, 14));
            var tree = mounted(picker().bound(date));

            date.set(LocalDate.of(2026, 9, 21));
            render(tree);

            assertEquals("2026-09-21", shown(tree));
        }

        @Test
        @DisplayName("and an empty model is an empty field rather than a null")
        void nothingBound() {
            var date = Property.<LocalDate>of(null);
            var tree = mounted(picker().bound(date));

            assertEquals("", shown(tree));
        }
    }

    @Nested
    @DisplayName("what a document writes")
    class FromMarkup {

        private DatePicker inflated(String kdl) {
            return (DatePicker)
                    Widgets.inflater().inflateAll(KdlParser.parse(kdl)).getFirst();
        }

        @Test
        @DisplayName("range, month, min and max")
        void properties() {
            var picker = inflated("date-picker range=#true month=\"2026-09\" min=\"2026-09-01\" max=\"2026-09-30\"");

            assertTrue(picker.range());
            assertEquals(SEPTEMBER, picker.month());
            assertEquals(LocalDate.of(2026, 9, 1), picker.min());
            assertEquals(LocalDate.of(2026, 9, 30), picker.max());
        }

        /// A typo already visible in the markup, and a picker that refused every
        /// date is a worse way to find out — `text-input`'s answer to an unknown
        /// `filter=`, unchanged.
        @Test
        @DisplayName("a min that is not an ISO date is logged and ignored")
        void badDate() {
            assertNull(inflated("date-picker min=\"01/09/2026\"").min());
        }

        /// The month falls back to `min` when a document did not say, which is the
        /// only other date it was given.
        @Test
        @DisplayName("the month falls back to min")
        void monthFromMin() {
            assertEquals(SEPTEMBER, inflated("date-picker min=\"2026-09-01\"").month());
        }

        /// §4 puts the formatter on the application and a `DateTimeFormatter` is
        /// not something KDL can carry, so a document gets the documented default
        /// rather than a gap.
        ///
        /// Compared by what it **writes**, not by `equals`: a `DateTimeFormatter`
        /// has no value equality, so two formatters built the same way are two
        /// objects — which is a fact about `java.time` and not about this.
        @Test
        @DisplayName("a document gets the locale's short form")
        void defaultFormat() {
            var picker = inflated("date-picker");
            var expected = DateFormat.of(Locale.getDefault(Locale.Category.FORMAT));

            assertEquals(
                    expected.format(LocalDate.of(2026, 9, 14)), picker.format().format(LocalDate.of(2026, 9, 14)));
        }
    }
}
