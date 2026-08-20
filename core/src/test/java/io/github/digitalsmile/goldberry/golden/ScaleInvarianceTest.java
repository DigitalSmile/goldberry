package io.github.digitalsmile.goldberry.golden;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.Frame;
import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.TestFrames;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

/// The check that closes ADR-0157's gap, checked itself.
///
/// A test harness that cannot fail is not a harness, and this one is easier to
/// get wrong than most: every threshold in it is a judgement about what
/// antialiasing is allowed to do, and a threshold set one step too generous
/// produces a suite that runs at three scales and notices nothing. So the
/// interesting test here is [Detection#aPhysicalSizeUsedAsALogicalOneIsCaught],
/// which reconstructs ADR-0157's actual bug — a rectangle sized in physical
/// pixels and drawn in logical ones — and asserts the check rejects it. The
/// tolerance tests either side of it say what it is allowed to forgive.
class ScaleInvarianceTest {

    @BeforeEach
    void requireRenderer() {
        RendererRequirement.enforce();
    }

    /// A solid image, for building comparison cases by hand.
    private static Png.Image filled(int width, int height, int argb) {
        var pixels = new int[width * height];
        java.util.Arrays.fill(pixels, argb);
        return new Png.Image(width, height, pixels);
    }

    private static Png.Image withRect(
            int width, int height, int background, int x, int y, int w, int h, int argb) {

        var image = filled(width, height, background);
        for (var row = y; row < y + h; row++) {
            for (var column = x; column < x + w; column++) {
                image.argb()[row * width + column] = argb;
            }
        }
        return image;
    }

    @Nested
    @DisplayName("resampling")
    class Resampling {

        /// The integer case: four source pixels average into one.
        @Test
        @DisplayName("a 2x render halves into the average of each quad")
        void halving() {
            var source = new Png.Image(2, 2, new int[] {
                    0xFF000000, 0xFF000000,
                    0xFF000000, 0xFFFFFFFF});
            var out = ScaleInvariance.resample(source, 1, 1);

            assertEquals(1, out.width());
            assertEquals(1, out.height());
            // Three black and one white, so each colour channel is 255/4 and
            // alpha stays 255 because every contributor is opaque.
            assertEquals(0xFF404040, out.pixel(0, 0));
        }

        /// The case that made this an area filter rather than a power-of-two
        /// halving: at 1.5 an output pixel covers one and a half input ones, and
        /// the half has to weigh half.
        @Test
        @DisplayName("a 1.5x render weighs a partly covered source pixel by its area")
        void fractional() {
            // Three columns of one row: black, black, white. Two output pixels,
            // each covering 1.5 of them -- so the first is all black, and the
            // second is the white one whole plus half of the second black one,
            // which is 255 * 1.0 / 1.5 and not the 128 a halving would give.
            var source = new Png.Image(3, 1, new int[] {0xFF000000, 0xFF000000, 0xFFFFFFFF});
            var out = ScaleInvariance.resample(source, 2, 1);

            assertEquals(0xFF000000, out.pixel(0, 0));
            assertEquals(0xFFAAAAAA, out.pixel(1, 0));
        }

        /// Nothing is lost off the edge and nothing is invented past it: an image
        /// of one colour resamples to that colour whatever the ratio.
        @Test
        @DisplayName("a flat image stays flat at any ratio")
        void flat() {
            var source = filled(37, 23, 0xFF3B4252);
            for (var size : new int[][] {{18, 11}, {25, 15}, {37, 23}}) {
                var out = ScaleInvariance.resample(source, size[0], size[1]);
                for (var pixel : out.argb()) {
                    assertEquals(0xFF3B4252, pixel);
                }
            }
        }
    }

    @Nested
    @DisplayName("what the comparison forgives and what it does not")
    class Tolerance {

        /// The sub-pixel case the whole neighbourhood search exists for: an edge
        /// that Yoga rounded onto a different device pixel moves by one, and
        /// that is not a defect.
        @Test
        @DisplayName("a one-pixel edge shift is forgiven")
        void shiftedEdge() {
            var reference = withRect(40, 40, 0xFF000000, 10, 10, 20, 20, 0xFFFFFFFF);
            var shifted = withRect(40, 40, 0xFF000000, 11, 10, 20, 20, 0xFFFFFFFF);

            assertTrue(ScaleInvariance.compare(reference, shifted, 2.0f).matches());
        }

        /// ADR-0157's bug in its purest form: the same rectangle, twice the size.
        /// Nothing in the middle of where it grew has a match anywhere near it.
        @Test
        @DisplayName("a rectangle drawn at twice its size is not")
        void doubledRectangle() {
            var reference = withRect(40, 40, 0xFF000000, 5, 5, 10, 10, 0xFFFFFFFF);
            var doubled = withRect(40, 40, 0xFF000000, 5, 5, 20, 20, 0xFFFFFFFF);

            var result = ScaleInvariance.compare(reference, doubled, 2.0f);
            assertFalse(result.matches());
            // The pixels with no match are the ones the growth covered, less the
            // one-pixel border the neighbourhood search reaches into.
            assertTrue(result.gross() > 200,
                    "a rectangle four times the area should light up hundreds of pixels, not "
                            + result.gross());
        }

