package dev.goldberry.paint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The two builders a box's presence is read back through: whether it is
/// elevated, and its aspect ratio.
class BoxPresenceTest {

    @Test
    @DisplayName("a box is elevated only when it says so")
    void elevated() {
        assertFalse(Box.of().elevated());
        assertTrue(Box.of().elevated(true).elevated());
    }

    @Test
    @DisplayName("an aspect ratio set is read back, and auto is NaN")
    void aspectRatio() {
        assertTrue(Double.isNaN(Box.of().aspectRatio()));
        assertEquals(1.5, Box.of().aspectRatio(1.5).aspectRatio());
    }
}
