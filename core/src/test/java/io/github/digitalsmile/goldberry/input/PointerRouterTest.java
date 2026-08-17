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

/// Dispatch, pseudo-classes and focus, without a window.
///
/// The hit-test snapshot is supplied directly rather than captured from a paint,
/// so these are about the routing rules and not about Yoga. [HitTestTest] covers
/// the other half.
class PointerRouterTest {

    private final List<String> log = new ArrayList<>();

    /// A node that records what it is told and can be asked to consume.
    private class Node implements Widget.Leaf, Styled, Handles {
        private final String name;
        private final List<Widget> children;
        private final boolean focusable;
        private PointerEvent.Kind consumeOn;
        private PointerEvent.Kind consumeOnCapture;
        private boolean disabled;

        Node(String name, boolean focusable, Widget... children) {
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
        public boolean isDisabled() {
            return disabled;
        }

        @Override
        public void onPointerCapture(PointerEvent event) {
            log.add("capture:" + name + ":" + event.kind());
            if (event.kind() == consumeOnCapture) {
                event.consume();
            }
        }

        @Override
        public void onPointer(PointerEvent event) {
            log.add("bubble:" + name + ":" + event.kind());
            if (event.kind() == consumeOn) {
                event.consume();
            }
        }
    }

    private PointerRouter router;
    private ElementTree tree;
    private Element outer;
    private Element inner;
    private Node outerWidget;
    private Node innerWidget;

    @BeforeEach
    void buildTree() {
        router = new PointerRouter();
        innerWidget = new Node("inner", true);
        outerWidget = new Node("outer", false, innerWidget);
        tree = new ElementTree(outerWidget);
        outer = tree.root();
        inner = outer.children().getFirst();

        // outer covers 0,0 100x100; inner sits inside it at 20,20 40x40.
        // Parent first, as a paint would record them.
        router.updateRegions(List.of(
                HitTest.Region.of(outer, 0, 0, 100, 100),
                HitTest.Region.of(inner, 20, 20, 40, 40)));
    }

    @Nested
    @DisplayName("hover")
    class Hover {

        @Test
        @DisplayName(":hover applies to the whole ancestor chain")
        void hoverChain() {
            router.pointerMoved(30, 30);

            // ".card:hover .title" has to work, so hover is not just the
            // deepest node.
            assertTrue(inner.hasState(PseudoClass.HOVER));
            assertTrue(outer.hasState(PseudoClass.HOVER));
        }

        @Test
        @DisplayName("moving out of a child keeps the parent hovered")
        void partialChainChange() {
            router.pointerMoved(30, 30);
            log.clear();
            router.pointerMoved(80, 80);

            assertFalse(inner.hasState(PseudoClass.HOVER));
            assertTrue(outer.hasState(PseudoClass.HOVER), "the parent was never left");
            // Only the part of the chain that changed gets an enter/exit. The
            // parent still gets the MOVED -- the pointer is over it -- which is
            // why this filters rather than comparing the whole log.
            assertEquals(
                    List.of("bubble:inner:EXITED"),
                    log.stream().filter(e -> e.endsWith("ENTERED") || e.endsWith("EXITED")).toList());
        }

        @Test
        @DisplayName("a disabled node never lights up, and its ancestors still do")
        void disabledNeverHovers() {
            // docs/design-system.md §2.1 gives :disabled one appearance. A
            // control that still lightened under the pointer would be saying it
            // can be used. Enforced in the router rather than per variant per
            // state in a stylesheet -- CSS would write `:not(:disabled):hover`,
            // and `:not()` is not in §8's subset.
            innerWidget.disabled = true;
            router.pointerMoved(30, 30);

            assertFalse(inner.hasState(PseudoClass.HOVER));
            assertTrue(outer.hasState(PseudoClass.HOVER),
                    "the chain above it is not disabled and still hovers");

            // The *events* still arrive: a disabled node hit-tests, so a click
            // cannot fall through to whatever is behind it, and a tooltip saying
            // why something is disabled needs the enter.
            assertTrue(log.contains("bubble:inner:ENTERED"));
        }

