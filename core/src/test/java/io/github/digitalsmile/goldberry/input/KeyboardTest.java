package io.github.digitalsmile.goldberry.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.github.digitalsmile.goldberry.css.select.Selector.PseudoClass;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.event.TextEvent;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.input.key.Key;
import io.github.digitalsmile.goldberry.input.key.Modifiers;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Styled;

class KeyboardTest {

    private final List<String> log = new ArrayList<>();

    private class Field implements Widget.Leaf, Styled, Handles {
        private final String name;
        private final boolean focusable;
        private final List<Widget> children;
        private Key consumeKey;

        /// What this node swallows on the way *down*, or null. Set per test,
        /// like [#consumeKey], because a capture phase only means anything if
        /// something can stop the event there.
        private String consumeText;

        Field(String name, boolean focusable, Widget... children) {
            this.name = name;
            this.focusable = focusable;
            this.children = List.of(children);
        }

        @Override
        public List<Widget> children() {
            return children;
        }

        @Override
        public String cssType() {
            return name;
        }

        @Override
        public boolean isFocusable() {
            return focusable;
        }

        @Override
        public void onKeyCapture(KeyEvent event) {
            log.add("capture:" + name + ":" + event.key());
        }

        @Override
        public void onKey(KeyEvent event) {
            log.add("key:" + name + ":" + event.kind() + ":" + event.key());
            if (event.key() == consumeKey) {
                event.consume();
            }
        }

        @Override
        public void onTextCapture(TextEvent event) {
            log.add("textCapture:" + name + ":" + event.text());
            if (event.text().equals(consumeText)) {
                event.consume();
            }
        }

        @Override
        public void onText(TextEvent event) {
            log.add("text:" + name + ":" + event.text());
        }
    }

    private PointerRouter router;
    private Element root;
    private Element first;
    private Element second;

    @BeforeEach
    void buildTree() {
        router = new PointerRouter();
        var firstField = new Field("first", true);
        var secondField = new Field("second", true);
        var container = new Field("container", false, firstField, secondField);
        var tree = new ElementTree(container);
        root = tree.root();
        first = root.children().get(0);
        second = root.children().get(1);
        router.focusRoot(root);
    }

    @Nested
    @DisplayName("dispatch")
    class Dispatch {

        @Test
        @DisplayName("keys go to the focused node and bubble to its ancestors")
        void goesToFocus() {
            router.focus(first, true);
            log.clear();

            router.keyPressed(Key.ESCAPE, Modifiers.NONE, false);

            assertEquals(
                    List.of(
                            "capture:container:ESCAPE",
                            "capture:first:ESCAPE",
                            "key:first:PRESSED:ESCAPE",
                            "key:container:PRESSED:ESCAPE"),
                    log);
        }

        @Test
        @DisplayName("with nothing focused, a key goes nowhere")
        void noFocusNoDispatch() {
            log.clear();
            router.keyPressed(Key.ESCAPE, Modifiers.NONE, false);

            assertTrue(log.isEmpty());
        }

        @Test
        @DisplayName("consuming stops the ancestors")
        void consume() {
            ((Field) first.widget()).consumeKey = Key.ESCAPE;
            router.focus(first, true);
            log.clear();

            assertTrue(router.keyPressed(Key.ESCAPE, Modifiers.NONE, false));
            assertFalse(log.contains("key:container:PRESSED:ESCAPE"));
        }

        @Test
        @DisplayName("a release is dispatched too")
        void release() {
            router.focus(first, true);
            log.clear();
            router.keyReleased(Key.ESCAPE, Modifiers.NONE);

            assertTrue(log.contains("key:first:RELEASED:ESCAPE"));
        }
    }

    @Nested
    @DisplayName("text")
    class Text {

        @Test
        @DisplayName("committed text reaches the focused node")
        void textToFocus() {
            router.focus(first, true);
            log.clear();

            router.textInput("é");

            // The platform already applied the layout, the dead key and any IME
            // conversion; the widget just appends it (§7.1).
            assertTrue(log.contains("text:first:é"));
        }

        /// [ADR-0246]: a `select` with its list open has an `option` focused
        /// inside a popup, so §3's typeahead had nothing to intercept the text
        /// in. A capture phase is the same shape `dispatchKey` has had all along.
        @Test
        @DisplayName("capture runs root-first, before the focused node is told")
        void captureIsRootFirst() {
            router.focus(first, true);
            log.clear();

            router.textInput("é");

            var capture = log.indexOf("textCapture:container:é");
            var deepest = log.indexOf("textCapture:first:é");
            var bubble = log.indexOf("text:first:é");
            assertTrue(capture >= 0 && deepest >= 0 && bubble >= 0, () -> "log was " + log);
            assertTrue(capture < deepest, () -> "capture should be root-first; log was " + log);
            assertTrue(deepest < bubble, () -> "capture should precede the bubble; log was " + log);
        }

        /// Which is the half that makes the phase worth having: a container that
        /// reads the text may keep it, and the row inside it never sees it.
        @Test
        @DisplayName("and a container that consumes on the way down stops it reaching the focus")
        void captureCanSwallow() {
            ((Field) root.widget()).consumeText = "n";
            router.focus(first, true);
            log.clear();

            router.textInput("n");

            assertTrue(log.contains("textCapture:container:n"), () -> "log was " + log);
            assertFalse(log.contains("text:first:n"), () -> "the focused node was told anyway; log was " + log);
        }

