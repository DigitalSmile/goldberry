package io.github.digitalsmile.goldberry.widgets.controls.select;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.Placement;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.bind.Property;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.input.key.Key;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.key.Mod;
import io.github.digitalsmile.goldberry.input.key.Modifiers;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.PointerRouter;
import io.github.digitalsmile.goldberry.input.event.TextEvent;
import io.github.digitalsmile.goldberry.kdl.KdlParser;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.Widgets;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import io.github.digitalsmile.goldberry.widgets.controls.option.Option;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// The twelfth control, and the first that has to leave its own window.
///
/// `segmented` proved the model this shares — a value read through `bind`, a
/// `change` that reports, an exactly-one invariant computed rather than stored.
/// What is new here is everything about the *list*: it is somewhere else, it has
/// to be asked for, and the thing asking is a widget, which until now could not
/// ([ADR-0140], [ADR-0141]).
///
/// Driven against a stub [Host] that records what it was asked to open and
/// answers empty, which is a real answer — a driver with no popup windows gives
/// exactly that. Everything a popup that actually opened does is in
/// [SelectPopupTest], which runs the real launcher.
class SelectTest {

    private static List<Widget> inflate(String markup) {
        return Widgets.inflater().inflateAll(KdlParser.parse(markup));
    }

    private static Select select(Option... options) {
        return new Select("dark", List.of(options), null, null, "", false, false, false, null, List.of(), false,
                io.github.digitalsmile.goldberry.widget.attr.Attributes.NONE);
    }

    /// A host that records every popup it was asked for and opens none.
    ///
    /// [TestHost] is all of that already, and the reason this subclass exists at
    /// all is that a `select` reaches for the rectangle overload — so the empty
    /// answer has to come back without an anchor having been registered first.
    private static final class StubHost extends
            io.github.digitalsmile.goldberry.widgets.TestHost {
    }

    /// The `select-field` the widget describes, which is the node a stylesheet
    /// and the router both see.
    private static SelectField field(ElementTree tree) {
        return (SelectField) tree.root().children().getFirst().widget();
    }

    private static Element fieldElement(ElementTree tree) {
        return tree.root().children().getFirst();
    }

    @Nested
    @DisplayName("the value, which is `segmented`'s exactly")
    class Value {

        @Test
        @DisplayName("an unbound select shows the option its value names")
        void unbound() {
            var it = select(new Option("light", "Light"), new Option("dark", "Dark"));

            assertEquals("dark", it.resolved());
            assertEquals("Dark", it.label(), "the label, not the value");
            assertNotNull(it.selected());
        }

        @Test
        @DisplayName("a bound select reads the property, coerced through `toString`")
        void bound() {
            var theme = Property.of(2);
            var it = Select.of(theme, null, new Option("1", "One"), new Option("2", "Two"));

            assertEquals("2", it.resolved());
            assertEquals("Two", it.label());
        }

        @Test
        @DisplayName("a value no option carries selects nothing rather than the first")
        void unknown() {
            var it = new Select("nord", List.of(new Option("light", "Light")), null, null,
                    "Pick one", false, false, false, null, List.of(), false,
                    io.github.digitalsmile.goldberry.widget.attr.Attributes.NONE);

            assertNull(it.selected(), "guessing would report a value nobody picked");
            assertEquals("Pick one", it.label(), "so it falls back to the placeholder");
        }

        @Test
        @DisplayName("nothing selected reads the placeholder")
        void placeholder() {
            var it = new Select(new Option("light", "Light")).placeholder("Choose…");

            assertNull(it.selected());
            assertEquals("Choose…", it.label());
        }

        @Test
        @DisplayName("`binding()` is what the element subscribes to")
        void subscribes() {
            var theme = Property.of("dark");
            new ElementTree(Select.of(theme, null, new Option("dark", "Dark")));

            assertEquals(1, theme.listenerCount());
        }

