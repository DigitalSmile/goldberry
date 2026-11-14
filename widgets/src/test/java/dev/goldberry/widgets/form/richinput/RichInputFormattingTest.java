package dev.goldberry.widgets.form.richinput;

import static dev.goldberry.text.mark.MarkKind.Style.BOLD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.Placement;
import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Theme;
import dev.goldberry.input.event.KeyEvent;
import dev.goldberry.input.event.PointerEvent;
import dev.goldberry.input.event.TextEvent;
import dev.goldberry.input.hit.Extent;
import dev.goldberry.input.key.Key;
import dev.goldberry.input.key.Mod;
import dev.goldberry.input.key.Modifiers;
import dev.goldberry.input.key.PrimaryModifier;
import dev.goldberry.render.model.LogicalRect;
import dev.goldberry.text.edit.keys.FormatCommand;
import dev.goldberry.text.mark.Mark;
import dev.goldberry.text.mark.MarkKind;
import dev.goldberry.text.mark.MarkedText;
import dev.goldberry.widget.Element;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widget.WidgetRenderer;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.TestHost;
import dev.goldberry.widgets.controls.TestFont;
import dev.goldberry.widgets.menu.Item;

/// What opens around a `rich-input`'s selection: the formatting bar, the link
/// editor and the text menu's Format submenu, and `Enter` that sends.
///
/// Driven through a [TestHost], which records every panel a widget asks for and
/// opens none, so the bar's content is read from what was asked.
@DisplayName("rich-input formatting")
class RichInputFormattingTest {

    /// Where the field was drawn in the window.
    private static final LogicalRect FIELD = LogicalRect.of(20, 200, 300, 40);

    private final TestHost host = new TestHost();
    private final List<MarkedText> changes = new ArrayList<>();
    private final List<MarkedText> sent = new ArrayList<>();

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private ElementTree mounted(RichInput input) {
        var tree = new ElementTree(input, host);
        render(tree);
        box(tree).measured(new Extent(300, 40), new Extent(300, 40));
        render(tree);
        box(tree).located(FIELD, FIELD);
        box(tree).onFocusChanged(true, false);
        render(tree);
        return tree;
    }

    private RichInput composer() {
        return new RichInput(MarkedText.EMPTY, changes::add);
    }

