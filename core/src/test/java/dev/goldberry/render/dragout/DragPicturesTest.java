package dev.goldberry.render.dragout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.render.PixelBuffer;
import dev.goldberry.render.model.DisplayScale;
import dev.goldberry.render.model.LogicalPoint;
import dev.goldberry.render.model.LogicalRect;
import dev.goldberry.render.model.PhysicalSize;
import dev.goldberry.render.model.PixelFormat;

@DisplayName("the picture a drag out hands the platform")
class DragPicturesTest {

    /// A 20x10 frame at scale 2 whose every byte is its column's index in
    /// pixels, so a cut can be read back by where it came from.
    private static PixelBuffer frame() {
        var frame = PixelBuffer.allocate(new PhysicalSize(20, 10), PixelFormat.BGRA32_PREMULTIPLIED);
        for (var y = 0; y < 10; y++) {
            for (var x = 0; x < 20; x++) {
                for (var channel = 0; channel < 4; channel++) {
                    frame.pixels().put(y * frame.stride() + x * 4 + channel, (byte) (x * 10));
                }
            }
        }
        return frame;
    }

    @Test
    @DisplayName("is the source's rectangle at the window's scale, faded, with the hotspot where it was taken hold of")
    void cut() {
        var picture = DragPictures.ghost(
                        frame(), LogicalRect.of(2, 1, 4, 3), new LogicalPoint(1, 2), new DisplayScale(2))
                .orElseThrow();

        assertEquals(new PhysicalSize(8, 6), picture.pixels().size());
        assertEquals(2, picture.hotspotX());
        assertEquals(4, picture.hotspotY());
        // Column 0 of the picture is column 4 of the frame, 40, faded to 28.
        assertEquals(
                Math.round(40 * DragPictures.OPACITY),
                Byte.toUnsignedInt(picture.pixels().pixels().get(0)));
        var lastRow = 5 * picture.pixels().stride();
        assertEquals(
                Math.round(110 * DragPictures.OPACITY),
                Byte.toUnsignedInt(picture.pixels().pixels().get(lastRow + 7 * 4)));
    }

    @Test
    @DisplayName("is clipped to the frame where the source hangs off its edge")
    void clipped() {
        var picture = DragPictures.ghost(frame(), LogicalRect.of(-3, 0, 6, 5), new LogicalPoint(5, 1), DisplayScale.ONE)
                .orElseThrow();

        assertEquals(new PhysicalSize(3, 5), picture.pixels().size());
        assertEquals(2, picture.hotspotX(), "kept on the picture");
    }

    @Test
    @DisplayName("is none where the source is not on the frame at all")
    void offFrame() {
        assertTrue(DragPictures.ghost(frame(), LogicalRect.of(30, 0, 4, 4), new LogicalPoint(0, 0), DisplayScale.ONE)
                .isEmpty());
    }
}
