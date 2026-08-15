package io.github.digitalsmile.goldberry.natives.blend2d;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.natives.NativeLibraryRequirement;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// What the wrappers refuse, and why each refusal is cheaper than the crash it
/// replaces.
class BlendOwnershipTest {

    @BeforeAll
    static void requireNativeLibrary() {
        NativeLibraryRequirement.enforce();
    }

    @Test
    @DisplayName("a heap buffer is refused rather than quietly copied")
    void heapBuffersAreRefused() {
        // A heap buffer has no address the collector will not move. Copying it
        // would give back an image that paints correctly into memory nobody
        // ever presents -- a blank window and no error anywhere.
        var heap = ByteBuffer.allocate(64);

        var thrown = assertThrows(
                IllegalArgumentException.class, () -> BlendImage.wrapping(heap, 4, 4, 16));

        assertTrue(thrown.getMessage().contains("direct"), thrown.getMessage());
    }

    @Test
    @DisplayName("a stride too narrow for the row is refused")
    void narrowStrideIsRefused() {
        var pixels = direct(64);

        var thrown = assertThrows(
                IllegalArgumentException.class, () -> BlendImage.wrapping(pixels, 4, 4, 12));

        assertTrue(thrown.getMessage().contains("cannot hold a row"), thrown.getMessage());
    }

    @Test
    @DisplayName("a buffer too small for the image is refused")
    void undersizedBufferIsRefused() {
        // Blend2D would take this and write past the end: it is told a size and
        // a stride and has no way to know how much memory is behind the pointer.
        var pixels = direct(32);

        var thrown = assertThrows(
                IllegalArgumentException.class, () -> BlendImage.wrapping(pixels, 4, 4, 16));

        assertTrue(thrown.getMessage().contains("offers"), thrown.getMessage());
    }

    @Test
    @DisplayName("a tightly packed buffer is accepted, padding for the last row and all")
    void exactlySizedBufferIsAccepted() {
        // The last row needs no padding after it, so the requirement is one
        // stride short of stride * height. Getting that wrong the other way
        // rejects every tightly packed buffer there is.
        var pixels = direct(4 * 4 * 4);

        assertDoesNotThrow(() -> BlendImage.wrapping(pixels, 4, 4, 16).close());
    }

    @Test
    @DisplayName("a zero-sized image is refused")
    void zeroSizeIsRefused() {
        var pixels = direct(64);

        assertThrows(IllegalArgumentException.class, () -> BlendImage.wrapping(pixels, 0, 4, 16));
        assertThrows(IllegalArgumentException.class, () -> BlendImage.wrapping(pixels, 4, 0, 16));
    }

