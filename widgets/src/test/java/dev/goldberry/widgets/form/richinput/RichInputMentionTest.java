package dev.goldberry.widgets.form.richinput;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Theme;
import dev.goldberry.input.event.KeyEvent;
import dev.goldberry.input.event.PreeditEvent;
import dev.goldberry.input.event.TextEvent;
import dev.goldberry.input.hit.Extent;
import dev.goldberry.input.key.Key;
import dev.goldberry.input.key.Modifiers;
import dev.goldberry.input.key.PrimaryModifier;
import dev.goldberry.render.model.LogicalRect;
import dev.goldberry.text.mark.Mark;
import dev.goldberry.text.mark.MarkKind;
import dev.goldberry.text.mark.MarkedText;
import dev.goldberry.widget.Element;
import dev.goldberry.widget.ElementTree;
import dev.goldberry.widget.WidgetRenderer;
import dev.goldberry.widgets.Controls;
import dev.goldberry.widgets.TestHost;
import dev.goldberry.widgets.controls.TestFont;

/// Mentions in a `rich-input`: a query reported as it is typed, keys offered to
/// the application while it is open, and the atom a chosen suggestion becomes.
@DisplayName("rich-input mentions")
class RichInputMentionTest {

    private static final LogicalRect FIELD = LogicalRect.of(20, 200, 300, 40);

    private static final MarkKind.Mention ANN = new MarkKind.Mention("user:1");

    private final TestHost host = new TestHost();

    private final List<Optional<MentionPrompt>> reports = new ArrayList<>();

    private final List<MarkedText> changes = new ArrayList<>();

    private final List<MarkedText> sent = new ArrayList<>();

    /// What the application's key handler takes; nothing by default.
    private Predicate<KeyEvent> keys = _ -> false;

    private final List<Key> offered = new ArrayList<>();

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private RichInput composer() {
        return new RichInput(MarkedText.EMPTY, changes::add)
                .mentions(Mentions.on(reports::add, "@", "#").keys(event -> {
                    offered.add(event.key());
                    return keys.test(event);
                }));
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

    private static void render(ElementTree tree) {
        tree.flush();
        new WidgetRenderer(List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load()), TestFont.get()).render(tree);
    }

    private static RichInputBox box(ElementTree tree) {
        return (RichInputBox) find(tree.root()).widget();
    }

    private static Element find(Element from) {
        if (from.widget() instanceof RichInputBox) {
            return from;
        }
        for (var child : from.children()) {
            try {
                return find(child);
            } catch (AssertionError notHere) {
                // the next child
            }
        }
        throw new AssertionError("no rich-input box");
    }

    private static RichInputState state(ElementTree tree) {
        return (RichInputState) tree.root().state().orElseThrow();
    }

    private static void type(ElementTree tree, String text) {
        for (var i = 0; i < text.length(); i++) {
            box(tree).onText(new TextEvent(text.substring(i, i + 1), null));
            render(tree);
        }
    }

    private static KeyEvent key(ElementTree tree, Key key, Modifiers modifiers) {
        var event = new KeyEvent(KeyEvent.Kind.PRESSED, key, modifiers, false, null);
        box(tree).onKey(event);
        render(tree);
        return event;
    }

    private static KeyEvent key(ElementTree tree, Key key) {
        return key(tree, key, Modifiers.NONE);
    }

    private MentionPrompt open() {
        assertFalse(reports.isEmpty(), "nothing was reported");
        return reports.getLast().orElseThrow(() -> new AssertionError("the last report closed the query"));
    }

    @Nested
    @DisplayName("the query")
    class Query {

        @Test
        @DisplayName("opens at a word start, follows typing, and closes on a space")
        void followsTyping() {
            var tree = mounted(composer());
            type(tree, "Hi @");
            assertEquals("", open().query().query());
            type(tree, "an");
            assertEquals("an", open().query().query());
            assertEquals(3, reports.size(), "opened by @, then once per letter");
            type(tree, " ");
            assertEquals(Optional.empty(), reports.getLast());
            assertNull(state(tree).prompt());
        }

        @Test
        @DisplayName("does not open inside a word")
        void notInsideAWord() {
            var tree = mounted(composer());
            type(tree, "mail@example");
            assertTrue(reports.isEmpty());
        }

        @Test
        @DisplayName("is anchored on its own line inside the field, in the window")
        void anchored() {
            var tree = mounted(composer());
            type(tree, "Hi @an");
            var anchor = open().anchor();
            assertTrue(anchor.left() > FIELD.left(), "after the field's left edge: " + anchor);
            assertTrue(anchor.top() >= FIELD.top() && anchor.top() < FIELD.bottom(), "on the field's line: " + anchor);
        }

        @Test
        @DisplayName("each trigger opens its own")
        void otherTrigger() {
            var tree = mounted(composer());
            type(tree, "see #re");
            assertEquals("#", open().query().trigger());
        }

        @Test
        @DisplayName("ends when the field loses the focus, and is not opened in a read-only one")
        void focusAndReadOnly() {
            var tree = mounted(composer());
            type(tree, "@a");
            box(tree).onFocusChanged(false, false);
            render(tree);
            assertEquals(Optional.empty(), reports.getLast());

            reports.clear();
            var fixed = mounted(new RichInput(MarkedText.of("@a"), changes::add)
                    .mentions(Mentions.on(reports::add, "@"))
                    .readOnly(true));
            key(fixed, Key.END);
            assertTrue(reports.isEmpty());
        }

