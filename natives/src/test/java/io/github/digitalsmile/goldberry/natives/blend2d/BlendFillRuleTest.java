package io.github.digitalsmile.goldberry.natives.blend2d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.natives.NativeLibraryRequirement;

/// What `bl_context_set_fill_rule` does to a path with a hole in it — ADR-0427.
///
/// The binding this exists to prove is one `int` wide, which is exactly the kind
/// that is wrong and silent: pass the wrong enumerator and every fill still
/// draws, still returns `BL_SUCCESS`, and quietly has no hole in it. So each
/// assertion below is a pixel that can be worked out by hand.
class BlendFillRuleTest {

    private static final int WHITE = 0xFFFFFFFF;

    private static final int BLACK = 0xFF000000;

    private static final int SIZE = 40;

    private static final int STRIDE = SIZE * 4;

    @BeforeAll
    static void requireNativeLibrary() {
        NativeLibraryRequirement.enforce();
    }

    @Test
    @DisplayName("even-odd leaves a hole where two sub-paths overlap")
    void evenOddCutsAHole() {
        var pixels = buffer();
        try (var image = BlendImage.wrapping(pixels, SIZE, SIZE, STRIDE);
                var context = BlendContext.on(image);
                var path = BlendPath.create()) {

            context.fillAll(WHITE);
            // An outer square covering the whole image and an inner one in the
            // middle of it. Both wound the same way, which is the point: the
            // rule and not the winding is what makes the hole.
            square(path, 0, 0, SIZE);
            square(path, 12, 12, 16);
            context.fillPathEvenOdd(0, 0, path, BLACK);
        }

        assertEquals(BLACK, pixelAt(pixels, 4, 4), "outside the inner square is filled");
        assertEquals(BLACK, pixelAt(pixels, 36, 36), "on every side");
        assertEquals(WHITE, pixelAt(pixels, 20, 20), "and the inner square is a hole");
    }

    @Test
    @DisplayName("the default rule fills the same path solid, which is what it could not do before")
    void nonZeroFillsItSolid() {
        // The contrast that says the new call is doing the work. Two identically
        // wound sub-paths under non-zero are one shape: every point inside the
        // inner square has winding 2, which is not zero, so it is inside.
        var pixels = buffer();
        try (var image = BlendImage.wrapping(pixels, SIZE, SIZE, STRIDE);
                var context = BlendContext.on(image);
                var path = BlendPath.create()) {

            context.fillAll(WHITE);
            square(path, 0, 0, SIZE);
            square(path, 12, 12, 16);
            context.fillPath(0, 0, path, BLACK);
        }

        assertEquals(BLACK, pixelAt(pixels, 20, 20), "no hole without the rule");
        assertNotEquals(WHITE, pixelAt(pixels, 20, 20));
    }

    @Test
    @DisplayName("the rule does not leak into the next fill")
    void theRuleIsPutBack() {
        // The failure this catches is the one the wrapper's `finally` exists for:
        // the fill rule is context state and Blend2D has no stack for it, so a
        // rule left set turns up as a hole in an unrelated shape several boxes
        // later — and nothing anywhere reports an error.
        var pixels = buffer();
        try (var image = BlendImage.wrapping(pixels, SIZE, SIZE, STRIDE);
                var context = BlendContext.on(image);
                var path = BlendPath.create()) {

            context.fillAll(WHITE);
            square(path, 0, 0, SIZE);
            square(path, 12, 12, 16);
            context.fillPathEvenOdd(0, 0, path, BLACK);

            // The very same path, filled the ordinary way immediately after.
            context.fillPath(0, 0, path, BLACK);
        }

        assertEquals(BLACK, pixelAt(pixels, 20, 20), "the second fill used the default rule again");
    }

    /// A closed axis-aligned square as one sub-path, wound clockwise.
    private static void square(BlendPath path, double x, double y, double side) {
        path.moveTo(x, y);
        path.lineTo(x + side, y);
        path.lineTo(x + side, y + side);
        path.lineTo(x, y + side);
        path.closeSubPath();
    }

    private static ByteBuffer buffer() {
        return ByteBuffer.allocateDirect(SIZE * SIZE * 4).order(ByteOrder.LITTLE_ENDIAN);
    }

    /// The pixel at `(x, y)`, as `0xAARRGGBB`.
    private static int pixelAt(ByteBuffer pixels, int x, int y) {
        return pixels.order(ByteOrder.LITTLE_ENDIAN).getInt(y * STRIDE + x * 4);
    }
}
