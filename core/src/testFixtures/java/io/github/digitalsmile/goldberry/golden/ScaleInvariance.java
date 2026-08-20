package io.github.digitalsmile.goldberry.golden;

import io.github.digitalsmile.goldberry.Frame;
import io.github.digitalsmile.goldberry.TestFrames;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.opentest4j.AssertionFailedError;

/// The second half of a golden: the same scene, drawn at another display scale.
///
/// A golden says what a scene looks like. It says it at **one** scale, and until
/// this existed that scale was 1.0 for 37 of the 39 images in the repository —
/// which is the gap [ADR-0157] closed one bug of and then wrote down: "almost
/// every pixel assertion in this repository is at 1x, where this whole class of
/// bug is invisible". The bug in question was a layer whose raster is allocated
/// in physical pixels being blitted one raster pixel per *logical* unit. At 1x
/// the two spaces coincide and the arithmetic is right by accident; at 2x every
/// disabled control on the screen was twice the size it should be, and not one
/// assertion here could see it.
///
/// ## Why this is not a second set of goldens
///
/// The obvious answer is to commit `name@2x.png` beside `name.png`. It doubles a
/// 106-image corpus, and — the reason it is the wrong answer rather than merely
/// an expensive one — a committed 2x image says only *this is what it drew*,
/// which a wrong image satisfies just as well as a right one, forever. Nobody
/// reviews a 2x render of a slider by eye and notices that its thumb is a
/// half-pixel out.
///
/// What is actually being claimed is **invariance**: the picture is a fact about
/// logical space, and changing the device the logical space is rasterized onto
/// must not change the picture. That claim can be checked against the 1x render
/// the golden already pins, with nothing new committed. So the same scene is
/// painted into a frame of the same *logical* size at a higher scale, area-
/// resampled back down, and compared.
///
/// ## What the comparison can and cannot demand
///
/// Not equality, and not [GoldenImage]'s two-level tolerance. Two things differ
/// legitimately between one scale and another and neither is a defect:
///
///   - **Edge placement.** Yoga's point scale factor rounds computed edges to
///     whole device pixels, so at 2x an edge may land on a half of a logical
///     one. A border therefore moves by up to half a pixel, and a high-contrast
///     border that moves half a pixel differs by over a hundred levels in the
///     column it moved out of.
///   - **Antialiasing.** A glyph rasterized at 2x and averaged down is a
///     different approximation of the same coverage integral than one rasterized
///     at 1x. Both are right; they are not the same bytes.
///
/// Both are *sub-pixel* differences, and the geometry faults this exists to
/// catch are not: a subtree at twice its size, a raster blitted at the wrong
/// origin, a stroke width that took the scale twice. So the metric allows a
/// pixel to find its match anywhere in the 3x3 neighbourhood around it —
/// absorbing half-pixel movement and resampling blur entirely — and then demands
/// that what is left be small. A doubled control has no match within a pixel
/// anywhere in the region it grew into.
///
/// The check runs in **both directions**, because "every pixel of the reference
/// appears near where it was" does not catch something that grew: the pixels the
/// growth covered are still there, just further out. Ink that appeared where
/// there was none is only visible looking the other way.
///
/// ## Turning it off
///
/// `-Dgoldberry.golden.scales=` runs no extra scales; a comma-separated list of
/// multipliers replaces the default `2,1.5`. `-Dgoldberry.golden.scales.report=true`
/// prints what every check measured, which is how the thresholds below were set
/// rather than guessed.
///
/// ## Without a golden
///
/// [#assertSamePictureAtEveryScale] is the same check with no committed image
/// behind it, for the tests that read pixels back directly rather than through
/// [GoldenImage] — the clip, the transform, the shadow, the icon. Those are
/// where the logical-against-physical arithmetic actually lives, and none of
/// them has a golden to hang a second scale off.
public final class ScaleInvariance {

    /// Which multiples of a golden's own scale to re-render it at.
    static final String SCALES_PROPERTY = "goldberry.golden.scales";

    /// Prints the measurement for every check, passing or not.
    static final String REPORT_PROPERTY = "goldberry.golden.scales.report";

    /// 2 is a Retina display and the scale ADR-0157's bug needed; 1.5 is the
    /// ordinary fractional case on Linux, and the one where a raster is rounded
    /// up to a whole pixel and has to be mapped back down by the fraction.
    private static final List<Float> DEFAULT_MULTIPLIERS = List.of(2.0f, 1.5f);

    /// How far a pixel may be from the nearest pixel of the other rendering, per
    /// channel, before it counts as differing at all.
    ///
    /// Measured rather than chosen: over the whole corpus the worst honest
    /// disagreement after the neighbourhood search is well under this, and a
    /// geometry fault puts thousands of pixels far above it.
    private static final int GROSS_DELTA = 72;

    /// The share of pixels allowed to be that far out.
    private static final double MAX_GROSS_FRACTION = 0.012;

    /// How far away a pixel may look for its match. One pixel: the differences
    /// being forgiven are sub-pixel, and a radius of two would start forgiving
    /// a control that moved.
    private static final int RADIUS = 1;

