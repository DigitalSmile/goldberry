package dev.goldberry.input.dragout;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.render.PixelBuffer;
import dev.goldberry.render.model.PhysicalSize;
import dev.goldberry.render.model.PixelFormat;

@DisplayName("a drag offer")
class DragOfferTest {

    @Test
    @DisplayName("is empty until it carries something, and offers a copy by default")
    void emptyByDefault() {
        assertTrue(DragOffer.empty().isEmpty());
        assertEquals(DragOffer.COPY_ONLY, DragOffer.empty().operations());
        assertFalse(DragOffer.text("x").isEmpty());
        assertFalse(DragOffer.link(URI.create("https://goldberry.dev")).isEmpty());
        assertFalse(DragOffer.image(() -> new byte[0]).isEmpty());
        assertFalse(DragOffer.data("application/x-card", () -> new byte[0]).isEmpty());
        assertFalse(DragOffer.files(Path.of("a.txt")).isEmpty());
    }

    @Test
    @DisplayName("holds its files absolute and normalised, in order, added to rather than replaced")
    void files() {
        var offer = DragOffer.files(Path.of("x/../a.txt")).withFiles(Path.of("/tmp/b.txt"));

        assertEquals(List.of(Path.of("a.txt").toAbsolutePath().normalize(), Path.of("/tmp/b.txt")), offer.files());
    }

    @Test
    @DisplayName("keeps the application's types in the order given, the last bytes winning")
    void dataOrder() {
        var offer = DragOffer.data("application/x-b", () -> new byte[] {1})
                .withData("application/x-a", () -> new byte[] {2})
                .withData("application/x-b", () -> new byte[] {3});

        assertEquals(
                List.of("application/x-b", "application/x-a"),
                List.copyOf(offer.data().keySet()));
        assertArrayEquals(new byte[] {3}, offer.data().get("application/x-b").get());
    }

    @Test
    @DisplayName("refuses a type that is not a MIME type")
    void mime() {
        assertThrows(IllegalArgumentException.class, () -> DragOffer.data("png", () -> new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> DragOffer.data(" ", () -> new byte[0]));
    }

    @Test
    @DisplayName("allows exactly the operations it is told, never none")
    void operations() {
        var offer = DragOffer.text("x").allowing(DragOperation.MOVE, DragOperation.LINK);

        assertEquals(Set.of(DragOperation.MOVE, DragOperation.LINK), offer.operations());
        assertThrows(
                IllegalArgumentException.class,
                () -> new DragOffer(List.of(), List.of(), null, null, Map.of(), Set.of(), null));
    }

    @Test
    @DisplayName("a picture's hotspot must be on it")
    void pictureHotspot() {
        var pixels = PixelBuffer.allocate(new PhysicalSize(4, 3), PixelFormat.BGRA32_PREMULTIPLIED);

        var picture = new DragPicture(pixels, 3, 2);
        assertEquals(picture, DragOffer.text("x").withPicture(picture).picture());
        assertThrows(IllegalArgumentException.class, () -> new DragPicture(pixels, 4, 0));
        assertThrows(IllegalArgumentException.class, () -> new DragPicture(pixels, 0, -1));
    }
}
