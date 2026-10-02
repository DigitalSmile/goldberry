package dev.goldberry.image;

import java.io.Serial;

/// Bytes that were meant to be an image and were not.
///
/// ```java
/// try {
///     return Image.decode(bytes);
/// } catch (ImageDecodeException e) {
///     status.set("That is not a picture we can read.");
/// }
/// ```
///
/// Decoding is the one thing an application does with bytes it did not produce
/// (a pasted screenshot, a dropped file, a field in a document written by an
/// older version of itself), so a failed decode is a normal branch of a working
/// program and not a bug in the toolkit. An application has to be able to catch
/// it, and the type it catches is Goldberry's own rather than the rasterizer's,
/// so no native-library type leaks through a `catch` clause.
///
/// Unchecked, like every other failure in this toolkit. The cause is kept, so the
/// rasterizer's code is still there to read when the question is why.
///
/// Read more: [Text, fonts and icons](https://goldberry.dev/docs/guide/text.html#images).
public final class ImageDecodeException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ImageDecodeException(String message, Throwable cause) {
        super(message, cause);
    }

    public ImageDecodeException(String message) {
        super(message);
    }
}
