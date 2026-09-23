package io.github.digitalsmile.goldberry.media.io;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Stripping ICY metadata, and reading `StreamTitle` out of it (S6).
@DisplayName("IcyStream")
class IcyStreamTest {

    /// `audio` with a metadata block after every `metaint` bytes, the titles in
    /// turn; `null` for an empty block.
    private static byte[] interleave(byte[] audio, int metaint, List<String> titles) {
        var out = new ByteArrayOutputStream();
        var block = 0;
        for (var at = 0; at < audio.length; at += metaint) {
            var count = Math.min(metaint, audio.length - at);
            out.write(audio, at, count);
            if (count == metaint) {
                var title = titles.get(block++ % titles.size());
                out.writeBytes(
                        title == null
                                ? new byte[] {0}
                                : TestHttpServer.metadata("StreamTitle='" + title + "';StreamUrl='';"));
            }
        }
        return out.toByteArray();
    }

    /// Reads everything in reads of `size` bytes.
    private static byte[] drain(InputStream in, int size) throws IOException {
        var out = new ByteArrayOutputStream();
        var buffer = new byte[size];
        int read;
        while ((read = in.read(buffer, 0, size)) >= 0) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    @Test
    @DisplayName("hands on only the audio, whatever the read size, and every title in order")
    void strips() throws IOException {
        var audio = HttpIOTest.pattern(10_000);
        var titles = new ArrayList<String>();
        var stream = new IcyStream(
                new ByteArrayInputStream(interleave(audio, 1_000, java.util.Arrays.asList("A", null, "B"))),
                1_000,
                titles::add);
        assertArrayEquals(audio, drain(stream, 333));
        // Ten blocks: A, (none), B, A, (none), B, ...
        assertEquals(List.of("A", "B", "A", "B", "A", "B", "A"), titles);
    }

    @Test
    @DisplayName("reads a byte at a time too")
    void singleBytes() throws IOException {
        var audio = HttpIOTest.pattern(50);
        var stream = new IcyStream(new ByteArrayInputStream(interleave(audio, 16, List.of("x"))), 16, _ -> {});
        var out = new ByteArrayOutputStream();
        int read;
        while ((read = stream.read()) >= 0) {
            out.write(read);
        }
        assertArrayEquals(audio, out.toByteArray());
        assertEquals(0, stream.read(new byte[4], 0, 0));
    }

    @Test
    @DisplayName("a stream cut off inside a metadata block ends there")
    void cutShort() throws IOException {
        var audio = HttpIOTest.pattern(100);
        var whole = interleave(audio, 100, List.of("A long title that needs two blocks"));
        var cut = java.util.Arrays.copyOf(whole, 100 + 5);
        var titles = new ArrayList<String>();
        var stream = new IcyStream(new ByteArrayInputStream(cut), 100, titles::add);
        assertArrayEquals(audio, drain(stream, 64));
        assertEquals(List.of(), titles);
    }

    @Test
    @DisplayName("refuses an interval that is not positive")
    void interval() {
        assertThrows(IllegalArgumentException.class, () -> new IcyStream(InputStream.nullInputStream(), 0, _ -> {}));
    }

    @Test
    @DisplayName("reads fields whose values hold quotes of their own")
    void quotes() {
        assertEquals(
                Map.of("StreamTitle", "Don't Stop - It's 'Live'", "StreamUrl", "http://x/?a=1';b"),
                IcyStream.fields(bytes("StreamTitle='Don't Stop - It's 'Live'';StreamUrl='http://x/?a=1';b';")));
        assertEquals(Map.of("StreamTitle", ""), IcyStream.fields(bytes("StreamTitle='';\0\0\0\0")));
        // No closing `';`: the value runs to its last quote.
        assertEquals(Map.of("StreamTitle", "Unterminated"), IcyStream.fields(bytes("StreamTitle='Unterminated'")));
        assertEquals(Map.of("StreamTitle", "No quote"), IcyStream.fields(bytes("StreamTitle='No quote")));
        assertEquals(Map.of(), IcyStream.fields(bytes("garbage")));
    }

    @Test
    @DisplayName("decodes UTF-8, and falls back to Latin-1 for what is not")
    void charsets() {
        assertEquals(
                "Sigur Rós - Hoppípolla",
                IcyStream.fields("StreamTitle='Sigur Rós - Hoppípolla';".getBytes(StandardCharsets.UTF_8))
                        .get("StreamTitle"));
        assertEquals(
                "Café",
                IcyStream.fields("StreamTitle='Café';".getBytes(StandardCharsets.ISO_8859_1))
                        .get("StreamTitle"));
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }
}
