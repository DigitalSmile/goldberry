package io.github.digitalsmile.goldberry.natives.blend2d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.natives.NativeLibraryRequirement;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The two symbols layers needed — `bl_context_blit_image_d` and
/// `bl_context_set_global_alpha`.
///
/// These are the first additions to the export list since it caught its third
/// local-symbol bug (`--exclude-libs`, then `BL_STATIC`, then HarfBuzz's bare
/// `HB_EXTERN`), and each of those linked a symbol in and left it **local** —
/// present to `nm`, absent to `nm -D`, and answered only by a run against a real
/// library. This file is what answers it here: every assertion is a pixel that
/// can be worked out by hand, and none of them can pass if either symbol failed
/// to export.
class BlendLayerTest {

    private static final int RED = 0xFFFF0000;
    private static final int BLUE = 0xFF0000FF;

    @BeforeAll
    static void requireNativeLibrary() {
        NativeLibraryRequirement.enforce();
    }

    private static ByteBuffer buffer(int width, int height) {
        return ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder());
    }

    private static int pixel(ByteBuffer pixels, int stride, int x, int y) {
        return pixels.duplicate().order(ByteOrder.LITTLE_ENDIAN).getInt(y * stride + x * 4);
    }

    @Test
    @DisplayName("a blit copies a layer's pixels onto another image")
    void blits() {
        // Draw red into a 4x4 layer, then blit it into the middle of an 8x8
        // target. If `bl_context_blit_image_d` had not exported, this would fail
        // to link rather than draw the wrong thing -- which is the point.
        var layerPixels = buffer(4, 4);
        try (var layer = BlendImage.wrapping(layerPixels, 4, 4, 16)) {
            // Each context in a scope of its own: the pixels are only complete
            // once it is closed, because a context may still have work queued.
            try (var into = BlendContext.on(layer, 1.0, 0)) {
                into.fillRect(0, 0, 4, 4, RED);
            }

            var targetPixels = buffer(8, 8);
            try (var target = BlendImage.wrapping(targetPixels, 8, 8, 32)) {
                try (var context = BlendContext.on(target, 1.0, 0)) {
                    context.clearTo(BLUE);
                    context.blit(2, 2, layer);
                }

                assertEquals(RED, pixel(targetPixels, 32, 2, 2), "the layer's top-left corner");
                assertEquals(RED, pixel(targetPixels, 32, 5, 5), "and its bottom-right");
                assertEquals(BLUE, pixel(targetPixels, 32, 1, 1), "outside it, the target");
                assertEquals(BLUE, pixel(targetPixels, 32, 6, 6));
            }
        }
    }

    @Test
    @DisplayName("global alpha fades a blit, and it is what makes a layer a group")
    void globalAlpha() {
        // Red at half alpha over blue. Premultiplied, straight down the middle:
        // 0xFF0000 at 0.5 over 0x0000FF gives roughly (127, 0, 128).
        var layerPixels = buffer(4, 4);
        try (var layer = BlendImage.wrapping(layerPixels, 4, 4, 16)) {
            try (var into = BlendContext.on(layer, 1.0, 0)) {
                into.fillRect(0, 0, 4, 4, RED);
            }

            var targetPixels = buffer(8, 8);
            try (var target = BlendImage.wrapping(targetPixels, 8, 8, 32)) {
                try (var context = BlendContext.on(target, 1.0, 0)) {
                    context.clearTo(BLUE);
                    context.globalAlpha(0.5);
                    context.blit(0, 0, layer);
                }

                var blended = pixel(targetPixels, 32, 1, 1);
                var red = (blended >>> 16) & 0xFF;
                var blue = blended & 0xFF;
                assertTrue(red > 100 && red < 155,
                        () -> "expected about half red, got #" + Integer.toHexString(blended));
                assertTrue(blue > 100 && blue < 155,
                        () -> "and about half blue, got #" + Integer.toHexString(blended));
                assertEquals(0xFF, blended >>> 24, "over an opaque backdrop the result is opaque");
            }
        }
    }

    @Test
    @DisplayName("two overlapping shapes in one layer fade together, not separately")
    void groupsRatherThanFadesEach() {
        // The whole difference between a layer and multiplying alpha per shape,
        // and the reason ADR-0064 left it as an open question. Two opaque
        // rectangles overlapping, the group at 50%: through a layer the overlap
        // is the *top* rectangle at 50% over the backdrop. Faded separately it
        // would be the top at 50% over the bottom at 50% over the backdrop --
        // visibly darker, and not what CSS says.
        var layerPixels = buffer(8, 8);
        try (var layer = BlendImage.wrapping(layerPixels, 8, 8, 32)) {
            try (var into = BlendContext.on(layer, 1.0, 0)) {
                into.fillRect(0, 0, 6, 6, RED);
                into.fillRect(2, 2, 6, 6, BLUE);
            }

            var targetPixels = buffer(8, 8);
            try (var target = BlendImage.wrapping(targetPixels, 8, 8, 32)) {
                try (var context = BlendContext.on(target, 1.0, 0)) {
                    context.clearTo(0xFF000000);
                    context.globalAlpha(0.5);
                    context.blit(0, 0, layer);
                }

                // Inside the overlap: blue won inside the layer, so what reaches
                // the frame is half blue over black and there is no red left in
                // it at all.
                var overlap = pixel(targetPixels, 32, 3, 3);
                assertEquals(0, (overlap >>> 16) & 0xFF,
                        () -> "the covered rectangle showed through: #"
                                + Integer.toHexString(overlap));
                assertTrue((overlap & 0xFF) > 100 && (overlap & 0xFF) < 155,
                        () -> "and the top one should be at half: #"
                                + Integer.toHexString(overlap));
            }
        }
    }

    @Test
    @DisplayName("an alpha outside 0..1 is refused rather than clamped")
    void refusesBadAlpha() {
        var pixels = buffer(2, 2);
        try (var image = BlendImage.wrapping(pixels, 2, 2, 8);
             var context = BlendContext.on(image, 1.0, 0)) {
            assertThrows(IllegalArgumentException.class, () -> context.globalAlpha(-0.1));
            assertThrows(IllegalArgumentException.class, () -> context.globalAlpha(1.5));
            assertThrows(IllegalArgumentException.class, () -> context.globalAlpha(Double.NaN));
        }
    }

}
