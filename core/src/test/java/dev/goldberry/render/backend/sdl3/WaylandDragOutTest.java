package dev.goldberry.render.backend.sdl3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.input.dragout.DragPicture;
import dev.goldberry.render.PixelBuffer;
import dev.goldberry.render.model.DisplayScale;
import dev.goldberry.render.model.PhysicalSize;
import dev.goldberry.render.model.PixelFormat;

/// The picture of a Wayland drag, as a buffer takes it, without a compositor.
@DisplayName("a drag out on Wayland, its picture")
class WaylandDragOutTest {

    private static DragPicture picture(int width, int height) {
        return new DragPicture(
                PixelBuffer.allocate(new PhysicalSize(width, height), PixelFormat.BGRA32_PREMULTIPLIED), 3, 4);
    }

    @Test
    @DisplayName("keeps the pixels, the stride and the hotspot")
    void pixels() {
        var icon = WaylandDragOut.icon(picture(40, 20), new DisplayScale(1f));

        assertNotNull(icon);
        assertEquals(40, icon.width());
        assertEquals(20, icon.height());
        assertEquals(160, icon.stride());
        assertEquals(3, icon.hotspotX());
        assertEquals(4, icon.hotspotY());
        assertEquals(1, icon.scale());
    }

    @Test
    @DisplayName("takes a whole display scale as the buffer's")
    void wholeScale() {
        var icon = WaylandDragOut.icon(picture(40, 20), new DisplayScale(2f));

        assertNotNull(icon);
        assertEquals(2, icon.scale());
    }

    @Test
    @DisplayName("shows the picture at its pixel size on a fractional scale")
    void fractionalScale() {
        var icon = WaylandDragOut.icon(picture(40, 20), new DisplayScale(1.25f));

        assertNotNull(icon);
        assertEquals(1, icon.scale());
    }

    @Test
    @DisplayName("has no picture to show when the offer has none")
    void none() {
        assertNull(WaylandDragOut.icon(null, new DisplayScale(1f)));
    }
}
