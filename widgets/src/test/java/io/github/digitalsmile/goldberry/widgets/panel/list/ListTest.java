package io.github.digitalsmile.goldberry.widgets.panel.list;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.event.TextEvent;
import io.github.digitalsmile.goldberry.input.key.Key;
import io.github.digitalsmile.goldberry.input.key.Modifiers;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widgets.TestHost;
import io.github.digitalsmile.goldberry.widgets.panel.Described;
import io.github.digitalsmile.goldberry.widgets.text.Text;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// `list` — `docs/core-widgets.md` §10's vertical list over an item model.
///
/// What is here is the **model over time**: which rows are drawn, what the
/// keyboard and the pointer ask for, and what the selection models resolve a
/// modifier to. The drawing is `ListGoldenTest`'s.
class ListTest {

    private final TestHost host = new TestHost();

    /// What the widget asked for, in order. A test applies these back to build
    /// the next frame, which is what an application does — the widget selects
    /// nothing itself ([ADR-0063]).
    private final List<Set<String>> asked = new ArrayList<>();

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private static final List<String> NORDICS =
            List.of("Norway", "Sweden", "Finland", "Denmark", "Iceland");

    private ElementTree tree(ListView<?> widget) {
        return new ElementTree(widget, host);
    }

    /// Positional, and written out rather than computed — which is the one use
    /// [Modifiers#Modifiers(boolean, boolean, boolean, boolean)] says it is for.
    private static final Modifiers CTRL = new Modifiers(false, true, false, false);
    private static final Modifiers SHIFT = new Modifiers(true, false, false, false);
    private static final Modifiers ALT = new Modifiers(false, false, true, false);

    /// The default list under test: the five names, multi-selectable, reporting
    /// into [#asked].
    private ListView<String> nordics(Set<String> selected) {
        return ListView.of(NORDICS)
                .selection(Selection.MULTIPLE)
                .selected(selected, asked::add);
    }

    private static List<String> rows(ElementTree tree) {
        return Described.of(tree, ListRow.class).stream().map(ListRow::id).toList();
    }

    private static ListRow row(ElementTree tree, String id) {
        return Described.of(tree, ListRow.class).stream()
                .filter(r -> r.id().equals(id))
                .findFirst().orElseThrow(() -> new AssertionError(
                        "no row \"" + id + "\"; showing " + rows(tree)));
    }

    private static void click(ListRow row, Modifiers modifiers) {
        row.onPointer(clickEvent(modifiers));
    }

    private static PointerEvent clickEvent(Modifiers modifiers) {
        return new PointerEvent(PointerEvent.Kind.CLICKED, 0, 0,
                PointerEvent.Button.PRIMARY, 1, Float.NaN, Float.NaN, modifiers, null);
    }

    private static void press(ListRow row, Key key, Modifiers modifiers) {
        row.onKey(new KeyEvent(KeyEvent.Kind.PRESSED, key, modifiers, false, null));
    }

    private Set<String> lastAsked() {
        assertFalse(asked.isEmpty(), "nothing was asked for");
        return asked.getLast();
    }

    @Nested
    @DisplayName("the model")
    class Model {

        @Test
        @DisplayName("one row per item, in the model's order")
        void oneRowPerItem() {
            assertEquals(List.of("list-Norway", "list-Sweden", "list-Finland",
                            "list-Denmark", "list-Iceland"),
                    rows(tree(ListView.of(NORDICS))));
        }

        @Test
        @DisplayName("an empty model is an empty list rather than a failure")
        void emptyIsFine() {
            assertEquals(List.of(), rows(tree(ListView.of(List.of()))));
        }

        @Test
        @DisplayName("the item-factory decides what a row looks like")
        void anyWidgetAsARow() {
            // §10's "any widget as row" -- the row's child is whatever came back,
            // and the row itself contributes only the box and the keyboard.
            var widget = new ListView<Integer>(List.of(1, 2), String::valueOf,
                    n -> new Text("#" + n));
            var content = Described.of(tree(widget), ListRow.class).stream()
                    .map(r -> ((Text) r.content()).content()).toList();
            assertEquals(List.of("#1", "#2"), content);
        }

