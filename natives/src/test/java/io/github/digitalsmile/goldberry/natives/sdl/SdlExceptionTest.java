package io.github.digitalsmile.goldberry.natives.sdl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SdlExceptionTest {

    @Test
    @DisplayName("names the C function and what SDL said")
    void carriesBothHalves() {
        var thrown = new SdlException("SDL_Init", "No available video device");

        assertEquals("SDL_Init", thrown.operation());
        assertEquals("No available video device", thrown.sdlError());
        assertTrue(thrown.getMessage().contains("SDL_Init"), thrown::getMessage);
        assertTrue(thrown.getMessage().contains("No available video device"), thrown::getMessage);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("says so when SDL failed without explaining itself")
    void handlesSilentFailure(String sdlError) {
        // SDL leaves the error empty more often than its documentation suggests,
        // and "SDL_Init failed: " with nothing after the colon reads like a bug
        // in Goldberry rather than a fact about SDL.
        var thrown = new SdlException("SDL_Init", sdlError);

        assertTrue(thrown.getMessage().contains("SDL_GetError() said nothing"), thrown::getMessage);
    }
}
