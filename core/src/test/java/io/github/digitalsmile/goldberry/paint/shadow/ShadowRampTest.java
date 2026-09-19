package io.github.digitalsmile.goldberry.paint.shadow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.digitalsmile.goldberry.css.value.Shadow;

/// The alphas a blur is faked with — ADR-0310.
///
/// These are the tests the golden images cannot write. A golden says "the
/// picture changed"; it does not say *why* a shadow with a visible ring in it is
/// wrong, and the answer — that nested fills composite and their alphas do not
/// add — is arithmetic that can be checked exactly.
class ShadowRampTest {

    private static final int BLACK = 0xFF000000;

    /// What is on the surface after every band of `shadow` has been painted over
    /// a point the band at index `upTo` still covers.
    ///
    /// The composite `over` operator, run by hand: this is what the rasterizer
    /// will do, and the whole design of [ShadowRamp] is a claim about its result.
    private static double accumulated(List<ShadowRamp.Band> bands, int upTo) {
        var alpha = 0.0;
        for (var i = 0; i <= upTo; i++) {
            alpha += (bands.get(i).argb() >>> 24) / 255.0 * (1 - alpha);
        }
        return alpha;
    }

    @Nested
    @DisplayName("what is drawn at all")
    class Emptiness {

        @Test
        @DisplayName("a transparent shadow is no bands")
        void transparent() {
            assertTrue(ShadowRamp.bands(new Shadow(0, 4, 16, 0, 0x00000000)).isEmpty());
            assertTrue(ShadowRamp.bands(Shadow.NONE).isEmpty());
        }

        @Test
        @DisplayName("a shadow with no blur is one hard fill at its own colour")
        void hardShadow() {
            var bands = ShadowRamp.bands(new Shadow(2, 2, 0, 3, BLACK));

            assertEquals(1, bands.size());
            assertEquals(3, bands.getFirst().grow());
            assertEquals(BLACK, bands.getFirst().argb());
        }

        @Test
        @DisplayName("a hard shadow entirely inside the border box is still a band here")
        void hardShadowHidden() {
            // Offset 2px with a 4px *negative* spread: the shape is inside the
            // box on every side. Before ADR-0427 this method was told so and
            // returned nothing; now it reports the arithmetic and
            // `ShadowGeometry.coveredAt` is what says the band cannot be seen —
            // see ShadowGeometryTest for the other half of this claim.
            var bands = ShadowRamp.bands(new Shadow(0, 2, 0, -4, BLACK));

            assertEquals(1, bands.size());
            assertTrue(
                    bands.getFirst().grow() <= ShadowGeometry.coveredAt(new Shadow(0, 2, 0, -4, BLACK)),
                    "the painter will skip it");
        }
    }

    @Nested
    @DisplayName("the fade")
    class Fade {

        @Test
        @DisplayName("the bands run outermost first, each one smaller than the last")
        void ordered() {
            var bands = ShadowRamp.bands(new Shadow(0, 0, 16, 0, BLACK));

            assertFalse(bands.isEmpty());
            for (var i = 1; i < bands.size(); i++) {
                assertTrue(
                        bands.get(i).grow() < bands.get(i - 1).grow(),
                        "band " + i + " should be inside band " + (i - 1));
            }
        }

        @Test
        @DisplayName("the outermost band sits half a blur radius out, and the innermost half a radius in")
        void span() {
            // CSS's definition of the blur radius: the fade is centred on the
            // shape's edge, so it reaches `blur / 2` either side of it and no
            // further. The damage rectangle is computed from the same number.
            var bands = ShadowRamp.bands(new Shadow(0, 0, 16, 0, BLACK));

            assertTrue(bands.getFirst().grow() <= 8, "reaches no further than half the blur");
            assertEquals(-8, bands.getLast().grow(), 1e-9);
        }

        @Test
        @DisplayName("spread moves the whole fade out without changing its width")
        void spread() {
            var plain = ShadowRamp.bands(new Shadow(0, 0, 16, 0, BLACK));
            var spread = ShadowRamp.bands(new Shadow(0, 0, 16, 5, BLACK));

            assertEquals(plain.size(), spread.size());
            for (var i = 0; i < plain.size(); i++) {
                assertEquals(plain.get(i).grow() + 5, spread.get(i).grow(), 1e-9);
                assertEquals(plain.get(i).argb(), spread.get(i).argb());
            }
        }

