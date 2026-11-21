package dev.goldberry.render.backend.sdl3;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.input.dragout.DragOffer;
import dev.goldberry.natives.desktop.dragout.PasteboardFormat;

/// An offer in macOS pasteboard types, without a Mac.
@DisplayName("a drag out on macOS, its pasteboard types")
class MacDragDataTest {

    private static List<String> types(List<PasteboardFormat> formats) {
        return formats.stream()
                .map(format -> (format.mime() ? "mime " : "") + format.type())
                .toList();
    }

    @Test
    @DisplayName("offers a link, text, an image and the application's own types, and leaves the files to their URLs")
    void order() {
        Supplier<byte[]> png = () -> new byte[] {(byte) 0x89, 'P'};
        var offer = new DragOffer(
                List.of(Path.of("/tmp/goldberry-drag-out/frame.png")),
                List.of(URI.create("https://goldberry.dev/board/7")),
                () -> "frame 7",
                png,
                Map.of("application/x-board", () -> new byte[] {7}),
                DragOffer.COPY_ONLY,
                null);

        var formats = MacDragData.formats(offer);

        assertEquals(
                List.of(PasteboardFormat.URL, PasteboardFormat.TEXT, PasteboardFormat.PNG, "mime application/x-board"),
                types(formats));
        assertArrayEquals(
                "https://goldberry.dev/board/7".getBytes(StandardCharsets.UTF_8),
                formats.get(0).bytes().get());
        assertArrayEquals(
                "frame 7".getBytes(StandardCharsets.UTF_8),
                formats.get(1).bytes().get());
        assertArrayEquals(png.get(), formats.get(2).bytes().get());
    }

    @Test
    @DisplayName("offers links as text too, one per line, where there is no text")
    void linksAsText() {
        var formats = MacDragData.formats(DragOffer.empty()
                .withLinks(List.of(URI.create("https://a.example/"), URI.create("https://b.example/"))));

        assertEquals(List.of(PasteboardFormat.URL, PasteboardFormat.TEXT), types(formats));
        assertArrayEquals(
                "https://a.example/\nhttps://b.example/".getBytes(StandardCharsets.UTF_8),
                formats.get(1).bytes().get());
    }

    @Test
    @DisplayName("has no pasteboard item's worth for files alone")
    void filesAlone() {
        assertEquals(List.of(), MacDragData.formats(DragOffer.files(Path.of("/tmp/a.txt"))));
    }
}