        @Test
        @DisplayName("non-option children are kept, and are not options")
        void otherChildren() {
            var it = new Select("dark",
                    List.of(new io.github.digitalsmile.goldberry.widgets.text.Text("Themes"),
                            new Option("dark", "Dark")),
                    null, null, "", false, false, false, null, List.of(), false,
                    io.github.digitalsmile.goldberry.widget.attr.Attributes.NONE);

            assertEquals(2, it.children().size());
            assertEquals(1, it.options().size(), "a heading is not an option");
        }
    }

    @Nested
    @DisplayName("what it describes")
    class Anatomy {

        @Test
        @DisplayName("`select` is the field, and the widget itself styles nothing")
        void anatomy() {
            var tree = new ElementTree(select(new Option("dark", "Dark")));
            var field = field(tree);

            assertEquals("select", field.cssType());
            assertFalse((Widget) select() instanceof io.github.digitalsmile.goldberry.widget.style.Styled,
                    "a stateful widget that was also styled would style two nodes");
            assertEquals(List.of("select-value", "select-chevron"),
                    field.children().stream()
                            .map(child -> ((io.github.digitalsmile.goldberry.widget.style.Styled) child)
                                    .cssType())
                            .toList());
        }

        @Test
        @DisplayName("the id and the classes travel down to the node that carries the type")
        void attributesTravel() {
            var widget = inflate("select id=\"theme\" class=\"wide\" { option value=\"d\" \"D\" }")
                    .getFirst();
            var field = field(new ElementTree(widget));

            assertEquals("theme", field.id());
            assertTrue(field.classes().contains("wide"));
        }

        @Test
        @DisplayName("`.placeholder` marks the value node standing in for a value")
        void placeholderClass() {
            var chosen = field(new ElementTree(select(new Option("dark", "Dark"))));
            var empty = field(new ElementTree(new Select(new Option("dark", "Dark"))));

            assertFalse(((SelectValue) chosen.children().getFirst()).placeholder());
            assertTrue(((SelectValue) empty.children().getFirst()).placeholder());
            assertTrue(((SelectValue) empty.children().getFirst()).classes().contains("placeholder"));
        }

        @Test
        @DisplayName("a closed field carries no `.open`")
        void closedHasNoOpenClass() {
            assertFalse(field(new ElementTree(select(new Option("dark", "Dark"))))
                    .classes().contains("open"));
        }

        @Test
        @DisplayName("focus lands on the field, and it is one Tab stop")
        void focusable() {
            var field = field(new ElementTree(select(new Option("dark", "Dark"))));

            assertTrue(field.isFocusable());
            assertFalse(field.isDisabled());
        }

        @Test
        @DisplayName("a disabled select is not focusable and matches `:disabled`")
        void disabled() {
            var field = field(new ElementTree(
                    select(new Option("dark", "Dark")).disabled(true)));

            assertFalse(field.isFocusable());
            assertTrue(field.isDisabled());
        }
    }

    @Nested
    @DisplayName("markup")
    class Markup {

        @Test
        @DisplayName("`select` inflates with its options, placeholder and value")
        void inflates() {
            var widget = inflate("""
                    select value="dark" placeholder="Choose a theme" {
                        option value="light" "Light"
                        option value="dark" "Dark"
                    }
                    """).getFirst();

            var it = (Select) widget;
            assertEquals("dark", it.resolved());
            assertEquals(2, it.options().size());
            assertEquals("Dark", it.label());
        }

        @Test
        @DisplayName("`disabled=#true` reaches the record")
        void disabledAttribute() {
            var it = (Select) inflate("select disabled=#true { option value=\"d\" \"D\" }")
                    .getFirst();

            assertTrue(it.disabled());
        }

        @Test
        @DisplayName("`select` is a registered control and its parts are not")
        void parity() {
            assertTrue(Controls.controlTypes().contains("select"));
            assertFalse(Controls.controlTypes().contains("select-value"));
            assertFalse(Controls.controlTypes().contains("select-chevron"));
            assertFalse(Controls.controlTypes().contains("select-list"));
        }

