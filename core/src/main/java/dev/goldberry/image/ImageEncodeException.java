package dev.goldberry.image;

import java.io.Serial;

/// Thrown when an encoder refuses an image it was given.
///
/// The mirror of [ImageDecodeException], and it exists for one case rather than
/// as a matter of symmetry: WebP cannot hold an image larger than 16383 pixels
/// on a side, so [Image#encodeWebp(float)] on a tall screenshot is a refusal
/// rather than a bug. An encode that refuses will refuse again for the same
/// image. Unchecked.
///
/// Read more: [Text, fonts and icons](https://goldberry.dev/docs/guide/text.html#images).
public class ImageEncodeException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ImageEncodeException(String message) {
        super(message);
    }

    public ImageEncodeException(String message, Throwable cause) {
        super(message, cause);
    }
}
