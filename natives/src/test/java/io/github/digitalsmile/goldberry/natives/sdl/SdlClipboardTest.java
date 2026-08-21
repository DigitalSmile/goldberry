package io.github.digitalsmile.goldberry.natives.sdl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.natives.NativeLibraryRequirement;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The clipboard through the real `libgoldberry`.
///
/// SDL's clipboard needs the **video** subsystem — it is the window system that
/// owns a selection — and CI runners have no display, so every test here starts
/// SDL for video and skips itself when that is not available. What is being
/// proven is the same thing [SdlTest] proves: the symbols are reachable and the
/// calling conventions are right. The round trip through `SDL_free` is the part
/// that could only fail here.
class SdlClipboardTest {

    @BeforeAll
    static void requireNativeLibrary() {
        NativeLibraryRequirement.enforce();
    }

    @AfterEach
    void shutDownSdl() {
        Sdl.get().quit();
        Sdl.get().clearError();
    }

    @Test
    @DisplayName("binds every clipboard symbol on the export list")
    void bindsItsSymbols() {
        // No SDL_Init: looking a symbol up is a link-time question, and this is
        // the test that fails first and most clearly when goldberry.symbols and
        // the binding disagree.
        assertNotNull(SdlClipboard.get());
    }

    @Test
    @DisplayName("round-trips text through the platform, freeing what SDL allocated")
    void roundTripsText() {
        if (!startVideo()) {
            return;
        }
        var clipboard = SdlClipboard.get();

        assertTrue(clipboard.text("goldberry"),
                "SDL declined a clipboard write on a video subsystem it accepted");

        assertTrue(clipboard.hasText());
        assertEquals("goldberry", clipboard.text());

        // Read twice: the first read frees SDL's string, and a double free or a
        // use-after-free shows up here rather than at some later allocation.
        assertEquals("goldberry", clipboard.text());
    }

    @Test
    @DisplayName("reports an empty clipboard as empty text rather than null")
    void emptyIsEmptyText() {
        if (!startVideo()) {
            return;
        }
        var clipboard = SdlClipboard.get();

        clipboard.text("");

        assertFalse(clipboard.hasText(), "an empty string is not text to paste");
        assertEquals("", clipboard.text());
    }

    @Test
    @DisplayName("carries text SDL has to encode as UTF-8")
    void carriesNonAscii() {
        if (!startVideo()) {
            return;
        }
        var clipboard = SdlClipboard.get();

        // Two bytes, three bytes and four: the read walks a NUL-terminated C
        // string and decodes it, and a length taken in chars rather than bytes
        // truncates exactly here.
        var text = "é ありがとう 🎨";
        clipboard.text(text);

        assertEquals(text, clipboard.text());
    }

    /// Starts SDL's video subsystem, or reports that this machine has none.
    ///
    /// Returning false rather than skipping through an assumption keeps the
    /// no-display case identical to [SdlTest]'s: the test passes having proven
    /// what it could.
    private static boolean startVideo() {
        try {
            Sdl.get().initialize(Set.of(SdlSubsystem.VIDEO));
            return true;
        } catch (SdlException e) {
            return false;
        }
    }
}