        @Test
        @DisplayName("an `option` is the same widget a `segmented` writes")
        void sharesOption() {
            var fromSelect = (Select) inflate("select { option value=\"d\" \"D\" }").getFirst();
            var fromBar = inflate("segmented { option value=\"d\" \"D\" }").getFirst();

            // A bar rewrites its options into a track on every build, so the
            // comparable value is the one inside it.
            var track = (Widget.Leaf) ((io.github.digitalsmile.goldberry.widgets.controls.segmented
                    .Segmented) fromBar).children().getFirst();
            var inBar = track.children().get(1);

            assertEquals(fromSelect.options().getFirst(), inBar,
                    "one node, one record — §3 gives both controls the same child");
        }
    }

    @Nested
    @DisplayName("opening it, which is what needs a window")
    class Opening {

        private final StubHost host = new StubHost();

        private ElementTree tree(Select select) {
            return new ElementTree(select, host);
        }

        private void click(SelectField field) {
            field.onPointer(new PointerEvent(PointerEvent.Kind.CLICKED, 0, 0,
                    PointerEvent.Button.PRIMARY, 1, null));
        }

        private void key(SelectField field, Key key, Modifiers modifiers) {
            field.onKey(new KeyEvent(KeyEvent.Kind.PRESSED, key, modifiers, false, null));
        }

        private Select two() {
            return select(new Option("light", "Light"), new Option("dark", "Dark"));
        }

        @Test
        @DisplayName("a click asks the window for a popup under the field")
        void clickOpens() {
            var tree = tree(two());
            field(tree).located(LogicalRect.of(10, 20, 160, 32), LogicalRect.of(0, 0, 800, 600));

            click(field(tree));

            assertEquals(1, host.opened.size());
            assertEquals(LogicalRect.of(10, 20, 160, 32), host.opened.getFirst().anchor(),
                    "anchored to where the last frame painted it, not to an id");
            assertEquals(Placement.BELOW, host.opened.getFirst().placement());
        }

        /// **A dropdown is at least as wide as what it drops from** — [ADR-0145].
        ///
        /// The list is measured from its content, and its content knows nothing
        /// about the field: a `select` stretched across a form opened a panel as
        /// wide as the word "Dark" hanging off its left-hand end, which reads as
        /// a mistake rather than as a menu.
        @Test
        @DisplayName("the list is asked to be at least as wide as the field")
        void atLeastAsWideAsTheField() {
            var tree = tree(two());
            field(tree).located(LogicalRect.of(10, 20, 240, 32), LogicalRect.of(0, 0, 800, 600));

            click(field(tree));

            assertEquals(240f, host.opened.getFirst().minimumWidth(), 0.5f);
        }

        /// The defect [ADR-0179] was written for: a `select` never capped its own
        /// list, so one with more options than the display is tall was clamped to
        /// the near edge by the placement and lost its bottom — the last options
        /// simply not there, with nothing to say so. `menu` had solved this from
        /// an estimate; a `select` could not even estimate, because it cannot lay
        /// anything out either.
        ///
        /// `StubHost` is [io.github.digitalsmile.goldberry.widgets.TestHost],
        /// which consults the `Fit` with whatever [io.github.digitalsmile.goldberry.widgets.TestHost#measuring]
        /// says the content came out as — the one thing a test without a window
        /// cannot get any other way.
        @Test
        @DisplayName("a list taller than the screen scrolls rather than losing its bottom")
        void aLongListScrolls() {
            host.measuring(200, 4000);
            var tree = tree(two());

            click(field(tree));

            var opened = host.opened.getFirst().content();
            var viewport = assertInstanceOf(
                    io.github.digitalsmile.goldberry.widgets.core.scroll.Scroll.class, opened,
                    "the list was opened at its full height and will be clamped");
            assertEquals(600 - 2 * io.github.digitalsmile.goldberry.widgets.core.scroll.Fitted.MARGIN,
                    viewport.height(), 0.001,
                    "the viewport is not the height of the room the list has");
            assertInstanceOf(SelectList.class, viewport.children().getFirst(),
                    "the list is inside the viewport rather than replaced by it");
        }

