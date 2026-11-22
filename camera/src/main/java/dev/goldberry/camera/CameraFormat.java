package dev.goldberry.camera;

/// One mode a camera offers: a size and a frame rate, and whether its frames
/// come compressed.
///
/// A compressed mode is Motion JPEG, which most USB cameras use for their
/// largest sizes and fastest rates: a camera opened in one decodes each frame
/// on its own thread, which costs a few milliseconds a frame (about 5 at
/// 1280×720, 12 at 1920×1080), where an uncompressed mode costs a copy.
///
/// @param width           pixels across
/// @param height          pixels down
/// @param rateNumerator   frames…
/// @param rateDenominator …per this many seconds
/// @param compressed      whether frames come compressed, as Motion JPEG
///
/// Read more: [Media](https://goldberry.dev/docs/components/media.html).
public record CameraFormat(int width, int height, int rateNumerator, int rateDenominator, boolean compressed) {

    public CameraFormat {
        if (width <= 0 || height <= 0 || rateNumerator < 0 || rateDenominator < 0) {
            throw new IllegalArgumentException(
                    width + "×" + height + " at " + rateNumerator + "/" + rateDenominator + " fps");
        }
    }

    /// An uncompressed mode at a whole number of frames a second.
    public static CameraFormat of(int width, int height, int framesPerSecond) {
        return new CameraFormat(width, height, framesPerSecond, 1, false);
    }

    /// Frames a second, or zero where the camera does not say.
    public double framesPerSecond() {
        return rateDenominator == 0 ? 0 : (double) rateNumerator / rateDenominator;
    }

    /// Pixels in a frame.
    public long pixels() {
        return (long) width * height;
    }

    @Override
    public String toString() {
        return width + "×" + height + " at " + Math.round(framesPerSecond() * 100) / 100.0 + " fps"
                + (compressed ? ", compressed" : "");
    }
}
