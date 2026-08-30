package io.github.digitalsmile.goldberry.natives.blend2d;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.natives.NativeLibraryRequirement;

/// What the six new symbols actually put in the buffer (ADR-0207).
///
/// Every assertion is a pixel a reader can work out by hand, for
/// [BlendPaintTest]'s reason and one more that is specific to a ramp: the two
/// ways of getting a gradient wrong — fading toward transparent *black*, and
/// leaving the style set for whatever draws next — both produce a frame that
/// renders and looks nearly right. Neither produces an error.
class BlendGradientTest {

    /// A slot-1 green from `charts.md` §2.1's dark palette, which is the colour
    /// an area chart's first band is actually drawn in.
    private static final int GREEN = 0xFF73A340;

    @BeforeAll
    static void requireNativeLibrary() {
        NativeLibraryRequirement.enforce();
    }

    @Test
    @DisplayName("a fade thins the colour out rather than passing through grey")
    void fadeKeepsItsHue() {
        // The whole reason `fade` exists. A ramp to 0x00000000 is a ramp to
        // transparent BLACK, and premultiplied interpolation takes the green
        // through grey on its way there -- visible on any hue that is not
        // already dark. The far stop has to repeat the RGB.
        var pixels = buffer(64, 1);
        try (var image = BlendImage.wrapping(pixels, 64, 1, 256);
                var context = BlendContext.on(image);
                var path = BlendPath.create();
                var gradient = BlendGradient.fade(0, 0, 64, 0, GREEN)) {

            context.clearTo(0xFFFFFFFF);
            rect(path, 0, 0, 64, 1);
            context.fillPath(0, 0, path, gradient);
        }

        // Over white, halfway along: the green at 50% over white. Every channel
        // is between the green's own and white's, and none of them has
        // collapsed toward the others -- which is what "went grey" would look
        // like.
        var middle = pixelAt(pixels, 32, 0, 256);
        var r = (middle >>> 16) & 0xFF;
        var g = (middle >>> 8) & 0xFF;
        var b = middle & 0xFF;
        assertTrue(
                g > r + 20 && g > b + 20,
                () -> "the middle of the fade is still recognisably green, and it is #" + Integer.toHexString(middle));
    }

    @Test
    @DisplayName("the near end is the colour and the far end is what was under it")
    void theRampRunsFromEndToEnd() {
        var pixels = buffer(64, 1);
        try (var image = BlendImage.wrapping(pixels, 64, 1, 256);
                var context = BlendContext.on(image);
                var path = BlendPath.create();
                var gradient = BlendGradient.fade(0, 0, 64, 0, GREEN)) {

            context.clearTo(0xFFFFFFFF);
            rect(path, 0, 0, 64, 1);
            context.fillPath(0, 0, path, gradient);
        }

        // **Near**, not equal, and the difference is the point: a gradient is
        // sampled at the centre of each pixel, so the leftmost pixel is already
        // half a pixel along the ramp -- 0.8% of the way to transparent across
        // 64 of them. An exact-equality assertion here would be asserting that
        // Blend2D samples at the pixel's left edge, which no rasterizer does.
        assertNear(GREEN, pixelAt(pixels, 0, 0, 256), 3, "opaque at the start point");
        assertNear(
                0xFFFFFFFF, pixelAt(pixels, 63, 0, 256), 3, "and the white underneath is all but untouched at the end");
        // Monotone in between: a gradient whose stops were added out of order,
        // or whose extend mode repeated, would not be.
        var quarter = pixelAt(pixels, 16, 0, 256) & 0xFF;
        var half = pixelAt(pixels, 32, 0, 256) & 0xFF;
        var threeQuarters = pixelAt(pixels, 48, 0, 256) & 0xFF;
        assertTrue(
                quarter < half && half < threeQuarters,
                () -> "the blue channel climbs toward white across the ramp: " + quarter + ", " + half + ", "
                        + threeQuarters);
    }

