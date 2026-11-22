package dev.goldberry.camera;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/// What an application asks a camera for: a size and a frame rate. The camera
/// opens the mode nearest to it ([#choose]).
///
/// @param width           pixels across wanted
/// @param height          pixels down wanted
/// @param framesPerSecond frames a second wanted
///
/// Read more: [Media](https://goldberry.dev/docs/components/media.html).
public record CameraSpec(int width, int height, double framesPerSecond) {

    /// What a video call asks for: 1280×720 at 30.
    public static final CameraSpec CALL = new CameraSpec(1280, 720, 30);

    /// A small, smooth preview: 640×480 at 30.
    public static final CameraSpec PREVIEW = new CameraSpec(640, 480, 30);

    public CameraSpec {
        if (width <= 0 || height <= 0 || !(framesPerSecond > 0) || !Double.isFinite(framesPerSecond)) {
            throw new IllegalArgumentException(width + "×" + height + " at " + framesPerSecond + " fps");
        }
    }

    /// The mode of `formats` this spec opens: the one nearest in size and
    /// rate, a rate below the one asked counting twice as much as a size away
    /// from it. Of two as near, an uncompressed mode, which costs no decoding;
    /// then the larger; then the rate nearer the one asked, so a call at 30 is
    /// not decoded at 60. Empty when there is no mode.
    ///
    /// So a camera that sends 1280×720 at 30 only as Motion JPEG, and
    /// uncompressed only at 10, opens the Motion JPEG mode for [#CALL]; one
    /// that offers 640×480 at 30 both ways opens it uncompressed for
    /// [#PREVIEW].
    public Optional<CameraFormat> choose(List<CameraFormat> formats) {
        return formats.stream()
                .min(Comparator.comparingDouble(this::distance)
                        .thenComparing(CameraFormat::compressed)
                        .thenComparing(
                                Comparator.comparingLong(CameraFormat::pixels).reversed())
                        .thenComparingDouble(this::rateGap));
    }

    /// How far `format` is from this, in natural logs of size and rate.
    double distance(CameraFormat format) {
        var size = Math.abs(Math.log((double) format.pixels() / ((long) width * height)));
        var rate = format.framesPerSecond();
        var slower = rate <= 0 ? 0 : Math.max(0, Math.log(framesPerSecond / rate));
        return size + 2 * slower;
    }

    /// How far `format`'s rate is from this one's either way, in a natural
    /// log; a mode that gives no rate is the furthest.
    double rateGap(CameraFormat format) {
        var rate = format.framesPerSecond();
        return rate <= 0 ? Double.MAX_VALUE : Math.abs(Math.log(rate / framesPerSecond));
    }
}
