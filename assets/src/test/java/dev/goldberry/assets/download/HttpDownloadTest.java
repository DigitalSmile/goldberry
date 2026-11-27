package dev.goldberry.assets.download;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

@Timeout(15)
class HttpDownloadTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(2);
    private static final byte[] COMPLETE = "complete archive".getBytes(StandardCharsets.UTF_8);

    enum Stall {
        HEADERS,
        BODY
    }

    @ParameterizedTest
    @EnumSource(Stall.class)
    @DisplayName("a stalled HTTP response is retried and only the complete body is left in the target")
    void retriesAStalledDownload(Stall stall, @TempDir Path directory) throws IOException {
        var waits = new ArrayList<Duration>();
        try (var server = new StallingServer(stall);
                var downloader = Downloader.standard(TIMEOUT, 2, waits::add)) {
            var target = directory.resolve("archive.zip");
            Files.writeString(target, "older archive with more bytes");

            downloader.copyTo(server.uri(), target);

            assertArrayEquals(COMPLETE, Files.readAllBytes(target));
            assertEquals(2, server.requests.get());
            assertEquals(List.of(Downloader.FIRST_WAIT), waits);
        }
    }

    @Test
    @DisplayName("an exhausted body timeout reports the URL and the stalled read")
    void reportsTheBodyTimeout() throws IOException {
        try (var server = new StallingServer(Stall.BODY);
                var downloader = Downloader.standard(TIMEOUT, 1, _ -> {})) {
            var uri = server.uri();
            var failure = assertThrows(IOException.class, () -> downloader.readAll(uri));

            var timeout = assertInstanceOf(HttpTimeoutException.class, failure.getCause());
            assertTrue(timeout.getMessage().contains("no response data"), timeout::getMessage);
            assertTrue(timeout.getMessage().contains(uri.toString()), timeout::getMessage);
            assertEquals(1, server.requests.get());
        }
    }

    /// Holds the first exchange open without finishing it. The second request
    /// can proceed while the stalled handler is still waiting.
    private static final class StallingServer implements AutoCloseable {

        private final AtomicInteger requests = new AtomicInteger();
        private final CountDownLatch release = new CountDownLatch(1);
        private final HttpServer server;

        StallingServer(Stall stall) throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(command -> Thread.startVirtualThread(command));
            server.createContext("/archive", exchange -> respond(exchange, stall));
            server.start();
        }

        URI uri() {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/archive");
        }

        private void respond(HttpExchange exchange, Stall stall) throws IOException {
            try (exchange) {
                var first = requests.incrementAndGet() == 1;
                if (!first || stall == Stall.BODY) {
                    exchange.sendResponseHeaders(200, COMPLETE.length);
                    var output = exchange.getResponseBody();
                    if (!first) {
                        output.write(COMPLETE);
                        return;
                    }
                    output.write('x');
                    output.flush();
                }
                try {
                    release.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        @Override
        public void close() {
            release.countDown();
            server.stop(0);
        }
    }
}
