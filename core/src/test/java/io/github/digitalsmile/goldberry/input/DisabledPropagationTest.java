package io.github.digitalsmile.goldberry.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.css.Selector;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// "A disabled container disables its descendants for input and semantics"
/// (`docs/core-widgets.md`) — [ADR-0077].
///
/// Written against bare widgets in `:core` rather than against `radio-group`,
/// because the containers this exists for are `form` and `group-box` and neither
/// is built yet. The mechanism has to be right before its users arrive; that is
/// the same reason [FocusScopeTest] and [DragOriginTest] live here.
///
/// The split under test is **input propagates, paint does not**. A disabled
/// container already fades everything under it, because opacity multiplies down a
/// subtree — so a descendant that also matched `:disabled` would be faded twice,
/// which is the bug `radio-group` used to paper over with an `opacity: 1` undo.
class DisabledPropagationTest {

    private final List<String> log = new ArrayList<>();

    /// A container that can be disabled, standing in for `form`.
    private class Node implements Widget.Leaf, Styled, Handles {
        private final String name;
        private final boolean disabled;
        private final List<Widget> children;

        Node(String name, boolean disabled, Widget... children) {
            this.name = name;
            this.disabled = disabled;
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
        public boolean isDisabled() {
            return disabled;
        }

        /// Deliberately **true regardless of `disabled`**, so these tests cannot
        /// pass by the widget quietly opting out. A real control returns
        /// `!disabled` here; the point is that a descendant which knows nothing
        /// about its container is still unreachable.
        @Override
        public boolean isFocusable() {
            return true;
        }

        /// No `disabled` check, on purpose. Every control in the catalog has one,
        /// and if the router is doing its job none of them needs it — a control
        /// written without one must still be unavailable.
        @Override
        public void onPointer(PointerEvent event) {
            log.add(name + ":" + event.kind());
        }

        @Override
        public void onKey(KeyEvent event) {
            log.add(name + ":key");
        }
    }

    private PointerRouter router;
    private ElementTree tree;

    /// A disabled outer box with an available inner one inside it — the shape of
    /// a `button` inside a disabled `form`.
    @BeforeEach
    void buildTree() {
        router = new PointerRouter();
        tree = new ElementTree(new Node("form", true, new Node("button", false)));
        var outer = tree.root();
        var inner = outer.children().getFirst();
        router.focusRoot(outer);
        router.updateRegions(List.of(
                HitTest.Region.of(outer, 0, 0, 100, 100),
                HitTest.Region.of(inner, 20, 20, 40, 40)));
    }

    private io.github.digitalsmile.goldberry.widget.Element inner() {
        return tree.root().children().getFirst();
    }

    /// The log with the observation events filtered out.
    ///
    /// `ENTERED`, `EXITED` and `MOVED` are supposed to arrive — that is the whole
    /// point of the split — so asserting on an empty log would be asserting the
    /// opposite of the decision. This keeps the input half honest without
    /// pretending the other half does not happen.
    private List<String> input() {
        return log.stream()
                .filter(entry -> !entry.endsWith(":ENTERED")
                        && !entry.endsWith(":EXITED")
                        && !entry.endsWith(":MOVED"))
                .toList();
    }

    @Nested
    @DisplayName("input does not reach it")
    class Input {

        @Test
        @DisplayName("a press and its click never reach a descendant of a disabled container")
        void pressIsRefused() {
            router.pointerPressed(30, 30, PointerEvent.Button.PRIMARY, 1);
            router.pointerReleased(30, 30, PointerEvent.Button.PRIMARY, 1);

            assertEquals(List.of(), input(),
                    "the inner node has no disabled check of its own and must still be inert");
        }

        @Test
        @DisplayName("the wheel is refused too")
        void wheelIsRefused() {
            router.pointerWheel(30, 30, 0, 1);

            assertEquals(List.of(), input());
        }

