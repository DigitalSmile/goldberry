package io.github.digitalsmile.goldberry.image;

/// Thrown when an encoder refuses an image it was given.
///
/// The mirror of [ImageDecodeException], and it exists for one case rather than
/// as a matter of symmetry: WebP cannot hold an image larger than 16383 pixels
/// on a side, so `encodeWebp` on a tall screenshot is a refusal rather than a
/// bug ([ADR-0385]).
public class ImageEncodeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ImageEncodeException(String message) {
        super(message);
    }

    public ImageEncodeException(String message, Throwable cause) {
        super(message, cause);
    }
}
