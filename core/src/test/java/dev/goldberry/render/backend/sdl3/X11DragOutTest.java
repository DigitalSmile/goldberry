package dev.goldberry.render.backend.sdl3;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.input.dragout.DragOffer;
import dev.goldberry.render.clipboard.UriList;
import dev.goldberry.render.dragout.OfferedData;

/// What an offer looks like to X11 targets, without an X server.
@DisplayName("a drag out on X11, as the targets see the offer")
class X11DragOutTest {

    @Test
    @DisplayName("offers UTF8_STRING beside UTF-8 text, for the clients that ask by the old name")
    void utf8String() {
        var data = X11DragOut.data(DragOffer.text("Frame 7"));

        assertEquals(List.of(OfferedData.TEXT_UTF8, OfferedData.TEXT, "UTF8_STRING"), data.types());
        assertArrayEquals("Frame 7".getBytes(StandardCharsets.UTF_8), data.bytes("UTF8_STRING"));
    }

    @Test
    @DisplayName("answers null for a type it does not offer")
    void notOffered() {
        assertEquals(null, X11DragOut.data(DragOffer.text("x")).bytes("image/png"));
    }

    @Test
    @DisplayName("writes an image to a PNG file as well, for the targets that take only files")
    void imageAsFile() throws Exception {
        var png = new byte[] {(byte) 0x89, 'P', 'N', 'G'};
        var offer = X11DragOut.withImageFile(DragOffer.image(() -> png));

        assertEquals(1, offer.files().size());
        Path file = offer.files().getFirst();
        assertTrue(file.getFileName().toString().endsWith(".png"));
        assertArrayEquals(png, Files.readAllBytes(file));
        var types = X11DragOut.data(offer).types();
        assertEquals(List.of(UriList.MIME, OfferedData.PNG), types);
        Files.deleteIfExists(file);
    }

    @Test
    @DisplayName("leaves an offer without an image as it is")
    void noImage() {
        var offer = DragOffer.text("x");

        assertSame(offer, X11DragOut.withImageFile(offer));
    }
}