        @Test
        @DisplayName("a node disabled while hovered loses the state")
        void disabledWhileHovered() {
            // The real sequence: a button disables itself in its own press
            // handler, with the pointer still over it. Only *setting* is
            // suppressed, so the next move clears what was already there.
            router.pointerMoved(30, 30);
            assertTrue(inner.hasState(PseudoClass.HOVER));

            innerWidget.disabled = true;
            router.pointerExited();
            router.pointerMoved(30, 30);

            assertFalse(inner.hasState(PseudoClass.HOVER));
        }

        @Test
        @DisplayName("a disabled node never looks pressed either")
        void disabledNeverActive() {
            innerWidget.disabled = true;
            router.pointerMoved(30, 30);
            router.pointerPressed(30, 30, PointerEvent.Button.PRIMARY, 1);

            assertFalse(inner.hasState(PseudoClass.ACTIVE));
        }

        @Test
        @DisplayName("leaving the window clears the whole chain")
        void pointerExited() {
            router.pointerMoved(30, 30);
            router.pointerExited();

            assertFalse(inner.hasState(PseudoClass.HOVER));
            assertFalse(outer.hasState(PseudoClass.HOVER));
            assertNull(router.hovered());
        }

        @Test
        @DisplayName("moving within one node changes nothing")
        void noChangeWithinANode() {
            router.pointerMoved(30, 30);
            router.takeStylesDirty();
            log.clear();

            router.pointerMoved(31, 31);

            assertFalse(router.takeStylesDirty(), "a move inside one node must not restyle");
            assertFalse(log.contains("bubble:inner:ENTERED"));
        }
    }

    @Nested
    @DisplayName("dispatch")
    class Dispatch {

        @Test
        @DisplayName("capture runs root-first, then bubble deepest-first")
        void phases() {
            log.clear();
            router.pointerMoved(30, 30);

            assertEquals(List.of(
                    "bubble:inner:ENTERED",
                    "bubble:outer:ENTERED",
                    "capture:outer:MOVED",
                    "capture:inner:MOVED",
                    "bubble:inner:MOVED",
                    "bubble:outer:MOVED"), log);
        }

        @Test
        @DisplayName("consuming during capture stops the target seeing it")
        void consumeInCapture() {
            outerWidget.consumeOnCapture = PointerEvent.Kind.MOVED;
            log.clear();
            router.pointerMoved(30, 30);

            // The whole point of a capture phase: a modal layer or a scroll view
            // intercepts before the target.
            assertTrue(log.contains("capture:outer:MOVED"));
            assertFalse(log.contains("capture:inner:MOVED"));
            assertFalse(log.contains("bubble:inner:MOVED"));
        }

        @Test
        @DisplayName("consuming while bubbling stops the ancestors")
        void consumeInBubble() {
            innerWidget.consumeOn = PointerEvent.Kind.MOVED;
            log.clear();
            router.pointerMoved(30, 30);

            assertTrue(log.contains("bubble:inner:MOVED"));
            assertFalse(log.contains("bubble:outer:MOVED"));
        }

        @Test
        @DisplayName("the target is the deepest node, in both phases")
        void targetIsStable() {
            var targets = new ArrayList<Element>();
            var recorder = new Node("outer2", false, innerWidget) {
                @Override
                public void onPointerCapture(PointerEvent event) {
                    targets.add(event.target());
                }

                @Override
                public void onPointer(PointerEvent event) {
                    if (event.kind() == PointerEvent.Kind.MOVED) {
                        targets.add(event.target());
                    }
                }
            };
            var localTree = new ElementTree(recorder);
            var localOuter = localTree.root();
            var localInner = localOuter.children().getFirst();
            router.updateRegions(List.of(
                    HitTest.Region.of(localOuter, 0, 0, 100, 100),
                    HitTest.Region.of(localInner, 20, 20, 40, 40)));

            router.pointerMoved(30, 30);

            // An ancestor sees the event during capture AND bubble, and in both
            // it can tell "below me" from "me" because the target never moves.
            assertFalse(targets.isEmpty());
            assertTrue(targets.stream().allMatch(t -> t == localInner),
                    "the target must stay the deepest node through both phases");
        }

