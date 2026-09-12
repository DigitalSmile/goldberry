package io.github.digitalsmile.goldberry.image;

import java.io.Serial;

/// Bytes that were meant to be an image and were not.
///
/// **Why this type exists rather than the rasterizer's own report.** Decoding is
/// the one thing an application does with bytes it did not produce — a pasted
/// screenshot, a dropped file, a field in a document written by an older version
/// of itself — so a failed decode is a normal branch of a working program and not
/// a bug in the toolkit. An application has to be able to catch it, and the type
/// it catches must be Goldberry's: `BlendException` is `:natives`', and an
/// application naming one would be the boundary leaking through a `catch` clause
/// instead of through a signature (ADR-0280, ADR-0283).
///
/// Unchecked, like every other failure in this toolkit. The cause is kept, so the
/// rasterizer's code is still there to read when the question is why.
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