    @Test
    @DisplayName("PAD holds the end colours outside the gradient's own extent")
    void beyondTheEndsTheStopsHold() {
        // The gradient is placed over the middle half of the image and the path
        // covers all of it. With REPEAT or REFLECT the outer quarters would show
        // a second copy of the ramp; with PAD they are the end colours, which is
        // what makes a gradient placed on a plot safe to fill any band with.
        var pixels = buffer(64, 1);
        try (var image = BlendImage.wrapping(pixels, 64, 1, 256);
                var context = BlendContext.on(image);
                var path = BlendPath.create();
                var gradient = BlendGradient.fade(16, 0, 48, 0, GREEN)) {

            context.clearTo(0xFFFFFFFF);
            rect(path, 0, 0, 64, 1);
            context.fillPath(0, 0, path, gradient);
        }

        assertEquals(GREEN, pixelAt(pixels, 0, 0, 256), "before the start, the first stop holds");
        assertEquals(GREEN, pixelAt(pixels, 8, 0, 256), "and still holds at the start");
        assertEquals(0xFFFFFFFF, pixelAt(pixels, 63, 0, 256), "past the end, the transparent stop holds");
    }

    @Test
    @DisplayName("the path moves with its origin and the gradient does not")
    void theOriginTranslatesTheShapeAlone() {
        // What lets one gradient fill several bands: the ramp is a statement
        // about a region of the surface, so drawing the same path at two origins
        // samples two parts of it rather than repeating the first.
        var pixels = buffer(64, 1);
        try (var image = BlendImage.wrapping(pixels, 64, 1, 256);
                var context = BlendContext.on(image);
                var path = BlendPath.create();
                var gradient = BlendGradient.fade(0, 0, 64, 0, GREEN)) {

            context.clearTo(0xFFFFFFFF);
            rect(path, 0, 0, 8, 1);
            context.fillPath(0, 0, path, gradient);
            context.fillPath(48, 0, path, gradient);
        }

        var near = pixelAt(pixels, 2, 0, 256);
        var far = pixelAt(pixels, 50, 0, 256);
        assertNotEquals(
                near,
                far,
                "the second copy is drawn through a later part of the same ramp, not through the"
                        + " start of a fresh one");
        assertTrue(
                (far & 0xFF) > (near & 0xFF),
                () -> "and the later part is the faded one: #" + Integer.toHexString(near) + " then #"
                        + Integer.toHexString(far));
    }

    @Test
    @DisplayName("the fill style goes back to a colour, so the next fill is not a ramp")
    void theStyleIsPutBack() {
        // The bug this guards is not in the gradient at all: it is in whatever
        // is painted after one. Blend2D's fill style is context state, and every
        // other call on BlendContext states its own colour -- so a gradient left
        // set would be drawn by the next styleless fill, somewhere else in the
        // frame entirely.
        var pixels = buffer(64, 2);
        try (var image = BlendImage.wrapping(pixels, 64, 2, 256);
                var context = BlendContext.on(image);
                var path = BlendPath.create()) {

            context.clearTo(0xFFFFFFFF);
            rect(path, 0, 0, 64, 1);
            try (var gradient = BlendGradient.fade(0, 0, 64, 0, GREEN)) {
                context.fillPath(0, 0, path, gradient);
            }
            // An ordinary coloured fill, on the row below.
            path.reset();
            rect(path, 0, 1, 64, 1);
            context.fillPath(0, 0, path, 0xFF000000);
        }

        assertEquals(0xFF000000, pixelAt(pixels, 0, 1, 256), "the near end of the row below");
        assertEquals(
                0xFF000000,
                pixelAt(pixels, 63, 1, 256),
                "and the far end, which is where a leaked ramp would have faded out");
    }

    @Test
    @DisplayName("a gradient may be released the moment the fill is issued")
    void theContextRetainsTheStyle() {
        // bl_context_set_fill_style retains, so the gradient's own lifetime ends
        // at the call. If it did not, this would draw with freed memory -- which
        // on a good day is a crash and on a bad one is a plausible frame.
        var pixels = buffer(64, 1);
        try (var image = BlendImage.wrapping(pixels, 64, 1, 256);
                var context = BlendContext.on(image);
                var path = BlendPath.create()) {

            context.clearTo(0xFFFFFFFF);
            rect(path, 0, 0, 64, 1);
            var gradient = BlendGradient.fade(0, 0, 64, 0, GREEN);
            context.fillPath(0, 0, path, gradient);
            gradient.close();
            assertTrue(gradient.isReleased());
            assertDoesNotThrow(gradient::close, "releasing twice is a no-op");
        }

        assertNear(GREEN, pixelAt(pixels, 0, 0, 256), 3, "the fill happened and is intact");
    }

    @Test
    @DisplayName("a released gradient refuses to be used again")
    void aReleasedGradientIsUnusable() {
        var gradient = BlendGradient.linear(0, 0, 1, 0);
        gradient.close();
        assertThrows(IllegalStateException.class, () -> gradient.addStop(0, GREEN));
    }

