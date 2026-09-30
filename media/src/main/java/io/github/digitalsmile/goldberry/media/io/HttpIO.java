package io.github.digitalsmile.goldberry.media.io;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.Authenticator;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedChannelException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/// A resource over HTTP or HTTPS as a [MediaIO], over the JDK's
/// [java.net.http.HttpClient] (`docs/goldberry-media.md` §4).
///
/// ## What it does
///
/// - **Range requests for seeking.** The first request asks for `bytes=0-`. A
///   `206` answer makes the resource seekable and says its length. A `200` means
///   the server ignores `Range`: the stream is then [#isSeekable()] false and
///   plays front to back.
/// - **A read-ahead cache** ([ReadAheadCache]). A fetcher thread reads ahead of
///   the demuxer, up to [Options#readAhead()] past it, into a cache of
///   [Options#cacheSize()]. A read the cache can answer never touches the
///   network, and a seek into what is cached opens no connection. A seek
///   outside it abandons the connection in hand and opens one at the new
///   position. [#buffered()] is what the cache holds.
/// - **Reconnects with backoff**, resuming by `Range` at the byte where the
///   connection broke. A connection that closes early, fails, or delivers nothing
///   for [Options#stallTimeout()] is retried after [Options#firstBackoff()],
///   doubling up to [Options#maxBackoff()], for up to [Options#maxReconnects()]
///   attempts in a row. A stream that cannot seek starts over and skips what it
///   had, and a live one simply carries on from "now".
/// - **ICY metadata** ([IcyStream]). Asked for with `Icy-MetaData: 1` unless
///   [Options#icyMetadata()] is off. When the server interleaves metadata, it is
///   taken out before the cache, and [#nowPlaying()] follows the `StreamTitle`
///   at the demuxer's position. An ICY server's stream is [#isLive()], as is any
///   stream that has neither a length nor `Range`.
///
/// Headers, authentication, proxies, redirects, HTTP/2 and TLS are the JDK's.
/// A [Source]'s headers go out with every request, and its timeout bounds both
/// the wait for a response and each read. Plain `http:` speaks HTTP/1.1, so a
/// radio server is never offered an `h2c` upgrade it does not understand;
/// `https:` negotiates HTTP/2 where the server offers it.
///
/// ## Threads
///
/// A virtual thread per instance fetches: it only waits on the network and never
/// calls native code, which is where the Engine's own threads have to be platform
/// threads (§3). The demux thread reads. [#close()] from any thread ends a
/// blocked read with an [AsynchronousCloseException], which is the abort
/// [MediaIO#close()] promises.
public final class HttpIO implements MediaIO {

    /// How an [HttpIO] fetches.
    ///
    /// @param readAhead     how far past the reader the fetcher reads, in bytes
    /// @param cacheSize     the most the cache holds, read-ahead included; at
    ///                      least the read-ahead and two chunks more
    /// @param stallTimeout  how long a connection may deliver nothing before it
    ///                      is dropped and made again
    /// @param maxReconnects how many failed attempts in a row before reads fail
    /// @param firstBackoff  the wait before the first reconnect
    /// @param maxBackoff    the longest wait between two, as the wait doubles
    /// @param icyMetadata   whether to ask a radio server for ICY metadata
    public record Options(
            long readAhead,
            long cacheSize,
            Duration stallTimeout,
            int maxReconnects,
            Duration firstBackoff,
            Duration maxBackoff,
            boolean icyMetadata) {

        /// 8 MB ahead in a 32 MB cache, a stall after 10 s, and eight reconnects
        /// from a quarter of a second up to 8 s apart: about half a minute of trying,
        /// the default [Source#timeout()].
        public static final Options DEFAULT = new Options(
                8L << 20, 32L << 20, Duration.ofSeconds(10), 8, Duration.ofMillis(250), Duration.ofSeconds(8), true);

        public Options {
            Objects.requireNonNull(stallTimeout, "stallTimeout");
            Objects.requireNonNull(firstBackoff, "firstBackoff");
            Objects.requireNonNull(maxBackoff, "maxBackoff");
            if (readAhead <= 0) {
                throw new IllegalArgumentException("readAhead " + readAhead);
            }
            if (cacheSize < readAhead + 2L * ReadAheadCache.CHUNK) {
                throw new IllegalArgumentException("cacheSize " + cacheSize + " leaves no room past readAhead "
                        + readAhead + "; it needs " + 2 * ReadAheadCache.CHUNK + " bytes more");
            }
            if (!stallTimeout.isPositive() || !firstBackoff.isPositive() || maxBackoff.compareTo(firstBackoff) < 0) {
                throw new IllegalArgumentException(
                        "stall " + stallTimeout + ", backoff " + firstBackoff + " to " + maxBackoff);
            }
            if (maxReconnects < 0) {
                throw new IllegalArgumentException("maxReconnects " + maxReconnects);
            }
        }

        /// These options with another read-ahead and cache size.
        public Options withBuffer(long readAhead, long cacheSize) {
            return new Options(
                    readAhead, cacheSize, stallTimeout, maxReconnects, firstBackoff, maxBackoff, icyMetadata);
        }

        /// These options with another stall timeout.
        public Options withStallTimeout(Duration stallTimeout) {
            return new Options(
                    readAhead, cacheSize, stallTimeout, maxReconnects, firstBackoff, maxBackoff, icyMetadata);
        }

        /// These options with another reconnect policy.
        public Options withReconnects(int maxReconnects, Duration firstBackoff, Duration maxBackoff) {
            return new Options(
                    readAhead, cacheSize, stallTimeout, maxReconnects, firstBackoff, maxBackoff, icyMetadata);
        }

        /// These options, asking for ICY metadata or not.
        public Options withIcyMetadata(boolean icyMetadata) {
            return new Options(
                    readAhead, cacheSize, stallTimeout, maxReconnects, firstBackoff, maxBackoff, icyMetadata);
        }
    }

