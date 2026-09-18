package io.github.digitalsmile.goldberry.natives.sdl;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.natives.NativeLibraryRequirement;
import io.github.digitalsmile.goldberry.natives.sdl.desktop.SdlClipboard;

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
    @DisplayName("round-trips text through the platform, freeing what SDL allocated")
    void roundTripsText() {
        requireVideo();
        var clipboard = SdlClipboard.get();

        assertTrue(clipboard.text("goldberry"), "SDL declined a clipboard write on a video subsystem it accepted");

        assertTrue(clipboard.hasText());
        assertEquals("goldberry", clipboard.text());

        // Read twice: the first read frees SDL's string, and a double free or a
        // use-after-free shows up here rather than at some later allocation.
        assertEquals("goldberry", clipboard.text());
    }

    @Test
    @DisplayName("reports an empty clipboard as empty text rather than null")
    void emptyIsEmptyText() {
        requireVideo();
        var clipboard = SdlClipboard.get();

        clipboard.text("");

        assertFalse(clipboard.hasText(), "an empty string is not text to paste");
        assertEquals("", clipboard.text());
    }

    @Test
    @DisplayName("carries text SDL has to encode as UTF-8")
    void carriesNonAscii() {
        requireVideo();
        var clipboard = SdlClipboard.get();

        // Two bytes, three bytes and four: the read walks a NUL-terminated C
        // string and decodes it, and a length taken in chars rather than bytes
        // truncates exactly here.
        var text = "é ありがとう 🎨";
        clipboard.text(text);

        assertEquals(text, clipboard.text());
    }

    // --- bytes under a MIME type (ADR-0286) ----------------------------------

    @Test
    @DisplayName("round-trips bytes through the platform, which runs the upcall")
    void roundTripsData() {
        requireVideo();
        var clipboard = SdlClipboard.get();
        var bytes = new byte[] {0, 1, 2, (byte) 0xFF, 'g', 'b'};

        assertTrue(clipboard.write("application/x-goldberry-test", bytes));

        // This is the assertion the whole design is for: reading it back runs
        // SDL's request path, which calls **our** callback to produce the bytes.
        // A wrong descriptor, a wrong `size_t*` write or a stub in a closed arena
        // all land here rather than somewhere later.
        assertTrue(clipboard.has("application/x-goldberry-test"));
        assertArrayEquals(bytes, clipboard.read("application/x-goldberry-test"));

        // Twice, because the first read frees what SDL allocated.
        assertArrayEquals(bytes, clipboard.read("application/x-goldberry-test"));
    }

    @Test
    @DisplayName("a type nobody offered reads as nothing rather than as a crash")
    void unknownTypeIsEmpty() {
        requireVideo();
        var clipboard = SdlClipboard.get();
        clipboard.write("application/x-goldberry-test", new byte[] {1});

        assertFalse(clipboard.has("application/x-goldberry-absent"));
        assertEquals(0, clipboard.read("application/x-goldberry-absent").length);
    }

    @Test
    @DisplayName("one copy can offer several types, and each comes back whole")
    void offersSeveralTypes() {
        requireVideo();
        var clipboard = SdlClipboard.get();
        var shape = new byte[] {'s', 'h', 'a', 'p', 'e'};
        var picture = new byte[] {'p', 'n', 'g'};

        // What one copy is on a board: the document's own format for pasting
        // back into it, and a picture for pasting anywhere else.
        assertTrue(clipboard.write(new java.util.LinkedHashMap<>(
                java.util.Map.of("application/x-goldberry-shape", shape, "application/x-goldberry-picture", picture))));

        assertArrayEquals(shape, clipboard.read("application/x-goldberry-shape"));
        assertArrayEquals(picture, clipboard.read("application/x-goldberry-picture"));
    }

    @Test
    @DisplayName("replacing an offer releases the one it replaced")
    void oneOfferAtATime() {
        requireVideo();
        var clipboard = SdlClipboard.get();

        clipboard.write("application/x-goldberry-test", new byte[] {1});
        clipboard.write("application/x-goldberry-test", new byte[] {2});
        clipboard.write("application/x-goldberry-test", new byte[] {3});

        // SDL calls the cleanup callback for what it is dropping, and that is
        // what closes the arena. Without it every copy in a session's life would
        // hold its bytes until the process ended.
        assertEquals(1, clipboard.liveOffers(), "only the current offer is still holding memory");
        assertArrayEquals(new byte[] {3}, clipboard.read("application/x-goldberry-test"));

        clipboard.clear();
        assertEquals(0, clipboard.liveOffers(), "and clearing lets the last one go");
    }

    @Test
    @DisplayName("an empty offer is a clear rather than an advertisement of nothing")
    void emptyOfferClears() {
        requireVideo();
        var clipboard = SdlClipboard.get();
        clipboard.write("application/x-goldberry-test", new byte[] {1});

        clipboard.write(java.util.Map.of());

        assertFalse(clipboard.has("application/x-goldberry-test"));
        assertEquals(0, clipboard.liveOffers());
    }

    @Test
    @DisplayName("an image/png offer is one the platform advertises like any other")
    void carriesAnImageType() {
        requireVideo();
        var clipboard = SdlClipboard.get();

        // The one MIME type this toolkit puts pictures on a clipboard as
        // (ADR-0283, ADR-0286). Nothing here decodes it -- `:core` does that --
        // and what is being proven is that SDL treats it exactly like the private
        // types above, which is what a paste into another application depends on.
        var png = new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
        assertTrue(clipboard.write("image/png", png));

        assertTrue(clipboard.has("image/png"));
        assertArrayEquals(png, clipboard.read("image/png"));
    }

    /// Starts SDL's video subsystem, or aborts the test with the reason it could
    /// not.
    ///
    /// An `Assumptions.abort` rather than a bare `return`: every test below needs
    /// a selection to round-trip through, and one that quietly passes without one
    /// is a green tick over a crossing nobody made — with nothing in the report to
    /// say how many of them there were.
    private static void requireVideo() {
        try {
            Sdl.get().initialize(Set.of(SdlSubsystem.VIDEO));
        } catch (SdlException e) {
            Assumptions.abort("SDL has no video subsystem on this machine: " + e.getMessage());
        }
    }
}