    @Test
    @DisplayName("a stop outside the gradient is refused with its own offset named")
    void stopsAreBounded() {
        try (var gradient = BlendGradient.linear(0, 0, 1, 0)) {
            var raised = assertThrows(IllegalArgumentException.class, () -> gradient.addStop(1.5, GREEN));
            assertTrue(
                    raised.getMessage().contains("1.5"), () -> "the message names the offset: " + raised.getMessage());
            assertThrows(IllegalArgumentException.class, () -> gradient.addStop(-0.1, GREEN));
            assertThrows(IllegalArgumentException.class, () -> gradient.addStop(Double.NaN, GREEN));
        }
    }

    @Test
    @DisplayName("a NaN endpoint is refused rather than filling with nothing")
    void endpointsMustBeFinite() {
        // Blend2D takes a NaN and draws nothing at all, which is
        // indistinguishable from a band whose arithmetic went wrong upstream --
        // the trap fillRect already guards.
        assertThrows(IllegalArgumentException.class, () -> BlendGradient.linear(0, Double.NaN, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> BlendGradient.linear(0, 0, Double.POSITIVE_INFINITY, 0));
    }

    @Test
    @DisplayName("a gradient with no stops draws nothing rather than failing")
    void noStopsIsEmptyRatherThanBroken() {
        var pixels = buffer(4, 1);
        try (var image = BlendImage.wrapping(pixels, 4, 1, 16);
                var context = BlendContext.on(image);
                var path = BlendPath.create();
                var gradient = BlendGradient.linear(0, 0, 4, 0)) {

            context.clearTo(0xFFFFFFFF);
            rect(path, 0, 0, 4, 1);
            assertDoesNotThrow(() -> context.fillPath(0, 0, path, gradient));
        }

        assertEquals(0xFFFFFFFF, pixelAt(pixels, 0, 0, 16), "an empty ramp is transparent, not an exception");
    }

    @Test
    @DisplayName("the scale transform applies to a gradient's coordinates too")
    void gradientsAreInLogicalCoordinates() {
        // The gradient is placed in the context's user space, which is logical
        // for a scaled context. Four logical pixels at 2x is the whole eight-
        // pixel image; if the ramp were read as physical it would be finished by
        // pixel four and the second half would be bare white.
        var pixels = buffer(8, 1);
        try (var image = BlendImage.wrapping(pixels, 8, 1, 32);
                var context = BlendContext.on(image, 2.0);
                var path = BlendPath.create();
                var gradient = BlendGradient.fade(0, 0, 4, 0, GREEN)) {

            context.clearTo(0xFFFFFFFF);
            rect(path, 0, 0, 4, 1);
            context.fillPath(0, 0, path, gradient);
        }

        assertNotEquals(
                0xFFFFFFFF,
                pixelAt(pixels, 5, 0, 32),
                "physical pixel 5 is still tinted, which is where a ramp read as physical would"
                        + " already have run out");
        assertTrue(
                (pixelAt(pixels, 7, 0, 32) & 0xFF) > 0xE0,
                "and it is nearly gone at the physical end, four LOGICAL pixels along");
    }

    // --- helpers ------------------------------------------------------------

    /// A rectangle as a closed path, since the styleless fill only takes paths.
    private static void rect(BlendPath path, double x, double y, double w, double h) {
        path.moveTo(x, y);
        path.lineTo(x + w, y);
        path.lineTo(x + w, y + h);
        path.lineTo(x, y + h);
        path.closeSubPath();
    }

    /// Asserts each channel of `actual` is within `tolerance` of `expected`'s.
    ///
    /// For the half-pixel sampling: a colour a gradient produces is never quite
    /// the stop's own, and a test that demanded it would be asserting something
    /// about the rasterizer's sampling grid rather than about the ramp.
    private static void assertNear(int expected, int actual, int tolerance, String what) {
        for (var shift = 0; shift <= 24; shift += 8) {
            var wanted = (expected >>> shift) & 0xFF;
            var got = (actual >>> shift) & 0xFF;
            assertTrue(
                    Math.abs(wanted - got) <= tolerance,
                    () -> what + ": expected #" + Integer.toHexString(expected) + " within " + tolerance
                            + " per channel, got #" + Integer.toHexString(actual));
        }
    }

    private static ByteBuffer buffer(int width, int height) {
        return ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static int pixelAt(ByteBuffer pixels, int x, int y, int stride) {
        return pixels.order(ByteOrder.LITTLE_ENDIAN).getInt(y * stride + x * 4);
    }
}
