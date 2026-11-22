package dev.goldberry.camera;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/// What an application asks a camera for: a size and a frame rate. The camera
/// opens the uncompressed mode nearest to it ([#choose]).
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

    /// The mode of `formats` this spec opens: the uncompressed one nearest in
    /// size and rate, a rate below the one asked counting twice as much as a
    /// size away from it, and the larger of two as near. Empty when every mode
    /// is compressed.
    ///
    /// So a camera that gives 1280×720 only at 10 fps uncompressed opens
    /// 640×480 at 30 for [#CALL]: a third of the rate is further from a call
    /// than half the size.
    public Optional<CameraFormat> choose(List<CameraFormat> formats) {
        return formats.stream()
                .filter(format -> !format.compressed())
                .min(Comparator.comparingDouble(this::distance)
                        .thenComparing(
                                Comparator.comparingLong(CameraFormat::pixels).reversed())
                        .thenComparing(Comparator.comparingDouble(CameraFormat::framesPerSecond)
                                .reversed()));
    }

    /// How far `format` is from this, in natural logs of size and rate.
    double distance(CameraFormat format) {
        var size = Math.abs(Math.log((double) format.pixels() / ((long) width * height)));
        var rate = format.framesPerSecond();
        var slower = rate <= 0 ? 0 : Math.max(0, Math.log(framesPerSecond / rate));
        return size + 2 * slower;
    }
}
