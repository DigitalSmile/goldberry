package io.github.digitalsmile.goldberry.media;

import java.io.Serial;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/// A [MediaError], thrown.
///
/// Unchecked, like the toolkit's other failures. What an application does with it
/// is read [#error()] and switch on it, and [#error()] is where the information is.
public final class MediaException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /// Not serialized: a sealed record hierarchy is not `Serializable`, and a media
    /// error sent over a wire is not a use this type is for.
    @SuppressWarnings("serial")
    private final transient MediaError error;

    public MediaException(MediaError error) {
        this(error, null);
    }

    public MediaException(MediaError error, @Nullable Throwable cause) {
        super(Objects.requireNonNull(error, "error").message(), cause);
        this.error = error;
    }

    /// What went wrong.
    public MediaError error() {
        return error;
    }
}
