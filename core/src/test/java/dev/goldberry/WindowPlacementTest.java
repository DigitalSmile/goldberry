package dev.goldberry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import dev.goldberry.render.backend.headless.HeadlessBackend;
import dev.goldberry.render.backend.headless.HeadlessWindow;
import dev.goldberry.render.display.Display;
import dev.goldberry.render.model.DisplayScale;
import dev.goldberry.render.model.LogicalPoint;
import dev.goldberry.render.model.LogicalRect;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.render.window.Attention;
import dev.goldberry.render.window.Ownership;
import dev.goldberry.render.window.WindowSpec;

/// Where a window opens, where it is, which display it is on, and the bounds
/// it returns to — what an application saves on close to open where the user
/// left it.
class WindowPlacementTest {

    private static final Display LAPTOP = new Display(
            1, "Built-in", LogicalRect.of(0, 0, 1920, 1080), LogicalRect.of(0, 0, 1920, 1040), DisplayScale.ONE, true);

    private static final Display MONITOR = new Display(
            2,
            "DELL U2720Q",
            LogicalRect.of(1920, 0, 2560, 1440),
            LogicalRect.of(1920, 0, 2560, 1440),
            DisplayScale.ONE,
            false);

    private static final LogicalSize SIZE = LogicalSize.of(800, 600);

    private HeadlessBackend backend;

    @BeforeEach
    void install() {
        RendererRequirement.enforce();
        backend = new HeadlessBackend().displays(List.of(LAPTOP, MONITOR));
        GoldberryTestAccess.install(backend);
    }

    @AfterEach
    void shutdown() {
        Goldberry.shutdown();
    }

    private HeadlessWindow platform(Window window) {
        return (HeadlessWindow) window.backendWindow();
    }

    /// Delivers what has been queued, then closes the window.
    private void settle(Window window) {
        platform(window).requestClose();
        Goldberry.run();
    }

    @Test
    @Timeout(10)
    @DisplayName("a window opens where it was asked, on the display that is there")
    void opensAtItsPosition() {
        var window = Window.open(WindowSpec.of("placed", SIZE).withPosition(new LogicalPoint(2100, 300)));

        assertEquals(Optional.of(new LogicalPoint(2100, 300)), window.position());
        assertEquals("DELL U2720Q", window.display().orElseThrow().name());
        assertEquals(List.of(LAPTOP, MONITOR), window.displays());
    }

    @Test
    @Timeout(10)
    @DisplayName("a window remembered on a monitor that has gone opens centred on the display it named")
    void theMonitorHasGone() {
        backend.displays(List.of(LAPTOP));

        var window = Window.open(WindowSpec.of("placed", SIZE)
                .withPosition(new LogicalPoint(2100, 300))
                .withDisplay("Built-in"));

        assertEquals(Optional.of(new LogicalPoint(560, 220)), window.position());
    }

    @Test
    @Timeout(10)
    @DisplayName("a move is clamped onto a display, clear of its panel")
    void moveIsClamped() {
        var window = Window.open(WindowSpec.of("placed", SIZE));

        assertTrue(window.move(new LogicalPoint(100, 900)));

        assertEquals(Optional.of(new LogicalPoint(100, 440)), window.position());
    }

    @Test
    @Timeout(10)
    @DisplayName("where the desktop places windows itself, there is no position, no move and no normal origin")
    void waylandPlacesItself() {
        backend.placesWindows(false);
        var window = Window.open(WindowSpec.of("placed", SIZE).withPosition(new LogicalPoint(2100, 300)));
        var platform = platform(window);

        assertEquals(Optional.empty(), window.position());
        assertFalse(window.move(new LogicalPoint(10, 10)));
        assertEquals(LogicalPoint.ZERO, platform.position().orElseThrow(), "nothing placed it");

        platform.resizeTo(LogicalSize.of(900, 700));
        settle(window);

        assertEquals(Optional.empty(), window.normalBounds());
        assertEquals(LogicalSize.of(900, 700), window.normalSize(), "the size is still everybody's to know");
    }

    @Test
    @Timeout(10)
    @DisplayName("the normal bounds follow a move and a resize, and are still answered once it has closed")
    void normalFollowsTheUser() {
        var window = Window.open(WindowSpec.of("placed", SIZE).withPosition(new LogicalPoint(100, 100)));
        var platform = platform(window);

        platform.moveTo(new LogicalPoint(50, 60));
        platform.resizeTo(LogicalSize.of(900, 700));
        settle(window);

        assertEquals(Optional.of(LogicalRect.of(50, 60, 900, 700)), window.normalBounds());
    }

    @Test
    @Timeout(10)
    @DisplayName("maximizing does not overwrite the bounds the window restores to")
    void maximizedKeepsTheNormalBounds() {
        var window = Window.open(WindowSpec.of("placed", SIZE).withPosition(new LogicalPoint(100, 100)));
        var platform = platform(window);

        platform.reportMaximized(true);
        platform.moveTo(LogicalPoint.ZERO);
        platform.resizeTo(LogicalSize.of(1920, 1040));
        settle(window);

        assertTrue(window.isMaximized());
        assertEquals(Optional.of(LogicalRect.of(100, 100, 800, 600)), window.normalBounds());
    }

    @Test
    @Timeout(10)
    @DisplayName("a window opening maximized keeps its asked-for size even if the size arrives before the state")
    void openingMaximized() {
        var window = Window.open(WindowSpec.of("placed", SIZE)
                .withPosition(new LogicalPoint(100, 100))
                .withMaximized(true));
        var platform = platform(window);

        platform.resizeTo(LogicalSize.of(1920, 1040));
        platform.reportMaximized(true);
        settle(window);

        assertEquals(SIZE, window.normalSize());
    }

    @Test
    @Timeout(10)
    @DisplayName("an owned window with nowhere of its own opens centred on its owner, and belongs to it")
    void ownedIsCentredOnItsOwner() {
        var owner = Window.open(WindowSpec.of("owner", SIZE).withPosition(new LogicalPoint(100, 100)));

        var owned = Window.open(WindowSpec.of("owned", LogicalSize.of(400, 300)).withOwnership(Ownership.MODAL), owner);

        assertEquals(Optional.of(new LogicalPoint(300, 250)), owned.position());
        assertEquals(Optional.of(platform(owner)), platform(owned).parentWindow());
        assertTrue(platform(owned).isModal());
    }

    @Test
    @Timeout(10)
    @DisplayName("Window.open refuses a window that asks for an owner, having none to give it")
    void openRefusesAnOwner() {
        var spec = WindowSpec.of("owned", SIZE).withOwnership(Ownership.OWNED);

        assertThrows(IllegalArgumentException.class, () -> Window.open(spec));
    }

    @Test
    @Timeout(10)
    @DisplayName("attention is asked of the platform, withdrawn, and refused once the window is gone")
    void attention() {
        var window = Window.open(WindowSpec.of("placed", SIZE));
        var platform = platform(window);

        assertTrue(window.requestAttention(Attention.UNTIL_FOCUSED));
        assertEquals(Optional.of(Attention.UNTIL_FOCUSED), platform.attention());
        assertTrue(window.cancelAttention());
        assertEquals(Optional.empty(), platform.attention());
        assertTrue(window.raise());
        assertEquals(1, platform.raiseCount());

        window.close();

        assertFalse(window.requestAttention(Attention.BRIEFLY));
        assertEquals(List.of(Attention.UNTIL_FOCUSED), platform.attentionRequests());
    }
}
