package io.github.digitalsmile.goldberry.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/// The accelerator map §7.2 asks for, and the text it is written in.
class ShortcutTest {

    @Nested
    @DisplayName("parsing")
    class Parsing {

        @ParameterizedTest
        @CsvSource({
            "Ctrl+S,        S,       false, true,  false, false",
            "ctrl+s,        S,       false, true,  false, false",
            "Ctrl+Shift+Z,  Z,       true,  true,  false, false",
            "Alt+F4,        F4,      false, false, true,  false",
            "Cmd+W,         W,       false, false, false, true",
            "Super+L,       L,       false, false, false, true",
            "F5,            F5,      false, false, false, false",
            "Ctrl+1,        DIGIT_1, false, true,  false, false",
            "Escape,        ESCAPE,  false, false, false, false",
            "Esc,           ESCAPE,  false, false, false, false",
            "Ctrl+Page_Up,  PAGE_UP, false, true,  false, false",
        })
        @DisplayName("a shortcut reads the way a menu prints it")
        void parses(String text, Key key, boolean shift, boolean control, boolean alt, boolean meta) {
            var shortcut = Shortcut.of(text);
            assertEquals(key, shortcut.key());
            assertEquals(new Modifiers(shift, control, alt, meta), shortcut.modifiers());
        }

        @ParameterizedTest
        @ValueSource(strings = {"Ctrl", "Ctrl+", "", "Ctrl+Nonesuch", "Ctrl+S+T", "Ctrl++S"})
        @DisplayName("nonsense is refused at construction, not at the keystroke")
        void rejects(String text) {
            // A shortcut that silently never fires is the worst outcome: the bug
            // report is "the menu item does nothing" and there is no error
            // anywhere.
            assertThrows(IllegalArgumentException.class, () -> Shortcut.of(text));
        }

        @Test
        @DisplayName("two shortcuts parsed from the same text are the same value")
        void valueSemantics() {
            assertEquals(Shortcut.of("Ctrl+S"), Shortcut.of("ctrl+S"));
            assertEquals(Shortcut.of("Ctrl+S").hashCode(), Shortcut.of("Ctrl+S").hashCode());
        }

        @Test
        @DisplayName("it prints back the way it was written")
        void printsBack() {
            assertEquals("Ctrl+S", Shortcut.of("Ctrl+S").toString());
            assertEquals("Ctrl+Shift+Z", Shortcut.of("Shift+Ctrl+Z").toString());
            assertEquals("F5", Shortcut.of("F5").toString());
            assertEquals("Ctrl+1", Shortcut.of("Ctrl+1").toString());
            assertEquals("Page Up", Shortcut.of("Page_Up").toString());
        }

        @Test
        @DisplayName("modifiers must match exactly")
        void exactModifiers() {
            var save = Shortcut.of("Ctrl+S");

            assertTrue(save.matches(Key.S, new Modifiers(false, true, false, false)));
            // Ctrl+Shift+S is a different shortcut, and applications bind both.
            assertFalse(save.matches(Key.S, new Modifiers(true, true, false, false)));
            assertFalse(save.matches(Key.S, Modifiers.NONE));
        }

        @Test
        @DisplayName("Cmd is not quietly turned into Ctrl")
        void cmdIsNotCtrl() {
            // A toolkit that remapped them would make Ctrl+C mean two different
            // things depending on where it ran.
            assertFalse(Shortcut.of("Cmd+C").equals(Shortcut.of("Ctrl+C")));
        }
    }

    @Nested
    @DisplayName("the accelerator map")
    class Accelerators {

        private final List<String> fired = new ArrayList<>();
        private PointerRouter router;
        private Element focusable;
        private Node widget;

        @BeforeEach
        void buildTree() {
            router = new PointerRouter();
            widget = new Node();
            var tree = new ElementTree(widget);
            focusable = tree.root();
            router.focusRoot(focusable);
            router.focus(focusable, true);
        }

        @Test
        @DisplayName("a bound shortcut fires and reports the key as handled")
        void fires() {
            router.shortcut("Ctrl+S", () -> fired.add("save"));

            var handled = router.keyPressed(Key.S, new Modifiers(false, true, false, false), false);

            assertTrue(handled);
            assertEquals(List.of("save"), fired);
        }

        @Test
        @DisplayName("an unbound combination is left alone")
        void unbound() {
            router.shortcut("Ctrl+S", () -> fired.add("save"));

            assertFalse(router.keyPressed(Key.S, new Modifiers(true, true, false, false), false));
            assertTrue(fired.isEmpty());
        }

        @Test
        @DisplayName("the focused widget gets first refusal")
        void focusWinsWhenItConsumes() {
            // A text field keeping Ctrl+A for "select all" is the case this is
            // for: the window's own binding must not steal it.
            widget.consume = true;
            router.shortcut("Ctrl+A", () -> fired.add("window"));

            var handled = router.keyPressed(Key.A, new Modifiers(false, true, false, false), false);

            assertTrue(handled);
            assertTrue(fired.isEmpty(), () -> "fired was " + fired);
        }

        @Test
        @DisplayName("a shortcut still fires when nothing has focus")
        void firesWithoutFocus() {
            router.focus(null, false);
            router.shortcut("F5", () -> fired.add("refresh"));

            assertTrue(router.keyPressed(Key.F5, Modifiers.NONE, false));
            assertEquals(List.of("refresh"), fired);
        }

        @Test
        @DisplayName("a held shortcut repeats, because that is what key repeat is for")
        void repeats() {
            router.shortcut("Ctrl+Z", () -> fired.add("undo"));
            var ctrl = new Modifiers(false, true, false, false);

            router.keyPressed(Key.Z, ctrl, false);
            router.keyPressed(Key.Z, ctrl, true);

            assertEquals(List.of("undo", "undo"), fired);
        }

        @Test
        @DisplayName("Tab still traverses when no shortcut claims it")
        void tabSurvives() {
            router.shortcut("Ctrl+Tab", () -> fired.add("next tab"));

            assertTrue(router.keyPressed(Key.TAB, Modifiers.NONE, false));
            assertTrue(fired.isEmpty(), "plain Tab is traversal, not the bound Ctrl+Tab");

            assertTrue(router.keyPressed(Key.TAB, new Modifiers(false, true, false, false), false));
            assertEquals(List.of("next tab"), fired);
        }

        @Test
        @DisplayName("rebinding replaces, and removing unbinds")
        void rebind() {
            router.shortcut("Ctrl+S", () -> fired.add("first"));
            router.shortcut("Ctrl+S", () -> fired.add("second"));
            var ctrl = new Modifiers(false, true, false, false);

            router.keyPressed(Key.S, ctrl, false);
            assertEquals(List.of("second"), fired);

            router.removeShortcut(Shortcut.of("Ctrl+S"));
            assertFalse(router.keyPressed(Key.S, ctrl, false));
            assertEquals(List.of("second"), fired);
        }

        @Test
        @DisplayName("the bound set is listed in the order it was bound")
        void listed() {
            // What a keyboard-shortcut sheet prints.
            router.shortcut("Ctrl+S", () -> { });
            router.shortcut("F5", () -> { });

            assertEquals(List.of(Shortcut.of("Ctrl+S"), Shortcut.of("F5")),
                    List.copyOf(router.shortcuts().keySet()));
        }
    }

    private static class Node implements Widget.Leaf, Styled, Handles {
        private boolean consume;

        @Override
        public String cssType() {
            return "node";
        }

        @Override
        public boolean isFocusable() {
            return true;
        }

        @Override
        public void onKey(KeyEvent event) {
            if (consume) {
                event.consume();
            }
        }
    }
}
