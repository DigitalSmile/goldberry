package dev.goldberry.image.gif;

import java.io.Serial;

/// Bytes that were meant to be a GIF and were not.
///
/// Thrown by [GifDecoder] alone. [dev.goldberry.image.Image]
/// translates it into
/// [dev.goldberry.image.ImageDecodeException], which is the
/// one type an application catches for a failed decode whatever the format was;
/// a caller that had to know which codec refused it would be the boundary
/// leaking through a `catch` clause. Unchecked.
///
/// Read more: [Text, fonts and icons](https://goldberry.dev/docs/guide/text.html#images).
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