        @Test
        @DisplayName("a pointer over nothing dispatches nothing")
        void missEverything() {
            router.updateRegions(List.of());
            log.clear();

            router.pointerMoved(30, 30);

            assertTrue(log.isEmpty());
            assertNull(router.hovered());
        }
    }

    @Nested
    @DisplayName("press and release")
    class Pressing {

        @Test
        @DisplayName(":active follows the press")
        void active() {
            router.pointerPressed(30, 30, PointerEvent.Button.PRIMARY, 1);
            assertTrue(inner.hasState(PseudoClass.ACTIVE));

            router.pointerReleased(30, 30, PointerEvent.Button.PRIMARY, 1);
            assertFalse(inner.hasState(PseudoClass.ACTIVE));
        }

        @Test
        @DisplayName("the button and click count reach the handler")
        void buttonAndCount() {
            var seen = new ArrayList<PointerEvent>();
            var recorder = new Node("rec", false) {
                @Override
                public void onPointer(PointerEvent event) {
                    seen.add(event);
                }
            };
            var localTree = new ElementTree(recorder);
            router.updateRegions(List.of(HitTest.Region.of(localTree.root(), 0, 0, 50, 50)));

            router.pointerPressed(10, 10, PointerEvent.Button.SECONDARY, 2);

            var press = seen.stream().filter(e -> e.kind() == PointerEvent.Kind.PRESSED).findFirst().orElseThrow();
            assertEquals(PointerEvent.Button.SECONDARY, press.button());
            assertEquals(2, press.clickCount());
        }
    }

    @Nested
    @DisplayName("focus")
    class Focus {

        @Test
        @DisplayName("a press focuses the nearest focusable ancestor")
        void pressFocuses() {
            router.pointerPressed(30, 30, PointerEvent.Button.PRIMARY, 1);

            // inner is focusable, outer is not.
            assertSame(inner, router.focused());
            assertTrue(inner.hasState(PseudoClass.FOCUS));
        }

        @Test
        @DisplayName("a press on a non-focusable node walks up to one that is")
        void focusWalksUp() {
            var label = new Node("label", false);
            var button = new Node("button", true, label);
            var localTree = new ElementTree(button);
            var buttonElement = localTree.root();
            var labelElement = buttonElement.children().getFirst();
            router.updateRegions(List.of(
                    HitTest.Region.of(buttonElement, 0, 0, 80, 30),
                    HitTest.Region.of(labelElement, 5, 5, 70, 20)));

            router.pointerPressed(10, 10, PointerEvent.Button.PRIMARY, 1);

            // Clicking the text inside a button focuses the button.
            assertSame(buttonElement, router.focused());
        }

        @Test
        @DisplayName("pointer focus is not :focus-visible; keyboard focus is")
        void focusVisible() {
            router.pointerPressed(30, 30, PointerEvent.Button.PRIMARY, 1);

            // §7.2: the focus ring renders only for keyboard focus.
            assertTrue(inner.hasState(PseudoClass.FOCUS));
            assertFalse(inner.hasState(PseudoClass.FOCUS_VISIBLE));

            router.focus(inner, true);
            assertTrue(inner.hasState(PseudoClass.FOCUS_VISIBLE));
        }

        @Test
        @DisplayName("pressing the background clears focus")
        void pressingNothingClearsFocus() {
            router.pointerPressed(30, 30, PointerEvent.Button.PRIMARY, 1);
            router.updateRegions(List.of());

            router.pointerPressed(90, 90, PointerEvent.Button.PRIMARY, 1);

            assertNull(router.focused());
            assertFalse(inner.hasState(PseudoClass.FOCUS));
        }

        @Test
        @DisplayName("focusing an unfocusable node is refused rather than silently losing focus")
        void refusesUnfocusable() {
            router.focus(inner, true);
            router.focus(outer, true);

            assertSame(inner, router.focused());
        }
    }

    @Nested
    @DisplayName("restyle bookkeeping")
    class Restyling {

        @Test
        @DisplayName("a pseudo-class change marks styles dirty exactly once")
        void dirtyOnce() {
            router.pointerMoved(30, 30);

            assertTrue(router.takeStylesDirty());
            // Cleared on read, so "did anything change since the last frame" is
            // the question it answers.
            assertFalse(router.takeStylesDirty());
        }
    }
}
