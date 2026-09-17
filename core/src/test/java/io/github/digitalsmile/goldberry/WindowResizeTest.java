package io.github.digitalsmile.goldberry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import io.github.digitalsmile.goldberry.render.backend.headless.HeadlessBackend;
import io.github.digitalsmile.goldberry.render.backend.headless.HeadlessWindow;
import io.github.digitalsmile.goldberry.render.model.DisplayScale;
import io.github.digitalsmile.goldberry.render.model.LogicalSize;
import io.github.digitalsmile.goldberry.render.window.WindowSpec;

/// [Window#resize] — a window resized from outside, which is what a frame loop
/// is measured under ([ADR-0342]).
///
/// The headless backend plays the window manager: it clamps the request to the
/// floor and delivers a `Resized`, exactly as it does for a test that drags.
class WindowResizeTest {

    private static final LogicalSize OPENING = LogicalSize.of(400, 300);

    private HeadlessBackend backend;

    @BeforeEach
    void install() {
        backend = new HeadlessBackend(new DisplayScale(1f));
        GoldberryTestAccess.install(backend);
    }

    @AfterEach
    void shutdown() {
        Goldberry.shutdown();
    }

    private HeadlessWindow platformWindow() {
        return (HeadlessWindow) backend.windows().getFirst();
    }

    @Test
    @Timeout(10)
    @DisplayName("a request reaches the platform window, and lands when the event does")
    void reachesThePlatform() {
        var window = Window.open(WindowSpec.of("resize", OPENING));

        window.resize(LogicalSize.of(500, 350));

        assertEquals(OPENING, window.size(), "not yet: the window manager has not answered");
        backend.pumpEvents(event -> {}, Duration.ZERO);
        assertEquals(LogicalSize.of(500, 350), platformWindow().size());
        assertEquals(LogicalSize.of(500, 350), window.size());
    }

    @Test
    @Timeout(10)
    @DisplayName("the window manager clamps it to the floor, like a drag")
    void clampedToTheFloor() {
        var window = Window.open(WindowSpec.of("resize", OPENING).withMinimumSize(LogicalSize.of(300, 200)));

        window.resize(LogicalSize.of(100, 100));
        backend.pumpEvents(event -> {}, Duration.ZERO);

        assertEquals(LogicalSize.of(300, 200), window.size());
    }

    @Test
    @Timeout(10)
    @DisplayName("what the manager decided arrives through onResize, and a frame follows")
    void arrivesThroughTheHandler() {
        var window = Window.open(WindowSpec.of("resize", OPENING));
        List<LogicalSize> resizes = new ArrayList<>();
        var painted = new int[1];
        window.onResize(resizes::add);
        window.onPaint(frame -> {
            painted[0]++;
            if (painted[0] == 1) {
                window.resize(LogicalSize.of(401, 301));
            } else {
                Goldberry.stop();
            }
        });

        Goldberry.run();

        assertEquals(List.of(LogicalSize.of(401, 301)), resizes);
        assertTrue(painted[0] >= 2, "the resize did not ask for a frame");
    }

    @Test
    @Timeout(10)
    @DisplayName("a size with nothing in it is refused")
    void refusesNothing() {
        var window = Window.open(WindowSpec.of("resize", OPENING));

        assertThrows(IllegalArgumentException.class, () -> window.resize(LogicalSize.of(0, 300)));
        assertThrows(IllegalArgumentException.class, () -> window.resize(LogicalSize.of(400, -1)));
        assertEquals(OPENING, window.size());
    }

    @Test
    @Timeout(10)
    @DisplayName("a closed window ignores the request")
    void closedIsIgnored() {
        var window = Window.open(WindowSpec.of("resize", OPENING));
        window.close();

        window.resize(LogicalSize.of(500, 350));
    }
}
