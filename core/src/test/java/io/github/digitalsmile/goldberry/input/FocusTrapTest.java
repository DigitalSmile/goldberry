package io.github.digitalsmile.goldberry.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Styled;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// `docs/core-widgets.md` §7's focus trap, and
/// [io.github.digitalsmile.goldberry.Host#focus].
///
/// In `:core` and built from bare widgets rather than from `dialog`, which lives
/// in `:widgets` — [FocusScopeTest]'s reason: the mechanism is the router's, and
/// it has to hold for whatever declares itself modal next. A dialog is the first,
/// a `wizard` step and a `sheet` are plausible seconds, and none of them will
/// look like the others.
///
/// The invariant under test is one sentence: **while something modal is mounted,
/// the focused node is inside it.** Every case here is that sentence approached
/// from a different direction ([ADR-0176]).
class FocusTrapTest {

    /// A focusable leaf.
    private static class Item implements Widget.Leaf, Styled, Handles {
        private final String name;

        Item(String name) {
            this.name = name;
        }

        @Override
        public String cssType() {
            return name;
        }

        @Override
        public String id() {
            return name;
        }

        @Override
        public boolean isFocusable() {
            return true;
        }
    }

    /// Scenery that holds things — a row, a panel, a dialog's body.
    private static class Box implements Widget.Leaf, Styled, Handles {
        private final String name;
        private final List<Widget> children;
        private final boolean modal;

        Box(String name, boolean modal, Widget... children) {
            this.name = name;
            this.modal = modal;
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
        public String id() {
            return name;
        }

        @Override
        public boolean isModal() {
            return modal;
        }
    }

    private PointerRouter router;
    private ElementTree tree;

    /// `window( a b modal?( x y ) )` — two ordinary stops, and a container that
    /// may or may not be modal with two more inside it.
    private void build(boolean modal) {
        tree = new ElementTree(new Box("window", false,
                new Item("a"), new Item("b"),
                new Box("panel", modal, new Item("x"), new Item("y"))));
        router = new PointerRouter();
        router.focusRoot(tree.root());
    }

    private Element byId(String id) {
        return find(tree.root(), id);
    }

    private static Element find(Element from, String id) {
        if (id.equals(from.id())) {
            return from;
        }
        for (var child : from.children()) {
            var found = find(child, id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /// The ids Tab visits, starting from nothing, for one full cycle of `steps`.
    private List<String> tabbing(int steps) {
        var visited = new ArrayList<String>(steps);
        for (var i = 0; i < steps; i++) {
            router.moveFocus(1);
            visited.add(router.focused() == null ? "(none)" : router.focused().id());
        }
        return visited;
    }

    @Nested
    @DisplayName("with nothing modal")
    class Open {

        @Test
        @DisplayName("Tab walks the whole window, as it always has")
        void tabWalksEverything() {
            build(false);

            assertEquals(List.of("a", "b", "x", "y", "a"), tabbing(5));
        }

        @Test
        @DisplayName("anything focusable can be focused by id")
        void focusByIdReachesAnything() {
            build(false);

            assertTrue(router.focusById("y", false));
            assertSame(byId("y"), router.focused());
        }
    }

    @Nested
    @DisplayName("with a modal open")
    class Trapped {

        @Test
        @DisplayName("Tab cycles inside it and never leaves")
        void tabStaysInside() {
            build(true);

            // Four steps round a two-stop trap: the point is that it wraps at the
            // end of the panel rather than continuing into the window behind it.
            assertEquals(List.of("x", "y", "x", "y"), tabbing(4));
        }

        @Test
        @DisplayName("Shift+Tab wraps backwards inside it too")
        void shiftTabStaysInside() {
            build(true);
            router.focusById("x", true);

            router.moveFocus(-1);

            assertSame(byId("y"), router.focused(), "back from the first went outside");
        }

        /// The trap is enforced at the one place focus is *set*, rather than at
        /// each of the routes that lead there — so a route nobody thought of is
        /// covered by construction. This is that assertion.
        @Test
        @DisplayName("focusing something outside lands on the first thing inside")
        void focusingOutsideIsRedirected() {
            build(true);

            router.focus(byId("a"), true);

            assertSame(byId("x"), router.focused(),
                    "a direct focus() escaped the trap");
        }

        @Test
        @DisplayName("Host.focus is refused for a node outside, and obeyed inside")
        void focusByIdRespectsTheTrap() {
            build(true);

            assertFalse(router.focusById("a", false), "a stray call stepped around the trap");
            assertTrue(router.focusById("y", false));
            assertSame(byId("y"), router.focused());
        }

        /// What a dialog relies on to open with the keyboard in it: it asks for
        /// itself by id, and the panel is not focusable.
        @Test
        @DisplayName("focusing a container by id lands on the first control in it")
        void focusByIdResolvesToTheFirstInside() {
            build(true);

            assertTrue(router.focusById("panel", false));
            assertSame(byId("x"), router.focused());
        }
    }

    @Nested
    @DisplayName("when it closes")
    class Released {

        /// Nothing is registered when a modal opens, so nothing has to be
        /// unregistered — the answer is recomputed from the tree. A dialog
        /// removed by any route at all gives the keyboard back.
        @Test
        @DisplayName("the window gets its keyboard back with no bookkeeping")
        void unmountingReleases() {
            build(true);
            router.focusById("x", true);

            // The modal goes away the way a dialog does: its overlay is removed,
            // so the element is simply not in the tree any more.
            tree = new ElementTree(new Box("window", false,
                    new Item("a"), new Item("b")));
            router.focusRoot(tree.root());

            assertEquals(List.of("a", "b", "a"), tabbing(3));
        }
    }

    @Nested
    @DisplayName("two of them")
    class Stacked {

        /// A dialog opened from a dialog is two filling overlays on one window,
        /// and the later one is drawn on top. Walking the tree forwards would
        /// hand the keyboard to the one underneath.
        @Test
        @DisplayName("the topmost traps, not the first one found")
        void theTopmostWins() {
            tree = new ElementTree(new Box("window", false,
                    new Item("a"),
                    new Box("first", true, new Item("x")),
                    new Box("second", true, new Item("z"))));
            router = new PointerRouter();
            router.focusRoot(tree.root());

            assertEquals(List.of("z", "z"), tabbing(2));
        }
    }
}