    private static final Path FAILURE_DIR = Path.of("build", "golden-failures");

    private ScaleInvariance() {
    }

    /// Asserts that `scene` draws the same picture at every configured scale,
    /// with no golden involved.
    ///
    /// The reference is the scene at 1.0, rendered here. A test that already has
    /// a golden goes through [GoldenImage] instead and gets this for free.
    ///
    /// @param name   what to call the scene in a failure message and its images
    /// @param width  the logical width, which is also the 1x pixel width
    /// @param height likewise
    public static void assertSamePictureAtEveryScale(
            String name, int width, int height, Consumer<Frame> scene) {

        var target = TestFrames.of(width, height, 1.0f);
        try {
            scene.accept(target.frame());
        } finally {
            target.end();
        }
        assertScaleInvariant(
                name, width, height, 1.0f, scene, GoldenImage.toImage(target, width, height));
    }

    /// Re-renders `scene` at each configured multiple of `scale` and asserts it
    /// describes the same picture as `reference`.
    ///
    /// `reference` is what this run drew, not what the golden file holds: the
    /// question is whether the renderer agrees with itself across scales, and
    /// comparing against the file would fold in a stale golden as well.
    static void assertScaleInvariant(
            String name, int width, int height, float scale,
            Consumer<Frame> scene, Png.Image reference) {

        for (var multiplier : multipliers()) {
            var result = measure(width, height, scale, multiplier, scene, reference);
            if (Boolean.getBoolean(REPORT_PROPERTY)) {
                System.out.println("scale-invariance " + name + " " + result.describe());
            }
            if (result.matches()) {
                continue;
            }
            // The same three files a golden failure leaves, so a scale fault is
            // looked at the same way: what 1x drew, what the other scale drew
            // once brought back to 1x, and where they disagree.
            var stem = name + "-at-" + label(multiplier) + "x";
            Png.write(FAILURE_DIR.resolve(stem + "-expected.png"), reference);
            Png.write(FAILURE_DIR.resolve(stem + "-actual.png"), result.resampled());
            Png.write(FAILURE_DIR.resolve(stem + "-diff.png"), result.diff());
            throw new AssertionFailedError(
                    "\"" + name + "\" does not draw the same picture at " + label(multiplier)
                            + "x its scale: " + result.describe()
                            + ". A difference this size is geometry rather than antialiasing —"
                            + " something is using a physical size where a logical one belongs,"
                            + " or the other way round (ADR-0157). The 1x render, the"
                            + " resampled one and the diff are in " + FAILURE_DIR.toAbsolutePath());
        }
    }

    /// The multipliers to check, from [#SCALES_PROPERTY].
    static List<Float> multipliers() {
        var configured = System.getProperty(SCALES_PROPERTY);
        if (configured == null) {
            return DEFAULT_MULTIPLIERS;
        }
        var parsed = new ArrayList<Float>();
        for (var token : configured.split(",")) {
            var trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            var value = Float.parseFloat(trimmed);
            if (value <= 0) {
                throw new IllegalArgumentException(
                        "a scale multiplier must be positive, and " + trimmed + " is not");
            }
            parsed.add(value);
        }
        return List.copyOf(parsed);
    }

    /// Paints the scene at `scale * multiplier` into the same logical area and
    /// brings it back to the reference's size.
    private static Result measure(
            int width, int height, float scale, float multiplier,
            Consumer<Frame> scene, Png.Image reference) {

        // The frame is described in PHYSICAL pixels (TestFrames), so both the
        // buffer and the scale are multiplied and the logical size — which is
        // the buffer divided by the scale — comes out unchanged. That is the
        // whole setup: same picture to draw, different device to draw it on.
        var physicalWidth = Math.round(width * multiplier);
        var physicalHeight = Math.round(height * multiplier);
        var target = TestFrames.of(physicalWidth, physicalHeight, scale * multiplier);
        try {
            scene.accept(target.frame());
        } finally {
            target.end();
        }

        var drawn = GoldenImage.toImage(target, physicalWidth, physicalHeight);
        var resampled = resample(drawn, width, height);
        return compare(reference, resampled, multiplier);
    }