        @Test
        @DisplayName("every band shares the shadow's colour and differs only in alpha")
        void oneColour() {
            var bands = ShadowRamp.bands(new Shadow(0, 2, 12, 0, 0xCC2E3440));

            for (var band : bands) {
                assertEquals(0x2E3440, band.argb() & 0x00FFFFFF);
            }
        }
    }

    @Nested
    @DisplayName("the alphas, which is the part that is easy to get wrong")
    class Alphas {

        @Test
        @DisplayName("the accumulated alpha grows as the bands go in, and never overshoots")
        void monotonic() {
            var shadow = new Shadow(0, 0, 16, 0, 0x80000000);
            var bands = ShadowRamp.bands(shadow);
            // 128/255, not 0.5: the ceiling is the alpha the shadow was actually
            // given, and eight bits do not divide in half.
            var ceiling = 0x80 / 255.0;

            var previous = 0.0;
            for (var i = 0; i < bands.size(); i++) {
                var alpha = accumulated(bands, i);
                assertTrue(alpha >= previous, "band " + i + " made the shadow lighter");
                assertTrue(alpha <= ceiling + 1e-6, "band " + i + " is darker than the shadow's own alpha");
                previous = alpha;
            }
        }

        @Test
        @DisplayName("the innermost band reaches the shadow's own alpha and not more")
        void reachesTheTarget() {
            // The failure this catches is the obvious implementation: alphas read
            // straight off the fade curve composite to `1 - Π(1 - aᵢ)`, which for
            // sixteen bands of a 50% shadow lands near fully opaque. A shadow
            // twice as dark as the colour it was given is what a stylesheet
            // author sees as "box-shadow is far too heavy".
            var bands = ShadowRamp.bands(new Shadow(0, 0, 16, 0, 0x80000000));

            assertEquals(128 / 255.0, accumulated(bands, bands.size() - 1), 0.01);
        }

        @ParameterizedTest
        @ValueSource(ints = {0x20000000, 0x80000000, 0xFF000000})
        @DisplayName("at every strength, half way across the fade is half the shadow")
        void halfWay(int argb) {
            // A blur is 50% opaque on the shape's own edge. That is the one point
            // on the curve with a definition rather than a taste, so it is the one
            // worth pinning.
            var shadow = new Shadow(0, 0, 16, 0, argb);
            var bands = ShadowRamp.bands(shadow);
            var edge = bands.size() / 2 - 1;

            assertEquals((argb >>> 24) / 255.0 / 2, accumulated(bands, edge), 0.03);
        }

        @Test
        @DisplayName("a fully opaque shadow is still fully opaque at its core")
        void opaque() {
            // The band alphas are solved as `1 - (1 - Aₖ)/(1 - Aₖ₋₁)`, and the
            // denominator goes to zero exactly here. Clamped rather than
            // divergent.
            var bands = ShadowRamp.bands(new Shadow(0, 0, 8, 0, BLACK));

            assertEquals(1.0, accumulated(bands, bands.size() - 1), 0.01);
        }
    }

    @Nested
    @DisplayName("what the hole in the band will erase")
    class Occlusion {

        /// How many of `shadow`'s bands the painter will actually fill — the
        /// prefix above [ShadowGeometry#coveredAt], since `grow` only decreases.
        private static long drawn(Shadow shadow) {
            var covered = ShadowGeometry.coveredAt(shadow);
            return ShadowRamp.bands(shadow).stream()
                    .filter(band -> band.grow() > covered)
                    .count();
        }