    /// How much the fetcher reads from the network at a time.
    private static final int READ_BLOCK = 16 * 1024;

    /// How often a waiting read looks for a stalled connection.
    private static final long WAIT_SLICE_NANOS = TimeUnit.MILLISECONDS.toNanos(100);

    /// `Content-Range: bytes 0-99/1000`, `bytes 0-99/*`, or `bytes */1000` on a 416.
    private static final Pattern CONTENT_RANGE = Pattern.compile("bytes\\s+(?:(\\d+)-(\\d+)|\\*)/(\\d+|\\*)");

    /// What one request brought back.
    ///
    /// @param body    the response body, from `start`
    /// @param start   the resource offset of the body's first byte
    /// @param total   the resource's length, or -1
    /// @param ranged  whether the server answered the `Range`
    /// @param icy     whether the server speaks ICY: any `icy-` header
    /// @param metaint the ICY metadata interval, or 0 for none
    private record Answer(InputStream body, long start, long total, boolean ranged, boolean icy, int metaint) {}

    /// How a connection's life ended, as the fetcher sees it.
    private sealed interface Outcome {
        /// The HttpIO is closed: the fetcher stops.
        record Closed() implements Outcome {}

        /// The body ended where it should, or the connection stopped being useful.
        record Done() implements Outcome {}

        /// The connection failed. `progressed` if it delivered something first,
        /// which starts the count of failures in a row again.
        record Failed(IOException cause, boolean progressed) implements Outcome {}
    }

    private final Source source;
    private final HttpClient client;
    private final Options options;
    private final boolean seekable;
    private final boolean live;
    /// Whether the server speaks ICY: a radio station, whose stream ending is a
    /// dropped connection rather than the end.
    private final boolean radio;
    private final ReadAheadCache cache;
    private final ReentrantLock lock = new ReentrantLock();
    /// Signalled whenever anything a waiter could be waiting for changes: bytes
    /// arrived or were read, the reader moved, the stream ended or failed, or it
    /// was closed.
    private final Condition changed = lock.newCondition();
    private final Thread fetcher;

    // Guarded by `lock`.
    private long position;
    private long size;
    /// Where a body of unknown length ended, or -1.
    private long endAt = -1;
    /// Where the connection in hand writes next. For a stream that cannot seek,
    /// where the stream carries on.
    private long fetchPosition;
    private @Nullable InputStream body;
    /// Bumped to abandon the connection in hand.
    private int generation;
    private int failures;
    /// Set by a reader that found the connection stalled, before it closes it.
    private boolean stalled;
    private boolean waitingForRoom;
    private long lastProgress;
    private @Nullable IOException failure;
    private boolean closed;
    private int requests;
    /// Each ICY title, by the offset of the first byte it describes.
    private final TreeMap<Long, String> titles = new TreeMap<>();

    /// The fetcher's own: the title a metadata block announced during a read.
    private @Nullable String announced;

