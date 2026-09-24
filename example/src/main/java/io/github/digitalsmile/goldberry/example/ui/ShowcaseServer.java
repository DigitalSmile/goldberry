package io.github.digitalsmile.goldberry.example.ui;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.jspecify.annotations.Nullable;

/// A small HTTP server inside the showcase, on the loopback address, so the
/// network samples play offline and still cross a real socket
/// (`docs/goldberry-media.md` §4).
///
/// It serves the bundled clips under `/media/<name>`, answers `Range` requests
/// the way a CDN does (`206 Partial Content`, one range), and sends no faster
/// than [#BYTES_PER_SECOND]. That is a little faster than the Mandelbrot clip
/// plays, so the seek bar's buffered stretch runs ahead of the playhead where it
/// can be seen, and a seek past it waits in BUFFERING while the engine asks for
/// the bytes from there.
///
/// Started on first use and stopped by [#stopShared()], which the showcase calls
/// on the way out. The JDK's server keeps a thread of its own that would
/// otherwise hold the JVM open.
final class ShowcaseServer implements AutoCloseable {

    /// How fast every response is sent: 64 KB a second.
    static final int BYTES_PER_SECOND = 64 * 1024;

    /// The unit the throttle sends in.
    private static final int CHUNK = 8 * 1024;

    private static final Pattern RANGE = Pattern.compile("bytes=(\\d+)-(\\d*)");

    private static @Nullable ShowcaseServer shared;

    private final HttpServer server;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final int bytesPerSecond;

    ShowcaseServer(int bytesPerSecond) throws IOException {
        this.bytesPerSecond = bytesPerSecond;
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/media/", this::serve);
        server.setExecutor(executor);
        server.start();
    }

    /// The server every player of the showcase shares, started on first use.
    static synchronized ShowcaseServer shared() throws IOException {
        if (shared == null) {
            shared = new ShowcaseServer(BYTES_PER_SECOND);
        }
        return shared;
    }

    /// Stops the shared server, if it was ever started.
    static synchronized void stopShared() {
        if (shared != null) {
            shared.close();
            shared = null;
        }
    }

    /// Where the clip `name` is served.
    URI uri(String name) {
        var address = server.getAddress();
        return URI.create("http://" + address.getHostString() + ":" + address.getPort() + "/media/" + name);
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    private void serve(HttpExchange exchange) throws IOException {
        try (exchange) {
            var name = exchange.getRequestURI().getPath().substring("/media/".length());
            var clip = clip(name);
            if (clip.isEmpty() || !exchange.getRequestMethod().equals("GET")) {
                exchange.sendResponseHeaders(clip.isEmpty() ? 404 : 405, -1);
                return;
            }
            var data = clip.get();
            var headers = exchange.getResponseHeaders();
            headers.set("Accept-Ranges", "bytes");
            headers.set("Content-Type", "application/octet-stream");
            var from = 0;
            var to = data.length - 1;
            var range = Optional.ofNullable(exchange.getRequestHeaders().getFirst("Range"))
                    .map(RANGE::matcher)
                    .filter(java.util.regex.Matcher::matches);
            if (range.isPresent()) {
                from = Integer.parseInt(range.get().group(1));
                if (!range.get().group(2).isEmpty()) {
                    to = Math.min(Integer.parseInt(range.get().group(2)), to);
                }
                if (from > to) {
                    headers.set("Content-Range", "bytes */" + data.length);
                    exchange.sendResponseHeaders(416, -1);
                    return;
                }
                headers.set("Content-Range", "bytes " + from + "-" + to + "/" + data.length);
                exchange.sendResponseHeaders(206, to - from + 1L);
            } else {
                exchange.sendResponseHeaders(200, data.length);
            }
            send(exchange, data, from, to + 1);
        } catch (IOException e) {
            // The engine closes a connection it no longer needs, after a seek:
            // the write that finds it closed is the end of that response.
        }
    }

    /// Writes `data[from, to)` at no more than the server's rate.
    private void send(HttpExchange exchange, byte[] data, int from, int to) throws IOException {
        var body = exchange.getResponseBody();
        var started = System.nanoTime();
        var sent = 0L;
        for (var at = from; at < to; at += CHUNK) {
            var count = Math.min(CHUNK, to - at);
            body.write(data, at, count);
            body.flush();
            sent += count;
            var due = started + sent * 1_000_000_000L / bytesPerSecond;
            var wait = due - System.nanoTime();
            if (wait > 0) {
                try {
                    Thread.sleep(wait / 1_000_000L, (int) (wait % 1_000_000L));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private static Optional<byte[]> clip(String name) throws IOException {
        if (name.contains("/") || name.contains("..")) {
            return Optional.empty();
        }
        try (InputStream in =
                ShowcaseServer.class.getResourceAsStream("/io/github/digitalsmile/goldberry/example/media/" + name)) {
            return in == null ? Optional.empty() : Optional.of(in.readAllBytes());
        }
    }
}
