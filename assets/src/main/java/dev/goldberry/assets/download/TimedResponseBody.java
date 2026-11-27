package dev.goldberry.assets.download;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/// Java 25 returns a streaming response as soon as its headers arrive. Bound
/// each blocked read by closing that response when it stops delivering bytes.
final class TimedResponseBody extends InputStream {

    private final InputStream body;
    private final URI uri;
    private final Duration timeout;
    private final ScheduledExecutorService timers;

    TimedResponseBody(InputStream body, URI uri, Duration timeout, ScheduledExecutorService timers) {
        this.body = body;
        this.uri = uri;
        this.timeout = timeout;
        this.timers = timers;
    }

    @Override
    public int read() throws IOException {
        return readWithTimeout(body::read);
    }

    @Override
    public int read(byte[] bytes, int offset, int length) throws IOException {
        return readWithTimeout(() -> body.read(bytes, offset, length));
    }

    @Override
    public void close() throws IOException {
        body.close();
    }

    private int readWithTimeout(Read read) throws IOException {
        var pending = new AtomicBoolean(true);
        var alarm = timers.schedule(() -> {
            if (pending.getAndSet(false)) {
                try {
                    body.close();
                } catch (IOException _) {
                    // The HTTP stream cancels its subscription and wakes the reader on close.
                }
            }
        }, timeout.toNanos(), TimeUnit.NANOSECONDS);
        try {
            var count = read.get();
            if (pending.getAndSet(false)) {
                return count;
            }
        } catch (IOException failure) {
            if (pending.getAndSet(false)) {
                throw failure;
            }
            var expired = timeout();
            expired.initCause(failure);
            throw expired;
        } finally {
            alarm.cancel(false);
        }
        throw timeout();
    }

    private HttpTimeoutException timeout() {
        return new HttpTimeoutException("no response data for " + timeout.toMillis() + " ms from " + uri);
    }

    @FunctionalInterface
    private interface Read {
        int get() throws IOException;
    }
}