        @Test
        @DisplayName("the bands inside the border box are the inner ones, so they are a suffix")
        void dropsTheTail() {
            // The property the whole arrangement rests on: what the painter
            // skips is a *tail*, so skipping it changes no earlier band's alpha
            // and the picture is identical to one that painted them under the
            // hole and had them erased.
            var shadow = new Shadow(0, 4, 16, 0, BLACK);
            var all = ShadowRamp.bands(shadow);
            var drawn = (int) drawn(shadow);

            assertTrue(drawn < all.size(), "an offset shadow has bands inside its own hole");
            for (var i = drawn; i < all.size(); i++) {
                assertTrue(all.get(i).grow() <= -4, "band " + i + " should be inside the box");
            }
        }

        @Test
        @DisplayName("a shadow cast straight down draws only its outer half")
        void centredShadow() {
            // No offset and no spread: the band at grow = 0 *is* the border box,
            // so the whole inner half of the fade is inside the hole and only
            // what reaches past the edge puts ink down. Half the fills, for an
            // identical picture — the common case, because `0 0 <blur>` is what
            // a glow is.
            var shadow = new Shadow(0, 0, 16, 0, BLACK);
            var all = ShadowRamp.bands(shadow);

            assertEquals(all.stream().filter(band -> band.grow() > 0).count(), drawn(shadow));
            assertTrue(drawn(shadow) < all.size() / 2 + 1);
        }

        @Test
        @DisplayName("a translucent box hides no more and no less than an opaque one")
        void translucencyIsNotAQuestionAnyMore() {
            // The point of ADR-0427, as arithmetic. This used to take a flag
            // meaning "will the background cover its own rectangle", and a
            // translucent box got every band because its shadow showed through
            // it. The hole is cut whatever the background's alpha is, so there
            // is one answer now and the box's colour is not an input to it.
            var shadow = new Shadow(0, 4, 16, 0, BLACK);

            assertEquals(ShadowRamp.bandCount(16), ShadowRamp.bands(shadow).size());
            assertEquals(4, -ShadowGeometry.coveredAt(shadow));
        }
    }

    @Nested
    @DisplayName("how many bands")
    class Bands {

        @Test
        @DisplayName("one per logical pixel of blur, between four and forty-eight")
        void perPixel() {
            assertEquals(4, ShadowRamp.bandCount(1));
            assertEquals(4, ShadowRamp.bandCount(4));
            assertEquals(16, ShadowRamp.bandCount(16));
            assertEquals(48, ShadowRamp.bandCount(200));
        }

        @Test
        @DisplayName("a pure function of the blur, so a scaled window draws the same shadow bigger")
        void scaleInvariant() {
            // The blur is in *logical* pixels and so is this count. A window at
            // 150% paints the same bands at 1.5x the size rather than half again
            // as many of them, which is what makes a shadow survive
            // `ScaleInvariance`.
            assertEquals(12, ShadowRamp.bandCount(11.2));
        }
    }

    @Nested
    @DisplayName("the profile")
    class Profile {

        @Test
        @DisplayName("nothing outside, half on the edge, everything inside")
        void endpoints() {
            assertEquals(0, ShadowRamp.coverage(1), 1e-12);
            assertEquals(0.5, ShadowRamp.coverage(0), 1e-12);
            assertEquals(1, ShadowRamp.coverage(-1), 1e-12);
        }

        @Test
        @DisplayName("it only ever falls")
        void monotonic() {
            var previous = Double.POSITIVE_INFINITY;
            for (var i = 0; i <= 100; i++) {
                var coverage = ShadowRamp.coverage(-1 + 2.0 * i / 100);
                assertTrue(coverage <= previous + 1e-12, "the fade rose at u=" + (-1 + 2.0 * i / 100));
                previous = coverage;
            }
        }

        @Test
        @DisplayName("it is flat at both ends, so the fade has no visible seam")
        void flatEnds() {
            // The property smoothstep has and a linear ramp does not: the
            // derivative is zero where the fade meets "nothing" and where it meets
            // "solid". A linear ramp leaves a hard line at both.
            var delta = 1e-4;
            assertEquals(0, (ShadowRamp.coverage(1 - delta) - ShadowRamp.coverage(1)) / delta, 1e-3);
            assertEquals(0, (ShadowRamp.coverage(-1) - ShadowRamp.coverage(-1 + delta)) / delta, 1e-3);
        }
    }
}
