package dev.goldberry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("a text scale")
class TextScalesTest {

    @Test
    @DisplayName("is clamped to 90-150%, and an application's unreadable value is 1")
    void clamps() {
        assertEquals(1.25, TextScales.clamp(1.25), 1e-9);
        assertEquals(0.9, TextScales.clamp(0.5), 1e-9);
        assertEquals(1.5, TextScales.clamp(4), 1e-9);
        assertEquals(1, TextScales.clamp(Double.NaN), 1e-9, "a settings file must not stop a window");
    }

    @Test
    @DisplayName("set at run time refuses what is not finite")
    void refusesAtRunTime() {
        assertEquals(1.5, TextScales.requireFinite(2), 1e-9);
        assertThrows(IllegalArgumentException.class, () -> TextScales.requireFinite(Double.NaN));
    }
}