    private HttpIO(Source source, HttpClient client, Options options, Answer first) {
        this.source = source;
        this.client = client;
        this.options = options;
        this.seekable = first.ranged();
        this.size = first.total();
        this.live = first.icy() || (!first.ranged() && first.total() < 0);
        this.radio = first.icy();
        this.cache = new ReadAheadCache(options.cacheSize());
        var stream = wrap(first);
        this.body = stream;
        this.lastProgress = System.nanoTime();
        this.requests = 1;
        this.fetcher =
                Thread.ofVirtual().name("goldberry-media-http").unstarted(() -> fetchLoop(stream, first.start()));
    }

    /// Opens `source`, an `http:` or `https:` URI, with the JDK's default proxy
    /// and authenticator, and the [Options#DEFAULT] options.
    ///
    /// @throws HttpStatusException when the server answers with an error status
    /// @throws IOException         when the server cannot be reached in the
    ///                             source's timeout
    public static HttpIO open(Source source) throws IOException {
        return open(source, DefaultClient.CLIENT, Options.DEFAULT);
    }

    /// Opens `source` through `client`, fetching as `options` say.
    ///
    /// Returns once the server has answered the first request, so a missing
    /// resource fails here rather than at the first read.
    ///
    /// @throws HttpStatusException when the server answers with an error status
    /// @throws IOException         when the server cannot be reached in the
    ///                             source's timeout
    public static HttpIO open(Source source, HttpClient client, Options options) throws IOException {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(options, "options");
        var scheme = source.scheme();
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException("not an http or https source: " + source.uri());
        }
        Answer first;
        try {
            first = request(client, source, options, 0);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("interrupted opening " + source.uri());
        }
        var io = new HttpIO(source, client, options, first);
        io.fetcher.start();
        return io;
    }

    @Override
    public int read(ByteBuffer target) throws IOException {
        if (!target.hasRemaining()) {
            return 0;
        }
        lock.lock();
        try {
            if (closed) {
                throw new ClosedChannelException();
            }
            var deadline = System.nanoTime() + source.timeout().toNanos();
            while (true) {
                var read = cache.read(position, target);
                if (read > 0) {
                    position += read;
                    changed.signalAll();
                    watchForStallLocked();
                    return read;
                }
                if (atEndLocked()) {
                    return -1;
                }
                var failed = failure;
                if (failed != null) {
                    // As the fetcher met it, so a caller can tell an
                    // HttpStatusException from a network that gave out.
                    throw failed;
                }
                abandonIfUselessLocked();
                watchForStallLocked();
                var remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    throw new HttpTimeoutException("no data from " + source.uri() + " in " + source.timeout());
                }
                var _ = changed.awaitNanos(Math.min(remaining, WAIT_SLICE_NANOS));
                if (closed) {
                    throw new AsynchronousCloseException();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("interrupted reading " + source.uri());
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void seek(long target) throws IOException {
        if (!seekable) {
            throw new IOException(source.uri() + " cannot seek: the server does not answer Range requests");
        }
        if (target < 0) {
            throw new IOException("negative position " + target);
        }
        lock.lock();
        try {
            if (closed) {
                throw new ClosedChannelException();
            }
            position = target;
            // A seek gives a network that gave up another chance, from the new place.
            failure = null;
            failures = 0;
            abandonIfUselessLocked();
            changed.signalAll();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public long position() {
        lock.lock();
        try {
            return position;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public OptionalLong size() {
        lock.lock();
        try {
            return size >= 0 ? OptionalLong.of(size) : OptionalLong.empty();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean isSeekable() {
        return seekable;
    }

    @Override
    public boolean isLive() {
        return live;
    }

    @Override
    public List<ByteRange> buffered() {
        lock.lock();
        try {
            return cache.ranges();
        } finally {
            lock.unlock();
        }
    }

    /// The last `StreamTitle` at or before the reader's position, when the
    /// server sends ICY metadata and the title is not blank.
    @Override
    public Optional<String> nowPlaying() {
        lock.lock();
        try {
            var entry = titles.floorEntry(position);
            return entry == null || entry.getValue().isBlank()
                    ? Optional.empty()
                    : Optional.of(entry.getValue().strip());
        } finally {
            lock.unlock();
        }
    }

    /// Stops the fetcher, drops the connection and the cache, and ends a blocked
    /// read. Idempotent.
    @Override
    public void close() {
        InputStream open;
        lock.lock();
        try {
            if (closed) {
                return;
            }
            closed = true;
            open = body;
            body = null;
            cache.clear();
            titles.clear();
            changed.signalAll();
        } finally {
            lock.unlock();
        }
        fetcher.interrupt();
        closeQuietly(open);
    }

    /// How many requests have been sent, the first included. For tests: a seek
    /// into the cache sends none.
    int requests() {
        lock.lock();
        try {
            return requests;
        } finally {
            lock.unlock();
        }
    }

    // ------------------------------------------------------------ the reader

    private boolean atEndLocked() {
        return (size >= 0 && position >= size) || (endAt >= 0 && position >= endAt);
    }

    /// Drops the connection in hand if the bytes it brings are not the next ones
    /// the reader lacks: the reader moved elsewhere, or what lies ahead is
    /// cached already.
    private void abandonIfUselessLocked() {
        var open = body;
        if (seekable && open != null && cache.firstMissing(position) != fetchPosition) {
            generation++;
            body = null;
            closeQuietly(open);
            changed.signalAll();
        }
    }

    /// Drops a connection that has delivered nothing for the stall timeout, so
    /// the fetcher makes it again. A fetcher waiting for room is not stalled.
    private void watchForStallLocked() {
        var open = body;
        if (open != null
                && !waitingForRoom
                && !stalled
                && System.nanoTime() - lastProgress > options.stallTimeout().toNanos()) {
            stalled = true;
            closeQuietly(open);
        }
    }

    // ----------------------------------------------------------- the fetcher

    private void fetchLoop(InputStream first, long firstStart) {
        @Nullable InputStream stream = first;
        var start = firstStart;
        int connection;
        lock.lock();
        try {
            connection = generation;
        } finally {
            lock.unlock();
        }
        while (true) {
            if (stream == null) {
                lock.lock();
                try {
                    start = awaitWorkLocked();
                    if (start < 0) {
                        return;
                    }
                    connection = generation;
                } finally {
                    lock.unlock();
                }
                try {
                    stream = connect(start, connection);
                } catch (InterruptedException | InterruptedIOException e) {
                    return;
                } catch (IOException e) {
                    if (!retryAfter(new Outcome.Failed(e, false))) {
                        return;
                    }
                    continue;
                }
                if (stream == null) {
                    continue;
                }
            }
            var outcome = pump(stream, start, connection);
            stream = null;
            switch (outcome) {
                case Outcome.Closed() -> {
                    return;
                }
                case Outcome.Done() -> {
                    lock.lock();
                    try {
                        failures = 0;
                    } finally {
                        lock.unlock();
                    }
                }
                case Outcome.Failed failed -> {
                    if (!retryAfter(failed)) {
                        return;
                    }
                }
            }
        }
    }

    /// Waits until there is something to fetch, and says from where; -1 once
    /// closed.
    private long awaitWorkLocked() {
        while (!closed) {
            if (failure == null) {
                var want = seekable ? cache.firstMissing(position) : fetchPosition;
                var beforeEnd = (size < 0 || want < size) && (endAt < 0 || want < endAt);
                if (beforeEnd && want - position < options.readAhead()) {
                    return want;
                }
            }
            try {
                changed.await();
            } catch (InterruptedException e) {
                return -1;
            }
        }
        return -1;
    }

    /// Opens a connection that brings the bytes from `start`, and makes it the
    /// one in hand, unless the reader moved on or the stream closed meanwhile.
    private @Nullable InputStream connect(long start, int connection) throws IOException, InterruptedException {
        lock.lock();
        try {
            requests++;
        } finally {
            lock.unlock();
        }
        var answer = request(client, source, options, seekable ? start : 0);
        var stream = wrap(answer);
        try {
            // A server that ignores Range sends it all again: skip to where the
            // last connection broke. A live stream has no "where"; it carries on
            // from now.
            var skip = live ? 0 : start - answer.start();
            if (skip > 0) {
                stream.skipNBytes(skip);
            }
        } catch (IOException e) {
            closeQuietly(stream);
            throw e;
        }
        lock.lock();
        try {
            if (closed || connection != generation) {
                closeQuietly(stream);
                return null;
            }
            body = stream;
            fetchPosition = start;
            lastProgress = System.nanoTime();
            stalled = false;
            if (answer.total() >= 0 && size < 0) {
                size = answer.total();
            }
            return stream;
        } finally {
            lock.unlock();
        }
    }

    /// Reads one connection into the cache until it ends, fails, or stops being
    /// useful.
    private Outcome pump(InputStream stream, long start, int connection) {
        var buffer = new byte[READ_BLOCK];
        var at = start;
        var progressed = false;
        try (stream) {
            while (true) {
                lock.lock();
                try {
                    while (!closed && connection == generation && at - position >= options.readAhead()) {
                        waitingForRoom = true;
                        changed.await();
                    }
                    if (waitingForRoom) {
                        waitingForRoom = false;
                        lastProgress = System.nanoTime();
                    }
                    if (closed) {
                        return new Outcome.Closed();
                    }
                    if (connection != generation) {
                        return new Outcome.Done();
                    }
                } finally {
                    lock.unlock();
                }
                var read = stream.read(buffer);
                lock.lock();
                try {
                    if (closed) {
                        return new Outcome.Closed();
                    }
                    if (connection != generation) {
                        return new Outcome.Done();
                    }
                    if (read < 0) {
                        return endedLocked(at, progressed);
                    }
                    var title = announced;
                    if (title != null) {
                        announced = null;
                        titles.put(at, title);
                        // Only the title in force at the reader is ever asked for.
                        var before = titles.floorKey(position);
                        // floorKey answers null when every title starts after the reader.
                        //noinspection ConstantValue
                        if (before != null) {
                            titles.headMap(before).clear();
                        }
                    }
                    cache.write(at, buffer, 0, read);
                    at += read;
                    fetchPosition = at;
                    lastProgress = System.nanoTime();
                    progressed = true;
                    cache.evict(position);
                    changed.signalAll();
                    if (seekable && cache.firstMissing(position) != at) {
                        return new Outcome.Done();
                    }
                } finally {
                    lock.unlock();
                }
            }
        } catch (IOException e) {
            lock.lock();
            try {
                if (closed) {
                    return new Outcome.Closed();
                }
                if (connection != generation) {
                    return new Outcome.Done();
                }
                if (stalled) {
                    stalled = false;
                    return new Outcome.Failed(
                            new IOException("no data for " + options.stallTimeout() + " from " + source.uri(), e),
                            progressed);
                }
                return new Outcome.Failed(e, progressed);
            } finally {
                lock.unlock();
            }
        } catch (InterruptedException e) {
            return new Outcome.Closed();
        } finally {
            lock.lock();
            try {
                if (body == stream) {
                    body = null;
                }
            } finally {
                lock.unlock();
            }
        }
    }

    /// The body ended at `at`: the end of the resource, or a connection that
    /// closed early, or a radio station that hung up. A station's stream has no
    /// end, so its ending is a drop; any other stream of unknown length ends
    /// where its body does.
    private Outcome endedLocked(long at, boolean progressed) {
        if (size >= 0 && at < size) {
            return new Outcome.Failed(
                    new EOFException("the connection closed at byte " + at + " of " + size), progressed);
        }
        if (radio) {
            return new Outcome.Failed(new EOFException("the station hung up at byte " + at), progressed);
        }
        if (size < 0) {
            endAt = at;
        }
        changed.signalAll();
        return new Outcome.Done();
    }

    /// Counts a failure and waits out the backoff before the next attempt; past
    /// the limit, records the failure for the reader instead.
    ///
    /// @return false once closed: the fetcher stops
    private boolean retryAfter(Outcome.Failed failed) {
        lock.lock();
        try {
            if (closed) {
                return false;
            }
            failures = failed.progressed() ? 1 : failures + 1;
            if (failures > options.maxReconnects() || refused(failed.cause())) {
                failure = refused(failed.cause())
                        ? failed.cause()
                        : new IOException(
                                "gave up on " + source.uri() + " after " + options.maxReconnects() + " reconnects: "
                                        + failed.cause().getMessage(),
                                failed.cause());
                changed.signalAll();
                return true;
            }
            var shift = Math.min(failures - 1, 20);
            var delay = Math.min(
                    options.firstBackoff().toNanos() << shift,
                    options.maxBackoff().toNanos());
            var deadline = System.nanoTime() + delay;
            var remaining = delay;
            while (!closed && remaining > 0) {
                var _ = changed.awaitNanos(remaining);
                remaining = deadline - System.nanoTime();
            }
            return !closed;
        } catch (InterruptedException e) {
            return false;
        } finally {
            lock.unlock();
        }
    }

    /// Whether retrying cannot help: the server said the resource is gone or not
    /// ours to read. A timeout (408) or a rate limit (429) is worth waiting out.
    private static boolean refused(IOException cause) {
        return cause instanceof HttpStatusException error
                && error.status() >= 400
                && error.status() < 500
                && error.status() != 408
                && error.status() != 429;
    }

    private InputStream wrap(Answer answer) {
        return answer.metaint() > 0
                ? new IcyStream(answer.body(), answer.metaint(), title -> announced = title)
                : answer.body();
    }

    // ------------------------------------------------------------- requests

    /// Asks for the resource from `start`, and says what came back.
    private static Answer request(HttpClient client, Source source, Options options, long start)
            throws IOException, InterruptedException {
        var builder =
                HttpRequest.newBuilder(source.uri()).timeout(source.timeout()).GET();
        if (source.scheme().equals("http")) {
            builder.version(HttpClient.Version.HTTP_1_1);
        }
        try {
            source.headers().forEach(builder::setHeader);
        } catch (IllegalArgumentException e) {
            throw new IOException("the HTTP client refuses a header of " + source.uri() + ": " + e.getMessage(), e);
        }
        builder.setHeader("Range", "bytes=" + start + "-");
        if (options.icyMetadata()) {
            builder.setHeader("Icy-MetaData", "1");
        }
        var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
        var body = response.body();
        try {
            var headers = response.headers();
            var icy = headers.map().keySet().stream()
                    .anyMatch(name -> name.toLowerCase(Locale.ROOT).startsWith("icy-"));
            var metaint = options.icyMetadata() ? (int) number(headers, "icy-metaint") : 0;
            return switch (response.statusCode()) {
                case 206 -> {
                    var range = contentRange(headers, source);
                    if (range[0] != start) {
                        throw new IOException("asked " + source.uri() + " for bytes from " + start
                                + " and was sent bytes from " + range[0]);
                    }
                    yield new Answer(body, start, range[1], true, icy, Math.max(metaint, 0));
                }
                case 200 -> new Answer(body, 0, number(headers, "Content-Length"), false, icy, Math.max(metaint, 0));
                // Asked from the end or past it: nothing to send, which is an end.
                case 416 -> {
                    var total = contentRange(headers, source)[1];
                    body.close();
                    yield new Answer(InputStream.nullInputStream(), start, total >= 0 ? total : start, true, icy, 0);
                }
                default -> throw new HttpStatusException(response.statusCode(), source.uri());
            };
        } catch (IOException | RuntimeException e) {
            closeQuietly(body);
            throw e;
        }
    }

    /// `{first byte, total}` from `Content-Range`, with -1 for what it leaves out.
    private static long[] contentRange(HttpHeaders headers, Source source) throws IOException {
        var value = headers.firstValue("Content-Range")
                .orElseThrow(() -> new IOException(source.uri() + " sent a range with no Content-Range"));
        var matcher = CONTENT_RANGE.matcher(value.strip());
        if (!matcher.matches()) {
            throw new IOException(source.uri() + " sent an unreadable Content-Range: " + value);
        }
        try {
            var first = matcher.group(1) == null ? -1 : Long.parseLong(matcher.group(1));
            var total = matcher.group(3).equals("*") ? -1 : Long.parseLong(matcher.group(3));
            return new long[] {first, total};
        } catch (NumberFormatException tooLong) {
            // Digits the pattern accepts and a long cannot hold: the server's
            // fault, and the reader's to hear about as an I/O failure.
            throw new IOException(source.uri() + " sent an unreadable Content-Range: " + value, tooLong);
        }
    }

    /// A header's number, or -1 when it is missing or not a number.
    private static long number(HttpHeaders headers, String name) {
        var value = headers.firstValue(name);
        if (value.isEmpty()) {
            return -1;
        }
        try {
            return Long.parseLong(value.get().strip());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static void closeQuietly(@Nullable InputStream stream) {
        if (stream == null) {
            return;
        }
        try {
            stream.close();
        } catch (IOException e) {
            // Closing to abandon it: whatever it had to say no longer matters.
        }
    }

    /// The client [#open(Source)] uses: redirects followed (never from HTTPS to
    /// HTTP), and the JDK's default proxy selector and authenticator.
    private static final class DefaultClient {
        static final HttpClient CLIENT = build();

        private static HttpClient build() {
            var builder = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL);
            var proxies = ProxySelector.getDefault();
            if (proxies != null) {
                builder.proxy(proxies);
            }
            var authenticator = Authenticator.getDefault();
            if (authenticator != null) {
                builder.authenticator(authenticator);
            }
            return builder.build();
        }
    }
}
