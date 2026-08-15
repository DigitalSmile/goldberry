package io.github.digitalsmile.goldberry.backend.headless;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.backend.BackendEvent;
import io.github.digitalsmile.goldberry.backend.BackendException;
import io.github.digitalsmile.goldberry.backend.DamageRect;
import io.github.digitalsmile.goldberry.backend.DisplayScale;
import io.github.digitalsmile.goldberry.backend.LogicalSize;
import io.github.digitalsmile.goldberry.backend.PhysicalSize;
import io.github.digitalsmile.goldberry.backend.PixelBuffer;
import io.github.digitalsmile.goldberry.backend.PixelFormat;
import io.github.digitalsmile.goldberry.backend.WindowSpec;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// The SPI's rules, enforced against the one backend that needs no platform.
///
/// These are not tests of the headless backend so much as tests of the interface
/// contract: a real backend that breaks one of these breaks the same assertion.
class HeadlessBackendTest {

    /// 150%, not 100%. Every HiDPI bug hides at 100%, so the default fixture is
    /// the scale that would expose one.
    private static final DisplayScale SCALE = new DisplayScale(1.5f);

    private static final WindowSpec SPEC =
            WindowSpec.of("Goldberry", LogicalSize.of(1280f, 720f));

    private HeadlessBackend backend;

    @BeforeEach
    void createBackend() {
        backend = new HeadlessBackend(SCALE);
    }

    @AfterEach
    void closeBackend() {
        backend.close();
    }

    @Test
    @DisplayName("a window resolves its physical size through the display scale")
    void windowResolvesPhysicalSize() {
        var window = backend.createWindow(SPEC);

        assertEquals(LogicalSize.of(1280f, 720f), window.size());
        assertEquals(new PhysicalSize(1920, 1080), window.physicalSize());
        assertEquals(SCALE, window.scale());
    }

    @Test
    @DisplayName("windows are listed in creation order")
    void listsWindows() {
        var first = backend.createWindow(SPEC);
        var second = backend.createWindow(SPEC);

        assertEquals(List.of(first, second), backend.windows());
    }

    @Test
    @DisplayName("a closed window leaves the list and stays closed")
    void closedWindowIsForgotten() {
        var window = backend.createWindow(SPEC);

        window.close();

        assertFalse(window.isOpen());
        assertEquals(List.of(), backend.windows());
        // Idempotent: shutdown paths close things twice and should not care.
        window.close();
    }

    @Test
    @DisplayName("presenting a frame of the wrong size is refused")
    void refusesStaleFrame() {
        var window = backend.createWindow(SPEC);
        var stale = PixelBuffer.allocate(new PhysicalSize(1280, 720), PixelFormat.BGRA32_PREMULTIPLIED);

        // 1280x720 is the LOGICAL size -- the classic mistake, which at 150% is a
        // frame two thirds the size of the window.
        var thrown = assertThrows(
                IllegalArgumentException.class, () -> window.present(stale, List.of()));

        assertTrue(thrown.getMessage().contains("1920x1080"), thrown::getMessage);
    }

    @Test
    @DisplayName("damage outside the frame is refused")
    void refusesOutOfBoundsDamage() {
        var window = backend.createWindow(SPEC);
        var frame = PixelBuffer.allocate(window.physicalSize(), PixelFormat.BGRA32_PREMULTIPLIED);

        assertThrows(
                IllegalArgumentException.class,
                () -> window.present(frame, List.of(new DamageRect(0, 0, 1921, 1080))));
    }

    @Test
    @DisplayName("a presented frame is kept, not aliased")
    void keepsFrameContents() {
        var window = (HeadlessWindow) backend.createWindow(SPEC);
        var frame = PixelBuffer.allocate(window.physicalSize(), PixelFormat.BGRA32_PREMULTIPLIED);
        frame.pixels().put(0, (byte) 0x7f);

        window.present(frame, List.of(DamageRect.all(window.physicalSize())));
        // The SPI lends the buffer for the call only; Blend2D reuses it straight
        // away. A backend that kept the reference would report this overwrite.
        frame.pixels().put(0, (byte) 0x00);

        assertEquals((byte) 0x7f, window.lastFrame().orElseThrow().pixels().get(0));
        assertEquals(1, window.presentCount());
    }