        @Test
        @DisplayName("a row is keyed by its item's identity, not by its index")
        void keyedByIdentity() {
            // The rule that keeps a row's element -- and its focus -- when the
            // model is re-sorted underneath it.
            assertEquals("list-Sweden", row(tree(ListView.of(NORDICS)), "list-Sweden").key());
        }

        @Test
        @DisplayName("a list with no identity or no factory is refused at construction")
        void theTwoRequiredFunctions() {
            assertThrows(NullPointerException.class,
                    () -> new ListView<String>(NORDICS, null, Text::new));
            assertThrows(NullPointerException.class,
                    () -> new ListView<String>(NORDICS, s -> s, null));
        }

        @Test
        @DisplayName("the selected set keeps the order it was given")
        void selectionKeepsItsOrder() {
            // Not Set.copyOf, whose hash order changes between runs -- a
            // diagnostic and a test both read this back.
            var given = new LinkedHashSet<>(List.of("Iceland", "Norway", "Denmark"));
            assertEquals(List.copyOf(given),
                    List.copyOf(ListView.of(NORDICS).selected(given, asked::add).selected()));
        }
    }

    @Nested
    @DisplayName("choosing with the pointer")
    class Pointer {

        @Test
        @DisplayName("a click asks for that row and nothing else")
        void plainClickReplaces() {
            var tree = tree(nordics(Set.of("Iceland")));
            click(row(tree, "list-Sweden"), Modifiers.NONE);
            assertEquals(Set.of("Sweden"), lastAsked());
        }

        @Test
        @DisplayName("the widget does not select itself — the value is the application's")
        void itAsksAndDoesNotAct() {
            var widget = nordics(Set.of());
            var tree = tree(widget);
            click(row(tree, "list-Sweden"), Modifiers.NONE);
            // Asked for, and the widget it was asked of still holds the old value.
            assertEquals(Set.of("Sweden"), lastAsked());
            assertEquals(Set.of(), widget.selected());
        }

        @Test
        @DisplayName("Ctrl adds a row, and is the only way to take one out")
        void controlToggles() {
            var selected = new LinkedHashSet<>(Set.of("Norway"));
            var tree = tree(nordics(selected));
            click(row(tree, "list-Finland"), CTRL);
            assertEquals(Set.of("Norway", "Finland"), lastAsked());

            // Applied back, which is what an application does -- and the second
            // Ctrl-click on the same row is the only gesture that removes one.
            var second = tree(nordics(lastAsked()));
            click(row(second, "list-Norway"), CTRL);
            assertEquals(Set.of("Finland"), lastAsked());
        }

        @Test
        @DisplayName("Shift sweeps from the anchor, over the rows as they are on screen")
        void shiftSelectsARange() {
            var tree = tree(nordics(Set.of()));
            click(row(tree, "list-Sweden"), Modifiers.NONE);
            click(row(tree, "list-Denmark"), SHIFT);
            assertEquals(List.of("Sweden", "Finland", "Denmark"), List.copyOf(lastAsked()));
        }

        @Test
        @DisplayName("the anchor does not move, so a run of Shift presses sweeps from one end")
        void theAnchorStaysPut() {
            var tree = tree(nordics(Set.of()));
            click(row(tree, "list-Finland"), Modifiers.NONE);
            click(row(tree, "list-Iceland"), SHIFT);
            assertEquals(List.of("Finland", "Denmark", "Iceland"), List.copyOf(lastAsked()));

            // Over-shot, and recoverable without starting again: the second
            // shifted press is measured from the same anchor rather than from
            // where the first one stopped.
            click(row(tree, "list-Denmark"), SHIFT);
            assertEquals(List.of("Finland", "Denmark"), List.copyOf(lastAsked()));
        }

        @Test
        @DisplayName("a Shift range runs upward as readily as down")
        void shiftRunsBothWays() {
            var tree = tree(nordics(Set.of()));
            click(row(tree, "list-Denmark"), Modifiers.NONE);
            click(row(tree, "list-Sweden"), SHIFT);
            assertEquals(List.of("Sweden", "Finland", "Denmark"), List.copyOf(lastAsked()));
        }