    private static void render(ElementTree tree) {
        tree.flush();
        new WidgetRenderer(List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load()), TestFont.get()).render(tree);
    }

    private static RichInputBox box(ElementTree tree) {
        return (RichInputBox) boxElement(tree.root()).widget();
    }

    private static Element boxElement(Element from) {
        if (from.widget() instanceof RichInputBox) {
            return from;
        }
        for (var child : from.children()) {
            var found = boxElementOrNull(child);
            if (found != null) {
                return found;
            }
        }
        throw new AssertionError("no rich-input box");
    }

    private static Element boxElementOrNull(Element from) {
        if (from.widget() instanceof RichInputBox) {
            return from;
        }
        for (var child : from.children()) {
            var found = boxElementOrNull(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static RichInputState state(ElementTree tree) {
        return (RichInputState) tree.root().state().orElseThrow();
    }

    private static void type(ElementTree tree, String text) {
        box(tree).onText(new TextEvent(text, null));
        render(tree);
    }

    private static KeyEvent key(ElementTree tree, Key key, Modifiers modifiers) {
        var event = new KeyEvent(KeyEvent.Kind.PRESSED, key, modifiers, false, null);
        box(tree).onKey(event);
        render(tree);
        return event;
    }

    private static KeyEvent primary(ElementTree tree, Key key) {
        return key(tree, key, Modifiers.of(PrimaryModifier.current()));
    }

    private void selectAll(ElementTree tree) {
        primary(tree, Key.A);
    }

    /// The last panel asked for, if it holds a `T`.
    private <T> T last(Class<T> type) {
        assertFalse(host.opened.isEmpty(), "nothing was opened");
        return assertInstanceOf(type, host.opened.getLast().content());
    }

    private static PointerEvent pointer(PointerEvent.Kind kind, float x) {
        var event = new PointerEvent(
                kind, x, 10, PointerEvent.Button.PRIMARY, 1, Float.NaN, Float.NaN, Modifiers.NONE, null);
        event.localTo(new PointerEvent.Local(x, 10, 300, 40));
        event.contentTo(new PointerEvent.Local(x, 10, 300, 40));
        return event;
    }

    @Nested
    @DisplayName("the bar")
    class Bar {

        @Test
        @DisplayName("opens above a selection, with the stock buttons")
        void opensOverSelection() {
            var tree = mounted(composer());
            type(tree, "Ship it");
            assertTrue(host.opened.isEmpty(), "no selection, no bar");
            selectAll(tree);
            var bar = last(FormatBar.class);
            assertEquals(FormatButton.defaults(), bar.buttons());
            var opened = host.opened.getLast();
            assertEquals(Placement.ABOVE, opened.placement());
            assertTrue(
                    opened.anchor().top() >= FIELD.top() && opened.anchor().top() < FIELD.bottom(),
                    "anchored on the selection's line inside the field: " + opened.anchor());
        }

        @Test
        @DisplayName("a button toggles its style, and the bar shows it as on")
        void togglesFromTheBar() {
            var tree = mounted(composer());
            type(tree, "Ship it");
            selectAll(tree);
            last(FormatBar.class).onPress().accept(new FormatCommand.Toggle(BOLD));
            render(tree);
            assertEquals(List.of(new Mark(0, 7, BOLD)), changes.getLast().marks());
            assertTrue(last(FormatBar.class).active().contains(new FormatCommand.Toggle(BOLD)));
        }

        @Test
        @DisplayName("waits for a drag to be let go")
        void waitsForRelease() {
            var tree = mounted(composer());
            type(tree, "Ship it");
            host.opened.clear();
            box(tree).onPointer(pointer(PointerEvent.Kind.PRESSED, 1));
            var drag = new PointerEvent(
                    PointerEvent.Kind.MOVED, 200, 10, PointerEvent.Button.PRIMARY, 1, 1, 10, Modifiers.NONE, null);
            drag.contentTo(new PointerEvent.Local(200, 10, 300, 40));
            box(tree).onPointer(drag);
            render(tree);
            assertTrue(host.opened.isEmpty(), "a selection being dragged gets no bar");
            box(tree).onPointer(pointer(PointerEvent.Kind.RELEASED, 200));
            render(tree);
            last(FormatBar.class);
        }

        @Test
        @DisplayName("is not offered to a field that is read-only, unfocused, or has no buttons")
        void notOffered() {
            var readOnly = mounted(new RichInput(MarkedText.of("fixed"), changes::add).readOnly(true));
            selectAll(readOnly);
            var none = mounted(composer().formatting(List.of()));
            type(none, "words");
            selectAll(none);
            assertTrue(host.opened.stream().noneMatch(open -> open.content() instanceof FormatBar));

            var blurred = mounted(composer());
            type(blurred, "words");
            box(blurred).onFocusChanged(false, false);
            render(blurred);
            host.opened.clear();
            selectAll(blurred);
            assertTrue(host.opened.isEmpty());
        }

        @Test
        @DisplayName("its icons are bundled ones")
        void icons() {
            var tree = mounted(composer());
            type(tree, "x");
            selectAll(tree);
            assertEquals(8, state(tree).popups().icons().size());
        }
    }

    @Nested
    @DisplayName("the link editor")
    class Links {

        @Test
        @DisplayName("Ctrl+K over a selection opens it empty, and Apply links the words")
        void addsALink() {
            var tree = mounted(composer());
            type(tree, "site");
            selectAll(tree);
            assertTrue(primary(tree, Key.K).isConsumed());
            var editor = last(LinkEditor.class);
            assertEquals("", editor.address());
            assertFalse(editor.existing());
            editor.onEdit().accept("goldberry.dev");
            editor.onApply().run();
            render(tree);
            var link = new MarkKind.Link(URI.create("https://goldberry.dev"));
            assertEquals(List.of(new Mark(0, 4, link)), changes.getLast().marks());
            assertNotNull(box(tree).id());
            assertEquals(box(tree).id(), host.focusRequests().getLast(), "the keyboard goes back to the field");
        }

        @Test
        @DisplayName("Ctrl+K inside a link selects it and offers its address, and Remove takes it off")
        void editsALink() {
            var link = new MarkKind.Link(URI.create("https://example.org"));
            var tree = mounted(new RichInput(MarkedText.of("see site now").mark(4, 8, link), changes::add));
            state(tree).editor().orElseThrow().caretTo(6, false);
            primary(tree, Key.K);
            var editor = last(LinkEditor.class);
            assertEquals("https://example.org", editor.address());
            assertTrue(editor.existing());
            editor.onRemove().run();
            render(tree);
            assertTrue(changes.getLast().isPlain());
        }

        @Test
        @DisplayName("Ctrl+K with nothing selected and no link is left alone")
        void nothingToLink() {
            var tree = mounted(composer());
            type(tree, "words");
            assertFalse(primary(tree, Key.K).isConsumed());
        }

        @Test
        @DisplayName("an address without a scheme is https, and one that is not an address is refused")
        void addresses() {
            assertEquals(URI.create("https://goldberry.dev/a"), RichInputState.addressOf("goldberry.dev/a"));
            assertEquals(URI.create("mailto:ann@example.org"), RichInputState.addressOf("mailto:ann@example.org"));
            assertNull(RichInputState.addressOf("two words"));
        }
    }

    @Nested
    @DisplayName("sending and the menu")
    class Sending {

        @Test
        @DisplayName("with somewhere to send, Enter sends and Shift+Enter breaks the line")
        void enterSends() {
            var tree = mounted(composer().onSubmit(sent::add));
            type(tree, "hi");
            assertTrue(key(tree, Key.ENTER, Modifiers.NONE).isConsumed());
            assertEquals(List.of(MarkedText.of("hi")), sent);
            key(tree, Key.ENTER, Modifiers.of(Mod.SHIFT));
            assertEquals("hi\n", state(tree).value().text());
            assertEquals(1, sent.size());
        }

        @Test
        @DisplayName("without, Enter breaks the line")
        void enterBreaks() {
            var tree = mounted(composer());
            type(tree, "hi");
            key(tree, Key.ENTER, Modifiers.NONE);
            assertEquals("hi\n", state(tree).value().text());
        }

        @Test
        @DisplayName("the text menu has a Format submenu, bold ticked over a bold selection")
        void formatSubmenu() {
            var tree = mounted(composer());
            type(tree, "Ship it");
            selectAll(tree);
            primary(tree, Key.B);
            var rows = state(tree).formatRows();
            var format = assertInstanceOf(Item.class, rows.getFirst());
            assertEquals(8, format.submenu().size());
            var bold = assertInstanceOf(Item.class, format.submenu().getFirst());
            assertEquals(Boolean.TRUE, bold.checked());
            var before = host.opened.size();
            box(tree).openContextMenu(FIELD);
            assertEquals(before + 1, host.opened.size(), "the menu was asked for");
        }

        @Test
        @DisplayName("a read-only field's menu has no Format submenu")
        void readOnlyMenu() {
            var tree = mounted(new RichInput(MarkedText.of("fixed"), changes::add).readOnly(true));
            assertTrue(state(tree).formatRows().isEmpty());
        }
    }
}
