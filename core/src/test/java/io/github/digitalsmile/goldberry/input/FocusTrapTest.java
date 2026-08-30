package io.github.digitalsmile.goldberry.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Styled;

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
        tree = new ElementTree(new Box(
                "window", false, new Item("a"), new Item("b"), new Box("panel", modal, new Item("x"), new Item("y"))));
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

            assertSame(byId("x"), router.focused(), "a direct focus() escaped the trap");
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
            tree = new ElementTree(new Box("window", false, new Item("a"), new Item("b")));
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
            tree = new ElementTree(new Box(
                    "window",
                    false,
                    new Item("a"),
                    new Box("first", true, new Item("x")),
                    new Box("second", true, new Item("z"))));
            router = new PointerRouter();
            router.focusRoot(tree.root());

            assertEquals(List.of("z", "z"), tabbing(2));
        }
    }

    /// A screen that can put its modal up and take it down, which is the only
    /// shape in which "closing a dialog" can be tested: the trap's other cases
    /// are all about a tree that stands still, and this one is entirely about a
    /// tree that changes under the router ([ADR-0180]).
    static final class Screen implements Widget.Stateful {

        /// The live state, so a test can close the modal from outside. A `dialog`
        /// has a controller for this; a test widget has three lines.
        static ScreenState live;

        @Override
        public State<?> createState() {
            return new ScreenState();
        }
    }

    static final class ScreenState extends State<Screen> {

        private boolean showing;

        @Override
        protected void initState() {
            Screen.live = this;
        }

        @Override
        public Widget build(BuildContext context) {
            var kids = new ArrayList<Widget>(3);
            kids.add(new Item("a"));
            kids.add(new Item("b"));
            if (showing) {
                kids.add(new Box("panel", true, new Item("x"), new Item("y")));
            }
            return new Box("window", false, kids.toArray(Widget[]::new));
        }

        void open() {
            setState(() -> showing = true);
        }

        void close() {
            setState(() -> showing = false);
        }
    }

    @Nested
    @DisplayName("giving the keyboard back")
    class Restoring {

        private ElementTree screen;
        private PointerRouter router;

        @BeforeEach
        void setUp() {
            screen = new ElementTree(new Screen());
            router = new PointerRouter();
            router.focusRoot(screen.root());
        }

        private Element in(String id) {
            return find(screen.root(), id);
        }

        /// Opens the modal and lets the tree settle, as a frame would.
        private void openModal() {
            Screen.live.open();
            screen.flush();
        }

        private void closeModal() {
            Screen.live.close();
            screen.flush();
            // What a window does after it paints. Without it the router is still
            // holding last frame's answer, which is the whole point of the check.
            router.refocus();
        }

        private String focusedId() {
            return router.focused() == null ? "(none)" : router.focused().id();
        }

        /// The bug under the feature, and the one nobody could see: `unmount`
        /// tells the element tree and nothing else, so the router went on holding
        /// an element that had left it — receiving keys, keeping a dead subtree
        /// reachable.
        @Test
        @DisplayName("the router never holds an element that has left the tree")
        void neverHoldsADeadElement() {
            openModal();
            router.focus(in("x"), true);
            assertEquals("x", focusedId());

            closeModal();

            assertTrue(
                    router.focused() == null || router.focused().isMounted(),
                    "the router is holding " + focusedId() + ", which is not in the tree");
        }

        /// §7: each overlay "wraps a `focus-scope` and restores focus on close".
        @Test
        @DisplayName("focus goes back to what had it before the modal opened")
        void restoredOnClose() {
            router.focus(in("a"), true);

            openModal();
            // The trap takes it: the first thing inside the modal, not `a`.
            router.focus(in("b"), true);
            assertEquals("x", focusedId(), "the trap did not take the keyboard");

            closeModal();

            assertEquals("a", focusedId(), "the keyboard landed nowhere in particular");
        }

        /// §7.2 keeps `:focus` and `:focus-visible` distinct, so putting the
        /// keyboard back has to put the **ring** back with it: a dialog dismissed
        /// with `Escape` should leave things as the user last saw them.
        @Test
        @DisplayName("what comes back comes back in the state it left in")
        void restoresTheRing() {
            router.focus(in("a"), true);
            openModal();
            router.focus(in("b"), true);
            closeModal();

            assertTrue(
                    in("a").hasState(io.github.digitalsmile.goldberry.css.select.Selector.PseudoClass.FOCUS_VISIBLE),
                    "the ring did not come back with the keyboard");

            // And the other way round: focus taken from a pointer comes back
            // without one, or a ring appears that nobody asked for.
            router.focus(in("a"), false);
            openModal();
            router.focus(in("b"), true);
            closeModal();

            assertEquals("a", focusedId());
            assertFalse(
                    in("a").hasState(io.github.digitalsmile.goldberry.css.select.Selector.PseudoClass.FOCUS_VISIBLE),
                    "a ring appeared under a pointer that nobody moved");
        }

        /// Nothing had the keyboard before the modal went up — a dialog opened
        /// from a menu command, or on the first frame of a window. There is
        /// nothing to give back, and holding a dead element rather than admitting
        /// that is the bug above.
        @Test
        @DisplayName("nothing to go back to means letting go, not holding on")
        void nothingToRestore() {
            openModal();
            router.focus(in("x"), true);

            closeModal();

            assertEquals("(none)", focusedId());
        }

        /// One slot, and the outermost answer wins — which is the one the user
        /// will still be looking at when everything has closed. The inner modal
        /// closing must not spend it, or the keyboard never finds its way home.
        @Test
        @DisplayName("a nested modal closing keeps the answer for the outer one")
        void nestedKeepsTheAnswer() {
            router.focus(in("a"), true);
            openModal();
            router.focus(in("b"), true);
            assertEquals("x", focusedId());

            // The modal goes and comes straight back, which is what a second
            // dialog opened over the first looks like to a one-slot memory: the
            // keyboard must still find `a` at the end of it.
            closeModal();
            openModal();
            router.focus(in("b"), true);
            closeModal();

            assertEquals("a", focusedId());
        }

        /// A `select` inside a dialog, and the dialog's own action removing the
        /// row it was opened from: what the keyboard was going to go back to is
        /// gone too, and the router must notice rather than restore a corpse.
        @Test
        @DisplayName("a restore target that leaves the tree is dropped, not restored")
        void targetThatWentAway() {
            router.focus(in("a"), true);
            openModal();
            router.focus(in("b"), true);

            // Everything goes at once, which is a window closing.
            screen.unmount();
            router.refocus();

            assertTrue(router.focused() == null || router.focused().isMounted());
        }
    }
}