        @Test
        @DisplayName("Shift with no anchor yet is just the pressed row")
        void shiftWithoutAnAnchor() {
            var tree = tree(nordics(Set.of()));
            click(row(tree, "list-Finland"), SHIFT);
            assertEquals(Set.of("Finland"), lastAsked());
        }
    }

    @Nested
    @DisplayName("the selection models")
    class SelectionModels {

        @Test
        @DisplayName("SINGLE ignores the modifiers rather than refusing them")
        void singleIgnoresModifiers() {
            // `Ctrl` means "and also" and `Shift` means "through to", and a
            // control that holds one row has nothing to say to either.
            var tree = tree(ListView.of(NORDICS)
                    .selected(Set.of("Norway"), asked::add));
            click(row(tree, "list-Sweden"), CTRL);
            assertEquals(Set.of("Sweden"), lastAsked());
            click(row(tree, "list-Denmark"), SHIFT);
            assertEquals(Set.of("Denmark"), lastAsked());
        }

        @Test
        @DisplayName("SINGLE is the default")
        void singleByDefault() {
            assertEquals(Selection.SINGLE, ListView.of(NORDICS).selection());
        }

        @Test
        @DisplayName("NONE asks for nothing, however it is clicked")
        void noneChoosesNothing() {
            var tree = tree(ListView.of(NORDICS)
                    .selection(Selection.NONE)
                    .selected(Set.of(), asked::add));
            click(row(tree, "list-Sweden"), Modifiers.NONE);
            press(row(tree, "list-Sweden"), Key.ENTER, Modifiers.NONE);
            assertTrue(asked.isEmpty(), () -> "a NONE list asked for " + asked);
        }

        @Test
        @DisplayName("a NONE list is still walkable, because its rows are still content")
        void noneIsStillFocusable() {
            var tree = tree(ListView.of(NORDICS).selection(Selection.NONE));
            assertTrue(row(tree, "list-Sweden").isFocusable());
        }

        @Test
        @DisplayName("a NONE row does not swallow the click, so a button on it still works")
        void noneDoesNotConsume() {
            var tree = tree(ListView.of(NORDICS).selection(Selection.NONE));
            var event = clickEvent(Modifiers.NONE);
            row(tree, "list-Sweden").onPointer(event);
            assertFalse(event.isConsumed());
        }

        @Test
        @DisplayName("a list with no listener asks nobody and stays quiet")
        void noCallbackIsNotACrash() {
            var tree = tree(ListView.of(NORDICS));
            click(row(tree, "list-Sweden"), Modifiers.NONE);
            assertTrue(asked.isEmpty());
        }

        @Test
        @DisplayName("the whole set goes out even when it holds one")
        void alwaysTheWholeSet() {
            // A `Shift` range is computed over rows only the list can see, so an
            // id on its own would be an answer the application could not turn
            // back into a selection.
            var tree = tree(nordics(Set.of()));
            click(row(tree, "list-Norway"), Modifiers.NONE);
            assertEquals(Set.of("Norway"), lastAsked());
        }

        @Test
        @DisplayName("the one-value form unwraps it again")
        void theSingleValueForm() {
            var chosen = new ArrayList<String>();
            var tree = tree(ListView.of(NORDICS).selected("Norway", chosen::add));
            click(row(tree, "list-Sweden"), Modifiers.NONE);
            assertEquals(List.of("Sweden"), chosen);
        }

        @Test
        @DisplayName("selectedOne is null rather than empty when nothing is chosen")
        void selectedOneIsNullWhenEmpty() {
            assertNull(ListView.of(NORDICS).selectedOne());
            assertEquals("Norway",
                    ListView.of(NORDICS).selected("Norway", s -> { }).selectedOne());
        }
    }

    @Nested
    @DisplayName("the keyboard")
    class Keyboard {

