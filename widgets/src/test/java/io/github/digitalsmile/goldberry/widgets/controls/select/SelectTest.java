package io.github.digitalsmile.goldberry.widgets.controls.select;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.FrameStats;
import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.Overlay;
import io.github.digitalsmile.goldberry.Placement;
import io.github.digitalsmile.goldberry.Popup;
import io.github.digitalsmile.goldberry.Window;
import io.github.digitalsmile.goldberry.backend.LogicalRect;
import io.github.digitalsmile.goldberry.bind.Property;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.input.HitTest;
import io.github.digitalsmile.goldberry.input.Key;
import io.github.digitalsmile.goldberry.input.KeyEvent;
import io.github.digitalsmile.goldberry.input.Mod;
import io.github.digitalsmile.goldberry.input.Modifiers;
import io.github.digitalsmile.goldberry.input.PointerEvent;
import io.github.digitalsmile.goldberry.input.PointerRouter;
import io.github.digitalsmile.goldberry.input.TextEvent;
import io.github.digitalsmile.goldberry.kdl.KdlParser;
import io.github.digitalsmile.goldberry.text.Fonts;
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
import java.util.Optional;
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
        return new Select("dark", List.of(options), null, null, "", false,
                io.github.digitalsmile.goldberry.widget.Attributes.NONE);
    }

    /// A host that records every popup it was asked for and opens none.
    ///
    /// Empty is what a driver without popup windows answers ([ADR-0102]), so this
    /// is not a crippled host — it is one of the two real cases, and the one a
    /// control must survive.
    private static final class StubHost implements Host {

        record Opened(Widget content, LogicalRect anchor, Placement placement,
                float minimumWidth) {
        }

        private final List<Opened> opened = new ArrayList<>();
        private int repaints;

        @Override
        public Optional<Popup> popup(Widget content, LogicalRect anchor, Placement placement) {
            return popup(content, anchor, placement, 0);
        }

        @Override
        public Optional<Popup> popup(Widget content, LogicalRect anchor, Placement placement,
                float minimumWidth) {
            opened.add(new Opened(content, anchor, placement, minimumWidth));
            return Optional.empty();
        }

        @Override
        public Optional<Popup> popup(Widget content, String anchorId, Placement placement) {
            return Optional.empty();
        }

        @Override
        public Optional<Popup> popup(Widget content,
                io.github.digitalsmile.goldberry.backend.LogicalPoint at,
                io.github.digitalsmile.goldberry.backend.LogicalSize size) {
            return Optional.empty();
        }

        @Override
        public Optional<Popup> tooltip(Widget content,
                io.github.digitalsmile.goldberry.backend.LogicalPoint at,
                io.github.digitalsmile.goldberry.backend.LogicalSize size) {
            return Optional.empty();
        }

        @Override
        public Optional<HitTest.Region> anchor(String id) {
            return Optional.empty();
        }

        @Override
        public LogicalRect placeableArea() {
            return LogicalRect.of(0, 0, 800, 600);
        }

        @Override
        public void repaint() {
            repaints++;
        }

        @Override
        public void restyle() {
        }

        @Override
        public void title(String title) {
        }

        @Override
        public void shortcut(io.github.digitalsmile.goldberry.input.Shortcut a, Runnable r) {
        }

        @Override
        public void shortcut(String accelerator, Runnable action) {
        }

        @Override
        public Overlay overlay(Widget widget,
                io.github.digitalsmile.goldberry.widget.Corner corner) {
            return Overlay.of(widget, corner);
        }

        @Override
        public Overlay overlay(Widget widget,
                io.github.digitalsmile.goldberry.widget.Corner corner, float margin) {
            return Overlay.of(widget, corner, margin);
        }

        @Override
        public Overlay fill(Widget widget) {
            return Overlay.filling(widget);
        }

        @Override
        public void onContextMenu(io.github.digitalsmile.goldberry.ContextMenuHandler handler) {
        }

        @Override
        public io.github.digitalsmile.goldberry.backend.EventLoop.Timer after(
                java.time.Duration delay, Runnable action) {
            throw new UnsupportedOperationException("no event loop in this stub");
        }

        @Override
        public FrameStats frames() {
            throw new UnsupportedOperationException("no frame loop in this stub");
        }

        @Override
        public Fonts fonts() {
            throw new UnsupportedOperationException("no fonts in this stub");
        }

        @Override
        public Window window() {
            throw new UnsupportedOperationException("no window in this stub");
        }
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
                    "Pick one", false, io.github.digitalsmile.goldberry.widget.Attributes.NONE);

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
                    null, null, "", false,
                    io.github.digitalsmile.goldberry.widget.Attributes.NONE);

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
            assertFalse((Widget) select() instanceof io.github.digitalsmile.goldberry.widget.Styled,
                    "a stateful widget that was also styled would style two nodes");
            assertEquals(List.of("select-value", "select-chevron"),
                    field.children().stream()
                            .map(child -> ((io.github.digitalsmile.goldberry.widget.Styled) child)
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
                    null, picked::add, "", false,
                    io.github.digitalsmile.goldberry.widget.Attributes.NONE));
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
                    List.of(new Option("light", "Light")), null, picked::add, "", true,
                    io.github.digitalsmile.goldberry.widget.Attributes.NONE));

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
}