    @Test
    @DisplayName("frame requests coalesce")
    void frameRequestsCoalesce() {
        var window = (HeadlessWindow) backend.createWindow(SPEC);

        window.requestFrame();
        window.requestFrame();
        window.requestFrame();

        assertEquals(1, backend.pendingEventCount(), "asking three times must not draw three times");
        assertTrue(window.isFramePending());
    }

    @Test
    @DisplayName("a frame request is satisfied by its event being delivered")
    void deliveryClearsTheRequest() {
        var window = (HeadlessWindow) backend.createWindow(SPEC);
        window.requestFrame();

        backend.pumpEvents(event -> {}, Duration.ZERO);

        assertFalse(window.isFramePending());
        window.requestFrame();
        assertTrue(window.isFramePending(), "a new frame can be requested once the last was delivered");
    }

    @Test
    @DisplayName("a repaint asked for while painting survives the present that follows")
    void repaintDuringPaintSurvives() {
        // The bug this exists for: present() used to clear the pending flag, so a
        // painter that asked for the next frame while drawing this one had its
        // request wiped by the present immediately after. Every animation stopped
        // after exactly one frame, and nothing in the SPI's own tests noticed
        // because they never repainted from inside a frame.
        var window = (HeadlessWindow) backend.createWindow(SPEC);
        var painted = new int[1];
        window.requestFrame();

        for (var pump = 0; pump < 3; pump++) {
            backend.pumpEvents(event -> {
                if (event instanceof BackendEvent.FrameDue frame) {
                    var target = (HeadlessWindow) frame.window();
                    target.present(
                            PixelBuffer.allocate(target.physicalSize(), PixelFormat.BGRA32_PREMULTIPLIED),
                            List.of());
                    painted[0]++;
                    target.requestFrame();
                }
            }, Duration.ZERO);
        }

        assertEquals(3, painted[0], "a self-scheduling repaint must keep producing frames");
    }

    @Test
    @DisplayName("events arrive in order, once each")
    void deliversEventsInOrder() {
        var window = (HeadlessWindow) backend.createWindow(SPEC);
        var seen = new ArrayList<BackendEvent>();

        window.expose();
        window.requestClose();

        var delivered = backend.pumpEvents(seen::add, Duration.ZERO);

        assertEquals(2, delivered);
        assertInstanceOf(BackendEvent.Exposed.class, seen.get(0));
        assertInstanceOf(BackendEvent.CloseRequested.class, seen.get(1));
        assertEquals(0, backend.pumpEvents(seen::add, Duration.ZERO), "events are delivered once");
    }

    @Test
    @DisplayName("a resize reports both sizes")
    void resizeCarriesBothSizes() {
        var window = (HeadlessWindow) backend.createWindow(SPEC);
        var seen = new ArrayList<BackendEvent>();

        window.resizeTo(LogicalSize.of(800f, 600f));
        backend.pumpEvents(seen::add, Duration.ZERO);

        var resized = assertInstanceOf(BackendEvent.Resized.class, seen.getFirst());
        assertEquals(LogicalSize.of(800f, 600f), resized.size());
        assertEquals(new PhysicalSize(1200, 900), resized.physicalSize());
    }

    @Test
    @DisplayName("a scale change keeps the logical size and moves the physical one")
    void scaleChangeKeepsLogicalSize() {
        var window = (HeadlessWindow) backend.createWindow(SPEC);
        var seen = new ArrayList<BackendEvent>();

        // Dragging the window from a 150% laptop panel to a 100% monitor.
        window.rescaleTo(DisplayScale.ONE);
        backend.pumpEvents(seen::add, Duration.ZERO);

        var rescaled = assertInstanceOf(BackendEvent.ScaleChanged.class, seen.getFirst());
        assertEquals(LogicalSize.of(1280f, 720f), window.size(), "logical layout must not move");
        assertEquals(new PhysicalSize(1280, 720), rescaled.physicalSize());
    }

