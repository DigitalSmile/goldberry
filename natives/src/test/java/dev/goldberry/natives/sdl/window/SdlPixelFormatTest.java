package dev.goldberry.natives.sdl.window;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SdlPixelFormatTest {

    @Test
    @DisplayName("reads back the two formats a window surface can have, and nothing else")
    void of() {
        assertEquals(Optional.of(SdlPixelFormat.ARGB8888), SdlPixelFormat.of(0x16362004));
        assertEquals(Optional.of(SdlPixelFormat.XRGB8888), SdlPixelFormat.of(0x16161804));
        assertEquals(Optional.empty(), SdlPixelFormat.of(0));
        assertFalse(SdlPixelFormat.isBlittable(0));
    }

    @Test
    @DisplayName("only ARGB has an alpha channel to be see-through with")
    void alpha() {
        assertTrue(SdlPixelFormat.ARGB8888.hasAlpha());
        assertFalse(SdlPixelFormat.XRGB8888.hasAlpha());
    }
}