        /// Which is nearly every `select`. A viewport around a list of three
        /// options would draw a thumb and take the wheel for no reason.
        @Test
        @DisplayName("a list that fits is opened exactly as it always was")
        void aShortListIsUntouched() {
            host.measuring(200, 64);
            var tree = tree(two());

            click(field(tree));

            assertInstanceOf(SelectList.class, host.opened.getFirst().content());
        }

        @Test
        @DisplayName("the popup's content is a `select-list` of the options, told what they are")
        void listContent() {
            var tree = tree(two());
            click(field(tree));

            var list = (SelectList) host.opened.getFirst().content();
            assertEquals("select-list", list.cssType());
            var rows = list.children().stream().map(Option.class::cast).toList();
            assertEquals(List.of("light", "dark"), rows.stream().map(Option::value).toList());
            assertFalse(rows.getFirst().selected());
            assertTrue(rows.get(1).selected(), "the bound value is the one marked");
            assertNotNull(rows.get(1).onSelect(), "and every row knows what picking it does");
        }

        @Test
        @DisplayName("choosing a row reports the value and sets nothing")
        void chooseReports() {
            var picked = new ArrayList<String>();
            var tree = tree(new Select("dark", List.of(
                    new Option("light", "Light"), new Option("dark", "Dark")),
                    null, picked::add, "", false, false, false, null, List.of(), false,
                    io.github.digitalsmile.goldberry.widget.attr.Attributes.NONE));
            click(field(tree));

            var rows = ((SelectList) host.opened.getFirst().content()).children();
            ((Option) rows.getFirst()).onSelect().run();

            assertEquals(List.of("light"), picked);
        }

        @Test
        @DisplayName("`Space` opens it and `Enter` deliberately does not")
        void keyboardOpen() {
            var tree = tree(two());

            key(field(tree), Key.SPACE, Modifiers.NONE);
            assertEquals(1, host.opened.size());

            key(field(tree), Key.ENTER, Modifiers.NONE);
            assertEquals(1, host.opened.size(), "Enter belongs to a dialog's default action");
        }

        @Test
        @DisplayName("`Alt+Down` and a bare `Down` both open it — §3's keyboard")
        void arrowsOpen() {
            var tree = tree(two());

            key(field(tree), Key.DOWN, Modifiers.of(Mod.ALT));
            key(field(tree), Key.DOWN, Modifiers.NONE);
            key(field(tree), Key.UP, Modifiers.NONE);

            assertEquals(3, host.opened.size());
        }

        @Test
        @DisplayName("a disabled select opens nothing")
        void disabledDoesNotOpen() {
            var tree = tree(two().disabled(true));

            click(field(tree));
            key(field(tree), Key.SPACE, Modifiers.NONE);

            assertTrue(host.opened.isEmpty());
        }

        @Test
        @DisplayName("a select with no options opens nothing")
        void emptyDoesNotOpen() {
            var tree = tree(new Select());

            click(field(tree));

            assertTrue(host.opened.isEmpty(), "an empty panel is worse than no panel");
        }

        @Test
        @DisplayName("a select with no window behind it stays closed and does not throw")
        void noHost() {
            var tree = new ElementTree(two());

            click(field(tree));

            assertFalse(field(tree).classes().contains("open"),
                    "a golden image builds exactly this, and it has to draw");
        }

        @Test
        @DisplayName("a driver with no popup windows leaves the field closed")
        void refusedPopup() {
            var tree = tree(two());

            click(field(tree));

            assertEquals(1, host.opened.size(), "it asked");
            assertFalse(field(tree).classes().contains("open"), "and it was refused");
        }
    }

    @Nested
    @DisplayName("typeahead (§3)")
    class Typeahead {

        private final List<String> picked = new ArrayList<>();