        @Test
        @DisplayName("Enter chooses the focused row")
        void enterChooses() {
            var tree = tree(nordics(Set.of()));
            press(row(tree, "list-Finland"), Key.ENTER, Modifiers.NONE);
            assertEquals(Set.of("Finland"), lastAsked());
        }

        @Test
        @DisplayName("Ctrl+Enter and Shift+Enter are the pointer's gestures, on the keyboard")
        void enterCarriesItsModifiers() {
            var tree = tree(nordics(Set.of("Norway")));
            press(row(tree, "list-Finland"), Key.ENTER, CTRL);
            assertEquals(Set.of("Norway", "Finland"), lastAsked());
        }

        @Test
        @DisplayName("Alt+Enter falls through, so an application accelerator still reaches it")
        void altEnterIsNobodys() {
            var tree = tree(nordics(Set.of()));
            var event = new KeyEvent(KeyEvent.Kind.PRESSED, Key.ENTER, ALT, false, null);
            row(tree, "list-Finland").onKey(event);
            assertFalse(event.isConsumed());
            assertTrue(asked.isEmpty());
        }

        @Test
        @DisplayName("Home and End go to the ends of the model")
        void homeAndEnd() {
            var tree = tree(nordics(Set.of()));
            press(row(tree, "list-Finland"), Key.END, Modifiers.NONE);
            assertEquals(List.of("list-Iceland"), host.focusRequests());

            host.forgetFocusRequests();
            press(row(tree, "list-Finland"), Key.HOME, Modifiers.NONE);
            assertEquals(List.of("list-Norway"), host.focusRequests());
        }

        @Test
        @DisplayName("Home and End move the focus and choose nothing")
        void endDoesNotSelect() {
            var tree = tree(nordics(Set.of()));
            press(row(tree, "list-Finland"), Key.END, Modifiers.NONE);
            assertTrue(asked.isEmpty(), () -> "End selected " + asked);
        }

        @Test
        @DisplayName("Home and End on an empty list do nothing at all")
        void endOfNothing() {
            var tree = tree(ListView.of(List.<String>of()));
            // Nothing to press: the assertion is that building and asking for the
            // rows of an empty list is not itself a failure.
            assertEquals(List.of(), rows(tree));
            assertEquals(List.of(), host.focusRequests());
        }
    }

    @Nested
    @DisplayName("type-to-select")
    class Typeahead {

        private ElementTree typeable() {
            return tree(ListView.of(NORDICS)
                    .selection(Selection.MULTIPLE)
                    .selected(Set.of(), asked::add));
        }

        private static void type(ListRow row, String text) {
            row.onText(new TextEvent(text, null));
        }

        @Test
        @DisplayName("a letter moves the focus to the next row that starts with it")
        void oneLetter() {
            var tree = typeable();
            type(row(tree, "list-Norway"), "f");
            assertEquals(List.of("list-Finland"), host.focusRequests());
        }

        @Test
        @DisplayName("it moves the focus and does not choose")
        void typingIsNotChoosing() {
            var tree = typeable();
            type(row(tree, "list-Norway"), "f");
            assertTrue(asked.isEmpty(), () -> "typing selected " + asked);
        }

        @Test
        @DisplayName("a longer prefix narrows rather than starting again")
        void aPrefixAccumulates() {
            var tree = typeable();
            type(row(tree, "list-Norway"), "d");
            assertEquals(List.of("list-Denmark"), host.focusRequests());
            host.forgetFocusRequests();
            // "de" is still Denmark, and must not be read as a fresh "e".
            type(row(tree, "list-Denmark"), "e");
            assertEquals(List.of(), host.focusRequests());
        }

        @Test
        @DisplayName("the same letter again steps to the next match rather than searching for \"nn\"")
        void repeatingALetterCycles() {
            // Three names begin with the same letter nowhere in this list, so the
            // cycle is shown with the two that do: Norway and nothing else means
            // a repeat wraps back to Norway itself and asks for no move.
            var tree = tree(ListView.of(List.of("Alpha", "Anvil", "Beta"))
                    .selection(Selection.MULTIPLE)
                    .selected(Set.of(), asked::add));
            type(row(tree, "list-Alpha"), "a");
            assertEquals(List.of("list-Anvil"), host.focusRequests());
            host.forgetFocusRequests();
            type(row(tree, "list-Anvil"), "a");
            // Wrapped: past the end, back to the first match.
            assertEquals(List.of("list-Alpha"), host.focusRequests());
        }

