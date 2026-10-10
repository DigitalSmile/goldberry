package dev.goldberry.render.backend.sdl3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.function.BiConsumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.natives.sdl.desktop.SdlSystemCursor;
import dev.goldberry.render.Cursor;
import dev.goldberry.render.cursor.CursorPicture;
import dev.goldberry.render.cursor.CursorPictures;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.render.window.IconImage;
import dev.goldberry.render.window.WindowSpec;

/// The pointer's shape on SDL: which size of a picture SDL is given, and the
/// platform's shape for one with no picture.
class Sdl3CursorsTest {

    private static CursorPicture size(int width) {
        return new CursorPicture(IconImage.of(width, width, (x, y) -> 0xFFC8A04A), width / 4, width / 8);
    }

    private static final CursorPictures GRAB =
            new CursorPictures(Cursor.GRAB, List.of(size(32), size(48), size(64), size(96)));

    @Nested
    @DisplayName("which sizes go to SDL")
    class Sizes {

        @Test
        @DisplayName("X11 draws a cursor pixel for pixel, and nothing else does")
        void pixelForPixel() {
            assertTrue(Sdl3Cursors.drawsPixelForPixel("x11"));
            assertFalse(Sdl3Cursors.drawsPixelForPixel("wayland"));
            assertFalse(Sdl3Cursors.drawsPixelForPixel("cocoa"));
            assertFalse(Sdl3Cursors.drawsPixelForPixel("windows"));
        }

        @Test
        @DisplayName("on X11 the size the scale wants goes alone")
        void x11() {
            assertEquals(List.of(64), widths(Sdl3Cursors.sizes(GRAB, true, 2)));
            assertEquals(List.of(48), widths(Sdl3Cursors.sizes(GRAB, true, 1.5)));
        }

        @Test
        @DisplayName("elsewhere every size goes, the 100% one first, and SDL picks")
        void elsewhere() {
            assertEquals(List.of(32, 48, 64, 96), widths(Sdl3Cursors.sizes(GRAB, false, 2)));
        }

        private static List<Integer> widths(List<CursorPicture> sizes) {
            return sizes.stream().map(CursorPicture::width).toList();
        }
    }

    @Test
    @DisplayName("without a picture a grab is the platform's move, and a pointer its hand")
    void systemShapes() {
        assertEquals(SdlSystemCursor.MOVE, Sdl3Cursors.systemShape(Cursor.GRAB));
        assertEquals(SdlSystemCursor.MOVE, Sdl3Cursors.systemShape(Cursor.GRABBING));
        assertEquals(SdlSystemCursor.POINTER, Sdl3Cursors.systemShape(Cursor.POINTER));
        for (var cursor : Cursor.values()) {
            // Exhaustive by the switch; this is the table being total at run time.
            Sdl3Cursors.systemShape(cursor);
        }
    }

    /// Against the real SDL under its `dummy` driver, which makes a picture
    /// cursor out of any surface and no system cursor at all: the binding
    /// links, each shape's picture is made and shown, and taking the pictures
    /// away forgets them.
    @Test
    @DisplayName("a shape with a picture shows it, and taking the pictures away forgets it")
    void againstSdl() {
        RendererRequirement.enforce();
        withBackend((backend, window) -> {
            var grabbing = new CursorPictures(Cursor.GRABBING, List.of(size(32), size(64)));
            backend.setCursorPictures(List.of(GRAB, grabbing));
            var cursors = backend.cursorsForTest();

            window.setCursor(Cursor.GRAB);
            assertEquals(new Sdl3Cursors.PictureKey(Cursor.GRAB, 32), cursors.shownPicture());

            window.setCursor(Cursor.GRABBING);
            assertEquals(
                    new Sdl3Cursors.PictureKey(Cursor.GRABBING, 32),
                    cursors.shownPicture(),
                    "every size goes in on this driver, the 100% one first");

            backend.setCursorPictures(List.of());
            assertNull(cursors.shownPicture(), "taking the pictures away forgets the one shown");
            window.setCursor(Cursor.GRAB);
            assertNull(cursors.shownPicture(), "and the grab is the platform's move again");
        });
    }

    private static void withBackend(BiConsumer<Sdl3Backend, Sdl3Window> body) {
        var previousDriver = System.getProperty(Sdl3Backend.VIDEO_DRIVER_PROPERTY);
        var previousRate = System.getProperty("goldberry.frame.rate");
        System.setProperty(Sdl3Backend.VIDEO_DRIVER_PROPERTY, "dummy");
        System.setProperty("goldberry.frame.rate", "0");
        try (var backend = new Sdl3Backend()) {
            var window = (Sdl3Window) backend.createWindow(WindowSpec.of("cursor", LogicalSize.of(64, 64)));
            body.accept(backend, window);
        } finally {
            restore(Sdl3Backend.VIDEO_DRIVER_PROPERTY, previousDriver);
            restore("goldberry.frame.rate", previousRate);
        }
    }

    private static void restore(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }
}