    @Test
    @DisplayName("a scale that would collapse or mirror the frame is refused")
    void badScalesAreRefused() {
        var pixels = direct(64);
        try (var image = BlendImage.wrapping(pixels, 4, 4, 16)) {
            // Blend2D would accept all of these and produce a frame that is
            // empty, inside out, or full of NaN coordinates.
            assertThrows(IllegalArgumentException.class, () -> BlendContext.on(image, 0));
            assertThrows(IllegalArgumentException.class, () -> BlendContext.on(image, -1.5));
            assertThrows(IllegalArgumentException.class, () -> BlendContext.on(image, Double.NaN));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> BlendContext.on(image, Double.POSITIVE_INFINITY));
        }
    }

    @Test
    @DisplayName("a NaN rectangle is reported rather than silently not drawn")
    void nanRectanglesAreReported() {
        var pixels = direct(64);
        try (var image = BlendImage.wrapping(pixels, 4, 4, 16);
                var context = BlendContext.on(image)) {

            // Blend2D rasterizes a NaN rectangle as nothing at all, so an
            // arithmetic bug upstream would look like a widget that did not draw.
            var thrown = assertThrows(
                    IllegalArgumentException.class,
                    () -> context.fillRect(Double.NaN, 0, 10, 10, 0xFFFFFFFF));

            assertTrue(thrown.getMessage().contains("NaN"), thrown.getMessage());
        }
    }

    @Test
    @DisplayName("an empty rectangle draws nothing and says nothing")
    void emptyRectanglesAreIgnored() {
        var pixels = direct(64);
        try (var image = BlendImage.wrapping(pixels, 4, 4, 16);
                var context = BlendContext.on(image)) {
            // Ordinary: a layout pass produces zero-sized boxes all the time.
            assertDoesNotThrow(() -> context.fillRect(0, 0, 0, 10, 0xFFFFFFFF));
            assertDoesNotThrow(() -> context.fillRect(0, 0, 10, -5, 0xFFFFFFFF));
        }

        assertEquals(0, pixels.order(ByteOrder.LITTLE_ENDIAN).getInt(0), "nothing was drawn");
    }

    @Test
    @DisplayName("a closed context and a closed image are unusable, and close twice cleanly")
    void closedObjectsAreUnusable() {
        var pixels = direct(64);
        var image = BlendImage.wrapping(pixels, 4, 4, 16);
        var context = BlendContext.on(image);

        context.close();
        assertTrue(context.isClosed());
        assertThrows(IllegalStateException.class, () -> context.fillAll(0xFFFFFFFF));
        assertDoesNotThrow(context::close, "closing twice does nothing");

        image.close();
        assertTrue(image.isClosed());
        assertThrows(IllegalStateException.class, image::data);
        assertDoesNotThrow(image::close, "closing twice does nothing");
    }

    @Test
    @DisplayName("the image reports the geometry it was created with")
    void geometryIsReported() {
        var pixels = direct(4 * 4 * 4);
        try (var image = BlendImage.wrapping(pixels, 4, 4, 16)) {
            assertEquals(4, image.width());
            assertEquals(4, image.height());
            assertEquals(16, image.stride());
            assertFalse(image.isClosed());
        }
    }

    @Test
    @DisplayName("a context belongs to the thread that created it")
    void contextsAreConfined() throws InterruptedException {
        var pixels = direct(64);
        try (var image = BlendImage.wrapping(pixels, 4, 4, 16);
                var context = BlendContext.on(image)) {

            var fromElsewhere = new AtomicReference<Throwable>();
            var other = Thread.ofVirtual().start(() -> {
                try {
                    context.fillAll(0xFFFFFFFF);
                } catch (Throwable t) {
                    fromElsewhere.set(t);
                }
            });
            other.join();

            var thrown = assertInstanceOf(IllegalStateException.class, fromElsewhere.get());
            assertTrue(thrown.getMessage().contains("thread"), thrown.getMessage());
        }
    }

    @Test
    @DisplayName("a transform op with the wrong operand shape is refused")
    void transformOperandShapeIsChecked() {
        // RESET takes no operand, and every operand crosses as a void* that
        // neither side type-checks. The refusal happens before the context
        // pointer is touched, which is why NULL is enough to reach it.
        var thrown = assertThrows(
                IllegalArgumentException.class,
                () -> Blend2D.get().contextTransform(
                        MemorySegment.NULL, BlendTransformOp.RESET, 1, 1));

        assertTrue(thrown.getMessage().contains("void*"), thrown.getMessage());
    }

    @Test
    @DisplayName("an unnamed result code still reports its number")
    void unnamedResultCodesAreReadable() {
        // Only a handful of Blend2D's codes are named, deliberately. One that is
        // not must still be traceable back to the header.
        var thrown = new BlendException("bl_something", 0x0001FFFF);

        assertTrue(thrown.code().isEmpty(), "not a code Goldberry names");
        assertTrue(thrown.getMessage().contains("0x0001FFFF"), thrown.getMessage());
        assertEquals(0x0001FFFF, thrown.result());

        var named = new BlendException("bl_something", BlendResultCode.INVALID_VALUE.nativeValue());
        assertEquals(BlendResultCode.INVALID_VALUE, named.code().orElseThrow());
        assertTrue(named.getMessage().contains("BL_ERROR_INVALID_VALUE"), named.getMessage());
    }

    private static ByteBuffer direct(int bytes) {
        return ByteBuffer.allocateDirect(bytes).order(ByteOrder.LITTLE_ENDIAN);
    }
}