        @Test
        @DisplayName("no match asks for no move")
        void noMatchDoesNothing() {
            var tree = typeable();
            type(row(tree, "list-Norway"), "z");
            assertEquals(List.of(), host.focusRequests());
        }

        @Test
        @DisplayName("items that expose no text get no typeahead, and the text is not swallowed")
        void withoutTextThereIsNone() {
            // §10 makes it conditional on items exposing text. A row that
            // consumed the keystroke anyway would stop a field elsewhere from
            // ever seeing one.
            var tree = tree(new ListView<>(NORDICS, s -> s, Text::new));
            var event = new TextEvent("f", null);
            row(tree, "list-Norway").onText(event);
            assertFalse(event.isConsumed());
            assertEquals(List.of(), host.focusRequests());
        }
    }

    @Nested
    @DisplayName("item context menus")
    class ItemMenus {

        @Test
        @DisplayName("the name lands on the row, where both ways in look for it")
        void theMenuIsOnTheRow() {
            // A right-click walks up from what is under the pointer and the menu
            // key walks up from what has the focus, which is the row -- so the
            // row is the only place a name works for both (ADR-0208).
            var tree = tree(ListView.of(NORDICS).itemMenu(name -> "country-menu"));
            assertEquals("country-menu", row(tree, "list-Sweden").attributes().contextMenu());
        }

        @Test
        @DisplayName("a per-item function may give one row a menu and another none")
        void aMenuPerItem() {
            var tree = tree(ListView.of(NORDICS)
                    .itemMenu(name -> "Norway".equals(name) ? "home-menu" : null));
            assertEquals("home-menu", row(tree, "list-Norway").attributes().contextMenu());
            assertNull(row(tree, "list-Sweden").attributes().contextMenu());
        }

        @Test
        @DisplayName("no itemMenu at all leaves every row saying nothing")
        void noMenuByDefault() {
            assertNull(row(tree(ListView.of(NORDICS)), "list-Norway").attributes().contextMenu());
        }
    }

    @Nested
    @DisplayName("two lists on one screen")
    class Scoping {

        @Test
        @DisplayName("a row's focus name is scoped by its list's id")
        void rowsAreScopedByTheListId() {
            // `host.focus` takes a name that is global to the window, so two
            // lists over the same items would each answer to the other's `Home`.
            var tree = tree(ListView.of(NORDICS).id("countries"));
            assertEquals(List.of("countries-Norway", "countries-Sweden", "countries-Finland",
                            "countries-Denmark", "countries-Iceland"),
                    rows(tree));
        }

        @Test
        @DisplayName("and End moves within its own list")
        void endStaysInItsOwnList() {
            var tree = tree(ListView.of(NORDICS).id("countries"));
            press(row(tree, "countries-Norway"), Key.END, Modifiers.NONE);
            assertEquals(List.of("countries-Iceland"), host.focusRequests());
        }
    }

    @Nested
    @DisplayName("what a stylesheet sees")
    class Styling {

        @Test
        @DisplayName("the list node carries the id and classes, and the widget styles nothing")
        void theBoxCarriesTheAttributes() {
            var tree = tree(ListView.of(NORDICS).id("countries").styled("dense"));
            var box = Described.of(tree, ListBox.class).getFirst();
            assertEquals("list", box.cssType());
            assertEquals("countries", box.id());
            assertEquals(Set.of("dense"), box.classes());
        }

        @Test
        @DisplayName("a chosen row says so with a class")
        void selectedIsAClass() {
            var tree = tree(nordics(Set.of("Sweden")));
            assertEquals(Set.of("selected"), row(tree, "list-Sweden").classes());
            assertEquals(Set.of(), row(tree, "list-Norway").classes());
        }
    }
}
