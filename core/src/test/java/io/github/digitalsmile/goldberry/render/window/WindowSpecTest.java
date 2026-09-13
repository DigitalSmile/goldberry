package io.github.digitalsmile.goldberry.render.window;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.render.model.LogicalSize;

/// That a window can be asked to open maximized, and that the ask is refused
/// where it would be silently dropped
/// (ADR-0221) — and the same for the floor a user may drag it down to
/// (ADR-0304).
class WindowSpecTest {

    private static final LogicalSize SIZE = LogicalSize.of(960, 640);

    @Test
    @DisplayName("a window is not maximized unless it says so")
    void notMaximizedByDefault() {
        assertFalse(
                WindowSpec.of("w", SIZE).maximized(),
                "an application that takes the whole screen without being asked is one the"
                        + " user has to undo before they can see anything else");
    }

    @Test
    @DisplayName("maximizing keeps the size, because that is what it restores to")
    void theSizeSurvives() {
        var spec = WindowSpec.of("w", SIZE).withMaximized(true);

        assertTrue(spec.maximized());
        assertEquals(
                SIZE,
                spec.size(),
                "the size is what the window returns to when it is un-maximized, so a"
                        + " maximized spec that forgot it would restore to nothing");
    }

    @Test
    @DisplayName("the three flags are independent of one another")
    void theWithersDoNotDisturbEachOther() {
        var spec = WindowSpec.of("w", SIZE).withMaximized(true).withDecorated(false);

        assertTrue(spec.maximized(), "withDecorated dropped the maximized flag");
        assertFalse(spec.decorated());
        assertTrue(spec.resizable());
        assertEquals("w", spec.title());
    }

    /// A fixed-size window has no maximized state to be in, and SDL **silently
    /// drops** the flag on one.
    ///
    /// Refused rather than warned about, because the two readings of a warning
    /// are opposite: the application gets a small window it asked to have filled,
    /// and the user cannot fix that from the desktop either — a window that
    /// cannot be resized has no maximize button.
    @Test
    @DisplayName("a window that cannot be resized cannot be maximized either")
    void maximizedNeedsResizable() {
        var thrown = assertThrows(
                IllegalArgumentException.class,
                () -> WindowSpec.of("w", SIZE).withResizable(false).withMaximized(true));

        assertTrue(thrown.getMessage().contains("maximized"), thrown.getMessage());
    }

    @Test
    @DisplayName("and the same the other way round, whichever wither is called last")
    void theOrderOfTheWithersDoesNotMatter() {
        assertThrows(
                IllegalArgumentException.class,
                () -> WindowSpec.of("w", SIZE).withMaximized(true).withResizable(false));
    }

    @Test
    @DisplayName("a window still needs a size it could be restored to")
    void anEmptySizeIsStillRefused() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new WindowSpec("w", LogicalSize.of(0, 0), WindowSpec.NO_MINIMUM, true, true, true));
    }

    @Test
    @DisplayName("a window has no minimum size unless it asks for one")
    void noMinimumByDefault() {
        var spec = WindowSpec.of("w", SIZE);

        assertEquals(WindowSpec.NO_MINIMUM, spec.minimumSize());
        assertFalse(spec.hasMinimumSize(), "a toolkit that does not know what the window holds has no floor to pick");
    }

    @Test
    @DisplayName("a minimum survives the other withers")
    void theMinimumIsNotDroppedByTheOthers() {
        var spec = WindowSpec.of("w", SIZE)
                .withMinimumSize(LogicalSize.of(400, 300))
                .withMaximized(true)
                .withDecorated(false);

        assertEquals(LogicalSize.of(400, 300), spec.minimumSize());
        assertTrue(spec.hasMinimumSize());
        assertTrue(spec.maximized());
    }

    @Test
    @DisplayName("a zero on one axis constrains only the other")
    void oneAxisIsEnough() {
        var spec = WindowSpec.of("w", SIZE).withMinimumSize(LogicalSize.of(480, 0));

        assertEquals(LogicalSize.of(480, 0), spec.minimumSize());
        assertFalse(
                spec.hasMinimumSize(),
                "`isEmpty` is per-record rather than per-axis, and the backend applies each axis on its own");
    }

    /// Refused rather than reconciled, because the two ways out are opposite:
    /// growing the window to its floor is not the size the application asked for,
    /// and opening below the floor gives a window the user can never drag back to
    /// the size it started at.
    @Test
    @DisplayName("a minimum larger than the opening size is refused")
    void aMinimumMustFitTheSize() {
        var thrown = assertThrows(
                IllegalArgumentException.class,
                () -> WindowSpec.of("w", SIZE).withMinimumSize(LogicalSize.of(1200, 300)));

        assertTrue(thrown.getMessage().contains("minimum"), thrown.getMessage());
        // Either axis, not both.
        assertThrows(
                IllegalArgumentException.class,
                () -> WindowSpec.of("w", SIZE).withMinimumSize(LogicalSize.of(400, 900)));
    }

    @Test
    @DisplayName("a window that cannot be resized has no minimum to stop at")
    void aMinimumNeedsResizable() {
        var thrown = assertThrows(
                IllegalArgumentException.class,
                () -> WindowSpec.of("w", SIZE).withResizable(false).withMinimumSize(LogicalSize.of(400, 300)));

        assertTrue(thrown.getMessage().contains("minimum"), thrown.getMessage());
        // And whichever wither is called last, as with maximized.
        assertThrows(
                IllegalArgumentException.class,
                () -> WindowSpec.of("w", SIZE)
                        .withMinimumSize(LogicalSize.of(400, 300))
                        .withResizable(false));
    }
}
