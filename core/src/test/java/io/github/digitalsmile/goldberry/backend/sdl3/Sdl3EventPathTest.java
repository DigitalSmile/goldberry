package io.github.digitalsmile.goldberry.backend.sdl3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.backend.BackendEvent;
import io.github.digitalsmile.goldberry.backend.BackendWindow;
import io.github.digitalsmile.goldberry.backend.EventSink;
import io.github.digitalsmile.goldberry.backend.LogicalSize;
import io.github.digitalsmile.goldberry.backend.WindowSpec;
import io.github.digitalsmile.goldberry.natives.sdl.SdlEventBuffer;
import io.github.digitalsmile.goldberry.natives.sdl.SdlEventType;
import io.github.digitalsmile.goldberry.natives.sdl.SdlVideo;
import io.github.digitalsmile.goldberry.natives.sdl.SdlWheelDirection;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The event path, driven through the **real** SDL.
///
/// Two branches of [Sdl3Backend] had never run anywhere: the wheel, because no
/// test can turn one and the showcase scrolls nothing, and the event watch,
/// because it fires from inside a resize gesture. Both are reachable through
/// `SDL_PushEvent`, which is SDL's own way to synthesize input — the event goes
/// on the queue, comes back out of the ordinary pump, and takes the shipping
/// route rather than a copy of it (ADR-0061).
///
/// Everything runs under SDL's `dummy` video driver, so there is no display, no
/// compositor and nothing to see. That is the point: it runs in CI, on all three
/// platforms, where a wheel and a drag are not available.
class Sdl3EventPathTest {

    /// A window big enough that a pointer position inside it is meaningful.
    private static final LogicalSize SIZE = LogicalSize.of(320, 240);

    @BeforeAll
    static void requireLibrary() {
        RendererRequirement.enforce();
    }

    @Test
    @DisplayName("a wheel event reaches the sink with the toolkit's sign, not SDL's")
    void wheelReachesTheSink() {
        withBackend((backend, window) -> {
            var events = pump(backend, sink -> push(buffer -> buffer.writeWheel(
                    id(window), -2f, 3f, SdlWheelDirection.NORMAL, 120f, 64f)));

            var wheel = only(events, BackendEvent.PointerWheel.class);
            assertSame(window, wheel.window());
            // SDL's y is positive away from the user; the SPI's is positive down
            // the document. The negation is the whole of the branch under test,
            // and getting it wrong scrolls every document backwards.
            assertEquals(-3f, wheel.deltaY());
            assertEquals(-2f, wheel.deltaX());
            // The position comes from the wheel arm's own fields. Reading it
            // through the motion arm's accessors would give 3.0 here -- the
            // vertical delta, which lands at exactly that offset.
            assertEquals(120f, wheel.x());
            assertEquals(64f, wheel.y());
        });
    }

    @Test
    @DisplayName("a flipped wheel event is un-flipped before the sign is applied")
    void flippedWheelIsUndoneOnce() {
        withBackend((backend, window) -> {
            var events = pump(backend, sink -> push(buffer -> buffer.writeWheel(
                    id(window), 0f, 3f, SdlWheelDirection.FLIPPED, 10f, 10f)));

            // Natural scrolling flips SDL's value; the SPI's negation flips it
            // back. Two flips and one sign convention, and the user who turned
            // the preference on scrolls the same way as the one who did not.
            assertEquals(3f, only(events, BackendEvent.PointerWheel.class).deltaY());
        });
    }

    @Test
    @DisplayName("a fractional delta survives the crossing, because a touchpad sends only those")
    void fractionalWheelSurvives() {
        withBackend((backend, window) -> {
            var events = pump(backend, sink -> push(buffer -> buffer.writeWheel(
                    id(window), 0f, 0.125f, SdlWheelDirection.NORMAL, 10f, 10f)));

            assertEquals(-0.125f, only(events, BackendEvent.PointerWheel.class).deltaY());
        });
    }

    @Test
    @DisplayName("a resize is drawn from inside the event watch, before the pump returns")
    void resizeIsHandledInsideTheWatch() {
        withBackend((backend, window) -> {
            // A frame is outstanding, so there is one for the watch to emit --
            // which is what a modal resize loop starves the window of.
            window.requestFrame();

            var events = new ArrayList<BackendEvent>();
            var nested = new ArrayList<BackendEvent>();
            var depth = new int[1];
            var pushed = new boolean[1];

            EventSink sink = event -> {
                events.add(event);
                if (depth[0] > 0) {
                    nested.add(event);
                }
                // Pushed from inside a handler, so the push happens while the
                // pump is still running -- which is the situation SDL's own
                // resize loop creates, and the only way to reach it from a test.
                if (!pushed[0] && event instanceof BackendEvent.Exposed) {
                    pushed[0] = true;
                    depth[0]++;
                    try {
                        push(buffer -> buffer.writeWindowEvent(
                                SdlEventType.WINDOW_RESIZED, id(window), 320, 240));
                    } finally {
                        depth[0]--;
                    }
                }
            };

            push(buffer -> buffer.writeWindowEvent(SdlEventType.WINDOW_EXPOSED, id(window), 0, 0));
            backend.pumpEvents(sink, Duration.ofMillis(50));

            assertTrue(pushed[0], "the seeded expose never arrived, so nothing was pushed");
            // The resize arrived *inside* the push, not from the queue afterwards.
            // Without the watch it would be at the end of the list instead, which
            // during a real drag means "when the user lets go".
            assertTrue(nested.stream().anyMatch(BackendEvent.Resized.class::isInstance),
                    () -> "the resize was not delivered from the watch: " + names(events));
            assertTrue(nested.stream().anyMatch(BackendEvent.FrameDue.class::isInstance),
                    () -> "no frame was drawn during the resize: " + names(events));
        });
    }