    /// Area-averages `source` down to `width` x `height`.
    ///
    /// A box filter over the exact overlap rather than nearest-neighbour or a
    /// power-of-two halving, because the multipliers are not all integers: at
    /// 1.5 an output pixel covers one and a half input ones and the weights have
    /// to say so. Nearest-neighbour would throw away three quarters of a 2x
    /// render and make the comparison noisier than the thing it is looking for.
    ///
    /// The channels are averaged as they are stored — premultiplied — which is
    /// the space averaging is linear in. Averaging unpremultiplied colour across
    /// a transparent edge darkens or lightens it depending on what the invisible
    /// pixels happen to hold.
    static Png.Image resample(Png.Image source, int width, int height) {
        var out = new int[Math.multiplyExact(width, height)];
        var stepX = (double) source.width() / width;
        var stepY = (double) source.height() / height;

        for (var y = 0; y < height; y++) {
            var top = y * stepY;
            var bottom = (y + 1) * stepY;
            for (var x = 0; x < width; x++) {
                var left = x * stepX;
                var right = (x + 1) * stepX;

                var a = 0.0;
                var r = 0.0;
                var g = 0.0;
                var b = 0.0;
                var weight = 0.0;

                for (var sy = (int) Math.floor(top); sy < Math.ceil(bottom); sy++) {
                    if (sy < 0 || sy >= source.height()) {
                        continue;
                    }
                    var coverY = Math.min(bottom, sy + 1.0) - Math.max(top, sy);
                    if (coverY <= 0) {
                        continue;
                    }
                    for (var sx = (int) Math.floor(left); sx < Math.ceil(right); sx++) {
                        if (sx < 0 || sx >= source.width()) {
                            continue;
                        }
                        var coverX = Math.min(right, sx + 1.0) - Math.max(left, sx);
                        if (coverX <= 0) {
                            continue;
                        }
                        var area = coverX * coverY;
                        var pixel = source.pixel(sx, sy);
                        a += area * (pixel >>> 24);
                        r += area * ((pixel >>> 16) & 0xFF);
                        g += area * ((pixel >>> 8) & 0xFF);
                        b += area * (pixel & 0xFF);
                        weight += area;
                    }
                }

                out[y * width + x] = weight == 0
                        ? 0
                        : round(a / weight) << 24 | round(r / weight) << 16
                                | round(g / weight) << 8 | round(b / weight);
            }
        }
        return new Png.Image(width, height, out);
    }

    private static int round(double channel) {
        return Math.clamp(Math.round(channel), 0, 255);
    }

    /// How much of `reference` has no counterpart within [#RADIUS] pixels of
    /// `resampled`, and how much of `resampled` has none in `reference`.
    static Result compare(Png.Image reference, Png.Image resampled, float multiplier) {
        if (reference.width() != resampled.width() || reference.height() != resampled.height()) {
            throw new AssertionFailedError(
                    "a resampled render is " + resampled.width() + "x" + resampled.height()
                            + " against a reference of " + reference.width() + "x"
                            + reference.height() + ", which is this class's own bug");
        }

        var diff = new int[reference.argb().length];
        var gross = 0;
        var worst = 0;

        for (var y = 0; y < reference.height(); y++) {
            for (var x = 0; x < reference.width(); x++) {
                // Both directions at once: the forward search misses ink that
                // appeared, the reverse misses ink that vanished, and a control
                // drawn at twice its size does both at its edges and only the
                // second in its middle.
                var delta = Math.max(
                        nearestDelta(reference, resampled, x, y),
                        nearestDelta(resampled, reference, x, y));
                worst = Math.max(worst, delta);
                if (delta > GROSS_DELTA) {
                    gross++;
                }
                var intensity = Math.min(255, delta * 3);
                diff[y * reference.width() + x] = 0xFF000000 | intensity << 16 | intensity;
            }
        }
        var image = new Png.Image(reference.width(), reference.height(), diff);
        return new Result(multiplier, gross, worst, diff.length, resampled, image);
    }

    /// The smallest per-channel difference between `from`'s pixel at `(x, y)` and
    /// any pixel of `to` within [#RADIUS] of it.
    private static int nearestDelta(Png.Image from, Png.Image to, int x, int y) {
        var pixel = from.pixel(x, y);
        var best = Integer.MAX_VALUE;
        for (var dy = -RADIUS; dy <= RADIUS; dy++) {
            var ny = y + dy;
            if (ny < 0 || ny >= to.height()) {
                continue;
            }
            for (var dx = -RADIUS; dx <= RADIUS; dx++) {
                var nx = x + dx;
                if (nx < 0 || nx >= to.width()) {
                    continue;
                }
                best = Math.min(best, channelDelta(pixel, to.pixel(nx, ny)));
                if (best == 0) {
                    return 0;
                }
            }
        }
        return best;
    }

    private static int channelDelta(int a, int b) {
        var delta = 0;
        for (var shift = 0; shift < 32; shift += 8) {
            delta = Math.max(delta, Math.abs(((a >>> shift) & 0xFF) - ((b >>> shift) & 0xFF)));
        }
        return delta;
    }

    /// `2` rather than `2.0`, so a failure message and a file name read the way
    /// a person says it.
    private static String label(float multiplier) {
        return multiplier == Math.rint(multiplier)
                ? Integer.toString((int) multiplier)
                : Float.toString(multiplier);
    }

    /// What one scale's comparison measured.
    record Result(
            float multiplier, int gross, int worst, int total,
            Png.Image resampled, Png.Image diff) {

        boolean matches() {
            return (double) gross / total <= MAX_GROSS_FRACTION;
        }

        String describe() {
            return String.format(
                    "at %sx: %d of %d pixels (%.3f%%, allowed %.3f%%) have no match within %d"
                            + " pixel at a channel tolerance of %d; worst nearby delta %d",
                    label(multiplier), gross, total, 100.0 * gross / total,
                    100 * MAX_GROSS_FRACTION, RADIUS, GROSS_DELTA, worst);
        }
    }
}
