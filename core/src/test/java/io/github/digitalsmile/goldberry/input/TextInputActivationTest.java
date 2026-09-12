package io.github.digitalsmile.goldberry.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// Turning the platform's text input on — ADR-0285.
///
/// **Why this exists.** Committed text is delivered to whatever has the focus,
/// and on a desktop the platform produces none until something says it is being
/// typed into. That call was `text-input`'s own, made from its state, so the
/// first thing to hold an `Editor` outside the catalogue — a canvas — got keys
/// and never a character. It is the router's now, asked of the focused widget on
/// every focus change, exactly as the cursor is asked of the widget under the
/// pointer.
class TextInputActivationTest {

    private final List<Boolean> told = new ArrayList<>();

    /// A focusable leaf that may or may not be typed into.
    private static class Item implements Widget.Leaf, Styled, Handles {

        private final String name;
        private final boolean wantsText;

        Item(String name, boolean wantsText) {
            this.name = name;
            this.wantsText = wantsText;
        }

        @Override
        public String cssType() {
            return name;
        }

        @Override
        public boolean isFocusable() {
            return true;
        }

        @Override
        public boolean wantsTextInput() {
            return wantsText;
        }
    }

    private PointerRouter router;
    private Element field;
    private Element board;

    @BeforeEach
    void buildTree() {
        var tree = new ElementTree(new Item("root", false) {
            @Override
            public List<Widget> children() {
                return List.of(new Item("field", true), new Item("board", false));
            }
        });
        router = new PointerRouter();
        router.focusRoot(tree.root());
        router.onTextInputChange(told::add);
        field = tree.root().children().get(0);
        board = tree.root().children().get(1);
        told.clear();
    }

    @Test
    @DisplayName("focusing something typed into turns it on")
    void turnsOnForAField() {
        router.focus(field, true);

        assertTrue(router.textInputActive());
        assertEquals(List.of(true), told);
    }

    @Test
    @DisplayName("focusing something that is not turns it off again")
    void turnsOffForEverythingElse() {
        router.focus(field, true);
        router.focus(board, true);

        assertFalse(router.textInputActive());
        assertEquals(List.of(true, false), told, "on, then off — and not on twice");
    }

    @Test
    @DisplayName("losing the focus entirely turns it off")
    void turnsOffWhenNothingHasTheFocus() {
        router.focus(field, true);
        router.focus(null, false);

        assertFalse(router.textInputActive());
        assertEquals(List.of(true, false), told);
    }

    @Test
    @DisplayName("moving between two fields does not toggle it")
    void staysOnBetweenFields() {
        var tree = new ElementTree(new Item("root", false) {
            @Override
            public List<Widget> children() {
                return List.of(new Item("one", true), new Item("two", true));
            }
        });
        router = new PointerRouter();
        router.focusRoot(tree.root());
        router.onTextInputChange(told::add);
        told.clear();

        router.focus(tree.root().children().get(0), true);
        router.focus(tree.root().children().get(1), true);

        // An input method torn down and rebuilt between two fields would drop a
        // half-typed composition on the floor.
        assertEquals(List.of(true), told);
    }

    @Test
    @DisplayName("a sink wired late is told the current state at once")
    void aLateSinkIsCaughtUp() {
        router.focus(field, true);

        var later = new ArrayList<Boolean>();
        router.onTextInputChange(later::add);

        // The same contract `onCursorChange` has: a window that attaches after
        // focus has already moved must not wait for the next change to find out.
        assertEquals(List.of(true), later);
    }

    @Test
    @DisplayName("a widget that says nothing is not typed into")
    void defaultsToOff() {
        router.focus(board, false);

        assertFalse(router.textInputActive());
        assertEquals(List.of(), told, "and nothing was said, because nothing changed");
    }

    @org.junit.jupiter.api.Nested
    @DisplayName("through the backend")
    class Plumbing {

        private io.github.digitalsmile.goldberry.render.backend.headless.HeadlessBackend backend;

        @BeforeEach
        void install() {
            io.github.digitalsmile.goldberry.RendererRequirement.enforce();
            backend = new io.github.digitalsmile.goldberry.render.backend.headless.HeadlessBackend(
                    new io.github.digitalsmile.goldberry.render.model.DisplayScale(1f));
            io.github.digitalsmile.goldberry.GoldberryTestAccess.install(backend);
        }

        @org.junit.jupiter.api.AfterEach
        void shutdown() {
            io.github.digitalsmile.goldberry.Goldberry.shutdown();
        }

        @Test
        @org.junit.jupiter.api.Timeout(10)
        @DisplayName("focusing a field turns the window's text input on, and leaving it turns it off")
        void routerReachesTheWindow() {
            // The whole wire, end to end: a widget says it is typed into, the
            // router asks it on a focus change, `Window` passes the answer to the
            // backend, and the backend calls the platform. Every link was there
            // except the middle two, which is why a canvas holding an `Editor`
            // received keys and never a character (ADR-0285).
            var window = io.github.digitalsmile.goldberry.Window.open(
                    io.github.digitalsmile.goldberry.render.window.WindowSpec.of(
                            "text input", io.github.digitalsmile.goldberry.render.model.LogicalSize.of(100f, 100f)));
            window.pointerRouter(router);
            var backendWindow = (io.github.digitalsmile.goldberry.render.backend.headless.HeadlessWindow)
                    backend.windows().getFirst();

            router.focus(field, true);
            assertTrue(backendWindow.isTextInputActive());

            router.focus(board, true);
            assertFalse(backendWindow.isTextInputActive());

            window.close();
        }
    }
}
