package io.github.digitalsmile.goldberry.widgets.overlay.toast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Corner;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;
import io.github.digitalsmile.goldberry.widgets.TestHost;

/// **A control deep in the tree can raise a toast**, which is the door
/// `TODO.md` asked for by name — "nothing wraps it in the
/// `Overlay.of(context)`-shaped call that would put a toast up from there
/// without the application's help" ([ADR-0264]).
///
/// The assertions are about the two ways `of` answers nothing, as much as about
/// the way it answers something: a widget with no window and a window with no
/// stack are both ordinary, and neither is a reason for a control to fail.
class ToastLookupTest {

    private TestHost host;
    private ToastController toasts;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        Toasts.forgetAttachments();
        host = new TestHost();
        toasts = new ToastController();
    }

    /// A widget that looks the stack up during its build and records what it
    /// found — which is how a test sees a `BuildContext` at all.
    private record Deep(List<ToastController> seen) implements Widget.Stateful {

        @Override
        public State<?> createState() {
            return new State<Deep>() {

                @Override
                public Widget build(BuildContext context) {
                    Toasts.of(context).ifPresent(widget().seen()::add);
                    return new Leafy();
                }
            };
        }
    }

    private record Leafy() implements Widget.Leaf, Styled, Paints {

        @Override
        public String cssType() {
            return "leafy";
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of().style(style);
        }
    }

    /// Nested a few deep, because the point of the door is that nothing in
    /// between has to carry a callback.
    private record Wrapper(Widget child) implements Widget.Leaf, Styled, Paints {

        @Override
        public String cssType() {
            return "wrapper";
        }

        @Override
        public List<Widget> children() {
            return List.of(child);
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of().style(style).children(children.toArray(Box[]::new));
        }
    }

    @Test
    @DisplayName("a widget three deep finds the stack its application attached")
    void findsTheAttachedStack() {
        Toasts.at(host, toasts, Corner.BOTTOM_END);
        var seen = new ArrayList<ToastController>();

        new ElementTree(new Wrapper(new Wrapper(new Deep(seen))), host);

        assertEquals(1, seen.size(), "the build never found a stack");
        assertSame(toasts, seen.getFirst(), "and it found somebody else's");
    }

    /// And having found it, it can use it — which is the whole point and is
    /// asserted separately, because a lookup that returns the right object and a
    /// toast that appears are two claims.
    ///
    /// The stack is **mounted** here as well as attached. `TestHost.overlay`
    /// records the overlay rather than building it, so a controller registered by
    /// `at` alone is still detached and swallows what it is shown — which is
    /// `ToastController`'s documented behaviour and would have made this pass by
    /// asserting nothing.
    @Test
    @DisplayName("and can raise one through it")
    void raisesOne() {
        Toasts.at(host, toasts, Corner.BOTTOM_END);
        new ElementTree(new Toaster(toasts, Corner.BOTTOM_END), host);
        var seen = new ArrayList<ToastController>();
        new ElementTree(new Wrapper(new Deep(seen)), host);

        seen.getFirst().show("Saved");

        assertEquals(
                List.of("Saved"), toasts.showing().stream().map(Toast::text).toList());
    }

    @Test
    @DisplayName("a window with no stack answers nothing, which is not a fault")
    void noStackAttached() {
        var seen = new ArrayList<ToastController>();

        new ElementTree(new Deep(seen), host);

        assertTrue(seen.isEmpty(), "an application that never attached a stack has not made a mistake");
    }

    @Test
    @DisplayName("and a tree with no window at all answers nothing too, which is every unit test")
    void noHostAtAll() {
        Toasts.at(host, toasts, Corner.BOTTOM_END);
        var seen = new ArrayList<ToastController>();

        // No host: the overwhelmingly common shape in a test, and a widget that
        // threw here would be a widget that cannot be tested without a window.
        new ElementTree(new Deep(seen));

        assertTrue(seen.isEmpty());
    }

    @Test
    @DisplayName("two windows do not see each other's stacks")
    void perWindow() {
        var second = new TestHost();
        var other = new ToastController();
        Toasts.at(host, toasts, Corner.BOTTOM_END);
        Toasts.at(second, other, Corner.TOP_END);

        var here = new ArrayList<ToastController>();
        var there = new ArrayList<ToastController>();
        new ElementTree(new Deep(here), host);
        new ElementTree(new Deep(there), second);

        assertSame(toasts, here.getFirst());
        assertSame(other, there.getFirst(), "a toast raised in one window must not land in another");
    }

    /// Moving a stack to another corner is `at`'s return value's whole purpose,
    /// and the lookup has to follow it — otherwise a window that moved its toasts
    /// would hand out a controller whose overlay has stopped drawing.
    @Test
    @DisplayName("re-attaching replaces what the lookup answers")
    void lastAttachmentWins() {
        var replacement = new ToastController();
        Toasts.at(host, toasts, Corner.BOTTOM_END).remove();
        Toasts.at(host, replacement, Corner.TOP_START);

        var seen = new ArrayList<ToastController>();
        new ElementTree(new Deep(seen), host);

        assertSame(replacement, seen.getFirst());
    }

    @Test
    @DisplayName("and neither argument may be null")
    void nullsAreRefused() {
        assertThrows(NullPointerException.class, () -> Toasts.of(null));
        assertThrows(NullPointerException.class, () -> Toasts.at(null, toasts, Corner.BOTTOM_END));
        assertThrows(NullPointerException.class, () -> Toasts.at(host, null, Corner.BOTTOM_END));
    }
}
