package dev.goldberry.media.io;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The test server's radio: what a station sends is MPEG frames, over and over,
/// and never the file's tag.
@DisplayName("TestHttpServer's radio")
class TestHttpServerTest {

    private static final byte[] ID3 = {'I', 'D', '3'};

    @Test
    @DisplayName("measures an ID3v2 tag by its syncsafe size, and nothing without one")
    void tagLength() {
        var tone = tone();
        assertEquals(44, TestHttpServer.id3v2Length(tone), "tone.mp3 opens with a 34-byte tag under a 10-byte header");
        assertEquals(0, TestHttpServer.id3v2Length(new byte[] {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'A', 'V', 'E'}));
        assertEquals(0, TestHttpServer.id3v2Length(new byte[] {'I', 'D'}));
        var oversized = new byte[] {'I', 'D', '3', 4, 0, 0, 0x7f, 0x7f, 0x7f, 0x7f, 0};
        assertEquals(oversized.length, TestHttpServer.id3v2Length(oversized), "a size past the file is the file");
    }

    @Test
    @DisplayName("loops the frames and leaves the tag out of every pass")
    void loopsFramesOnly() throws IOException, InterruptedException {
        var tone = tone();
        var frames = Arrays.copyOfRange(tone, 44, tone.length);
        try (var server = new TestHttpServer(tone);
                var client = HttpClient.newHttpClient()) {
            server.icy = 8_192;
            var response = client.send(
                    HttpRequest.newBuilder(server.uri("stream")).GET().build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            try (var body = response.body()) {
                var passes = 3;
                var heard = body.readNBytes(frames.length * passes);
                for (var pass = 0; pass < passes; pass++) {
                    assertArrayEquals(
                            frames,
                            Arrays.copyOfRange(heard, pass * frames.length, (pass + 1) * frames.length),
                            "pass " + pass);
                }
                assertEquals(-1, indexOf(heard, ID3), "a tag reached the stream");
            }
        }
    }

    /// The one-second MP3 the radio tests loop: an ID3v2 tag, then the frames.
    private static byte[] tone() {
        try (var in = TestHttpServerTest.class.getResourceAsStream("/dev/goldberry/media/fixtures/tone.mp3")) {
            if (in == null) {
                throw new IllegalStateException("tone.mp3 is not on the test class path");
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        for (var i = 0; i <= haystack.length - needle.length; i++) {
            if (Arrays.equals(haystack, i, i + needle.length, needle, 0, needle.length)) {
                return i;
            }
        }
        return -1;
    }
}
