package dev.goldberry.media.platform.linux;

import java.io.Serial;

import dev.goldberry.media.platform.linux.GLib.GError;

/// GStreamer refused something: a pipeline that would not build or start, or an
/// error an element posted while decoding.
///
/// Thrown from a decoder, it is what makes the Engine walk its fallback ladder,
/// as `OsStatus.Failure` is on macOS.
final class GstException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /// A failure with GStreamer's own error, as `function` raised it.
    GstException(String function, GError error) {
        super(function + ": " + error.message());
    }

    /// A failure described here.
    GstException(String message) {
        super(message);
    }
}
