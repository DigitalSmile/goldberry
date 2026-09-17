package io.github.digitalsmile.goldberry.natives.sdl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.natives.NativeLibraryRequirement;
import io.github.digitalsmile.goldberry.natives.sdl.window.SdlIconImage;

/// `SDL_SetWindowIcon` and `SDL_AddSurfaceAlternateImage`, which is what
/// `docs/gaps.md` G40 is ([ADR-0351]).
///
/// A taskbar cannot be looked at from a test. What can be checked is the binding:
/// both symbols are on the export list and link, so a library built from this
/// checkout answers "can" rather than falling back to the platform's icon. And the
/// image type refuses the shapes SDL would read past the end of.
class SdlWindowIconTest {

    @Test
    @DisplayName("binds both calls a window icon needs, which is a link-time question")
    void bindsTheCalls() {
        NativeLibraryRequirement.enforce();

        assertTrue(
                SdlVideo.get().canSetWindowIcon(), "SDL_SetWindowIcon or SDL_AddSurfaceAlternateImage is not exported");
    }

    @Test
    @DisplayName("an icon is four bytes a pixel, tightly packed")
    void strideIsTight() {
        var image = new SdlIconImage(ByteBuffer.allocateDirect(16 * 16 * 4), 16, 16);

        assertEquals(64, image.stride());
    }

    @Test
    @DisplayName("a buffer too small for its size is refused before SDL reads past it")
    void tooSmall() {
        assertThrows(IllegalArgumentException.class, () -> new SdlIconImage(ByteBuffer.allocateDirect(15), 2, 2));
    }

    @Test
    @DisplayName("a heap buffer is refused, because it has no address to hand over")
    void heap() {
        assertThrows(IllegalArgumentException.class, () -> new SdlIconImage(ByteBuffer.allocate(16), 2, 2));
    }
}
