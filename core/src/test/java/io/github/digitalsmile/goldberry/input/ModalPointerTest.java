package io.github.digitalsmile.goldberry.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.css.select.Selector.PseudoClass;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.input.hit.HitTest;
import io.github.digitalsmile.goldberry.widget.Element;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// **What the pointer may reach**, which used to be an accident of paint order
/// and a scrim ([ADR-0232]).
///
/// Two rules, and neither had ever been written down:
///
/// - **The topmost painted region takes the pointer.** The capture is in paint
///   order and is scanned backwards, and the window's overlay layer is painted
///   after the application — so a button in a `toast` takes the pointer from
///   whatever is under it, without either of them knowing about the other.
/// - **While a modal is mounted, the pointer reaches its subtree and its
///   ancestors, and nothing else.** That used to be two mechanisms: `isModal`
///   trapped the keyboard, and a `dialog`'s scrim happened to cover the window.
///   A modal without a scrim trapped the keyboard and let every click through.
///
/// In `:core` and built from bare widgets rather than from `dialog`, for
/// [FocusTrapTest]'s reason: the mechanism is the router's and has to hold for
/// whatever declares itself modal next.
class ModalPointerTest {

    private final List<String> pressed = new ArrayList<>();

    /// A node that records presses and may declare itself modal.
    private class Node implements Widget.Leaf, Styled, Handles {

        private final String name;
        private final boolean modal;
        private final List<Widget> children;

        Node(String name, boolean modal, Widget... children) {
            this.name = name;
            this.modal = modal;
            this.children = List.of(children);
        }

        Node(String name, Widget... children) {
            this(name, false, children);
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
        public boolean isFocusable() {
            return true;
        }

        @Override
        public boolean isModal() {
            return modal;
        }

        @Override
        public void onPointer(PointerEvent event) {
            if (event.kind() == PointerEvent.Kind.PRESSED) {
                pressed.add(name);
            }
        }
    }

    private PointerRouter router;
    private ElementTree tree;

    /// `window( app( button ) scrim( panel( ok ) ) )`, with `panel` modal or not.
    ///
    /// The shape a `dialog` has, with **one thing deliberately removed**: the
    /// scrim does not fill the window. A `dialog`'s does, and that is exactly why
    /// nothing here could tell the rule from the geometry — a filling scrim takes
    /// every press whether or not anything is modal. A small overlay is the case
    /// the entry names: a modal *without* a scrim, which used to trap the keyboard
    /// and let every click through ([ADR-0232]).
    private void build(boolean modal) {
        pressed.clear();
        tree = new ElementTree(new Node(
                "window",
                new Node("app", new Node("button")),
                new Node("scrim", false, new Node("panel", modal, new Node("ok")))));
        router = new PointerRouter();
        router.focusRoot(tree.root());
        // Paint order: the application first, the overlay after it — which is
        // what `WindowRoot` describes and therefore what a capture holds.
        router.updateRegions(List.of(
                HitTest.Region.of(byId("window"), 0, 0, 200, 200),
                HitTest.Region.of(byId("app"), 0, 0, 200, 200),
                HitTest.Region.of(byId("button"), 10, 10, 40, 20),
                HitTest.Region.of(byId("scrim"), 55, 55, 90, 90),
                HitTest.Region.of(byId("panel"), 60, 60, 80, 80),
                HitTest.Region.of(byId("ok"), 70, 70, 40, 20)));
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

    private void press(float x, float y) {
        router.pointerPressed(x, y, PointerEvent.Button.PRIMARY, 1);
    }

    @Nested
    @DisplayName("with nothing modal")
    class Open {

        @Test
        @DisplayName("the topmost painted region wins, which is the overlay rule")
        void topmostWins() {
            build(false);

            // (57, 57) is inside the scrim *and* inside the application's own
            // full-window box, and inside neither the panel nor the button. The
            // scrim was painted later, so it answers — and `app` never hears the
            // press, because dispatch bubbles up the *element* chain and the
            // application is the overlay's sibling rather than its parent.
            press(57, 57);

            assertEquals(List.of("scrim", "window"), pressed, "the pointer went under the overlay");
        }

        @Test
        @DisplayName("a press on the application reaches it")
        void applicationIsReachable() {
            build(false);

            press(20, 20);

            assertEquals(List.of("button", "app", "window"), pressed, "the button under the pointer heard nothing");
        }
    }

    @Nested
    @DisplayName("with a modal mounted")
    class Modal {

        @Test
        @DisplayName("a press on the application reaches nothing at all")
        void applicationIsUnreachable() {
            build(true);

            press(20, 20);

            assertEquals(List.of(), pressed, "a click went through a modal to the window behind it");
        }

        /// Not a loophole — the point. A `dialog`'s scrim is the panel's parent,
        /// and a press on it is how a dialog is dismissed by clicking outside.
        @Test
        @DisplayName("a press on the modal's own ancestor still reaches it")
        void theScrimIsReachable() {
            build(true);

            // Inside the scrim, outside the panel.
            press(57, 57);

            assertEquals(List.of("scrim", "window"), pressed);
        }

        @Test
        @DisplayName("a press inside the modal reaches what it landed on")
        void insideIsReachable() {
            build(true);

            press(80, 80);

            assertEquals(List.of("ok", "panel", "scrim", "window"), pressed);
        }

        /// Hover is the pointer too. A button under a dialog that lit up when the
        /// mouse crossed it would say it was pressable when it is not.
        @Test
        @DisplayName("nothing under the modal takes :hover either")
        void nothingHoversUnderneath() {
            build(true);

            router.pointerMoved(20, 20);

            assertFalse(
                    byId("button").hasState(PseudoClass.HOVER), "a control behind a modal lit up under the pointer");
        }

        /// A press on the background normally moves focus off whatever had it.
        /// Behind a modal, "the background" is the application — and emptying the
        /// trap only to have the next frame refill it is a frame of nothing
        /// focused.
        @Test
        @DisplayName("a press on the unreachable application does not empty the trap")
        void focusStays() {
            build(true);
            router.focus(byId("ok"), true);

            press(20, 20);

            assertSame(byId("ok"), router.focused());
        }
    }

    /// The read a widget outside the router needs, and the reason it exists.
    ///
    /// `web-view` is the one widget nothing painted can cover: a page is a
    /// platform window above the frame, so a `dialog` over it is drawn where
    /// nobody can see it. The widget therefore has to take the page off the
    /// screen itself, and to do that it has to be able to ask ([ADR-0444]).
    ///
    /// Answered from the same field the two rules above are enforced with, so
    /// there is one notion of "a modal is in force" rather than two that can
    /// disagree.
    @Nested
    @DisplayName("asked whether a modal is in force")
    class Asking {

        @Test
        @DisplayName("says no while the window is open")
        void noWhileOpen() {
            build(false);

            assertFalse(router.isModal());
        }

        @Test
        @DisplayName("says yes while something modal is mounted")
        void yesWhileModal() {
            build(true);

            assertTrue(router.isModal());
        }

        /// The answer is recomputed from the tree on every frame rather than
        /// latched when a dialog opens, which is what lets a modal that goes
        /// away by any route at all give the window back.
        @Test
        @DisplayName("and says no again once it is gone, without being told")
        void noAgainWhenItGoes() {
            build(true);
            assertTrue(router.isModal());

            build(false);

            assertFalse(router.isModal());
        }
    }
}
