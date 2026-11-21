package dev.goldberry.render.backend.sdl3;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.input.dragout.DragOffer;
import dev.goldberry.natives.desktop.dragout.OleFormat;

/// An offer in Windows' clipboard formats, without Windows.
@DisplayName("a drag out on Windows, its formats")
class WindowsDragDataTest {

    private static final Path FILE =
            Path.of("/tmp/goldberry-drag-out/frame.png").toAbsolutePath();

    private static List<String> labels(List<OleFormat> formats) {
        return formats.stream().map(OleFormat::label).toList();
    }

    @Test
    @DisplayName("offers files, a link, text, an image and the application's own types, in that order")
    void order() {
        Supplier<byte[]> png = () -> new byte[] {(byte) 0x89, 'P', 'N', 'G'};
        Supplier<byte[]> board = () -> new byte[] {7};
        var offer = new DragOffer(
                List.of(FILE),
                List.of(URI.create("https://goldberry.dev/board/7")),
                () -> "frame 7",
                png,
                Map.of("application/x-board", board),
                DragOffer.COPY_ONLY,
                null);

        var formats = WindowsDragData.formats(offer);

        assertEquals(
                List.of("CF_15", WindowsDragData.URL, "CF_13", WindowsDragData.PNG, "CF_17", "application/x-board"),
                labels(formats));
        assertArrayEquals(
                WindowsDragData.unicode("https://goldberry.dev/board/7"),
                formats.get(1).bytes().get());
        assertArrayEquals(
                WindowsDragData.unicode("frame 7"), formats.get(2).bytes().get());
        assertArrayEquals(png.get(), formats.get(3).bytes().get());
        assertArrayEquals(new byte[] {7}, formats.get(5).bytes().get());
    }

    @Test
    @DisplayName("offers links as text too, one per line, Windows' way, where there is no text")
    void linksAsText() {
        var formats = WindowsDragData.formats(DragOffer.empty()
                .withLinks(List.of(URI.create("https://a.example/"), URI.create("https://b.example/"))));

        assertEquals(List.of(WindowsDragData.URL, "CF_13"), labels(formats));
        assertArrayEquals(
                WindowsDragData.unicode("https://a.example/\r\nhttps://b.example/"),
                formats.get(1).bytes().get());
    }

    @Test
    @DisplayName("ends text with the NUL C expects and every line with CR LF")
    void text() {
        assertArrayEquals("é\0".getBytes(StandardCharsets.UTF_16LE), WindowsDragData.unicode("é"));
        assertEquals("a\r\nb\r\nc\r\nd", WindowsDragData.windowsLines("a\nb\r\nc\rd"));
    }

    @Test
    @DisplayName("lays CF_HDROP out as DROPFILES, wide paths, and a NUL after the last")
    void dropFiles() {
        var second = Path.of("/tmp/b.txt").toAbsolutePath();
        var bytes = WindowsDragData.dropFiles(List.of(FILE, second));
        var header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);

        assertAll(
                () -> assertEquals(WindowsDragData.DROP_FILES_SIZE, header.getInt(0), "pFiles"),
                () -> assertEquals(0, header.getInt(4)),
                () -> assertEquals(0, header.getInt(8)),
                () -> assertEquals(0, header.getInt(12), "fNC"),
                () -> assertEquals(1, header.getInt(16), "fWide"),
                () -> assertEquals(
                        FILE + "\0" + second + "\0\0",
                        new String(
                                bytes,
                                WindowsDragData.DROP_FILES_SIZE,
                                bytes.length - WindowsDragData.DROP_FILES_SIZE,
                                StandardCharsets.UTF_16LE)));
    }

    @Test
    @DisplayName("lays CF_DIBV5 out bottom-up, BGRA by its masks, alpha made straight")
    void bitmap() {
        // Two rows of one pixel: a half-transparent red on top, a clear one below.
        var stride = 8;
        var pixels = ByteBuffer.allocate(stride * 2);
        pixels.put(0, new byte[] {0, 0, (byte) 0x80, (byte) 0x80});
        pixels.put(stride, new byte[] {5, 5, 5, 0});

        var bytes = WindowsDragData.dibV5(1, 2, stride, pixels);
        var dib = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        var start = WindowsDragData.BITMAP_V5_HEADER_SIZE;

        assertAll(
                () -> assertEquals(WindowsDragData.BITMAP_V5_HEADER_SIZE, dib.getInt(0)),
                () -> assertEquals(1, dib.getInt(4)),
                () -> assertEquals(2, dib.getInt(8), "positive: bottom-up"),
                () -> assertEquals(32, dib.getShort(14)),
                () -> assertEquals(3, dib.getInt(16), "BI_BITFIELDS"),
                () -> assertEquals(8, dib.getInt(20)),
                () -> assertEquals(0x00FF0000, dib.getInt(40)),
                () -> assertEquals(0xFF000000, dib.getInt(52)),
                () -> assertEquals(0x73524742, dib.getInt(56), "LCS_sRGB"),
                () -> assertEquals(4, dib.getInt(108), "LCS_GM_IMAGES"),
                () -> assertEquals(start + 8, bytes.length),
                () -> assertArrayEquals(
                        new byte[] {0, 0, 0, 0}, Arrays.copyOfRange(bytes, start, start + 4), "the clear row first"),
                () -> assertArrayEquals(
                        new byte[] {0, 0, (byte) 0xFF, (byte) 0x80},
                        Arrays.copyOfRange(bytes, start + 4, start + 8),
                        "red, no longer premultiplied"));
    }
}
