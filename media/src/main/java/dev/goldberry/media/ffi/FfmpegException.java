package dev.goldberry.media.ffi;

import java.io.Serial;

/// An FFmpeg function returned a negative `AVERROR`.
///
/// Internal to the engine. What an application sees is a
/// [dev.goldberry.media.MediaException], translated at the
/// boundary where it is known whether the error meant damaged data, a failed read
/// or an abort.
public final class FfmpegException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String function;
    private final int code;

    /// @param function    the FFmpeg function that failed
    /// @param code        its negative return value
    /// @param description `av_strerror`'s text for the code
    public FfmpegException(String function, int code, String description) {
        super(function + "() failed: " + description + " (" + code + ")");
        this.function = function;
        this.code = code;
    }

    /// The FFmpeg function that failed.
    public String function() {
        return function;
    }

    /// The `AVERROR` it returned.
    public int code() {
        return code;
    }
}
