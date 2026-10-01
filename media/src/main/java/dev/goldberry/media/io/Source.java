package dev.goldberry.media.io;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/// What to play: a URI plus the options for opening it.
///
/// A value, and deliberately not an open resource. A `Source` can be kept,
/// compared, and opened again after a network drop. [MediaIOs#open] is what turns
/// it into bytes, by URI scheme.
///
/// ```java
/// var local = Source.of(Path.of("clip.webm"));
/// var radio = Source.of(URI.create("https://radio.example/stream"))
///         .withHeader("User-Agent", "brd/1.0")
///         .withTimeout(Duration.ofSeconds(10));
/// ```
///
/// @param uri     where the bytes are; a `file:` URI for a local path
/// @param headers request headers for a protocol that has them, in insertion
///                order; ignored by `file:`
/// @param timeout how long an open or a read may wait before it fails
public record Source(URI uri, Map<String, String> headers, Duration timeout) {

    /// The timeout a source has unless it says otherwise. Long enough for a slow
    /// server's first byte, and short enough that a dead one surfaces as an error
    /// rather than as a spinner.
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    public Source {
        Objects.requireNonNull(uri, "uri");
        Objects.requireNonNull(headers, "headers");
        Objects.requireNonNull(timeout, "timeout");
        if (uri.getScheme() == null) {
            throw new IllegalArgumentException("a source needs an absolute URI with a scheme: " + uri);
        }
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive: " + timeout);
        }
        // An insertion-ordered, unmodifiable copy: headers go out in the order they
        // were written, and a caller's map changing later changes nothing here.
        headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers));
    }

    /// A local file.
    public static Source of(Path path) {
        return of(path.toAbsolutePath().normalize().toUri());
    }

    /// A URI, with no headers and the default timeout.
    public static Source of(URI uri) {
        return new Source(uri, Map.of(), DEFAULT_TIMEOUT);
    }

    /// This source with one more request header. A header of the same name is
    /// replaced.
    public Source withHeader(String name, String value) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(value, "value");
        var copy = new LinkedHashMap<>(headers);
        copy.put(name, value);
        return new Source(uri, copy, timeout);
    }

    /// This source with a different timeout.
    public Source withTimeout(Duration timeout) {
        return new Source(uri, headers, timeout);
    }

    /// The URI's scheme in lower case: `file`, `https`, or an application's own.
    public String scheme() {
        return uri.getScheme().toLowerCase(Locale.ROOT);
    }

    /// The last segment of the URI's path, when there is one.
    ///
    /// Handed to FFmpeg as the stream's name. The demuxer probes content first
    /// and reads the extension only as a hint, which settles the formats whose
    /// first bytes say nothing: a bare MP3 with no ID3 tag, or a subtitle file.
    public Optional<String> fileName() {
        var path = uri.getPath();
        if (path == null || path.isEmpty() || path.endsWith("/")) {
            return Optional.empty();
        }
        return Optional.of(path.substring(path.lastIndexOf('/') + 1));
    }
}
