package dev.goldberry.image.gif;

import java.io.Serial;

/// Bytes that were meant to be a GIF and were not — [ADR-0329].
///
/// Internal to the decoder: [dev.goldberry.image.Image]
/// translates it into
/// [dev.goldberry.image.ImageDecodeException], which is the
/// one type an application catches for a failed decode whatever the format was.
/// A caller that had to know which codec refused it would be the boundary leaking
/// through a `catch` clause.
public final class GifFormatException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public GifFormatException(String message) {
        super(message);
    }

    public GifFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
