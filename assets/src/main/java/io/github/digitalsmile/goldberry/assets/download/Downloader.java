package io.github.digitalsmile.goldberry.assets.download;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Objects;

/// Fetches a URL, and tries again when the failure is the network's rather than
/// the request's.
///
/// A snapshot build went red because `github.com` answered one release download
/// with a 500. Nothing was wrong with the pin, the checksum or the build, and a
/// second request a few seconds later would have succeeded. So a failure that
/// can pass on its own — a 5xx, a 408, a 429, or a connection that dropped — is
/// retried with a doubling wait, and anything else fails on the first attempt: a
/// 404 is a pin that names nothing, and asking four times will not change that.
///
/// The checksum is not this class's business. A download that arrives whole and
/// hashes wrong is a changed upstream, not a flaky one, and
/// [io.github.digitalsmile.goldberry.assets.AssetCache] refuses it without retrying.
public final class Downloader {

    /// One request, and what came back.
    @FunctionalInterface
    public interface Transport {

        /// The status and, when there is one, the body. The caller closes it.
        Response get(URI uri) throws IOException, InterruptedException;
    }

    /// A status code and a body stream.
    ///
    /// @param status the HTTP status
    /// @param body   the response body; closed by the downloader
    public record Response(int status, InputStream body) {

        public Response {
            Objects.requireNonNull(body, "body");
        }
    }

    /// How a wait between attempts is spent — a real sleep, or nothing in a test.
    @FunctionalInterface
    public interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    /// Attempts in all, including the first.
    public static final int ATTEMPTS = 4;

    /// The wait after the first failure; each one after doubles it.
    public static final Duration FIRST_WAIT = Duration.ofSeconds(2);

    private final Transport transport;
    private final int attempts;
    private final Duration firstWait;
    private final Sleeper sleeper;

    public Downloader(Transport transport, int attempts, Duration firstWait, Sleeper sleeper) {
        this.transport = Objects.requireNonNull(transport, "transport");
        if (attempts < 1) {
            throw new IllegalArgumentException("a download is attempted at least once, not " + attempts);
        }
        this.attempts = attempts;
        this.firstWait = Objects.requireNonNull(firstWait, "firstWait");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
    }

    /// The build's downloader: `java.net.http`, redirects followed (a GitHub
    /// release asset is a redirect), [#ATTEMPTS] attempts from [#FIRST_WAIT].
    public static Downloader standard() {
        var client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        Transport transport = uri -> {
            var response = client.send(
                    HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
            return new Response(response.statusCode(), response.body());
        };
        return new Downloader(transport, ATTEMPTS, FIRST_WAIT, duration -> Thread.sleep(duration));
    }

    /// Whether a status is worth asking again for.
    public static boolean isTransient(int status) {
        return status == 408 || status == 429 || status >= 500;
    }

    /// Writes the body at `uri` to `target`, replacing what is there.
    ///
    /// @throws IOException when the status is not a success and not transient,
    ///                     or when every attempt failed
    public void copyTo(URI uri, Path target) throws IOException {
        attempt(uri, body -> {
            Files.copy(body, target, StandardCopyOption.REPLACE_EXISTING);
            return null;
        });
    }

    /// The body at `uri`, in memory. For a licence, not a 30 MB archive.
    public byte[] readAll(URI uri) throws IOException {
        return attempt(uri, InputStream::readAllBytes);
    }

    @FunctionalInterface
    private interface BodyReader<T> {
        T read(InputStream body) throws IOException;
    }

    private <T> T attempt(URI uri, BodyReader<T> reader) throws IOException {
        IOException last = null;
        var wait = firstWait;
        for (var attempt = 1; attempt <= attempts; attempt++) {
            if (attempt > 1) {
                pause(uri, wait, last);
                wait = wait.multipliedBy(2);
            }
            try {
                var response = transport.get(uri);
                try (var body = response.body()) {
                    var status = response.status();
                    if (status >= 200 && status < 300) {
                        return reader.read(body);
                    }
                    var failure = new IOException("HTTP " + status + " for " + uri);
                    if (!isTransient(status)) {
                        throw new PermanentFailure(failure);
                    }
                    last = failure;
                }
            } catch (PermanentFailure permanent) {
                throw permanent.getCause();
            } catch (IOException dropped) {
                last = dropped;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted downloading " + uri, interrupted);
            }
            System.err.println("download attempt " + attempt + " of " + attempts + " failed: " + last.getMessage());
        }
        throw new IOException(uri + " failed " + attempts + " times; the last failure was: " + last.getMessage(), last);
    }

    private void pause(URI uri, Duration wait, IOException last) throws IOException {
        try {
            sleeper.sleep(wait);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            var stopped = new IOException("interrupted waiting to retry " + uri, interrupted);
            stopped.addSuppressed(last);
            throw stopped;
        }
    }

    /// Carries a non-transient failure past the `catch` that retries dropped
    /// connections.
    private static final class PermanentFailure extends UncheckedIOException {

        PermanentFailure(IOException cause) {
            super(cause);
        }
    }
}
