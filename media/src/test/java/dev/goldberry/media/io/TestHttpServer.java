package dev.goldberry.media.io;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/// A local HTTP server serving one resource, with the faults a test switches on:
/// the server half of `docs/goldberry-media.md` §9's fault-injecting network,
/// for [HttpIO]'s tests and the Engine's S3 and S6.
///
/// Every path serves the same bytes. By default it answers `Range` with `206`
/// and sends a `Content-Length`. The switches:
///
/// - [#ranges] off: every answer is a `200` with the whole resource, as a server
///   that ignores `Range`.
/// - [#contentLength] off: a `200` is sent chunked, with no length.
/// - [#status]: every request is answered with that status and no body.
/// - [#contentRange]: when not empty, the `Content-Range` every `206` sends,
///   whatever was asked for, as a server that gets it wrong.
/// - [#dropAfter] and [#drops]: the first `drops` connections close after
///   `dropAfter` bytes of body, short of their `Content-Length`.
/// - [#stallAt]: the first connection to reach that resource offset stops sending
///   until [#release()] (or 30 s), as a link that goes quiet without closing.
/// - [#icy]: an internet radio station. `200`, no length, `icy-metaint`, and the
///   resource looped forever with a metadata block every `metaint` bytes, whose
///   `StreamTitle` goes through [#titles], one per block.
///
/// [#requests()] records each request's `Range` header, "" for none, and
/// [#marks()] its `X-Goldberry` header, so a test can see a source's headers go
/// out.
public final class TestHttpServer implements AutoCloseable {

    private static final Pattern RANGE = Pattern.compile("bytes=(\\d+)-");
    private static final int BLOCK = 8 * 1024;

    private final HttpServer server;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final byte[] data;
    private final List<String> requests = Collections.synchronizedList(new ArrayList<>());
    private final List<String> marks = Collections.synchronizedList(new ArrayList<>());
    private final AtomicInteger dropped = new AtomicInteger();
    private final AtomicInteger stalls = new AtomicInteger();
    private final CountDownLatch released = new CountDownLatch(1);

    /// Whether `Range` is answered.
    public volatile boolean ranges = true;

    /// Whether a `200` says how long it is.
    public volatile boolean contentLength = true;

    /// When not 0, the status every request gets.
    public volatile int status;

    /// When not empty, the `Content-Range` every `206` sends instead of its own.
    public volatile String contentRange = "";

    /// How many body bytes a dropped connection sends; -1 for none dropped.
    public volatile long dropAfter = -1;

    /// How many connections are dropped.
    public volatile int drops = 1;

    /// The resource offset at which one connection stalls; -1 for none.
    public volatile long stallAt = -1;

    /// The ICY metadata interval, or 0 for an ordinary server.
    public volatile int icy;

    /// The titles an ICY stream announces, one per metadata block, in turn.
    public volatile List<String> titles = List.of("Goldberry - Test Tone");

    public TestHttpServer(byte[] data) throws IOException {
        this.data = data.clone();
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(executor);
        server.createContext("/", this::handle);
        server.start();
    }

    /// Where `name` is served.
    public URI uri(String name) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/" + name);
    }

    /// Each request's `Range` header, in order: "" for a request with none.
    public List<String> requests() {
        synchronized (requests) {
            return List.copyOf(requests);
        }
    }

    /// Each request's `X-Goldberry` header, in order: "" for a request with none.
    public List<String> marks() {
        synchronized (marks) {
            return List.copyOf(marks);
        }
    }

    /// How many connections have stalled at [#stallAt].
    public int stalls() {
        return stalls.get();
    }

    /// Lets a stalled connection carry on.
    public void release() {
        released.countDown();
    }

    @Override
    public void close() {
        release();
        server.stop(0);
        executor.shutdownNow();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            var range = exchange.getRequestHeaders().getFirst("Range");
            requests.add(range == null ? "" : range);
            var mark = exchange.getRequestHeaders().getFirst("X-Goldberry");
            marks.add(mark == null ? "" : mark);
            if (status != 0) {
                exchange.sendResponseHeaders(status, -1);
                return;
            }
            if (icy > 0) {
                radio(exchange);
                return;
            }
            var start = 0L;
            var partial = false;
            if (ranges && range != null) {
                var matcher = RANGE.matcher(range);
                if (matcher.matches()) {
                    start = Long.parseLong(matcher.group(1));
                    partial = true;
                }
            }
            var headers = exchange.getResponseHeaders();
            headers.set("Content-Type", "application/octet-stream");
            if (ranges) {
                headers.set("Accept-Ranges", "bytes");
            }
            if (partial && start >= data.length) {
                headers.set("Content-Range", "bytes */" + data.length);
                exchange.sendResponseHeaders(416, -1);
                return;
            }
            var length = data.length - start;
            if (partial) {
                var sent = contentRange;
                headers.set(
                        "Content-Range",
                        sent.isEmpty() ? "bytes " + start + "-" + (data.length - 1) + "/" + data.length : sent);
                exchange.sendResponseHeaders(206, length);
            } else {
                exchange.sendResponseHeaders(200, contentLength ? length : 0);
            }
            send(exchange.getResponseBody(), (int) start);
        } catch (IOException e) {
            // The client went away: an abandoned connection, which is expected.
        }
    }

    private void send(OutputStream out, int start) throws IOException {
        var drop = dropAfter >= 0 && dropped.getAndIncrement() < drops ? dropAfter : -1;
        var at = start;
        var sent = 0L;
        while (at < data.length) {
            var count = Math.min(BLOCK, data.length - at);
            if (drop >= 0) {
                count = (int) Math.min(count, drop - sent);
                if (count <= 0) {
                    // Short of the promised length: the client sees the connection
                    // close early.
                    throw new IOException("dropped on purpose");
                }
            }
            var stall = stallAt;
            if (stall > at && stall < at + count) {
                // Up to the stall exactly, then stall on the next pass.
                count = (int) (stall - at);
            } else if (stall == at && stalls.compareAndSet(0, 1)) {
                out.flush();
                awaitRelease();
            }
            out.write(data, at, count);
            at += count;
            sent += count;
        }
    }

    /// An endless ICY stream of the resource, looped.
    private void radio(HttpExchange exchange) throws IOException {
        var metaint = icy;
        var headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "audio/mpeg");
        headers.set("icy-name", "Goldberry test radio");
        var wantsMetadata = "1".equals(exchange.getRequestHeaders().getFirst("Icy-MetaData"));
        if (wantsMetadata) {
            headers.set("icy-metaint", Integer.toString(metaint));
        }
        exchange.sendResponseHeaders(200, 0);
        var out = exchange.getResponseBody();
        var at = 0;
        var block = 0;
        while (true) {
            var sent = 0;
            while (sent < metaint) {
                var count = Math.min(metaint - sent, data.length - at);
                out.write(data, at, count);
                sent += count;
                at = (at + count) % data.length;
            }
            if (wantsMetadata) {
                var names = titles;
                out.write(metadata("StreamTitle='" + names.get(block % names.size()) + "';StreamUrl='';"));
                block++;
            }
            out.flush();
        }
    }

    /// One metadata block: the length byte, then the text padded to 16s.
    static byte[] metadata(String text) {
        var bytes = text.getBytes(StandardCharsets.UTF_8);
        var blocks = (bytes.length + 15) / 16;
        var block = new byte[1 + blocks * 16];
        block[0] = (byte) blocks;
        System.arraycopy(bytes, 0, block, 1, bytes.length);
        return block;
    }

    private void awaitRelease() {
        try {
            released.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
