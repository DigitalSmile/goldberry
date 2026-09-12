package io.github.digitalsmile.goldberry.natives.blend2d;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.natives.NativeLibraryRequirement;
import io.github.digitalsmile.goldberry.natives.blend2d.enums.BlendStrokeCap;
import io.github.digitalsmile.goldberry.natives.blend2d.enums.BlendStrokeJoin;

/// What the one new symbol actually puts in the buffer (ADR-0278).
///
/// [BlendGradientTest]'s shape and its reason: a stroke property that is set and
/// not read produces a frame that renders and looks nearly right, and no error
/// at all. `bl_context_set_stroke_miter_limit` is read — its three dash-shaped
/// neighbours are not, which is why they are not bound and why dashing is
/// `paint.Dash`'s job instead.
class BlendMiterLimitTest {

    private static final int BLACK = 0xFF000000;
    private static final int WHITE = 0xFFFFFFFF;

    @BeforeAll
    static void requireNativeLibrary() {
        NativeLibraryRequirement.enforce();
    }

    @Test
    @DisplayName("the miter limit decides whether a sharp corner keeps its spike")
    void miterLimitClipsTheSpike() {
        // A `V` whose apex is sharp enough that its miter runs to 1.67 times the
        // stroke width: inside the default limit of 4, and outside a limit of 1.
        //
        // Asserted as "the spike is longer" rather than as an exact tip pixel,
        // deliberately. Where a clipped miter ends is Blend2D's arithmetic and
        // not a promise in any header, and a test that pinned it would fail on
        // an upstream bump for no reason a reader could act on. That the limit
        // is read at all is the thing nothing checked before.
        var generous = inkedBelowTheApex(4);
        var tight = inkedBelowTheApex(1);

        assertTrue(generous > 0, "a miter inside the limit reaches past the apex");
        assertTrue(generous > tight, () -> "a limit of 1 cuts the spike back: " + generous + " became " + tight);
    }

    @Test
    @DisplayName("a miter limit below 1 is refused rather than passed on")
    void miterLimitIsBounded() {
        try (var image = BlendImage.wrapping(buffer(4, 4), 4, 4, 16);
                var context = BlendContext.on(image)) {

            var raised = assertThrows(IllegalArgumentException.class, () -> context.strokeMiterLimit(0.5));
            assertTrue(raised.getMessage().contains("0.5"), () -> "the message names it: " + raised.getMessage());
            assertThrows(IllegalArgumentException.class, () -> context.strokeMiterLimit(Double.NaN));
            assertDoesNotThrow(() -> context.strokeMiterLimit(1));
        }
    }

    // --- helpers ------------------------------------------------------------

    /// How many pixels of the apex column are inked below the corner point.
    ///
    /// The `V` is `(4, 0) -> (16, 16) -> (28, 0)` at width 6, whose miter runs to
    /// 1.67 times the width — so the spike reaches about five pixels past the
    /// apex when the limit allows it and about three when it does not.
    private static int inkedBelowTheApex(double miterLimit) {
        var pixels = buffer(32, 32);
        try (var image = BlendImage.wrapping(pixels, 32, 32, 128);
                var context = BlendContext.on(image);
                var path = BlendPath.create()) {

            context.clearTo(WHITE);
            path.moveTo(4, 0);
            path.lineTo(16, 16);
            path.lineTo(28, 0);
            context.strokeWidth(6);
            context.strokeCaps(BlendStrokeCap.BUTT);
            context.strokeJoin(BlendStrokeJoin.MITER_CLIP);
            context.strokeMiterLimit(miterLimit);
            context.strokePath(0, 0, path, BLACK);
        }

        var inked = 0;
        for (var y = 20; y < 32; y++) {
            if (pixelAt(pixels, 16, y, 128) != WHITE) {
                inked++;
            }
        }
        return inked;
    }

    private static ByteBuffer buffer(int width, int height) {
        return ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static int pixelAt(ByteBuffer pixels, int x, int y, int stride) {
        return pixels.order(ByteOrder.LITTLE_ENDIAN).getInt(y * stride + x * 4);
    }
}
