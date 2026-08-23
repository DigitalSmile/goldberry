package io.github.digitalsmile.goldberry.widgets.overlay.toast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.style.Corner;
import io.github.digitalsmile.goldberry.widgets.TestHost;
import io.github.digitalsmile.goldberry.widgets.controls.button.Button;
import io.github.digitalsmile.goldberry.widgets.panel.Described;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
                .map(box -> box.toast().text()).toList();
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
                    List.of(io.github.digitalsmile.goldberry.widgets.Controls.baseStylesheet(),
                            io.github.digitalsmile.goldberry.css.Theme.NORD_DARK.load()),
                    io.github.digitalsmile.goldberry.widgets.controls.TestFont.get()).clock(clock);

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

            assertEquals(Duration.ofMillis(600), host.scheduledDelays().getLast(),
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
            assertTrue(Described.first(tree, ToastBox.class).leaving(),
                    "the toast stayed after its action was taken");
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
            assertThrows(IllegalArgumentException.class,
                    () -> new Toast("x", "Undo", null, Duration.ofSeconds(5)));
            assertThrows(IllegalArgumentException.class,
                    () -> new Toast("x", null, null, Duration.ofSeconds(-1)));
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

            assertTrue(Described.of(tree, ToastBox.class).stream().allMatch(ToastBox::leaving),
                    "something is staying");
            host.tickAll();
            tree.flush();
            assertEquals(List.of(), texts(tree), "the queue put another one up");
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

            assertTrue(Described.first(tree, ToasterBox.class).classes().contains("top-start"),
                    "the stylesheet cannot tell which way to grow");
        }
    }
}
