package io.github.digitalsmile.goldberry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

/// The floor a user may drag a window down to — [ADR-0304].
///
/// The constraint is the window manager's on a real backend, so what is asserted
/// here is the headless one standing in for it: a drag below the floor stops at
/// the floor rather than being refused, which is what a user sees, and the
/// application is told the size it was actually given.
class MinimumSizeTest {

    private static final LogicalSize OPENING = LogicalSize.of(800, 600);
    private static final LogicalSize FLOOR = LogicalSize.of(400, 300);

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

    private static Window open(LogicalSize minimum) {
        return Window.open(WindowSpec.of("floor", OPENING).withMinimumSize(minimum));
    }

    private HeadlessWindow platformWindow() {
        return (HeadlessWindow) backend.windows().getFirst();
    }

    @Test
    @Timeout(10)
    @DisplayName("a window opens with no minimum unless one is asked for")
    void noneByDefault() {
        var window = Window.open(WindowSpec.of("floor", OPENING));

        assertEquals(WindowSpec.NO_MINIMUM, window.minimumSize());
    }

    @Test
    @Timeout(10)
    @DisplayName("a minimum in the spec reaches the platform window")
    void theSpecIsApplied() {
        var window = open(FLOOR);

        assertEquals(FLOOR, window.minimumSize());
        assertEquals(FLOOR, platformWindow().minimumSize());
    }

    @Test
    @Timeout(10)
    @DisplayName("dragging smaller stops at the floor instead of going through it")
    void aResizeIsClamped() {
        open(FLOOR);
        var platform = platformWindow();

        platform.resizeTo(LogicalSize.of(100, 100));

        // Clamped rather than refused: a window manager stops the pointer at the
        // edge, so the application is told about a 400x300 window and lays out
        // for one. Refusing would leave the drag and the window disagreeing.
        assertEquals(FLOOR, platform.size());
    }

    @Test
    @Timeout(10)
    @DisplayName("each axis stops on its own")
    void theAxesAreIndependent() {
        open(LogicalSize.of(400, 0));
        var platform = platformWindow();

        platform.resizeTo(LogicalSize.of(100, 50));

        // A window that cares about its width alone says so, and gets to be as
        // short as the user likes.
        assertEquals(LogicalSize.of(400, 50), platform.size());
    }

    @Test
    @Timeout(10)
    @DisplayName("a resize above the floor is untouched")
    void aLargerResizePassesThrough() {
        open(FLOOR);
        var platform = platformWindow();

        platform.resizeTo(LogicalSize.of(1000, 900));

        assertEquals(LogicalSize.of(1000, 900), platform.size());
    }

    @Test
    @Timeout(10)
    @DisplayName("the floor can be set after the window is open")
    void itCanChangeLater() {
        var window = Window.open(WindowSpec.of("floor", OPENING));

        window.minimumSize(FLOOR);

        assertEquals(FLOOR, window.minimumSize());
        platformWindow().resizeTo(LogicalSize.of(10, 10));
        assertEquals(FLOOR, platformWindow().size());
    }

    @Test
    @Timeout(10)
    @DisplayName("a zero size later takes the floor away again")
    void itCanBeRemoved() {
        var window = open(FLOOR);

        window.minimumSize(WindowSpec.NO_MINIMUM);

        platformWindow().resizeTo(LogicalSize.of(10, 10));
        assertEquals(LogicalSize.of(10, 10), platformWindow().size());
    }

    /// Setting a floor does **not** grow a window that is already under it. A
    /// floor is about what the user may do next; resizing the window out from
    /// under whoever is looking at it is a different thing nobody asked for.
    @Test
    @Timeout(10)
    @DisplayName("a floor set late leaves the current size alone")
    void aLateFloorDoesNotGrowTheWindow() {
        var window = Window.open(WindowSpec.of("floor", OPENING));
        platformWindow().resizeTo(LogicalSize.of(200, 150));

        window.minimumSize(FLOOR);

        assertEquals(LogicalSize.of(200, 150), window.size());
    }

    @Test
    @Timeout(10)
    @DisplayName("a window cannot open smaller than the floor it declares")
    void theSpecRefusesTheContradiction() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Window.open(
                        WindowSpec.of("floor", LogicalSize.of(300, 200)).withMinimumSize(FLOOR)));
    }
}
