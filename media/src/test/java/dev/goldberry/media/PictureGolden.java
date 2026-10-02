package dev.goldberry.media;

import static org.junit.jupiter.api.Assertions.fail;

import java.nio.file.Files;
import java.nio.file.Path;

import dev.goldberry.golden.Png;
import dev.goldberry.media.picture.VideoPicture;

/// Byte-exact goldens of decoded pictures.
///
/// The toolkit's own `GoldenImage` allows two levels a channel on 2% of the
/// pixels, which is right for a rasterizer whose SIMD paths round differently.
/// A decoded picture has no such excuse: the software decoders are bit-exact by
/// specification and CPU present converts with `SWS_BITEXACT`. So this compares
/// every pixel exactly, and a golden that holds on one machine holds on all of
/// them.
///
/// The goldens are PNGs under `src/test/resources/golden/video/`, lossless and
/// reviewable. They are read and written by the golden harness's own [Png], in
/// `java.base`, and not decoded by the rasterizer: a picture golden says what
/// FFmpeg decoded, and the Media workflow's runners have FFmpeg and no
/// `libgoldberry` to decode a PNG with; a lane without the library stays green.
/// `-Dgoldberry.golden.update=true` rewrites them, which is a review step as it
/// is for every golden. A mismatch writes the actual picture to
/// `build/golden-failures/`.
public final class PictureGolden {

    /// The switch every golden harness shares: rewrite rather than compare.
    public static final String UPDATE_PROPERTY = "goldberry.golden.update";

    private static final Path GOLDEN_DIR = Path.of("src", "test", "resources", "golden", "video");
    private static final Path FAILURE_DIR = Path.of("build", "golden-failures");

    private PictureGolden() {}

    /// Asserts that `picture` is, pixel for pixel, the golden called `name`.
    public static void assertExact(String name, VideoPicture picture) {
        var actual = toImage(picture);
        var file = GOLDEN_DIR.resolve(name + ".png");
        var update = Boolean.getBoolean(UPDATE_PROPERTY);
        if (update || !Files.exists(file)) {
            if (!update) {
                fail("no golden " + file.toAbsolutePath() + "; run with -D" + UPDATE_PROPERTY + "=true");
            }
            Png.write(file, actual);
            return;
        }
        var expected = Png.read(file);
        if (expected.width() != actual.width() || expected.height() != actual.height()) {
            failWith(
                    name,
                    actual,
                    "the golden is " + expected.width() + "x" + expected.height() + " and the picture " + actual.width()
                            + "x" + actual.height());
        }
        var differing = 0;
        var first = "";
        for (var y = 0; y < actual.height(); y++) {
            for (var x = 0; x < actual.width(); x++) {
                if (expected.pixel(x, y) != actual.pixel(x, y)) {
                    if (differing == 0) {
                        first = "(" + x + ", " + y + ") is " + Integer.toHexString(actual.pixel(x, y)) + ", the golden "
                                + Integer.toHexString(expected.pixel(x, y));
                    }
                    differing++;
                }
            }
        }
        if (differing > 0) {
            failWith(name, actual, differing + " pixels differ; the first: " + first);
        }
    }

    private static void failWith(String name, Png.Image actual, String why) {
        var written = FAILURE_DIR.resolve(name + "-actual.png");
        Png.write(written, actual);
        fail("picture golden " + name + ": " + why + ". The picture is in " + written.toAbsolutePath());
    }

    /// The picture as the harness's pixels: opaque, so premultiplied and straight
    /// agree.
    public static Png.Image toImage(VideoPicture picture) {
        var argb = new int[picture.width() * picture.height()];
        for (var y = 0; y < picture.height(); y++) {
            for (var x = 0; x < picture.width(); x++) {
                argb[y * picture.width() + x] = picture.argb(x, y);
            }
        }
        return new Png.Image(picture.width(), picture.height(), argb);
    }
}