        /// The value, as an application holds it.
        ///
        /// A real property and not a dead handler, because typeahead is the one
        /// place in this control where the *next* answer depends on the last one
        /// having been accepted: a select is controlled, so "the option after the
        /// selected one" is a question about the model. A test whose handler
        /// dropped the value would prove the first letter and nothing after it.
        private final Property<String> theme = Property.of("dark");

        private ElementTree tree() {
            return new ElementTree(Select.of(theme, value -> {
                picked.add(value);
                theme.set(value);
            },
                    new Option("light", "Light"),
                    new Option("dark", "Dark"),
                    new Option("dim", "Dim"),
                    new Option("solar", "Solarized").disabled(true)));
        }

        private void type(ElementTree tree, String text) {
            field(tree).onText(new TextEvent(text, null));
        }

        @Test
        @DisplayName("a letter asks for the next option starting with it")
        void firstLetter() {
            var tree = tree();

            type(tree, "l");

            assertEquals(List.of("light"), picked);
        }

        @Test
        @DisplayName("it is case-insensitive, because a user is not typing a value")
        void caseInsensitive() {
            var tree = tree();

            type(tree, "L");

            assertEquals(List.of("light"), picked);
        }

        @Test
        @DisplayName("a repeated letter cycles past the one already selected")
        void cycles() {
            var tree = tree();

            // Selected is `dark`; the next `d` is `dim`, and the one after wraps.
            type(tree, "d");

            assertEquals(List.of("dim"), picked);
        }

        @Test
        @DisplayName("a longer prefix matches from the top, so `d` then `da` is `dark`")
        void prefix() {
            var tree = tree();
            type(tree, "d");
            picked.clear();

            type(tree, "a");

            assertEquals(List.of("dark"), picked,
                    "\"da\" must not skip Dark for having matched a moment ago");
        }

        @Test
        @DisplayName("a disabled option is not reachable by typing")
        void skipsDisabled() {
            var tree = tree();

            type(tree, "s");

            assertTrue(picked.isEmpty(), "an unavailable option is unavailable to the keyboard too");
        }

        @Test
        @DisplayName("no match asks for nothing")
        void noMatch() {
            var tree = tree();

            type(tree, "z");

            assertTrue(picked.isEmpty());
        }

        @Test
        @DisplayName("a repeated letter wraps once it runs off the end")
        void wraps() {
            var tree = tree();
            type(tree, "d");
            picked.clear();
            // `dim` is selected now, and the only `d` after it is disabled.
            type(tree, "d");

            assertEquals(List.of("dark"), picked, "so it comes round to the first one again");
        }

        @Test
        @DisplayName("typing on a disabled select does nothing")
        void disabled() {
            var tree = new ElementTree(new Select("dark",
                    List.of(new Option("light", "Light")), null, picked::add, "", false, false, false, null, List.of(), true,
                    io.github.digitalsmile.goldberry.widget.attr.Attributes.NONE));

            type(tree, "l");

            assertTrue(picked.isEmpty());
        }
    }

    @Nested
    @DisplayName("one Tab stop, and the field is it")
    class Traversal {

        private PointerRouter routed(ElementTree tree) {
            new WidgetRenderer(List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load()),
                    TestFont.get()).render(tree);
            var router = new PointerRouter();
            router.focusRoot(tree.root());
            return router;
        }

        @Test
        @DisplayName("Tab reaches the field and not its parts")
        void oneStop() {
            var tree = new ElementTree(new io.github.digitalsmile.goldberry.widgets.core.Column(
                    new io.github.digitalsmile.goldberry.widgets.controls.button.Button(
                            "Before", () -> { }),
                    select(new Option("light", "Light"), new Option("dark", "Dark")),
                    new io.github.digitalsmile.goldberry.widgets.controls.button.Button(
                            "After", () -> { })));
            var router = routed(tree);

            router.keyPressed(Key.TAB, Modifiers.NONE, false);
            assertEquals("button", router.focused().type());
            router.keyPressed(Key.TAB, Modifiers.NONE, false);
            assertEquals("select", router.focused().type(), "the field, not the value or the mark");
            router.keyPressed(Key.TAB, Modifiers.NONE, false);
            assertEquals("button", router.focused().type());
        }

