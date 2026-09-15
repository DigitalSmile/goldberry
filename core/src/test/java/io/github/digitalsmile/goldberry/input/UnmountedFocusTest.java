package io.github.digitalsmile.goldberry.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// The router never speaks to an element it has let go of — `docs/gaps.md` G31,
/// ADR-0317.
///
/// In `:core` and built from bare widgets rather than from `text-input`, which
/// lives in `:widgets`, for [FocusTrapTest]'s reason: what is under test is the
/// router's rule, and it has to hold for whatever control disappears next. A
/// field in a closing `dialog` is the one that found it; a tab that switched and
/// a list that shortened are the same fault by another route.
///
/// The invariant is one sentence: **a focus notification is only ever delivered
/// to an element that is still in the tree** — and, the other half of it, to
/// every element that *is*.
class UnmountedFocusTest {

    /// What each widget was told, in order, as `"<id> <call>"`.
    private final List<String> told = new ArrayList<>();

    /// A focusable leaf that writes down every notification it is handed — and,
    /// like a real [io.github.digitalsmile.goldberry.widget.State], refuses one
    /// that arrives after it has gone.
    private class Item implements Widget.Leaf, Styled, Handles {
        private final String name;

        /// Set by the test once the element describing this widget has been
        /// unmounted, which is the only way a widget can know: it is handed no
        /// element.
        private boolean gone;

        /// Told, by the test, that the element describing it has been unmounted.
        void gone() {
            gone = true;
        }

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

        @Override
        public void onFocusChanged(boolean gained, boolean fromKeyboard) {
            record(gained ? "gained" : "lost");
        }

        @Override
        public void onFocusWithin(boolean within, boolean fromKeyboard) {
            record(within ? "entered" : "left");
        }

        private void record(String call) {
            told.add(name + " " + call);
            if (gone) {
                // `State.setState`'s own complaint, in the one place a test can
                // stand it up: a callback that outlived the widget that
                // registered it.
                throw new IllegalStateException(name + " was told \"" + call + "\" after it was unmounted");
            }
        }
    }

    /// Scenery that holds things — a row, a panel, a dialog's body. Not a Tab
    /// stop: a container is what `:focus-within` is about.
    private class Panel extends Item {
        private final List<Widget> children;

        Panel(String name, Widget... children) {
            super(name);
            this.children = List.of(children);
        }

        @Override
        public List<Widget> children() {
            return children;
        }

        @Override
        public boolean isFocusable() {
            return false;
        }
    }

    private PointerRouter router;
    private ElementTree tree;
    private Item a;
    private Item b;
    private Panel panel;

    /// `window( a panel( b ) )` — one ordinary stop, and one inside a container
    /// the test can make vanish.
    @BeforeEach
    void build() {
        told.clear();
        a = new Item("a");
        b = new Item("b");
        panel = new Panel("panel", b);
        tree = new ElementTree(new Panel("window", a, panel));
        router = new PointerRouter();
        router.focusRoot(tree.root());
    }

    /// Re-describes the window without the panel — a dialog closing, a tab
    /// switching, a list shortening — and marks what went with it.
    private void closeThePanel() {
        tree.update(new Panel("window", a));
        tree.flush();
        b.gone();
        panel.gone();
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

    @Nested
    @DisplayName("when the focused element goes away")
    class Vanishes {

        @Test
        @DisplayName("refocus lets go of it without telling it")
        void refocusIsSilent() {
            var focused = byId("b");
            router.focus(focused, true);
            told.clear();

            closeThePanel();
            assertFalse(focused.isMounted(), "the panel's child is still in the tree");

            router.refocus();

            // The window is still there and has genuinely lost the keyboard, so
            // it is told; the two elements that were disposed are not.
            assertEquals(List.of("window left"), told, "the router spoke to something it had let go of");
            assertNull(router.focused(), "the router is still holding a node that is not in the tree");
        }

        /// The path the reported crash actually took: a frame is painted, the
        /// router is handed the new regions, and `refocus` runs inside
        /// [PointerRouter#updateRegions].
        @Test
        @DisplayName("a painted frame does not raise it either")
        void updateRegionsIsSilent() {
            router.focus(byId("b"), true);
            told.clear();

            closeThePanel();

            router.updateRegions(List.of());

            assertEquals(List.of("window left"), told);
            assertNull(router.focused());
        }

        /// `:focus-within` walks the same two chains and wants the same guard —
        /// a container that went away with its child is as disposed as the child.
        @Test
        @DisplayName("the containers it was inside are not told either")
        void focusWithinIsSilent() {
            router.focus(byId("b"), true);
            assertEquals(List.of("b gained", "b entered", "panel entered", "window entered"), told);
            told.clear();

            closeThePanel();
            router.refocus();

            assertFalse(told.contains("panel left"), "a dead container was told it lost the keyboard");
            assertFalse(told.contains("b left"), "a dead control was told it lost the keyboard");
        }

        /// The guard is per element rather than "say nothing once anything died".
        @Test
        @DisplayName("an ancestor that survived still hears about it")
        void aSurvivingAncestorIsTold() {
            router.focus(byId("b"), true);
            told.clear();

            closeThePanel();
            router.refocus();
            router.focus(byId("a"), true);

            assertEquals(List.of("window left", "a gained", "a entered", "window entered"), told);
        }
    }

    @Nested
    @DisplayName("an ordinary focus change")
    class StillNotified {

        @Test
        @DisplayName("tells what lost the keyboard, as it always did")
        void theLoserIsTold() {
            router.focus(byId("a"), false);
            told.clear();

            router.focus(byId("b"), false);

            assertEquals(List.of("a lost", "b gained", "a left", "b entered", "panel entered"), told);
        }

        @Test
        @DisplayName("focusing nothing tells what had it")
        void lettingGoIsTold() {
            router.focus(byId("a"), false);
            told.clear();

            router.focus(null, false);

            assertEquals(List.of("a lost", "a left", "window left"), told);
        }
    }

    @Nested
    @DisplayName("what refocus hands back to")
    class Restored {

        /// The other half of `refocus`: when there **is** somewhere to go back
        /// to, it is focused — and the element that died is still not told.
        @Test
        @DisplayName("a modal that closes gives the keyboard back, silently")
        void restoreToIsStillHonoured() {
            router.focus(byId("a"), false);

            var inside = new Item("inside");
            var modal = new Panel("modal", inside) {
                @Override
                public boolean isModal() {
                    return true;
                }
            };
            tree.update(new Panel("window", a, modal));
            tree.flush();
            b.gone();
            panel.gone();

            // The trap: asking for something outside a modal lands inside it and
            // remembers where the keyboard came from.
            router.focus(byId("a"), true);
            assertSame(byId("inside"), router.focused(), "the trap did not redirect");
            told.clear();

            tree.update(new Panel("window", a));
            tree.flush();
            inside.gone();
            modal.gone();

            router.refocus();

            assertSame(byId("a"), router.focused(), "the keyboard did not come back");
            assertEquals(List.of("a gained", "a entered"), told);
        }
    }
}