    @Test
    @DisplayName("the same resize arriving twice is reported once")
    void duplicateResizeIsCoalesced() {
        withBackend((backend, window) -> {
            // Exactly what the watch and the queue produce between them: the
            // event is handled while the drag runs, and handed over again when it
            // ends. A second layout pass and a second frame for a size the window
            // already has is the cost of the watch if nothing coalesces.
            var events = pump(backend, sink -> {
                push(buffer -> buffer.writeWindowEvent(
                        SdlEventType.WINDOW_RESIZED, id(window), 320, 240));
                push(buffer -> buffer.writeWindowEvent(
                        SdlEventType.WINDOW_RESIZED, id(window), 320, 240));
            });

            assertEquals(1L, count(events, BackendEvent.Resized.class),
                    () -> "expected one resize, got " + names(events));
        });
    }

    @Test
    @DisplayName("an event for an unknown window is dropped rather than guessed at")
    void unknownWindowIsIgnored() {
        withBackend((backend, window) -> {
            var events = pump(backend, sink -> push(buffer -> buffer.writeWheel(
                    Integer.MAX_VALUE, 0f, 1f, SdlWheelDirection.NORMAL, 0f, 0f)));

            assertFalse(events.stream().anyMatch(BackendEvent.PointerWheel.class::isInstance),
                    () -> "a wheel event for no window reached the sink: " + names(events));
        });
    }

    // --- the machinery ------------------------------------------------------

    /// Runs `body` against a backend with a window, under the dummy video driver.
    ///
    /// The driver and the frame rate are set as system properties because that is
    /// where the backend reads them, and restored afterwards because the test JVM
    /// is shared. Pacing is turned off explicitly: a paced loop holds frames back
    /// until the display could want them, which is correct and would make
    /// "was a frame emitted?" a question about timing.
    private static void withBackend(java.util.function.BiConsumer<Sdl3Backend, Sdl3Window> body) {
        var driver = System.getProperty(Sdl3Backend.VIDEO_DRIVER_PROPERTY);
        var rate = System.getProperty("goldberry.frame.rate");
        System.setProperty(Sdl3Backend.VIDEO_DRIVER_PROPERTY, "dummy");
        System.setProperty("goldberry.frame.rate", "0");
        try (var backend = new Sdl3Backend()) {
            var window = (Sdl3Window) backend.createWindow(WindowSpec.of("events", SIZE));
            body.accept(backend, window);
        } finally {
            restore(Sdl3Backend.VIDEO_DRIVER_PROPERTY, driver);
            restore("goldberry.frame.rate", rate);
        }
    }

    private static void restore(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

    /// Pushes what `setup` queues, then pumps once and collects what came out.
    private static List<BackendEvent> pump(Sdl3Backend backend, Consumer<EventSink> setup) {
        var events = new ArrayList<BackendEvent>();
        EventSink sink = events::add;
        setup.accept(sink);
        backend.pumpEvents(sink, Duration.ofMillis(50));
        return events;
    }

    /// Fabricates one event and pushes it onto SDL's queue.
    private static void push(Consumer<SdlEventBuffer> fill) {
        try (var buffer = new SdlEventBuffer()) {
            fill.accept(buffer);
            assertTrue(SdlVideo.get().push(buffer), "SDL refused the event");
        }
    }

    private static int id(BackendWindow window) {
        return ((Sdl3Window) window).handleId();
    }

    private static <T extends BackendEvent> T only(List<BackendEvent> events, Class<T> type) {
        var matching = events.stream().filter(type::isInstance).map(type::cast).toList();
        assertEquals(1, matching.size(),
                () -> "expected one " + type.getSimpleName() + ", got " + names(events));
        return matching.getFirst();
    }

    private static long count(List<BackendEvent> events, Class<? extends BackendEvent> type) {
        return events.stream().filter(type::isInstance).count();
    }

    private static List<String> names(List<BackendEvent> events) {
        return events.stream().map(event -> event.getClass().getSimpleName()).toList();
    }
}
