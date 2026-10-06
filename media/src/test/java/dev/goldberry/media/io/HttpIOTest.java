package dev.goldberry.media.io;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// `HttpIO` against a local server: Range, the read-ahead cache, reconnects,
/// stalls, timeouts, ICY, and the abort.
///
/// No FFmpeg here: this is the byte stream the demuxer would read, checked byte
/// for byte against what the server holds.
///
/// Nothing here compares a measured duration with a bound. A read that must
/// give up is asserted to give up, and the test's own timeout catches one that
/// sat a stall out instead; how long the giving up took is the machine's.
@DisplayName("HttpIO")
class HttpIOTest {

    private static final int SIZE = 300_000;
    private static final byte[] DATA = pattern(SIZE);

    /// Short waits, so the fault tests take milliseconds.
    private static final HttpIO.Options FAST = HttpIO.Options.DEFAULT
            .withReconnects(3, Duration.ofMillis(10), Duration.ofMillis(40))
            .withStallTimeout(Duration.ofMillis(300));

    private static HttpClient client;
    private TestHttpServer server;
    private HttpIO io;

    @BeforeAll
    static void client() {
        client = HttpClient.newHttpClient();
    }

    @AfterAll
    static void closeClient() {
        client.close();
    }

    @AfterEach
    void close() {
        if (io != null) {
            io.close();
        }
        if (server != null) {
            server.close();
        }
    }

    /// Bytes that say where they are, so a byte from the wrong offset shows.
    static byte[] pattern(int size) {
        var bytes = new byte[size];
        for (var i = 0; i < size; i++) {
            bytes[i] = (byte) (i * 31 + (i >>> 8));
        }
        return bytes;
    }

    private HttpIO open(byte[] data, HttpIO.Options options) throws IOException {
        server = new TestHttpServer(data);
        return open(Source.of(server.uri("clip.bin")), options);
    }

    private HttpIO open(Source source, HttpIO.Options options) throws IOException {
        io = HttpIO.open(source, client, options);
        return io;
    }

    /// Everything from the position to the end.
    static byte[] readAll(MediaIO io) throws IOException {
        var out = new ByteArrayOutputStream();
        var buffer = ByteBuffer.allocate(32 * 1024);
        while (true) {
            buffer.clear();
            var read = io.read(buffer);
            if (read < 0) {
                return out.toByteArray();
            }
            out.write(buffer.array(), 0, read);
        }
    }

    static byte[] read(MediaIO io, int count) throws IOException {
        var buffer = ByteBuffer.allocate(count);
        while (buffer.hasRemaining()) {
            if (io.read(buffer) < 0) {
                break;
            }
        }
        return Arrays.copyOf(buffer.array(), buffer.position());
    }

