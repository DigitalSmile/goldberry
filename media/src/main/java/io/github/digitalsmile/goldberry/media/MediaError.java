package io.github.digitalsmile.goldberry.media;

import java.util.List;
import java.util.Objects;

/// Why media could not be opened or played.
///
/// Sealed, so the error state of a player widget, and an application's own
/// message, is a `switch` the compiler checks is complete. This is also the
/// `error` property `docs/goldberry-media.md` §3 lists on the Engine.
///
/// ```java
/// String explain(MediaError error) {
///     return switch (error) {
///         case MediaError.UnsupportedCodec(var codecs) -> "Can't play " + String.join(", ", codecs);
///         case MediaError.NativesUnavailable _ -> "Media playback isn't installed";
///         default -> error.message();
///     };
/// }
/// ```
public sealed interface MediaError {

    /// A sentence for a log or a developer, and not for an end user.
    String message();

    /// FFmpeg could not be loaded. The natives jar is missing, the libraries are
    /// the wrong major, or their structs do not match the ones this module was
    /// written against. No struct is touched in any of these cases.
    ///
    /// @param detail what was expected and what was found
    record NativesUnavailable(String detail) implements MediaError {
        public NativesUnavailable {
            Objects.requireNonNull(detail, "detail");
        }

        @Override
        public String message() {
            return "FFmpeg is not available: " + detail;
        }
    }

    /// No protocol opens the source's URI scheme.
    ///
    /// @param scheme the scheme, in lower case
    record UnsupportedScheme(String scheme) implements MediaError {
        public UnsupportedScheme {
            Objects.requireNonNull(scheme, "scheme");
        }

        @Override
        public String message() {
            return "nothing opens '" + scheme + ":' sources; install a MediaIOProvider for it";
        }
    }

    /// The tracks that would be played have no decoder: a patent-pool codec the
    /// published natives do not build, and no DecoderProvider claims it
    /// (`docs/goldberry-media.md` S7).
    ///
    /// @param codecs FFmpeg's names for the codecs, such as `h264` or `aac`
    record UnsupportedCodec(List<String> codecs) implements MediaError {
        public UnsupportedCodec {
            codecs = List.copyOf(codecs);
            if (codecs.isEmpty()) {
                throw new IllegalArgumentException("an unsupported codec error names at least one codec");
            }
        }

        @Override
        public String message() {
            return "no decoder for " + String.join(", ", codecs);
        }
    }

    /// The bytes are not media any demuxer here reads, or they are damaged.
    ///
    /// @param detail FFmpeg's description of what went wrong
    record InvalidData(String detail) implements MediaError {
        public InvalidData {
            Objects.requireNonNull(detail, "detail");
        }

        @Override
        public String message() {
            return "not playable media: " + detail;
        }
    }

    /// Reading the source failed.
    ///
    /// @param detail the I/O failure, as the [io.github.digitalsmile.goldberry.media.io.MediaIO]
    ///               reported it
    record Io(String detail) implements MediaError {
        public Io {
            Objects.requireNonNull(detail, "detail");
        }

        @Override
        public String message() {
            return "could not read the source: " + detail;
        }
    }

    /// The operation was cancelled by closing its source.
    record Aborted() implements MediaError {
        @Override
        public String message() {
            return "aborted";
        }
    }
}
