package dev.goldberry.natives.sdl;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.natives.NativeLibraryRequirement;

/// `SDL_OpenURL` — what a `link` opens an `href` through.
///
/// A URL cannot be opened on a CI runner without a desktop, and a test that
/// tried would leave a browser behind. What **can** be checked is the binding's
/// half: that the symbol is on the export list and links, so a library built
/// from this checkout answers "available" rather than the "built before the
/// export" fallback. Nothing here calls it: on Linux SDL hands even an empty
/// string to `xdg-open` and answers true once the process has been started,
/// so a "refuses nonsense" test would open nonsense.
class SdlOpenUrlTest {

    @BeforeAll
    static void requireNativeLibrary() {
        NativeLibraryRequirement.enforce();
    }

    @Test
    @DisplayName("binds SDL_OpenURL, which is a link-time question")
    void bindsTheCall() {
        assertTrue(Sdl.get().canOpenUrl(), "SDL_OpenURL is not exported by this libgoldberry");
    }
}
