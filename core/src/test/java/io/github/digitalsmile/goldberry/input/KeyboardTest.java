package io.github.digitalsmile.goldberry.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.css.Selector.PseudoClass;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class KeyboardTest {

    private final List<String> log = new ArrayList<>();

    private class Field implements Widget.Leaf, Styled, Handles {
        private final String name;
        private final boolean focusable;
        private final List<Widget> children;
        private Key consumeKey;

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

            assertEquals(List.of(
                    "capture:container:ESCAPE",
                    "capture:first:ESCAPE",
                    "key:first:PRESSED:ESCAPE",
                    "key:container:PRESSED:ESCAPE"), log);
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
        @DisplayName("Tab skips nodes that are not focusable")
        void skipsUnfocusable() {
            // `container` is in the tree and is not focusable; traversal must
            // never land on it, or Tab would stop on scenery.
            router.keyPressed(Key.TAB, Modifiers.NONE, false);
            router.keyPressed(Key.TAB, Modifiers.NONE, false);
            router.keyPressed(Key.TAB, Modifiers.NONE, false);

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

        @Test
        @DisplayName("named keys map from SDL keycodes")
        void keycodes() {
            assertEquals(Key.TAB, Key.fromSdl(0x00000009));
            assertEquals(Key.ESCAPE, Key.fromSdl(0x0000001b));
            assertEquals(Key.LEFT, Key.fromSdl(0x40000050));
            assertEquals(Key.F1, Key.fromSdl(0x4000003a));
        }

        @Test
        @DisplayName("an unnamed key is UNKNOWN rather than null")
        void unknownKey() {
            // A bracket types a character and nothing binds it, so it stays
            // unnamed: what was typed arrives as text.
            assertEquals(Key.UNKNOWN, Key.fromSdl('['));
            assertEquals(Key.UNKNOWN, Key.fromSdl(0x4000003a - 1), "F13 is not named");
        }

        @Test
        @DisplayName("letters and digits are named, because accelerators need them")
        void letters() {
            // Ctrl+S produces no text event on any platform, so the letter has to
            // come from the key event or a shortcut could not be expressed.
            assertEquals(Key.S, Key.fromSdl('s'));
            assertEquals(Key.DIGIT_7, Key.fromSdl('7'));
            assertEquals(Key.COMMA, Key.fromSdl(','));
        }

        @Test
        @DisplayName("an uppercase keycode folds to the same key as its lowercase")
        void caseFolding() {
            // SDL reports the unmodified keycode, but documents platforms that
            // only ever give modified ones -- where Shift+S arrives as 'S'.
            assertEquals(Key.S, Key.fromSdl('S'));
            assertEquals(Key.fromSdl('s'), Key.fromSdl('S'));
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
