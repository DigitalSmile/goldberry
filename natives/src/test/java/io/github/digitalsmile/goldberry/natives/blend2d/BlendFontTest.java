package io.github.digitalsmile.goldberry.natives.blend2d;

import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.digitalsmile.goldberry.natives.NativeLibraryRequirement;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The font chain's arguments and lifetime, without a font file.
///
/// `:natives` bundles no fonts — they live in `:core` (ADR-0033) — so what a
/// real face measures is checked there. What can be checked here is everything
/// that goes wrong *before* the outlines matter: refusing arguments Blend2D
/// would accept, and unwinding a chain of three native objects when the middle
/// one fails.
class BlendFontTest {

    @BeforeAll
    static void requireNativeLibrary() {
        NativeLibraryRequirement.enforce();
    }

    @Test
    @DisplayName("bytes that are not a font are reported, not turned into an empty face")
    void garbageIsRejected() {
        // The difference from HarfBuzz, and worth pinning down: hb_face_create
        // on nonsense gives a face with no glyphs and no error, so text shapes
        // to .notdef and looks like a styling bug. Blend2D says so instead --
        // and unwinding the half-built chain when it does is what this checks.
        var garbage = new byte[512];
        assertThrows(BlendException.class, () -> BlendFont.fromBytes(garbage, 16));
    }

    @Test
    @DisplayName("a font with no bytes is refused before Blend2D sees it")
    void emptyDataIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> BlendFont.fromBytes(new byte[0], 16));
    }

    @Test
    @DisplayName("a size that is not a positive finite number is refused")
    void impossibleSizesAreRefused() {
        var bytes = new byte[512];
        // Blend2D accepts all three and renders nothing, or renders a mirrored
        // pile at the origin. None of them reports a problem later.
        assertThrows(IllegalArgumentException.class, () -> BlendFont.fromBytes(bytes, 0));
        assertThrows(IllegalArgumentException.class, () -> BlendFont.fromBytes(bytes, -12));
        assertThrows(IllegalArgumentException.class, () -> BlendFont.fromBytes(bytes, Double.NaN));
    }

    @Test
    @DisplayName("a negative face index is refused")
    void negativeFaceIndexIsRefused() {
        assertThrows(
                IllegalArgumentException.class, () -> BlendFont.fromBytes(new byte[512], -1, 16));
    }

    @Test
    @DisplayName("a context still refuses a glyph run once it has been closed")
    void closedContextRefusesGlyphRuns() {
        var pixels = ByteBuffer.allocateDirect(64).order(ByteOrder.LITTLE_ENDIAN);
        try (var image = BlendImage.wrapping(pixels, 4, 4, 16);
                var glyphs = BlendGlyphBuffer.create()) {

            var context = BlendContext.on(image);
            context.close();

            // Drawing into a context that has detached would write through a
            // handle Blend2D has released. What the run holds does not matter;
            // the state check comes first.
            assertThrows(
                    IllegalStateException.class,
                    () -> context.fillGlyphRun(0, 0, null, glyphs, 0xFFFFFFFF));
        }
    }
}
