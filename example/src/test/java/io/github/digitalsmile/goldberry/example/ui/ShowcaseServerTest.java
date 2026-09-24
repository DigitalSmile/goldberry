package io.github.digitalsmile.goldberry.example.ui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The showcase's own HTTP server, which the network samples are played from:
/// it answers range requests the way the engine's HTTP reader asks them, and
/// sends no faster than it says.
@DisplayName("The showcase's HTTP server")
class ShowcaseServerTest {

    private static final String CLIP = "mandelbrot.srt";

    private final HttpClient client = HttpClient.newHttpClient();
    private ShowcaseServer server;

    @BeforeEach
    void start() throws IOException {
        // Fast, so the tests that do not measure the throttle do not wait on it.
        server = new ShowcaseServer(16 * 1024 * 1024);
    }

    @AfterEach
    void stop() {
        server.close();
        client.close();
    }

    private static byte[] bundled(String name) throws IOException {
        try (InputStream in = ShowcaseServerTest.class.getResourceAsStream(
                "/io/github/digitalsmile/goldberry/example/media/" + name)) {
            return in.readAllBytes();
        }
    }

    private HttpResponse<byte[]> get(String name, String range) throws Exception {
        var request = HttpRequest.newBuilder(server.uri(name));
        if (range != null) {
            request.header("Range", range);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    @Test
    @DisplayName("serves a bundled clip whole, on the loopback address, and says it takes ranges")
    void whole() throws Exception {
        assertTrue(server.uri(CLIP).getHost().equals("127.0.0.1")
                || server.uri(CLIP).getHost().equals("localhost"));
        var response = get(CLIP, null);
        assertEquals(200, response.statusCode());
        assertEquals("bytes", response.headers().firstValue("Accept-Ranges").orElseThrow());
        assertArrayEquals(bundled(CLIP), response.body());
    }

    @Test
    @DisplayName("answers a range with 206 and the bytes it names, open-ended or closed")
    void ranges() throws Exception {
        var data = bundled(CLIP);
        var closed = get(CLIP, "bytes=10-19");
        assertEquals(206, closed.statusCode());
        assertEquals(
                "bytes 10-19/" + data.length,
                closed.headers().firstValue("Content-Range").orElseThrow());
        assertArrayEquals(Arrays.copyOfRange(data, 10, 20), closed.body());

        var open = get(CLIP, "bytes=100-");
        assertEquals(206, open.statusCode());
        assertArrayEquals(Arrays.copyOfRange(data, 100, data.length), open.body());

        assertEquals(416, get(CLIP, "bytes=" + (data.length + 5) + "-").statusCode());
    }

    @Test
    @DisplayName("has nothing but the bundled clips: an unknown name or a path out is 404")
    void onlyTheClips() throws Exception {
        assertEquals(404, get("no-such-clip.webm", null).statusCode());
        assertEquals(404, get("..%2Fui%2Faudio.kdl", null).statusCode());
    }

    @Test
    @DisplayName("sends no faster than its rate")
    void throttles() throws Exception {
        server.close();
        server = new ShowcaseServer(64 * 1024);
        var started = System.nanoTime();
        var body = get("arpeggio.opus", "bytes=0-65535").body();
        var seconds = (System.nanoTime() - started) / 1e9;
        assertEquals(65_536, body.length);
        // 64 KB at 64 KB a second: the last chunk is due after about 7/8 s.
        assertTrue(seconds >= 0.8, "64 KB came in " + seconds + " s");
    }
}
