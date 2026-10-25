package dev.goldberry.render.window;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/// What each present mode falls back to, and which one waits for the display.
@DisplayName("a present mode")
class PresentModeTest {

    @ParameterizedTest
    @EnumSource(PresentMode.class)
    @DisplayName("tries itself first and VSYNC, which every window has, last")
    void fallbacksEndInVsync(PresentMode mode) {
        var fallbacks = mode.fallbacks();
        assertEquals(mode, fallbacks.getFirst());
        assertEquals(PresentMode.VSYNC, fallbacks.getLast());
        assertEquals(fallbacks.size(), fallbacks.stream().distinct().count(), "no mode twice");
    }

    @Test
    @DisplayName("falls back to the other mode that does not wait before the one that does")
    void nearestFirst() {
        assertEquals(
                List.of(PresentMode.MAILBOX, PresentMode.IMMEDIATE, PresentMode.VSYNC),
                PresentMode.MAILBOX.fallbacks());
        assertEquals(
                List.of(PresentMode.IMMEDIATE, PresentMode.MAILBOX, PresentMode.VSYNC),
                PresentMode.IMMEDIATE.fallbacks());
        assertEquals(List.of(PresentMode.VSYNC), PresentMode.VSYNC.fallbacks());
    }

    @Test
    @DisplayName("waits for the display in VSYNC alone")
    void waits() {
        assertTrue(PresentMode.VSYNC.waitsForTheDisplay());
        assertFalse(PresentMode.MAILBOX.waitsForTheDisplay());
        assertFalse(PresentMode.IMMEDIATE.waitsForTheDisplay());
    }
}