        @Test
        @DisplayName("text with nothing focused is dropped, not broadcast")
        void textNeedsFocus() {
            log.clear();
            router.textInput("x");

            // Typing into whatever happens to be under the pointer is a bug.
            assertTrue(log.isEmpty());
        }

        @Test
        @DisplayName("a compose sequence commits several characters at once")
        void multiCharacterCommit() {
            router.focus(first, true);
            log.clear();
            router.textInput("ありがとう");

            assertTrue(log.contains("text:first:ありがとう"));
        }
    }

    @Nested
    @DisplayName("Tab traversal")
    class Traversal {

        @Test
        @DisplayName("Tab moves forward in document order and wraps")
        void forward() {
            router.keyPressed(Key.TAB, Modifiers.NONE, false);
            assertSame(first, router.focused());

            router.keyPressed(Key.TAB, Modifiers.NONE, false);
            assertSame(second, router.focused());

            router.keyPressed(Key.TAB, Modifiers.NONE, false);
            assertSame(first, router.focused(), "traversal wraps");
        }

        @Test
        @DisplayName("Shift+Tab moves backward")
        void backward() {
            router.focus(second, true);
            router.keyPressed(Key.TAB, new Modifiers(true, false, false, false), false);

            assertSame(first, router.focused());
        }

        @Test
        @DisplayName("Tab focus is keyboard focus, so the ring shows")
        void tabIsKeyboardFocus() {
            router.keyPressed(Key.TAB, Modifiers.NONE, false);

            // §7.2: the focus ring renders only for keyboard focus. Tab is the
            // canonical way to get it.
            assertTrue(first.hasState(PseudoClass.FOCUS));
            assertTrue(first.hasState(PseudoClass.FOCUS_VISIBLE));
        }

        @Test
        @DisplayName("Ctrl+Tab is not traversal")
        void modifiedTabIsNotTraversal() {
            // It belongs to the application -- switching tabs in a tab strip,
            // usually -- so traversal must not eat it.
            assertFalse(router.keyPressed(Key.TAB, new Modifiers(false, true, false, false), false));
            assertNull(router.focused());
        }

        @Test
        @DisplayName("a widget that consumes Tab keeps focus")
        void consumedTabDoesNotTraverse() {
            ((Field) first.widget()).consumeKey = Key.TAB;
            router.focus(first, true);

            assertTrue(router.keyPressed(Key.TAB, Modifiers.NONE, false));
            // A text area that inserts a tab character has to be able to say so.
            assertSame(first, router.focused());
        }

        @Test
        @DisplayName("with no focus root, traversal does nothing but keys still work")
        void noFocusRoot() {
            var lone = new PointerRouter();
            assertFalse(lone.moveFocus(1));
        }
    }

    @Nested
    @DisplayName("SDL translation")
    class Translation {

        /// SDL's keycode and the key it names, one row each, so a translation
        /// that stopped answering for a keycode says which.
        ///
        /// Letters and digits are named because Ctrl+S produces no text event on
        /// any platform: the letter has to come from the key event or a shortcut
        /// could not be expressed at all. An uppercase keycode folds to the same
        /// key as its lowercase — SDL reports the unmodified keycode but
        /// documents platforms that only ever give modified ones, where Shift+S
        /// arrives as `S`. Anything unnamed is `UNKNOWN` rather than null: a
        /// bracket types a character and nothing binds it, so what was typed
        /// arrives as text instead.
        @ParameterizedTest(name = "{0} is {1}")
        @CsvSource({
            "0x00000009, TAB",
            "0x0000001b, ESCAPE",
            "0x40000050, LEFT",
            "0x4000003a, F1",
            "0x00000073, S", // 's'
            "0x00000053, S", // 'S', folded onto the same key
            "0x00000037, DIGIT_7", // '7'
            "0x0000002c, COMMA", // ','
            "0x0000005b, UNKNOWN", // '[' -- typed, not bound
            "0x40000039, UNKNOWN", // one below F1: F13 is not named
        })
        @DisplayName("an SDL keycode names the key it is, or UNKNOWN")
        void keycodes(int keycode, Key expected) {
            assertEquals(expected, Key.fromSdl(keycode));
        }

        @Test
        @DisplayName("modifiers fold left and right into one flag each")
        void modifiers() {
            assertTrue(Modifiers.fromSdl(0x0001).shift());
            assertTrue(Modifiers.fromSdl(0x0002).shift(), "right shift counts too");
            assertTrue(Modifiers.fromSdl(0x0040).control());
            assertTrue(Modifiers.fromSdl(0x0100).alt());
            assertTrue(Modifiers.fromSdl(0x0400).meta());
            assertTrue(Modifiers.fromSdl(0).none());
        }

        @Test
        @DisplayName("onlyControl distinguishes Ctrl+S from Ctrl+Shift+S")
        void onlyControl() {
            assertTrue(Modifiers.fromSdl(0x0040).onlyControl());
            assertFalse(Modifiers.fromSdl(0x0040 | 0x0001).onlyControl());
        }
    }
}
