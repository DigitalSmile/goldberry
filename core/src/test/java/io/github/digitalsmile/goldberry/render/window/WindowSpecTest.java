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
/// ([ADR-0221](../../../../../../book/src/adr/0221-a-window-may-open-maximized.md)).
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
        assertThrows(IllegalArgumentException.class, () -> new WindowSpec("w", LogicalSize.of(0, 0), true, true, true));
    }
}
