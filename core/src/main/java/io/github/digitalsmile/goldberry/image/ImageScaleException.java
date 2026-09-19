package io.github.digitalsmile.goldberry.image;

/// Thrown when the rasterizer refuses to resample an image.
///
/// The third of these, after [ImageDecodeException] and [ImageEncodeException],
/// and the rarest: a decode fails because bytes are not what they claimed, and
/// an encode fails because WebP has a size limit, whereas a resample of a valid
/// image to a positive size fails only when the destination cannot be allocated
/// — which for a size an application computed from a window or a file is a
/// reachable state rather than an impossible one ([ADR-0428]).
///
/// Separate from [ImageEncodeException] rather than folded into it because the
/// two say different things to an application: an encode that refuses will
/// refuse again, and a resample that ran out of memory may not.
public class ImageScaleException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ImageScaleException(String message) {
        super(message);
    }

    public ImageScaleException(String message, Throwable cause) {
        super(message, cause);
    }
}
