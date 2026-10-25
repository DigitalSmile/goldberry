package dev.goldberry.media.ffmpegcli;

import java.io.Serial;

/// `ffmpeg` could not be started, stopped early, or handed back something that
/// is not what it was asked for.
///
/// Thrown from a decoder, it is what makes the Engine walk its fallback ladder.
final class FfmpegCliException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    FfmpegCliException(String message) {
        super(message);
    }

    FfmpegCliException(String message, Throwable cause) {
        super(message, cause);
    }
}