    @Test
    @DisplayName("a sink that posts does not extend the pump it is inside")
    void selfPostingSinkTerminates() {
        var window = (HeadlessWindow) backend.createWindow(SPEC);
        window.expose();

        // Without draining into a batch first, this is an infinite loop.
        var delivered = backend.pumpEvents(event -> window.requestFrame(), Duration.ZERO);

        assertEquals(1, delivered);
        assertEquals(1, backend.pendingEventCount(), "the new event waits for the next pump");
    }

    @Test
    @DisplayName("a throwing sink propagates and leaves the rest queued")
    void throwingSinkPropagates() {
        var window = (HeadlessWindow) backend.createWindow(SPEC);
        window.expose();
        window.requestClose();

        assertThrows(
                IllegalStateException.class,
                () -> backend.pumpEvents(
                        event -> {
                            throw new IllegalStateException("handler failed");
                        },
                        Duration.ZERO));
    }

    @Test
    @DisplayName("closing a window drops the events queued for it")
    void closedWindowEventsAreDropped() {
        var window = (HeadlessWindow) backend.createWindow(SPEC);
        window.expose();

        window.close();

        assertEquals(0, backend.pendingEventCount(), "an event for a dead window has nowhere to go");
    }

    @Test
    @Timeout(10)
    @DisplayName("pumpEvents returns when the timeout elapses")
    void pumpTimesOut() {
        var start = System.nanoTime();

        var delivered = backend.pumpEvents(event -> {}, Duration.ofMillis(50));

        assertEquals(0, delivered);
        assertTrue(
                System.nanoTime() - start >= Duration.ofMillis(40).toNanos(),
                "it should have waited rather than spun");
    }

    @Test
    @Timeout(10)
    @DisplayName("wakeup releases a waiting pump from another thread")
    void wakeupCrossesThreads() throws Exception {
        var started = new CountDownLatch(1);

        var waker = new Thread(() -> {
            try {
                started.await(5, TimeUnit.SECONDS);
                // The one call the SPI permits from off the UI thread.
                backend.wakeup();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "waker");
        waker.start();

        started.countDown();
        var delivered = backend.pumpEvents(event -> {}, Duration.ofSeconds(30));
        waker.join();

        assertEquals(0, delivered, "a wakeup carries no event of its own");
    }

    @Test
    @Timeout(10)
    @DisplayName("everything except wakeup refuses another thread")
    void otherThreadsAreRefused() throws Exception {
        var window = backend.createWindow(SPEC);
        var failures = new ArrayList<Class<?>>();

        // AppKit requires window calls on the first thread, so the SPI requires
        // it everywhere -- a rule that only bites on macOS is a rule that gets
        // discovered at release time.
        var other = new Thread(() -> {
            failures.add(assertThrows(BackendException.class, () -> backend.createWindow(SPEC)).getClass());
            failures.add(assertThrows(BackendException.class, window::title).getClass());
            failures.add(assertThrows(BackendException.class, () -> backend.windows()).getClass());
        }, "off-ui");
        other.start();
        other.join();

        assertEquals(3, failures.size());
    }

    @Test
    @DisplayName("a closed backend refuses further work")
    void closedBackendRefuses() {
        backend.close();

        assertTrue(backend.isClosed());
        assertThrows(BackendException.class, () -> backend.createWindow(SPEC));
    }

    @Test
    @DisplayName("closing the backend closes its windows")
    void closingBackendClosesWindows() {
        var window = backend.createWindow(SPEC);

        backend.close();

        assertFalse(window.isOpen());
    }

    @Test
    @DisplayName("a closed window refuses to present")
    void closedWindowRefusesPresent() {
        var window = backend.createWindow(SPEC);
        var frame = PixelBuffer.allocate(window.physicalSize(), PixelFormat.BGRA32_PREMULTIPLIED);
        window.close();

        assertThrows(BackendException.class, () -> window.present(frame, List.of()));
    }

    @Test
    @DisplayName("the backend names itself")
    void namesItself() {
        assertEquals("headless", backend.name());
    }

    @Test
    @DisplayName("the title round-trips")
    void titleRoundTrips() {
        var window = backend.createWindow(SPEC);

        window.setTitle("Goldberry — untitled");

        assertEquals("Goldberry — untitled", window.title());
    }
}