        /// The keyboard needs no separate guard, and that is the design rather
        /// than an accident: focus is the only route a key event has, so a
        /// subtree that cannot be focused cannot be typed into either.
        @Test
        @DisplayName("nothing in a disabled subtree can be focused, so no key can reach it")
        void focusIsRefused() {
            router.moveFocus(1);

            assertNull(router.focused(), "a disabled subtree is out of the Tab order entirely");

            router.keyPressed(Key.SPACE, Modifiers.NONE, false);
            assertEquals(List.of(), input());
        }

        @Test
        @DisplayName("a press does not focus the nearest focusable ancestor either")
        void pressDoesNotFocus() {
            router.pointerPressed(30, 30, PointerEvent.Button.PRIMARY, 1);

            assertNull(router.focused());
        }
    }

    @Nested
    @DisplayName("observation still does")
    class Observation {

        /// ADR-0059's two cases, and they are why this is not simply "drop every
        /// event": a disabled control still hit-tests so a click cannot fall
        /// through to whatever is behind it, and a tooltip explaining *why* it is
        /// unavailable needs the enter and the exit.
        @Test
        @DisplayName("enter, exit and motion still arrive")
        void hoverStillArrives() {
            router.pointerMoved(30, 30);

            assertTrue(log.contains("button:ENTERED"), () -> "log was " + log);
            assertTrue(log.contains("button:MOVED"), () -> "log was " + log);

            log.clear();
            router.pointerMoved(90, 90);
            assertTrue(log.contains("button:EXITED"), () -> "log was " + log);
        }

        @Test
        @DisplayName("it still hit-tests, so a click cannot fall through it")
        void stillHitTests() {
            router.pointerMoved(30, 30);

            assertSame(inner(), router.hovered(),
                    "unavailable is not invisible: something behind it must not receive the press");
        }
    }

    @Nested
    @DisplayName("paint deliberately does not propagate")
    class Paint {

        /// The half that must **not** happen. `:disabled` stays on the node that
        /// declared it, because the container's own 45% already fades everything
        /// under it — the painter multiplies opacity down the subtree — and a
        /// descendant that matched too would land at 20%.
        @Test
        @DisplayName(":hover is refused inside a disabled container, and :disabled is not set")
        void pseudoClassesStayPut() {
            router.pointerMoved(30, 30);

            assertFalse(inner().hasState(Selector.PseudoClass.HOVER),
                    "a disabled thing does not light up under the pointer");
            assertFalse(inner().hasState(Selector.PseudoClass.DISABLED),
                    "the fade belongs to the container; propagating it would apply 45% twice");
        }

        @Test
        @DisplayName(":active is refused as well, on the whole chain")
        void activeIsRefused() {
            router.pointerPressed(30, 30, PointerEvent.Button.PRIMARY, 1);

            assertFalse(inner().hasState(Selector.PseudoClass.ACTIVE));
            assertFalse(tree.root().hasState(Selector.PseudoClass.ACTIVE));
        }
    }

    @Nested
    @DisplayName("an available container is unaffected")
    class Available {

        @Test
        @DisplayName("the same tree with nothing disabled works normally")
        void controlCase() {
            var open = new PointerRouter();
            var openTree = new ElementTree(new Node("form", false, new Node("button", false)));
            open.focusRoot(openTree.root());
            open.updateRegions(List.of(
                    HitTest.Region.of(openTree.root(), 0, 0, 100, 100),
                    HitTest.Region.of(openTree.root().children().getFirst(), 20, 20, 40, 40)));

            open.pointerPressed(30, 30, PointerEvent.Button.PRIMARY, 1);
            open.pointerReleased(30, 30, PointerEvent.Button.PRIMARY, 1);

            // Without this the tests above would pass against a router that
            // dropped every event from every tree.
            assertTrue(log.contains("button:PRESSED"), () -> "log was " + log);
            assertTrue(log.contains("button:CLICKED"), () -> "log was " + log);
        }
    }
}