        @Test
        @DisplayName("is not touched by a composition until it commits")
        void composition() {
            var tree = mounted(composer());
            type(tree, "@");
            var before = reports.size();
            box(tree).onPreedit(new PreeditEvent("た", -1, -1, null));
            render(tree);
            assertEquals(before, reports.size(), "the input method's text is not the field's yet");
            box(tree).onText(new TextEvent("田", null));
            render(tree);
            assertEquals("田", open().query().query());
        }
    }

    @Nested
    @DisplayName("keys")
    class Keys {

        @Test
        @DisplayName("go to the application first while a query is open")
        void routed() {
            var tree = mounted(composer().onSubmit(sent::add));
            keys = event -> event.is(Key.DOWN) || event.is(Key.ENTER);
            type(tree, "@a");
            assertTrue(key(tree, Key.DOWN).isConsumed());
            assertTrue(key(tree, Key.ENTER).isConsumed());
            assertEquals(List.of(Key.DOWN, Key.ENTER), offered);
            assertTrue(sent.isEmpty(), "the application took Enter, so nothing was sent");
        }

        @Test
        @DisplayName("are the field's again when no query is open")
        void notRoutedWithoutQuery() {
            var tree = mounted(composer().onSubmit(sent::add));
            keys = _ -> true;
            type(tree, "hello");
            key(tree, Key.ENTER);
            assertTrue(offered.isEmpty());
            assertEquals(1, sent.size());
        }

        @Test
        @DisplayName("an Enter the application leaves is the field's")
        void leftEnter() {
            var tree = mounted(composer().onSubmit(sent::add));
            type(tree, "@a");
            key(tree, Key.ENTER);
            assertEquals(List.of(Key.ENTER), offered);
            assertEquals(1, sent.size());
        }

        @Test
        @DisplayName("Escape left to the field closes the query until the caret leaves the word")
        void escape() {
            var tree = mounted(composer());
            type(tree, "@a");
            assertTrue(key(tree, Key.ESCAPE).isConsumed());
            assertEquals(Optional.empty(), reports.getLast());
            reports.clear();
            type(tree, "n");
            assertTrue(reports.isEmpty(), "dismissed, so typing on in the word reopens nothing");
            type(tree, " @");
            assertEquals("", open().query().query(), "a new word opens a new query");
        }

        @Test
        @DisplayName("with a modifier are not offered")
        void modified() {
            var tree = mounted(composer());
            type(tree, "@a");
            key(tree, Key.DOWN, Modifiers.of(PrimaryModifier.current()));
            assertTrue(offered.isEmpty());
        }
    }

    @Nested
    @DisplayName("completing")
    class Completing {

        @Test
        @DisplayName("puts in an atom and a space, reports the change, and ends the query")
        void completes() {
            var tree = mounted(composer());
            type(tree, "Hi @an");
            assertTrue(open().complete("@Ann Lee", ANN));
            render(tree);
            var value = changes.getLast();
            assertEquals("Hi @Ann Lee ", value.text());
            assertEquals(List.of(new Mark(3, 11, ANN)), value.marks());
            assertEquals(Optional.empty(), reports.getLast());
        }

        @Test
        @DisplayName("from the application's key handler, so the keyboard alone mentions somebody")
        void fromKeys() {
            var tree = mounted(composer());
            keys = event -> event.is(Key.ENTER) && open().complete("@Ann Lee", ANN);
            type(tree, "@a");
            key(tree, Key.ENTER);
            assertEquals("@Ann Lee ", changes.getLast().text());
        }

        @Test
        @DisplayName("the atom is stepped over, deleted whole, and comes back with undo")
        void theAtom() {
            var tree = mounted(composer());
            type(tree, "@a");
            open().complete("@Ann Lee", ANN);
            render(tree);
            key(tree, Key.BACKSPACE);
            key(tree, Key.BACKSPACE);
            assertEquals("", changes.getLast().text(), "the space, then the whole atom");
            key(tree, Key.Z, Modifiers.of(PrimaryModifier.current()));
            assertEquals(List.of(new Mark(0, 8, ANN)), changes.getLast().marks());
            key(tree, Key.LEFT);
            key(tree, Key.LEFT);
            var editor = state(tree).editor().orElseThrow();
            assertEquals(0, editor.edit().caret(), "two steps back: the space, then the atom");
        }

        @Test
        @DisplayName("a prompt the field has moved on from changes nothing")
        void stale() {
            var tree = mounted(composer());
            type(tree, "@a");
            var old = open();
            type(tree, "n");
            assertFalse(old.isCurrent());
            assertFalse(old.complete("@Ann Lee", ANN));
            assertFalse(old.dismiss());
            assertTrue(open().isCurrent());
            assertEquals("@an", changes.getLast().text());
        }

        @Test
        @DisplayName("dismiss closes it as Escape does")
        void dismiss() {
            var tree = mounted(composer());
            type(tree, "@a");
            assertTrue(open().dismiss());
            assertEquals(Optional.empty(), reports.getLast());
            assertNull(state(tree).prompt());
        }
    }

    @Nested
    @DisplayName("set up")
    class SetUp {

        @Test
        @DisplayName("needs a trigger, and one that cannot be in a word")
        void triggers() {
            assertThrows(IllegalArgumentException.class, () -> Mentions.on(_ -> {}));
            assertThrows(IllegalArgumentException.class, () -> Mentions.on(_ -> {}, "a"));
        }

        @Test
        @DisplayName("taken away while a query is open tells the listener it ended")
        void removed() {
            var tree = mounted(composer());
            type(tree, "@a");
            tree.update(new RichInput(MarkedText.EMPTY, changes::add));
            render(tree);
            assertEquals(Optional.empty(), reports.getLast());
            assertNotNull(state(tree));
        }
    }
}
