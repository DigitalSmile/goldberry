package dev.goldberry.media.codec;

import dev.goldberry.media.MediaError;
import dev.goldberry.media.MediaException;

/// The largest picture the toolkit decodes: 8K. No side longer than 8192, and
/// no more pixels than 8192 × 4352, the largest picture the highest levels of
/// AV1 and VP9 allow.
///
/// A conformant stream never comes near it; a damaged or hostile header can
/// ask for 65536 × 65536, about six gigabytes for one picture. So the cap is
/// held at every step a size passes: the built-in decoders are told it before
/// they allocate (dav1d's frame size limit, libvpx's size limit), a track whose
/// container states a size past it is refused when it opens, and a picture
/// past it from any decoder fails the playback with
/// [MediaError.InvalidData]. An application's own
/// [DecoderProvider] can hold its decoder to the same numbers.
///
/// 8K UHD (7680 × 4320) fits, portrait 8K too, and so does a panorama of
/// 8192 × 4352.
///
/// Read more: [Bringing a codec](https://goldberry.dev/docs/components/media.html#bringing-a-codec).
public final class PictureLimit {

    /// The longest side, in pixels.
    public static final int MAX_SIDE = 8192;

    /// The most pixels in one picture: 8192 × 4352.
    public static final long MAX_PIXELS = 8192L * 4352L;

    private PictureLimit() {}

    /// Whether a picture of `width` × `height` is within the cap. A size of 0,
    /// which a container writes when it does not say, is.
    public static boolean allows(int width, int height) {
        return width >= 0
                && height >= 0
                && width <= MAX_SIDE
                && height <= MAX_SIDE
                && (long) width * height <= MAX_PIXELS;
    }

    /// Refuses a picture of `width` × `height` past the cap.
    ///
    /// @param what what the size belongs to, for the message: "a picture",
    ///             "track 0"
    /// @throws MediaException [MediaError.InvalidData] when it is past it
    public static void require(int width, int height, String what) {
        if (!allows(width, height)) {
            throw new MediaException(new MediaError.InvalidData(what + " of " + width + "×" + height
                    + " is past the largest picture decoded, " + MAX_SIDE + " on a side and "
                    + MAX_PIXELS + " pixels"));
        }
    }
}
