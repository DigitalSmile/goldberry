package dev.goldberry.render.dragout;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.input.dragout.DragOffer;
import dev.goldberry.render.clipboard.UriList;

@DisplayName("an offer as MIME types")
class OfferedDataTest {

    private static final Path FILE = Path.of("/tmp/frame.png");
    private static final URI LINK = URI.create("https://goldberry.dev/board/7");

    @Test
    @DisplayName("names files and links as one uri-list, then text, the image and the application's own types")
    void types() {
        var offer = DragOffer.files(FILE)
                .withLinks(List.of(LINK))
                .withText("Frame 7")
                .withImage(() -> new byte[] {1})
                .withData("application/x-board-frame", () -> new byte[] {2});

        assertEquals(
                List.of(
                        UriList.MIME,
                        OfferedData.TEXT_UTF8,
                        OfferedData.TEXT,
                        OfferedData.PNG,
                        "application/x-board-frame"),
                OfferedData.of(offer).types());
    }

    @Test
    @DisplayName("the uri-list holds the files first and then the links")
    void uriList() {
        var data = OfferedData.of(DragOffer.link(LINK).withFiles(FILE));

        var list = UriList.parse(data.bytes(UriList.MIME).orElseThrow());
        assertEquals(List.of(FILE.toUri(), LINK), list.uris());
    }

    @Test
    @DisplayName("text is UTF-8 under every name a target asks it by")
    void text() {
        var data = OfferedData.of(DragOffer.text("Ünïcode"));

        for (var name : List.of("text/plain", "text/plain;charset=utf-8", "TEXT/PLAIN", "UTF8_STRING", "STRING")) {
            assertArrayEquals(
                    "Ünïcode".getBytes(StandardCharsets.UTF_8), data.bytes(name).orElseThrow(), name);
        }
    }

    @Test
    @DisplayName("links with no text of their own are offered as text, one per line")
    void linksAsText() {
        var other = URI.create("https://goldberry.dev/board/8");
        var data = OfferedData.of(DragOffer.link(LINK).withLinks(List.of(other)));

        assertTrue(data.types().contains(OfferedData.TEXT));
        assertEquals(LINK + "\n" + other, data.text());
    }

    @Test
    @DisplayName("asks a supplier only when a type is read, and each time it is")
    void lazy() {
        var encoded = new AtomicInteger();
        var data = OfferedData.of(DragOffer.image(() -> {
            encoded.incrementAndGet();
            return new byte[] {9};
        }));

        assertEquals(List.of(OfferedData.PNG), data.types());
        assertEquals(0, encoded.get());
        assertArrayEquals(new byte[] {9}, data.bytes("image/png").orElseThrow());
        data.bytes("image/png");
        assertEquals(2, encoded.get());
    }

    @Test
    @DisplayName("the application's own bytes replace a built-in type of the same name")
    void ownTypeWins() {
        var data = OfferedData.of(DragOffer.text("plain").withData("text/plain", () -> new byte[] {7}));

        assertArrayEquals(new byte[] {7}, data.bytes("text/plain").orElseThrow());
        assertEquals(1, data.types().stream().filter("text/plain"::equals).count());
    }

    @Test
    @DisplayName("answers empty for a type it does not offer")
    void notOffered() {
        assertTrue(OfferedData.of(DragOffer.text("x")).bytes("image/png").isEmpty());
        assertTrue(OfferedData.of(DragOffer.text("x")).bytes(UriList.MIME).isEmpty());
    }
}
