package io.github.digitalsmile.goldberry.golden;

import io.github.digitalsmile.goldberry.TestFrames;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import org.opentest4j.AssertionFailedError;

/// Renders a scene and compares it against a committed image.
///
/// This is the check `ARCHITECTURE.md` §14 asks for: the same scene, rasterized
/// on Linux, macOS and Windows, compared against one reference. Nothing about it
/// touches a window — `Frame` paints into memory (ADR-0031), so a golden test
/// needs no display, no compositor and no `xvfb`.
///
/// ## Why there is a tolerance
///
/// The obvious design is an exact match, and it is the wrong one **across
/// platforms**. Blend2D compiles its rasterizer pipelines at run time with AsmJit
/// (ADR-0030), specialized to the CPU it finds: AVX2 on one runner, SSE2 on
/// another, NEON on an Apple Silicon one. Those pipelines agree on what they draw
/// and are not required to agree on the last bit of a blended subpixel. Demanding
/// bit-equality would make the suite fail on the architecture nobody generated
/// the goldens on, and the fix would be to generate three sets — which is three
/// references that can each rot separately.
///
/// So: a per-channel tolerance, and a cap on how much of the image may differ at
/// all. Both are tight enough that a real regression — a colour that changed, a
/// box in the wrong place, text that stopped shaping — moves thousands of pixels
/// by tens of levels and cannot hide underneath them.
///
/// ## Updating a golden
///
/// `./gradlew :core:test -Dgoldberry.golden.update=true` rewrites every golden
/// from what the code currently draws. That is a **review** step, not a fix: the
/// diff in the pull request is the only thing that says whether the change was
/// intended.
///
/// ## The other scales
///
/// A golden pins one scale, and it is 1.0 for almost all of them — the gap
/// ADR-0157 named after a HiDPI bug that every image here was blind to. So every
/// golden that matches is then drawn again at 2x and 1.5x its own scale and
/// checked for describing the same picture, with nothing further committed. That
/// is [ScaleInvariance], `-Dgoldberry.golden.scales=` turns it off, and ADR-0162
/// is why it is a second question rather than a second set of files.
public final class GoldenImage {

    /// Rewrites the goldens instead of asserting on them.
    public static final String UPDATE_PROPERTY = "goldberry.golden.update";

    /// The largest per-channel difference treated as the same colour.
    ///
    /// Two levels out of 256. A rounding disagreement between two SIMD pipelines
    /// lands at one; a colour that actually changed is nowhere near this close.
    private static final int CHANNEL_TOLERANCE = 2;

    /// The share of pixels allowed to differ within that tolerance.
    ///
    /// Antialiased edges are where the pipelines disagree, and an edge is a
    /// small fraction of a frame. A regression in a fill or a layout moves far
    /// more than 2% of the image.
    private static final double MAX_DIFFERING_FRACTION = 0.02;

    private static final Path GOLDEN_DIR = Path.of("src", "test", "resources", "golden");
    private static final Path FAILURE_DIR = Path.of("build", "golden-failures");

    private GoldenImage() {
    }

    /// Paints `scene` into a `width` x `height` frame at `scale` and compares it
    /// against `golden`.
    ///
    /// @param name the golden's file name without an extension
    public static void assertMatches(
            String name, int width, int height, float scale, Consumer<io.github.digitalsmile.goldberry.Frame> scene) {

        var target = TestFrames.of(width, height, scale);
        try {
            scene.accept(target.frame());
        } finally {
            // Before reading a pixel: Blend2D may still have work queued, and a
            // buffer read from an unended context is half-drawn (ADR-0042).
            target.end();
        }

        var actual = toImage(target, width, height);
        var goldenFile = GOLDEN_DIR.resolve(name + ".png");

        if (Boolean.getBoolean(UPDATE_PROPERTY)) {
            Png.write(goldenFile, actual);
            return;
        }
        if (!Files.exists(goldenFile)) {
            // Written anyway, so the first run of a new test produces something
            // to look at and commit rather than only a message about it.
            Png.write(FAILURE_DIR.resolve(name + "-actual.png"), actual);
            throw new AssertionFailedError(
                    "no golden for \"" + name + "\" at " + goldenFile
                            + ". What was drawn is in " + FAILURE_DIR.resolve(name + "-actual.png")
                            + "; re-run with -D" + UPDATE_PROPERTY + "=true to accept it.");
        }

        var expected = Png.read(goldenFile);
        var comparison = compare(expected, actual);
        if (comparison.matches()) {
            // Only once the image is right: a scene whose golden has drifted
            // would report both faults, and the first one is the one to read.
            ScaleInvariance.assertScaleInvariant(name, width, height, scale, scene, actual);
            return;
        }

        // Three files, because "it differs" is not actionable: the reference, what
        // was drawn, and where. CI uploads the directory.
        Png.write(FAILURE_DIR.resolve(name + "-expected.png"), expected);
        Png.write(FAILURE_DIR.resolve(name + "-actual.png"), actual);
        Png.write(FAILURE_DIR.resolve(name + "-diff.png"), comparison.diff());
        throw new AssertionFailedError(
                "\"" + name + "\" does not match its golden: " + comparison.describe()
                        + ". Expected, actual and diff images are in " + FAILURE_DIR.toAbsolutePath());
    }

    /// Package-private so [ScaleInvariance] can read back the frame it drew the
    /// same way this does.
    static Png.Image toImage(TestFrames.Target target, int width, int height) {
        var pixels = target.buffer().pixels().duplicate().order(ByteOrder.LITTLE_ENDIAN);
        var argb = new int[width * height];
        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                argb[y * width + x] = pixels.getInt(y * target.buffer().stride() + x * 4);
            }
        }
        return new Png.Image(width, height, argb);
    }

    private record Comparison(int differing, int worstChannel, int total, Png.Image diff) {

        boolean matches() {
            return worstChannel <= CHANNEL_TOLERANCE
                    && (double) differing / total <= MAX_DIFFERING_FRACTION;
        }

        String describe() {
            return String.format(
                    "%d of %d pixels differ (%.2f%%, allowed %.2f%%), worst channel delta %d (allowed %d)",
                    differing, total, 100.0 * differing / total, 100 * MAX_DIFFERING_FRACTION,
                    worstChannel, CHANNEL_TOLERANCE);
        }
    }

    private static Comparison compare(Png.Image expected, Png.Image actual) {
        if (expected.width() != actual.width() || expected.height() != actual.height()) {
            throw new AssertionFailedError(
                    "golden is " + expected.width() + "x" + expected.height()
                            + " but the scene rendered " + actual.width() + "x" + actual.height());
        }

        var diff = new int[expected.argb().length];
        var differing = 0;
        var worst = 0;
        for (var i = 0; i < diff.length; i++) {
            var a = expected.argb()[i];
            var b = actual.argb()[i];
            var delta = 0;
            for (var shift = 0; shift < 32; shift += 8) {
                delta = Math.max(delta, Math.abs(((a >>> shift) & 0xFF) - ((b >>> shift) & 0xFF)));
            }
            worst = Math.max(worst, delta);
            if (delta > 0) {
                differing++;
            }
            // Magenta where they differ, scaled by how much, on black. A
            // greyscale diff is unreadable at a delta of two.
            var intensity = Math.min(255, delta * 32);
            diff[i] = 0xFF000000 | intensity << 16 | intensity;
        }
        return new Comparison(differing, worst, diff.length, new Png.Image(expected.width(), expected.height(), diff));
    }
}