    static void await(BooleanSupplier condition) {
        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("timed out");
            }
            Thread.onSpinWait();
        }
    }

    @Nested
    @DisplayName("a server that answers Range")
    class Ranged {

        @Test
        @DisplayName("is seekable, knows its size, is not live, and reads every byte")
        void readsEverything() throws IOException {
            open(DATA, FAST);
            assertTrue(io.isSeekable());
            assertFalse(io.isLive());
            assertEquals(OptionalLong.of(SIZE), io.size());
            assertArrayEquals(DATA, readAll(io));
            assertEquals(SIZE, io.position());
            assertEquals(-1, io.read(ByteBuffer.allocate(1)));
            assertEquals(List.of("bytes=0-"), server.requests());
        }

        @Test
        @DisplayName("sends the source's headers with every request")
        void sendsHeaders() throws IOException {
            server = new TestHttpServer(DATA);
            open(Source.of(server.uri("clip.bin")).withHeader("X-Goldberry", "brd"), FAST);
            io.seek(SIZE - 10);
            readAll(io);
            assertTrue(server.marks().size() >= 2, server.marks().toString());
            assertTrue(
                    server.marks().stream().allMatch("brd"::equals),
                    server.marks().toString());
        }

        @Test
        @DisplayName("a seek lands on the right byte")
        void seeks() throws IOException {
            open(DATA, FAST);
            io.seek(123_457);
            assertEquals(123_457, io.position());
            assertArrayEquals(Arrays.copyOfRange(DATA, 123_457, 123_457 + 1000), read(io, 1000));
            io.seek(7);
            assertArrayEquals(Arrays.copyOfRange(DATA, 7, 40), read(io, 33));
        }

        @Test
        @DisplayName("a seek into what is cached sends no request")
        void seekInsideTheCache() throws IOException {
            open(DATA, FAST);
            assertArrayEquals(DATA, readAll(io));
            await(() -> io.buffered().equals(List.of(new ByteRange(0, SIZE))));
            var before = io.requests();
            io.seek(1000);
            assertArrayEquals(Arrays.copyOfRange(DATA, 1000, 5000), read(io, 4000));
            io.seek(SIZE - 100);
            assertArrayEquals(Arrays.copyOfRange(DATA, SIZE - 100, SIZE), readAll(io));
            assertEquals(before, io.requests());
        }

        @Test
        @DisplayName("a seek outside the cache opens a Range at the new place, under a new connection")
        void seekOutsideTheCache() throws IOException {
            // A read-ahead of four chunks: the far end of the file is not fetched
            // until asked for.
            var small = FAST.withBuffer(4L * ReadAheadCache.CHUNK, 8L * ReadAheadCache.CHUNK);
            var large = pattern(2_000_000);
            open(large, small);
            read(io, 10);
            io.seek(1_500_000);
            assertArrayEquals(Arrays.copyOfRange(large, 1_500_000, 1_510_000), read(io, 10_000));
            assertTrue(
                    server.requests().contains("bytes=1500000-"),
                    server.requests().toString());
        }

        @Test
        @DisplayName("reads ahead only so far, and reports what it holds as buffered")
        void boundedReadAhead() throws IOException {
            var small = FAST.withBuffer(4L * ReadAheadCache.CHUNK, 8L * ReadAheadCache.CHUNK);
            open(pattern(2_000_000), small);
            read(io, 100);
            await(() -> !io.buffered().isEmpty() && io.buffered().getFirst().end() >= 4L * ReadAheadCache.CHUNK);
            // Give the fetcher every chance to overrun.
            sleep(100);
            var buffered = io.buffered();
            assertEquals(1, buffered.size());
            assertEquals(0, buffered.getFirst().start());
            assertTrue(buffered.getFirst().end() <= 100 + 4L * ReadAheadCache.CHUNK + 16 * 1024, buffered.toString());
        }

        @Test
        @DisplayName("a dropped connection resumes by Range at the byte where it broke")
        void resumesAfterADrop() throws IOException {
            server = new TestHttpServer(DATA);
            server.dropAfter = 50_000;
            server.drops = 2;
            open(Source.of(server.uri("clip.bin")), FAST);
            assertArrayEquals(DATA, readAll(io));
            // Each reconnect asks from the first byte the last one did not deliver:
            // at most where the server broke off, and less by what the client had
            // received and not yet handed on when the connection failed.
            var requests = server.requests();
            assertEquals(3, requests.size(), requests.toString());
            assertEquals("bytes=0-", requests.getFirst());
            var first = offset(requests.get(1));
            var second = offset(requests.get(2));
            assertTrue(first > 0 && first <= 50_000, requests.toString());
            assertTrue(second > first && second <= first + 50_000, requests.toString());
        }

        @Test
        @DisplayName("a connection that goes quiet is dropped after the stall timeout and made again")
        void recoversFromAStall() throws IOException {
            server = new TestHttpServer(DATA);
            server.stallAt = 100_000;
            open(Source.of(server.uri("clip.bin")), FAST);
            var started = System.nanoTime();
            assertArrayEquals(DATA, readAll(io));
            assertEquals(1, server.stalls());
            var requests = server.requests();
            assertEquals(2, requests.size(), requests.toString());
            assertTrue(offset(requests.get(1)) <= 100_000, requests.toString());
            assertTrue(System.nanoTime() - started >= TimeUnit.MILLISECONDS.toNanos(250));
        }

        @Test
        @DisplayName("gives up after the reconnect limit, and a seek tries again")
        void givesUp() throws IOException {
            server = new TestHttpServer(DATA);
            server.stallAt = 50_000;
            open(Source.of(server.uri("clip.bin")), FAST);
            // The link goes quiet, and every reconnect finds the server down for
            // maintenance: a 503 is worth retrying, three times.
            server.status = 503;
            var failure = assertThrows(IOException.class, () -> readAll(io));
            assertTrue(failure.getMessage().contains("gave up"), failure.getMessage());
            // The first request, then a reconnect and three more.
            assertEquals(1 + 3, server.requests().size(), server.requests().toString());
            server.status = 0;
            io.seek(0);
            assertArrayEquals(DATA, readAll(io));
        }

        @Test
        @Timeout(5)
        @DisplayName("a resource that disappears fails at once rather than retrying")
        void goneIsFinal() throws IOException {
            server = new TestHttpServer(DATA);
            server.stallAt = 50_000;
            open(
                    Source.of(server.uri("clip.bin")),
                    FAST.withReconnects(8, Duration.ofMillis(200), Duration.ofSeconds(5)));
            server.status = 404;
            // Eight attempts would take over 6 s of backoff; the 404 that answers
            // the first is final. The request count says so exactly, and the
            // test's timeout is short of what eight attempts would take.
            var failure = assertThrows(HttpStatusException.class, () -> readAll(io));
            assertEquals(404, failure.status());
            assertEquals(2, server.requests().size());
        }

        @Test
        @Timeout(20)
        @DisplayName("a read gives up after the source's timeout, not before")
        void timesOut() throws IOException {
            server = new TestHttpServer(DATA);
            // The open waits on the same timeout for the headers. A client's first
            // connection and a server's first request pay for their own start-up,
            // which a loaded runner has stretched past two seconds; so the first
            // connection is an untimed one that reads a little and closes, and the
            // timed open comes second, to a server that is already answering.
            open(Source.of(server.uri("clip.bin")), FAST);
            read(io, 10);
            io.close();
            server.stallAt = 0;
            var timeout = Duration.ofSeconds(2);
            open(Source.of(server.uri("clip.bin")).withTimeout(timeout), FAST.withStallTimeout(Duration.ofSeconds(30)));
            var started = System.nanoTime();
            assertThrows(HttpTimeoutException.class, () -> read(io, 10));
            var waited = Duration.ofNanos(System.nanoTime() - started);
            // At least the timeout, less a tick of a coarse clock: Windows' timer
            // advances in steps of about 16 ms. A lower bound only: a loaded
            // machine can make the wait longer and never shorter. A read that
            // waited the 30 s stall out instead would not throw this, and the
            // test's timeout is short of the stall besides.
            assertTrue(waited.compareTo(timeout.minusMillis(50)) >= 0, () -> "gave up after " + waited);
        }

        @Test
        @DisplayName("closing ends a read blocked on the network")
        void closeAborts() throws Exception {
            server = new TestHttpServer(DATA);
            server.stallAt = 0;
            open(Source.of(server.uri("clip.bin")), FAST.withStallTimeout(Duration.ofSeconds(30)));
            var reading = CompletableFuture.supplyAsync(() -> {
                try {
                    return read(io, 10).length;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            sleep(100);
            io.close();
            var failure = assertThrows(ExecutionException.class, () -> reading.get(5, TimeUnit.SECONDS));
            assertInstanceOf(ClosedChannelException.class, failure.getCause().getCause());
            assertThrows(ClosedChannelException.class, () -> io.read(ByteBuffer.allocate(1)));
            assertThrows(ClosedChannelException.class, () -> io.seek(0));
            assertEquals(List.of(), io.buffered());
            io.close();
        }

        @Test
        @DisplayName("asked from its end, reads nothing")
        void pastTheEnd() throws IOException {
            open(DATA, FAST);
            io.seek(SIZE + 10);
            assertEquals(-1, io.read(ByteBuffer.allocate(10)));
            assertEquals(0, io.read(ByteBuffer.allocate(0)));
        }

        @Test
        @DisplayName("refuses a negative position")
        void negativeSeek() throws IOException {
            open(DATA, FAST);
            assertThrows(IOException.class, () -> io.seek(-1));
        }
    }

    @Nested
    @DisplayName("a server that ignores Range")
    class Unranged {

        @Test
        @DisplayName("is not seekable, and still knows its size and ends")
        void notSeekable() throws IOException {
            server = new TestHttpServer(DATA);
            server.ranges = false;
            open(Source.of(server.uri("clip.bin")), FAST);
            assertFalse(io.isSeekable());
            assertFalse(io.isLive());
            assertEquals(OptionalLong.of(SIZE), io.size());
            assertThrows(IOException.class, () -> io.seek(10));
            assertArrayEquals(DATA, readAll(io));
        }

        @Test
        @DisplayName("after a drop, fetches it all again and skips what it had")
        void resumesBySkipping() throws IOException {
            server = new TestHttpServer(DATA);
            server.ranges = false;
            server.dropAfter = 120_000;
            open(Source.of(server.uri("clip.bin")), FAST);
            assertArrayEquals(DATA, readAll(io));
            assertEquals(2, server.requests().size());
        }

        @Test
        @DisplayName("with no length either, is live, and ends where the body does")
        void noLength() throws IOException {
            server = new TestHttpServer(DATA);
            server.ranges = false;
            server.contentLength = false;
            open(Source.of(server.uri("clip.bin")), FAST);
            assertFalse(io.isSeekable());
            assertTrue(io.isLive());
            assertEquals(OptionalLong.empty(), io.size());
            assertArrayEquals(DATA, readAll(io));
            assertEquals(1, server.requests().size());
        }
    }

    @Nested
    @DisplayName("an ICY radio station")
    class Radio {

        @Test
        @DisplayName("hands on only the audio, and names what is playing")
        void stripsMetadata() throws IOException {
            server = new TestHttpServer(DATA);
            server.icy = 4_096;
            server.titles = List.of("First Song", "Second Song");
            open(Source.of(server.uri("radio")), FAST);
            assertTrue(io.isLive());
            assertFalse(io.isSeekable());
            assertEquals(OptionalLong.empty(), io.size());
            assertEquals(Optional.empty(), io.nowPlaying());
            // Three times round the loop: the metadata blocks are gone, and the
            // audio is the resource over and over.
            var audio = read(io, SIZE * 3);
            for (var lap = 0; lap < 3; lap++) {
                assertArrayEquals(DATA, Arrays.copyOfRange(audio, lap * SIZE, (lap + 1) * SIZE), "lap " + lap);
            }
            // At 300,000 bytes in, the 73rd block (every 4096) has passed; the
            // titles alternate, starting with the first.
            var title = io.nowPlaying().orElseThrow();
            assertTrue(title.equals("First Song") || title.equals("Second Song"), title);
        }

        @Test
        @DisplayName("says what is playing at the reader, not at the fetcher")
        void titleFollowsTheReader() throws IOException {
            server = new TestHttpServer(DATA);
            server.icy = 4_096;
            server.titles = List.of("First Song", "Second Song");
            open(Source.of(server.uri("radio")), FAST);
            read(io, 10);
            // The fetcher has read far ahead, through many blocks. The first title
            // takes effect at audio byte 4096 and the reader is short of it.
            await(() -> io.buffered().getFirst().end() > 100_000);
            assertEquals(Optional.empty(), io.nowPlaying());
            read(io, 4_096);
            assertEquals(Optional.of("First Song"), io.nowPlaying());
            read(io, 4_096);
            assertEquals(Optional.of("Second Song"), io.nowPlaying());
        }

        @Test
        @DisplayName("asks for no metadata when told not to, and gets the raw stream")
        void withoutMetadata() throws IOException {
            server = new TestHttpServer(DATA);
            server.icy = 4_096;
            open(Source.of(server.uri("radio")), FAST.withIcyMetadata(false));
            assertTrue(io.isLive());
            assertArrayEquals(DATA, read(io, SIZE));
            assertEquals(Optional.empty(), io.nowPlaying());
        }
    }

    @Test
    @DisplayName("an error status fails the open, with the status")
    void errorStatus() throws IOException {
        server = new TestHttpServer(DATA);
        server.status = 404;
        var failure = assertThrows(HttpStatusException.class, () -> open(Source.of(server.uri("gone")), FAST));
        assertEquals(404, failure.status());
        assertEquals(server.uri("gone"), failure.uri());
        assertTrue(failure.getMessage().contains("404"));
    }

    @Test
    @DisplayName("a Content-Range too long for a long fails the open as I/O, not as a number")
    void contentRangeOverflow() throws IOException {
        server = new TestHttpServer(DATA);
        server.contentRange = "bytes 0-99/99999999999999999999";
        var failure = assertThrows(IOException.class, () -> open(Source.of(server.uri("clip.bin")), FAST));
        assertTrue(failure.getMessage().contains("unreadable Content-Range"), failure.getMessage());
        assertInstanceOf(NumberFormatException.class, failure.getCause());
    }

    @Test
    @DisplayName("an unreachable server fails the open")
    void unreachable() throws IOException {
        // A port that was free a moment ago: nothing listens there now.
        int port;
        try (var socket = new java.net.ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        var source = Source.of(URI.create("http://127.0.0.1:" + port + "/x")).withTimeout(Duration.ofSeconds(2));
        assertThrows(IOException.class, () -> open(source, FAST));
    }

    @Test
    @DisplayName("opens only http and https")
    void schemes() {
        assertThrows(
                IllegalArgumentException.class,
                () -> HttpIO.open(Source.of(URI.create("ftp://example.com/x")), client, FAST));
    }

    @Test
    @DisplayName("refuses options that leave the cache no room past the read-ahead")
    void options() {
        assertThrows(IllegalArgumentException.class, () -> HttpIO.Options.DEFAULT.withBuffer(1 << 20, 1 << 20));
        assertThrows(IllegalArgumentException.class, () -> HttpIO.Options.DEFAULT.withBuffer(0, 1 << 20));
        assertThrows(
                IllegalArgumentException.class,
                () -> HttpIO.Options.DEFAULT.withReconnects(-1, Duration.ofMillis(1), Duration.ofMillis(1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> HttpIO.Options.DEFAULT.withReconnects(1, Duration.ofSeconds(2), Duration.ofSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> HttpIO.Options.DEFAULT.withStallTimeout(Duration.ZERO));
    }

    @Test
    @DisplayName("MediaIOs opens http: and https: with HttpIO")
    void builtIn() throws IOException {
        server = new TestHttpServer(DATA);
        try (var opened = MediaIOs.open(Source.of(server.uri("clip.bin")), List.of())) {
            assertInstanceOf(HttpIO.class, opened);
            assertArrayEquals(DATA, readAll(opened));
        }
    }

    /// The first byte a `bytes=N-` header asks for.
    private static long offset(String range) {
        return Long.parseLong(range.substring("bytes=".length(), range.length() - 1));
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
