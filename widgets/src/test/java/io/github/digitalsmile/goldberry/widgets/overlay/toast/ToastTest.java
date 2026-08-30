package io.github.digitalsmile.goldberry.widgets.overlay.toast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.Theme;
import io.github.digitalsmile.goldberry.css.value.Transform;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.hit.Extent;
import io.github.digitalsmile.goldberry.motion.Clock;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.WidgetRenderer;
import io.github.digitalsmile.goldberry.widget.style.Corner;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.TestHost;
import io.github.digitalsmile.goldberry.widgets.controls.TestFont;
import io.github.digitalsmile.goldberry.widgets.controls.button.Button;
import io.github.digitalsmile.goldberry.widgets.panel.Described;

/// `toast` — §7's "queued, timeout with hover-pause, optional action button,
/// stacking corner configurable".
///
/// Every one of those four is a **behaviour over time**, which is why almost
/// nothing here is about what a toast looks like: `ToastGoldenTest` has the
/// pictures. What is here is the queue ([ADR-0177]).
class ToastTest {

    private TestHost host;
    private ToastController toasts;
    private List<String> pressed;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        host = new TestHost();
        toasts = new ToastController();
        pressed = new ArrayList<>();
    }

    private ElementTree stack() {
        return stack(Toaster.DEFAULT_MAXIMUM);
    }

    private ElementTree stack(int maximum) {
        return new ElementTree(new Toaster(toasts, Corner.BOTTOM_END, maximum), host);
    }

    /// The ids showing, in the order the stack describes them — oldest first.
    private static List<String> texts(ElementTree tree) {
        return Described.of(tree, ToastBox.class).stream()
                .map(box -> box.toast().text())
                .toList();
    }

    /// Fires the timer that was scheduled first, which is the one that would go
    /// off first when every toast has the same timeout.
    private void tickOldest() {
        host.tickFirst();
    }

    @Nested
    @DisplayName("the controller")
    class Controller {

        /// A toast raised into a window that is gone is a background job
        /// finishing late, which is ordinary rather than exceptional.
        @Test
        @DisplayName("a detached controller loses toasts quietly")
        void detachedIsInert() {
            assertFalse(toasts.isAttached());
            toasts.show("Saved");
            toasts.clear();

            assertEquals(List.of(), toasts.showing());
        }

        @Test
        @DisplayName("a stack attaches when it is mounted and lets go when it is not")
        void attaches() {
            var tree = stack();
            assertTrue(toasts.isAttached());

            tree.unmount();

            assertFalse(toasts.isAttached(), "a controller still pointing at a tree that is gone");
        }

        @Test
        @DisplayName("what is raised is what is showing")
        void showing() {
            var tree = stack();

            toasts.show("Draft saved");
            tree.flush();

            assertEquals(List.of("Draft saved"), texts(tree));
            assertEquals(1, toasts.showing().size());
        }
    }

    @Nested
    @DisplayName("the queue")
    class Queue {

        /// §7's "queued". The fourth does not appear, is not dropped, and is not
        /// shown out of order.
        @Test
        @DisplayName("only the maximum are showing and the rest wait their turn")
        void queued() {
            var tree = stack(2);

            toasts.show("one");
            toasts.show("two");
            toasts.show("three");
            tree.flush();

            assertEquals(List.of("one", "two"), texts(tree));
        }

        @Test
        @DisplayName("a waiting toast comes forward as soon as there is room")
        void promoted() {
            var tree = stack(2);
            toasts.show("one");
            toasts.show("two");
            toasts.show("three");
            tree.flush();

            // The oldest times out; the third takes its place while the first is
            // still fading, which is why a stack may briefly hold three.
            tickOldest();
            tree.flush();

            assertTrue(texts(tree).contains("three"), "the queue did not move: " + texts(tree));
        }

        @Test
        @DisplayName("a stack that shows none of them is refused")
        void maximumIsAtLeastOne() {
            assertThrows(IllegalArgumentException.class, () -> new Toaster(toasts, null, 0));
        }
    }

    @Nested
    @DisplayName("the clock")
    class Timeout {

        @Test
        @DisplayName("a toast goes on its own after its timeout")
        void expires() {
            var tree = stack();
            toasts.show("Saved");
            tree.flush();

            tickOldest();
            tree.flush();
            // Leaving, not gone: §1.7 keeps it mounted for the length of the exit.
            assertEquals(List.of("Saved"), texts(tree));

            host.tickFirst();
            tree.flush();
            assertEquals(List.of(), texts(tree), "it never actually went");
        }

        @Test
        @DisplayName("a zero timeout is the one that stays")
        void zeroStays() {
            var tree = stack();

            toasts.show(new Toast("Copy failed").timeout(Duration.ZERO));
            tree.flush();

            assertFalse(host.hasPendingTimer(), "something is going to take it away");
            assertEquals(List.of("Copy failed"), texts(tree));
        }

        /// §7's hover-pause, and the half that is easy to get wrong: leaving
        /// **resumes** rather than restarting, so a toast you glanced at does not
        /// owe you another five seconds.
        @Test
        @DisplayName("the pointer stops the clock, and leaving resumes what was left")
        void hoverPauses() {
            // A real renderer with a virtual clock, because the stack's clock is
            // the **frame** clock: `Host.after` gives a timer and no way to ask
            // how much of it has run, so the only way to pause one is to know
            // what time it is, and `render` is the only place a widget is told.
            var clock = io.github.digitalsmile.goldberry.motion.Clock.virtual();
            var tree = stack();
            var renderer = new io.github.digitalsmile.goldberry.widget.WidgetRenderer(
                            List.of(
                                    io.github.digitalsmile.goldberry.widgets.Controls.baseStylesheet(),
                                    io.github.digitalsmile.goldberry.css.Theme.NORD_DARK.load()),
                            io.github.digitalsmile.goldberry.widgets.controls.TestFont.get())
                    .clock(clock);

            toasts.show(new Toast("Saved").timeout(Duration.ofMillis(1000)));
            tree.flush();
            renderer.render(tree);
            assertEquals(List.of(Duration.ofMillis(1000)), host.scheduledDelays());

            // 400ms of the second have run when the pointer arrives.
            clock.advance(400);
            renderer.render(tree);
            Described.first(tree, ToastBox.class).onPointer(pointer(PointerEvent.Kind.ENTERED));
            // There is exactly one timer in this test — the toast's stay — so
            // "every timer is cancelled" is "the clock stopped". `hasPendingTimer`
            // would not do: the host keeps a cancelled timer's action in its list.
            assertTrue(host.allTimersCancelled(), "the clock did not stop");

            Described.first(tree, ToastBox.class).onPointer(pointer(PointerEvent.Kind.EXITED));

            assertEquals(
                    Duration.ofMillis(600),
                    host.scheduledDelays().getLast(),
                    "it was given the whole timeout again rather than what was left");
        }

        @Test
        @DisplayName("a toast on its way out ignores the pointer")
        void leavingIgnoresHover() {
            var tree = stack();
            toasts.show("Saved");
            tree.flush();
            tickOldest();
            tree.flush();

            var going = Described.first(tree, ToastBox.class);
            going.onPointer(pointer(PointerEvent.Kind.ENTERED));
            going.onPointer(pointer(PointerEvent.Kind.EXITED));

            assertTrue(going.leaving());
        }

        private PointerEvent pointer(PointerEvent.Kind kind) {
            return new PointerEvent(kind, 0, 0, PointerEvent.Button.PRIMARY, 0, null);
        }
    }

    @Nested
    @DisplayName("the action")
    class Action {

        @Test
        @DisplayName("pressing it runs the handler and takes the toast away")
        void pressed() {
            var tree = stack();
            toasts.show(new Toast("Message sent").action("Undo", () -> pressed.add("undo")));
            tree.flush();

            Described.first(tree, Button.class).onPress().run();
            tree.flush();

            assertEquals(List.of("undo"), pressed);
            assertTrue(Described.first(tree, ToastBox.class).leaving(), "the toast stayed after its action was taken");
        }

        @Test
        @DisplayName("no action means no button")
        void noButton() {
            var tree = stack();
            toasts.show("Saved");
            tree.flush();

            assertEquals(0, Described.of(tree, Button.class).size());
        }

        /// A label with nothing behind it invites the one click that will not
        /// work, so it is refused where every other value error is: at
        /// construction.
        @Test
        @DisplayName("a labelled action that does nothing is refused")
        void labelNeedsAHandler() {
            assertThrows(IllegalArgumentException.class, () -> new Toast("x", "Undo", null, Duration.ofSeconds(5)));
            assertThrows(IllegalArgumentException.class, () -> new Toast("x", null, null, Duration.ofSeconds(-1)));
        }
    }

    @Nested
    @DisplayName("clearing")
    class Clearing {

        /// What a screen change wants: a toast about the page you have left is a
        /// toast about nothing. Each one still gets its exit.
        @Test
        @DisplayName("clear takes them all, and the queue with them")
        void clear() {
            var tree = stack(2);
            toasts.show("one");
            toasts.show("two");
            toasts.show("three");
            tree.flush();

            toasts.clear();
            tree.flush();

            assertTrue(Described.of(tree, ToastBox.class).stream().allMatch(ToastBox::leaving), "something is staying");
            host.tickAll();
            tree.flush();
            assertEquals(List.of(), texts(tree), "the queue put another one up");
        }
    }

    /// §3's "siblings reflow via `translate` base — the one sanctioned movement
    /// effect", and the last thing §7 owed
    /// (ADR-0178).
    ///
    /// Every test here builds a **real renderer**, which the rest of this file
    /// mostly does not need, because the reflow reads two things only a frame
    /// has: `toaster`'s resolved `gap`, and the clock a
    /// [io.github.digitalsmile.goldberry.widgets.core.Phase]
    /// runs on.
    @Nested
    @DisplayName("the sibling reflow")
    class Reflowing {

        /// What every toast in these tests is measured at. Any number would do —
        /// what matters is that the stack uses the one it was told rather than
        /// one it worked out.
        private static final float HEIGHT = 44;

        /// `toaster { gap: 8px }` in `controls.css`. Written here as the number
        /// the stylesheet says, so that changing one and not the other fails.
        private static final double GAP = 8;

        private Clock.Virtual clock;
        private WidgetRenderer renderer;

        @BeforeEach
        void renderer() {
            clock = Clock.virtual();
            renderer = new WidgetRenderer(List.of(Controls.baseStylesheet(), Theme.NORD_DARK.load()), TestFont.get())
                    .clock(clock);
        }

        /// Three toasts that never go on their own and can each be dismissed,
        /// measured and drawn once.
        private ElementTree three(Corner corner) {
            return three(corner, true);
        }

        /// @param measured whether a frame has reported how tall they came out.
        ///                 Fed by hand because a widget test has no window: it is
        ///                 the router that tells a `Measured` widget what the
        ///                 frame made of it ([ADR-0117]), and there is no router
        ///                 here.
        private ElementTree three(Corner corner, boolean measured) {
            var tree = new ElementTree(new Toaster(toasts, corner), host);
            for (var text : List.of("one", "two", "three")) {
                toasts.show(
                        new Toast(text).action("Undo", () -> pressed.add(text)).timeout(Duration.ZERO));
            }
            tree.flush();
            renderer.render(tree);
            if (measured) {
                for (var box : Described.of(tree, ToastBox.class)) {
                    box.measured(new Extent(360, HEIGHT), new Extent(360, HEIGHT));
                }
            }
            return tree;
        }

        /// Presses one toast's action button and lets its exit finish.
        ///
        /// Both halves matter: the survivors move when the toast is **gone**
        /// rather than when it starts going, because until then it is still
        /// holding its place in the column and there is no hole.
        private void dismiss(ElementTree tree, String text) {
            var index = texts(tree).indexOf(text);
            assertTrue(index >= 0, "no toast says \"" + text + "\": " + texts(tree));
            Described.of(tree, Button.class).get(index).onPress().run();
            tree.flush();
            host.tickAll();
            tree.flush();
        }

        /// Where each toast is going, oldest first, with null for one that is
        /// where it belongs.
        private List<ToastBox.Reflow> reflows(ElementTree tree) {
            return Described.of(tree, ToastBox.class).stream()
                    .map(ToastBox::reflow)
                    .toList();
        }

        /// The vertical translate on the toast at `index` of the painted column,
        /// which describes its children oldest first whichever corner it is in.
        private double translateY(ElementTree tree, int index) {
            var column = renderer.render(tree);
            for (var function : column.children().get(index).transform().functions()) {
                if (function instanceof Transform.Function.Translate(var ignored, var y)) {
                    return y.value();
                }
            }
            return 0;
        }

        /// The stack is anchored by the toast nearest the corner, which is the
        /// newest — so a hole in the middle is closed from the **far** side.
        @Test
        @DisplayName("the toasts older than the one that went travel, and the newer ones do not")
        void onlyTheOlderOnesMove() {
            var tree = three(Corner.BOTTOM_END);

            dismiss(tree, "two");

            assertEquals(List.of("one", "three"), texts(tree));
            var going = reflows(tree);
            assertNotNull(
                    going.getFirst(), "the toast on the far side of the hole did not move, so the hole is still there");
            assertNull(going.getLast(), "a toast between the hole and the corner moved, and nothing had moved it");
        }

        @Test
        @DisplayName("the distance is the height of the hole plus the gap it was keeping")
        void theHoleAndTheGap() {
            var tree = three(Corner.BOTTOM_END);

            dismiss(tree, "two");

            assertEquals(
                    HEIGHT + GAP,
                    reflows(tree).getFirst().distance(),
                    0.001,
                    "the stack is closing a hole of a size nothing measured");
        }

        /// The translate runs **backwards**: the layout has already closed the
        /// gap, so the first frame puts the toast back where it was and the rest
        /// let go of it.
        @Test
        @DisplayName("it is drawn where it was and travels to where it now is")
        void backwards() {
            var tree = three(Corner.BOTTOM_END);

            dismiss(tree, "two");

            assertEquals(-(HEIGHT + GAP), translateY(tree, 0), 0.001, "the first frame jumped rather than staying put");
            clock.advance(ToasterState.REFLOW_MILLIS / 2);
            assertEquals(-(HEIGHT + GAP) / 2, translateY(tree, 0), 1.0);
            clock.advance(ToasterState.REFLOW_MILLIS);
            assertEquals(0, translateY(tree, 0), 0.001, "it never arrived");
        }

        /// A stack at the top of the window grows downwards, so its older toasts
        /// are **below** the hole and close it by coming up — which means being
        /// drawn below where the layout now puts them.
        @Test
        @DisplayName("a stack at the top closes its hole the other way round")
        void theCornerDecidesWhichWay() {
            var tree = three(Corner.TOP_START);

            dismiss(tree, "two");

            assertEquals(
                    HEIGHT + GAP,
                    translateY(tree, 0),
                    0.001,
                    "a stack at the top is closing its hole away from its corner");
        }

        /// Two toasts going in quick succession are two holes. A survivor that
        /// restarted for the second would arrive short by however far it still
        /// had to go on the first, and settle a toast's height from where it
        /// belongs — which is what clearing a stack looks like.
        @Test
        @DisplayName("a second departure adds to what was left of the first")
        void twoHoles() {
            var tree = three(Corner.BOTTOM_END);
            dismiss(tree, "two");
            renderer.render(tree);
            // Half of the first journey has run when the second one starts.
            clock.advance(ToasterState.REFLOW_MILLIS / 2);
            renderer.render(tree);

            dismiss(tree, "three");

            assertEquals(
                    (HEIGHT + GAP) * 1.5,
                    reflows(tree).getFirst().distance(),
                    1.0,
                    "the second hole threw away what was left of the first");
        }

        /// [ADR-0176]'s lesson, asserted the only way it can be: a widget that
        /// says it has stopped moving is a widget nobody repaints, and one nobody
        /// repaints does not move — it stands still and then is somewhere else.
        /// A golden would photograph that happily.
        @Test
        @DisplayName("a travelling toast asks for the frames it travels on")
        void asksForItsFrames() {
            var tree = three(Corner.BOTTOM_END);
            // Let the arrivals finish first, or this passes for the wrong reason.
            clock.advance(500);
            renderer.render(tree);
            renderer.render(tree);
            assertFalse(renderer.isAnimating(), "something is still arriving");

            dismiss(tree, "two");
            renderer.render(tree);

            assertTrue(renderer.isAnimating(), "nothing will repaint the survivors, so they will not travel");

            clock.advance(ToasterState.REFLOW_MILLIS + 1);
            renderer.render(tree);
            renderer.render(tree);
            assertFalse(renderer.isAnimating(), "it arrived and kept asking for frames");
        }

        /// §1.7: reduced motion turns the movement off, not the outcome. The hole
        /// is closed either way — it is closed *at once*.
        @Test
        @DisplayName("reduced motion closes the hole without travelling")
        void reducedMotion() {
            var tree = three(Corner.BOTTOM_END);
            renderer.reducedMotion(true);

            dismiss(tree, "two");

            assertEquals(0, translateY(tree, 0), 0.001, "something moved");
            // The second frame is the one that can answer: `isAnimating` reports
            // the render it was sampled during, and the render above is the one
            // that did the skipping.
            renderer.render(tree);
            assertFalse(renderer.isAnimating(), "it is still asking for frames to move on");
        }

        /// `Measured` is last frame's, so a toast raised and dismissed inside one
        /// frame has no height. The stack must read that as no hole rather than
        /// as a hole of nothing — the gap alone is a real number, and 8px in a
        /// direction nobody asked for is worse than the jump this replaces.
        @Test
        @DisplayName("a toast dismissed before it was ever drawn leaves no hole")
        void neverDrawnLeavesNoHole() {
            var tree = three(Corner.BOTTOM_END, false);

            dismiss(tree, "two");

            assertTrue(
                    reflows(tree).stream().allMatch(java.util.Objects::isNull),
                    "the stack moved to close a hole nothing had ever filled");
        }
    }

    /// The way out §7's shape left missing: a toast with `Duration.ZERO` and no
    /// action button could only be removed by `ToastController.clear()`, which is
    /// a notification nobody can get rid of ([ADR-0182]).
    @Nested
    @DisplayName("dismissing one")
    class Dismissing {

        private PointerEvent click() {
            return new PointerEvent(PointerEvent.Kind.CLICKED, 0, 0, PointerEvent.Button.PRIMARY, 1, null);
        }

        @Test
        @DisplayName("a click on the plate takes it away")
        void clickDismisses() {
            var tree = stack();
            toasts.show(new Toast("Copy failed").timeout(Duration.ZERO));
            tree.flush();

            Described.first(tree, ToastBox.class).onPointer(click());
            tree.flush();

            assertTrue(
                    Described.first(tree, ToastBox.class).leaving(),
                    "the one kind of toast that cannot go on its own still cannot go");
        }

        /// Dismissing is not answering. A toast clicked anywhere but its button
        /// runs no handler — which is what tells the plate apart from the action
        /// on it.
        @Test
        @DisplayName("dismissing runs no handler")
        void dismissIsNotAnAnswer() {
            var tree = stack();
            toasts.show(new Toast("Message sent")
                    .action("Undo", () -> pressed.add("undo"))
                    .timeout(Duration.ZERO));
            tree.flush();

            Described.first(tree, ToastBox.class).onPointer(click());
            tree.flush();

            assertEquals(List.of(), pressed, "a dismissal was taken for an answer");
            assertTrue(Described.first(tree, ToastBox.class).leaving());
        }

        /// The button is told first — a click bubbles from the node it hit — so a
        /// hit on the action is never lost to the plate underneath it, and the
        /// second dismissal is the one the stack already ignores.
        @Test
        @DisplayName("the action still acts, and dismissing twice is harmless")
        void theActionStillActs() {
            var tree = stack();
            toasts.show(new Toast("Message sent")
                    .action("Undo", () -> pressed.add("undo"))
                    .timeout(Duration.ZERO));
            tree.flush();

            Described.first(tree, Button.class).onPress().run();
            // ...and then the same click reaches the plate, as it does on screen.
            Described.first(tree, ToastBox.class).onPointer(click());
            tree.flush();

            assertEquals(List.of("undo"), pressed, "the action ran twice, or not at all");
            assertTrue(Described.first(tree, ToastBox.class).leaving());
        }

        /// §1.7's "no ghost clicks": input is off from the moment an answer is
        /// given, so a click landing during the 160ms exit must not re-end
        /// anything.
        @Test
        @DisplayName("a toast on its way out ignores a click")
        void leavingIgnoresIt() {
            var tree = stack();
            toasts.show("Saved");
            tree.flush();
            tickOldest();
            tree.flush();

            var going = Described.first(tree, ToastBox.class);
            going.onPointer(click());

            assertTrue(going.leaving());
            assertEquals(List.of("Saved"), texts(tree), "it went early");
        }
    }

    @Nested
    @DisplayName("the corner")
    class Corners {

        /// Which corner it is in reaches the stylesheet as a class, so which way
        /// the column grows is a rule rather than a branch.
        @Test
        @DisplayName("the corner is a class on the stack")
        void cornerIsAClass() {
            var tree = new ElementTree(new Toaster(toasts, Corner.TOP_START), host);

            assertTrue(
                    Described.first(tree, ToasterBox.class).classes().contains("top-start"),
                    "the stylesheet cannot tell which way to grow");
        }
    }
}