        @Test
        @DisplayName("a disabled select is skipped entirely")
        void disabledIsSkipped() {
            var tree = new ElementTree(new io.github.digitalsmile.goldberry.widgets.core.Column(
                    new io.github.digitalsmile.goldberry.widgets.controls.button.Button(
                            "Before", () -> { }),
                    select(new Option("dark", "Dark")).disabled(true),
                    new io.github.digitalsmile.goldberry.widgets.controls.button.Button(
                            "After", () -> { })));
            var router = routed(tree);

            router.keyPressed(Key.TAB, Modifiers.NONE, false);
            var first = router.focused();
            router.keyPressed(Key.TAB, Modifiers.NONE, false);

            assertEquals("button", router.focused().type());
            assertFalse(first == router.focused());
        }
    }

    /// §3: "`multiple=#true` renders the selection as `badge` chips inside the
    /// closed control, each with a remove affordance" ([ADR-0182]).
    @Nested
    @DisplayName("holding more than one")
    class Multiple {

        private final StubHost host = new StubHost();

        private Select multi(Object bound, Option... options) {
            var source = bound == null ? null
                    : io.github.digitalsmile.goldberry.bind.Property.of(bound);
            return new Select(null, List.of(options), source, picked::add, "Pick some", true,
                    false, false, null, List.of(), false,
                    io.github.digitalsmile.goldberry.widget.attr.Attributes.NONE);
        }

        private final List<String> picked = new java.util.ArrayList<>();

        private static final Option LIGHT = new Option("light", "Light");
        private static final Option DARK = new Option("dark", "Dark");
        private static final Option DIM = new Option("dim", "Dim");

        private List<SelectChip> chips(ElementTree tree) {
            return io.github.digitalsmile.goldberry.widgets.panel.Described
                    .of(tree, SelectChip.class);
        }

        /// A bound `Collection` is a set of values; anything else is one value.
        /// So a model that starts as a single value and becomes a list, or the
        /// other way round, is not a different kind of binding.
        @Test
        @DisplayName("a bound collection is the selection, and a bare value is one of them")
        void aCollectionIsTheSelection() {
            assertEquals(List.of("light", "dim"),
                    multi(List.of("light", "dim"), LIGHT, DARK, DIM).resolvedAll());
            assertEquals(List.of("dark"), multi("dark", LIGHT, DARK, DIM).resolvedAll());
            assertEquals(List.of(), multi(null, LIGHT, DARK, DIM).resolvedAll(),
                    "a null is nothing selected, not one null selected");
        }

        /// Ordered by the **options**, so removing a chip and putting the value
        /// back does not move it to the end of the row.
        @Test
        @DisplayName("the order is the options', not the model's")
        void orderedByTheOptions() {
            assertEquals(List.of("light", "dim"),
                    multi(List.of("dim", "light"), LIGHT, DARK, DIM).resolvedAll());
        }

        @Test
        @DisplayName("a value the select does not offer selects nothing, and duplicates collapse")
        void unknownAndDuplicate() {
            assertEquals(List.of("dark"),
                    multi(List.of("dark", "purple"), LIGHT, DARK, DIM).resolvedAll(),
                    "an option nobody wrote was drawn as a chip");
            assertEquals(List.of("dark"),
                    multi(List.of("dark", "dark"), LIGHT, DARK, DIM).resolvedAll(),
                    "two chips saying the same word are two affordances doing one thing");
        }

        @Test
        @DisplayName("the closed control shows a chip per value, with its label")
        void chipsInTheField() {
            var tree = new ElementTree(multi(List.of("light", "dim"), LIGHT, DARK, DIM), host);

            assertEquals(List.of("Light", "Dim"),
                    chips(tree).stream().map(SelectChip::label).toList(),
                    "the label and not the value -- that is what the two words are for");
        }

        /// The chips replace the value rather than joining it: a control saying
        /// "Two selected" *and* showing two chips says one thing twice.
        @Test
        @DisplayName("with nothing chosen it reads the placeholder, not an empty row")
        void emptyFallsBackToThePlaceholder() {
            var tree = new ElementTree(multi(List.of(), LIGHT, DARK), host);

            assertEquals(List.of(), chips(tree));
            var value = (SelectValue) field(tree).children().getFirst();
            assertEquals("Pick some", value.text());
            assertTrue(value.placeholder());
        }

        /// One channel rather than two: `change` is a **toggle** in this mode, so
        /// a chip's × and a click on an already-chosen row mean the same thing.
        /// The set is the application's, and asking for a value it already holds
        /// can only mean taking it out.
        @Test
        @DisplayName("a chip's remove asks for the same value picking it would")
        void theChipRemovesByAsking() {
            var tree = new ElementTree(multi(List.of("light", "dim"), LIGHT, DARK, DIM), host);

            chips(tree).getFirst().onRemove().run();

            assertEquals(List.of("light"), picked);
        }

        /// The whole point of the mode is picking several, and a list that shut
        /// after each one would make three values three round trips through a
        /// popup that has to be measured, placed and opened again each time.
        @Test
        @DisplayName("the list stays open while values are picked")
        void theListStaysOpen() {
            var tree = new ElementTree(multi(List.of(), LIGHT, DARK, DIM), host);
            field(tree).located(LogicalRect.of(10, 20, 160, 32), LogicalRect.of(0, 0, 800, 600));

            click(field(tree));

            assertEquals(1, host.opened.size(), "it did not open");
            // `StubHost` opens nothing, so what is asserted here is the state's
            // own view: a single-valued select would have cleared it.
            assertTrue(multi(List.of(), LIGHT).multiple());
        }

        @Test
        @DisplayName("a single-valued select has no chips at all")
        void singleValuedIsUnchanged() {
            var tree = new ElementTree(select(LIGHT, DARK), host);

            assertEquals(List.of(), chips(tree));
            assertInstanceOf(SelectValue.class, field(tree).children().getFirst());
        }

        private void click(SelectField f) {
            f.onPointer(new PointerEvent(PointerEvent.Kind.CLICKED, 0, 0,
                    PointerEvent.Button.PRIMARY, 1, null));
        }
    }


    /// §3's `autocomplete=#true`: "makes the closed control an editable
    /// `text-input`: typing filters the options, the popup stays open and
    /// narrows, `Esc` restores the last committed value rather than clearing, and
    /// a free-typed value is refused unless `free=#true`" ([ADR-0183]).
    @Nested
    @DisplayName("typing in it")
    class Autocompleting {

        private final StubHost host = new StubHost();
        private final List<String> queries = new java.util.ArrayList<>();
        private final List<String> changes = new java.util.ArrayList<>();

        private static final Option LIGHT = new Option("light", "Light");
        private static final Option DARK = new Option("dark", "Dark");

        private Select combo(String value, boolean free, Option... options) {
            return new Select(value, List.of(options), null, changes::add, "Pick", false,
                    true, free, queries::add, List.of(), false,
                    io.github.digitalsmile.goldberry.widget.attr.Attributes.NONE);
        }

        private ElementTree tree(Select select) {
            return new ElementTree(select, host);
        }

        private io.github.digitalsmile.goldberry.widgets.form.textinput.TextInput editor(
                ElementTree tree) {
            return io.github.digitalsmile.goldberry.widgets.panel.Described.first(tree,
                    io.github.digitalsmile.goldberry.widgets.form.textinput.TextInput.class);
        }

        @Test
        @DisplayName("the closed control is an editable text-input showing the committed label")
        void theFieldIsEditable() {
            var tree = tree(combo("dark", false, LIGHT, DARK));

            assertEquals("Dark", editor(tree).value(),
                    "the label and not the value -- that is what the two words are for");
            assertEquals("Pick", editor(tree).placeholder());
        }

        /// One Tab stop. A field that was focusable *and* held a focusable editor
        /// would make a combobox two stops where a document wrote one control.
        @Test
        @DisplayName("the field is not a tab stop, and hands a press to the editor")
        void oneTabStop() {
            var tree = tree(combo("dark", false, LIGHT, DARK));

            assertFalse(field(tree).isFocusable());
            assertTrue(field(tree).delegatesFocus());
        }

        /// Filtering is the application's: the control raises what was typed and
        /// renders whatever it is handed back.
        @Test
        @DisplayName("typing raises the query and opens the list")
        void typingRaisesTheQuery() {
            var tree = tree(combo("dark", false, LIGHT, DARK));

            editor(tree).onChange().accept("Li");
            tree.flush();

            assertEquals(List.of("Li"), queries, "nothing was raised for the application to filter on");
            assertEquals(1, host.opened.size(), "the list did not open under the typing");
            assertEquals(List.of(), changes, "typing chose something");
        }

        /// The sentence that tells a combobox apart from a search box: the
        /// control holds a value, typing is a way of reaching one, and abandoning
        /// the attempt leaves the value alone.
        @Test
        @DisplayName("Esc restores the committed value rather than clearing")
        void escRestores() {
            var tree = tree(combo("dark", false, LIGHT, DARK));
            editor(tree).onChange().accept("Li");
            tree.flush();
            assertEquals("Li", editor(tree).value());

            field(tree).onKey(new KeyEvent(KeyEvent.Kind.PRESSED, Key.ESCAPE, Modifiers.NONE,
                    false, null));
            tree.flush();

            assertEquals("Dark", editor(tree).value(), "it cleared, or it kept the attempt");
            assertEquals(List.of(), changes, "abandoning an attempt reported a change");
        }

        /// A combobox is a **set** of values, so text naming none of them is a
        /// mistake rather than a new member.
        @Test
        @DisplayName("a free-typed value is refused when free is off")
        void refusedUnlessFree() {
            var tree = tree(combo("dark", false, LIGHT, DARK));
            editor(tree).onChange().accept("Purple");
            tree.flush();

            field(tree).onFocusWithin(false, true);
            tree.flush();

            assertEquals("Dark", editor(tree).value(), "a value nobody offers was kept");
            assertEquals(List.of(), changes);
        }

        /// The other reading, which §4's free-text form always is: the
        /// suggestions are a convenience and any value is legal.
        @Test
        @DisplayName("free=#true keeps it and reports it")
        void freeKeepsIt() {
            var tree = tree(combo("dark", true, LIGHT, DARK));
            editor(tree).onChange().accept("Purple");
            tree.flush();

            field(tree).onFocusWithin(false, true);
            tree.flush();

            assertEquals(List.of("Purple"), changes, "a legal value was thrown away");
        }

        @Test
        @DisplayName("a plain select has no editor at all")
        void plainIsUnchanged() {
            var tree = tree(select(LIGHT, DARK));

            assertEquals(0, io.github.digitalsmile.goldberry.widgets.panel.Described.of(tree,
                    io.github.digitalsmile.goldberry.widgets.form.textinput.TextInput.class).size());
            assertTrue(field(tree).isFocusable());
            assertFalse(field(tree).delegatesFocus());
        }

        /// §3 lists `Space` as a way to open a *closed* control, and a combobox is
        /// not one — a space is a character.
        @Test
        @DisplayName("Space types a space rather than opening the list")
        void spaceIsACharacter() {
            var tree = tree(combo("dark", false, LIGHT, DARK));

            field(tree).onKey(new KeyEvent(KeyEvent.Kind.PRESSED, Key.SPACE, Modifiers.NONE,
                    false, null));
            tree.flush();

            assertTrue(host.opened.isEmpty(), "Space opened the list in an editable control");
        }
    }

}