        /// The other direction, which a forward-only search would miss: the
        /// reference's ink is all still present, so only "what appeared where
        /// nothing was" says anything.
        @Test
        @DisplayName("something that appeared is caught, not only something that moved")
        void addedInk() {
            var reference = filled(40, 40, 0xFF000000);
            var extra = withRect(40, 40, 0xFF000000, 8, 8, 24, 24, 0xFFFFFFFF);

            assertFalse(ScaleInvariance.compare(reference, extra, 2.0f).matches());
        }

        /// A whole image a couple of levels out — what two SIMD pipelines do to
        /// each other — is not a scale fault.
        @Test
        @DisplayName("a uniform two-level difference is not a scale fault")
        void slightlyDifferentColour() {
            var reference = filled(40, 40, 0xFF3B4252);
            var nudged = filled(40, 40, 0xFF3D4454);

            assertTrue(ScaleInvariance.compare(reference, nudged, 2.0f).matches());
        }
    }

    @Nested
    @DisplayName("against a real frame")
    class Detection {

        private static final int BACKGROUND = 0xFF2E3440;
        private static final int INK = 0xFF88C0D0;

        private static final int WIDTH = 120;
        private static final int HEIGHT = 80;

        /// Renders `scene` at 1x, which is what a golden pins and what the check
        /// compares the other scales against.
        private Png.Image at1x(Consumer<Frame> scene) {
            var target = TestFrames.of(WIDTH, HEIGHT, 1.0f);
            try {
                scene.accept(target.frame());
            } finally {
                target.end();
            }
            return GoldenImage.toImage(target, WIDTH, HEIGHT);
        }

        private void check(Consumer<Frame> scene) {
            ScaleInvariance.assertScaleInvariant(
                    "scale-invariance-self-test", WIDTH, HEIGHT, 1.0f, scene, at1x(scene));
        }

        /// A scene drawn in logical coordinates is the same picture on every
        /// device, which is the claim the whole toolkit rests on.
        @Test
        @DisplayName("a rectangle in logical coordinates passes at every scale")
        void logicalCoordinatesAreInvariant() {
            check(frame -> {
                frame.fill(BACKGROUND);
                frame.fillRect(20, 16, 60, 40, INK);
            });
        }

        /// **The one that matters.** ADR-0157's bug was a size in physical pixels
        /// handed to a call that takes logical ones — right at 1x by coincidence
        /// and twice too big at 2x. Every golden in the repository was blind to
        /// it. This asserts that none of them is any more.
        @Test
        @DisplayName("a physical size used as a logical one is caught")
        void aPhysicalSizeUsedAsALogicalOneIsCaught() {
            var caught = assertThrows(AssertionFailedError.class, () -> check(frame -> {
                frame.fill(BACKGROUND);
                var factor = frame.scale().factor();
                // The bug, written out: the rectangle is measured on the device
                // and drawn in logical units, so it grows with the display.
                frame.fillRect(20, 16, 60 * factor, 40 * factor, INK);
            }));
            assertTrue(caught.getMessage().contains("does not draw the same picture"),
                    caught.getMessage());
            assertTrue(caught.getMessage().contains("ADR-0157"), caught.getMessage());
        }

        /// The subtler half of the same family, and the reason the threshold is
        /// not merely "a lot of pixels moved": a fault can be one stroke wide.
        /// A 2px border that takes the scale twice is 4px on a Retina display and
        /// nothing else on the screen moves.
        @Test
        @DisplayName("a border thickened by the scale is caught, though it is only a stroke")
        void aScaledStrokeIsCaught() {
            assertThrows(AssertionFailedError.class, () -> check(frame -> {
                frame.fill(BACKGROUND);
                var thickness = 2 * frame.scale().factor();
                frame.fillRect(20, 16, 60, thickness, INK);
                frame.fillRect(20, 56 - thickness, 60, thickness, INK);
                frame.fillRect(20, 16, thickness, 40, INK);
                frame.fillRect(80 - thickness, 16, thickness, 40, INK);
            }));
        }
    }

    @Nested
    @DisplayName("the scales it runs at")
    class Multipliers {

        @Test
        @DisplayName("2x and 1.5x by default")
        void byDefault() {
            assertEquals(java.util.List.of(2.0f, 1.5f), ScaleInvariance.multipliers());
        }

        /// An empty value is how a run turns the second half off — a golden
        /// update, or a machine bisecting a rasterizer change.
        @Test
        @DisplayName("an empty list is how it is turned off")
        void off() {
            withProperty("", () -> assertTrue(ScaleInvariance.multipliers().isEmpty()));
        }

        @Test
        @DisplayName("a list replaces the default")
        void configured() {
            withProperty("3, 1.25",
                    () -> assertEquals(
                            java.util.List.of(3.0f, 1.25f), ScaleInvariance.multipliers()));
        }

        @Test
        @DisplayName("a multiplier of zero is refused rather than dividing by it later")
        void refusesZero() {
            withProperty("0", () -> assertThrows(
                    IllegalArgumentException.class, ScaleInvariance::multipliers));
        }

        private void withProperty(String value, Runnable body) {
            var previous = System.getProperty(ScaleInvariance.SCALES_PROPERTY);
            System.setProperty(ScaleInvariance.SCALES_PROPERTY, value);
            try {
                body.run();
            } finally {
                if (previous == null) {
                    System.clearProperty(ScaleInvariance.SCALES_PROPERTY);
                } else {
                    System.setProperty(ScaleInvariance.SCALES_PROPERTY, previous);
                }
            }
        }
    }
}
