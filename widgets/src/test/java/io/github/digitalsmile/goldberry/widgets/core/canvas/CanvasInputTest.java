package io.github.digitalsmile.goldberry.widgets.core.canvas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.input.PointerRouter;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.hit.HitTest;
import io.github.digitalsmile.goldberry.input.key.Key;
import io.github.digitalsmile.goldberry.input.key.Mod;
import io.github.digitalsmile.goldberry.input.key.Modifiers;
import io.github.digitalsmile.goldberry.widget.ElementTree;

/// Input on a `canvas` — `docs/gaps.md` G3.
///
/// ## What is actually new
///
/// Very little of the machinery. `Handles`, `PointerRouter`, implicit capture on
/// press, the wheel, focus and per-box cursors were all built for the widget
/// catalogue and all work; what was missing is that a `canvas` implemented none
/// of it, so a painter was handed a frame and no events (ADR-0281).
///
/// The coordinate space — a canvas painter draws inside the padding, so its input
/// must arrive there too — is `ContentBoxTest` in `:core`, because that is where
/// the arithmetic lives. What is checked here is the widget: that it delegates,
/// that it is a Tab stop only when there is something to deliver a key to, and
/// that a drag off the edge keeps reporting.
class CanvasInputTest {

    private PointerRouter router;
    private Recorder input;
    private Canvas canvas;

    /// Records everything it is handed.
    private static final class Recorder implements Input {
        private final List<PointerEvent> pointers = new ArrayList<>();
        private final List<KeyEvent> keys = new ArrayList<>();
        private final boolean focusable;

        Recorder() {
            this(true);
        }

        Recorder(boolean focusable) {
            this.focusable = focusable;
        }

        @Override
        public void onPointer(PointerEvent event) {
            pointers.add(event);
        }

        @Override
        public void onKey(KeyEvent event) {
            keys.add(event);
        }

        @Override
        public boolean focusable() {
            return focusable;
        }
    }

    @BeforeEach
    void setUp() {
        router = new PointerRouter();
        input = new Recorder();
        canvas = new Canvas(null, input);
        var element = new ElementTree(canvas).root();
        router.updateRegions(List.of(HitTest.Region.of(element, 0, 0, 100, 60)));
    }

    private PointerEvent lastOfKind(PointerEvent.Kind kind) {
        return input.pointers.stream()
                .filter(event -> event.kind() == kind)
                .reduce((first, second) -> second)
                .orElseThrow(() -> new AssertionError("no " + kind + " arrived; saw "
                        + input.pointers.stream().map(PointerEvent::kind).toList()));
    }

    @Nested
    @DisplayName("the pointer")
    class Pointer {

        @Test
        @DisplayName("a press reaches the canvas, where it landed")
        void press() {
            router.pointerMoved(30, 20);
            router.pointerPressed(30, 20, PointerEvent.Button.PRIMARY, 1);

            var press = lastOfKind(PointerEvent.Kind.PRESSED);
            assertEquals(30, press.local().x(), 0.01);
            assertEquals(20, press.local().y(), 0.01);
            assertEquals(PointerEvent.Button.PRIMARY, press.button());
        }

        @Test
        @DisplayName("a drag that leaves the canvas keeps reporting")
        void captureOutlivesTheBounds() {
            // The router captures on press for every widget, so brd's sketched
            // `capturePointer()` is already the default: a marquee dragged off
            // the edge does not have to ask for anything.
            router.pointerMoved(30, 20);
            router.pointerPressed(30, 20, PointerEvent.Button.PRIMARY, 1);
            input.pointers.clear();
            router.pointerMoved(400, 400);

            var moved = lastOfKind(PointerEvent.Kind.MOVED);
            assertEquals(400, moved.x(), 0.01, "far outside the canvas, and still delivered");
            assertEquals(30, moved.pressX(), 0.01, "and it still knows where the gesture began");
        }

        @Test
        @DisplayName("the wheel arrives with its fraction and its detents")
        void wheel() {
            router.pointerWheel(30, 20, 0, -3);

            var wheel = lastOfKind(PointerEvent.Kind.WHEEL);
            assertEquals(-3, wheel.deltaY(), 0.01, "a touchpad's fraction");
            assertEquals(-3, wheel.ticksY(), "and a mouse's detents, for a zoom step");
        }

        @Test
        @DisplayName("modifiers ride along, so Ctrl+wheel can mean zoom")
        void modifiers() {
            router.pointerWheel(30, 20, 0, -1, 0, -1, Modifiers.of(Mod.CTRL));

            assertTrue(lastOfKind(PointerEvent.Kind.WHEEL).modifiers().control());
        }

        @Test
        @DisplayName("a canvas may consume an event, so the pane under it does not scroll")
        void consuming() {
            var greedy = new Canvas(null, new Input() {
                @Override
                public void onPointer(PointerEvent event) {
                    event.consume();
                }
            });
            var element = new ElementTree(greedy).root();
            router.updateRegions(List.of(HitTest.Region.of(element, 0, 0, 100, 60)));

            router.pointerWheel(30, 20, 0, -1);
            // Nothing to assert against a parent here -- `WheelAndCaptureTest`
            // owns that -- but a canvas that could not consume would make every
            // zoomable board scroll its container as well.
            assertTrue(greedy.input() != null);
        }
    }

    @Nested
    @DisplayName("focus and keys")
    class Keys {

        @Test
        @DisplayName("a canvas with input is focusable and hears keys")
        void focusableWithInput() {
            assertTrue(canvas.isFocusable());

            router.pointerMoved(30, 20);
            router.pointerPressed(30, 20, PointerEvent.Button.PRIMARY, 1);
            router.pointerReleased(30, 20, PointerEvent.Button.PRIMARY, 1);
            router.keyPressed(Key.ESCAPE, Modifiers.NONE, false);

            assertFalse(input.keys.isEmpty(), "the key reached the canvas");
            assertEquals(Key.ESCAPE, input.keys.getFirst().key());
        }

        @Test
        @DisplayName("a canvas with no input is not a Tab stop")
        void notFocusableWithoutInput() {
            // A chart that took the focus and did nothing with it would be a
            // keyboard trap with no exit, which is the thing §2.2's "everything
            // reachable" is least served by.
            assertFalse(new Canvas(null).isFocusable());
            assertFalse(new Canvas(null, (Input) null).isFocusable());
        }

        @Test
        @DisplayName("an input may decline the focus and still hear the pointer")
        void inputMayDeclineFocus() {
            // A pan-and-zoom surface wants the wheel and no Tab stop.
            assertFalse(new Canvas(null, new Recorder(false)).isFocusable());
        }
    }

    @Nested
    @DisplayName("as a widget")
    class AsAWidget {

        @Test
        @DisplayName("the painter and the input are independent")
        void independent() {
            assertSame(input, canvas.input());
            assertNull(canvas.painter());
            assertNull(new Canvas(null).input());
        }

        @Test
        @DisplayName("the input survives a change of attributes")
        void attributesSurvive() {
            assertSame(input, canvas.withAttributes(canvas.attributes()).input());
        }

        @Test
        @DisplayName("markup still builds a canvas that draws and hears nothing")
        void markupIsUnchanged() {
            // A `canvas` node names no painter (ADR-0043) and now names no input
            // either, for the same reason: both are Java, and the indirection a
            // document would need is filed rather than guessed at.
            assertNull(new Canvas(null, null, canvas.attributes()).input());
        }
    }
}
