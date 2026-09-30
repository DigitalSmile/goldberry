package io.github.digitalsmile.goldberry.media;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.github.digitalsmile.goldberry.image.Image;
import io.github.digitalsmile.goldberry.media.picture.VideoPicture;

/// Byte-exact goldens of decoded pictures (`docs/goldberry-media.md` §7, S5).
///
/// The toolkit's own `GoldenImage` allows two levels a channel on 2% of the
/// pixels, which is right for a rasterizer whose SIMD paths round differently.
/// A decoded picture has no such excuse: the software decoders are bit-exact by
/// specification and CPU present converts with `SWS_BITEXACT`. So this compares
/// every pixel exactly, and a golden that holds on one machine holds on all of
/// them.
///
/// The goldens are PNGs under `src/test/resources/golden/video/`, lossless and
/// reviewable. `-Dgoldberry.golden.update=true` rewrites them, which is a review
/// step as it is for every golden. A mismatch writes the actual picture to
/// `build/golden-failures/`.
public final class PictureGolden {

    private static final Path GOLDEN_DIR = Path.of("src", "test", "resources", "golden", "video");
    private static final Path FAILURE_DIR = Path.of("build", "golden-failures");

    private PictureGolden() {}

    /// Asserts that `picture` is, pixel for pixel, the golden called `name`.
    public static void assertExact(String name, VideoPicture picture) {
        var actual = toImage(picture);
        var file = GOLDEN_DIR.resolve(name + ".png");
        try {
            if (Boolean.getBoolean("goldberry.golden.update") || !Files.exists(file)) {
                if (!Boolean.getBoolean("goldberry.golden.update")) {
                    fail("no golden " + file.toAbsolutePath() + "; run with -Dgoldberry.golden.update=true");
                }
                Files.createDirectories(file.getParent());
                Files.write(file, actual.encodePng());
                return;
            }
            var expected = Image.decode(file);
            var differing = 0;
            var first = "";
            if (expected.width() != actual.width() || expected.height() != actual.height()) {
                failWith(name, actual, "the golden is " + expected.size() + " and the picture " + actual.size());
            }
            for (var y = 0; y < actual.height(); y++) {
                for (var x = 0; x < actual.width(); x++) {
                    if (expected.argb(x, y) != actual.argb(x, y)) {
                        if (differing == 0) {
                            first = "(" + x + ", " + y + ") is " + Integer.toHexString(actual.argb(x, y))
                                    + ", the golden " + Integer.toHexString(expected.argb(x, y));
                        }
                        differing++;
                    }
                }
            }
            if (differing > 0) {
                failWith(name, actual, differing + " pixels differ; the first: " + first);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void failWith(String name, Image actual, String why) throws IOException {
        Files.createDirectories(FAILURE_DIR);
        var written = FAILURE_DIR.resolve(name + "-actual.png");
        Files.write(written, actual.encodePng());
        fail("picture golden " + name + ": " + why + ". The picture is in " + written.toAbsolutePath());
    }

    /// The picture as an image: opaque, so premultiplied and straight agree.
    public static Image toImage(VideoPicture picture) {
        var argb = new int[picture.width() * picture.height()];
        for (var y = 0; y < picture.height(); y++) {
            for (var x = 0; x < picture.width(); x++) {
                argb[y * picture.width() + x] = picture.argb(x, y);
            }
        }
        return Image.ofArgb(picture.width(), picture.height(), argb);
    }
}
