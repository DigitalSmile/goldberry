package io.github.digitalsmile.goldberry.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class DisplayScaleTest {

    /// The scales that actually ship. 125% and 150% are the Windows and GNOME
    /// defaults on laptop panels, and they are where a toolkit that only ever
    /// tested at 100% and 200% falls over.
    @ParameterizedTest
    @CsvSource({
        "1.0,  1280, 720, 1280, 720",
        "1.25, 1280, 720, 1600, 900",
        "1.5,  1280, 720, 1920, 1080",
        "1.75, 1280, 720, 2240, 1260",
        "2.0,  1280, 720, 2560, 1440",
        "2.5,  800,  600, 2000, 1500",
    })
    @DisplayName("logical sizes scale to whole physical pixels")
    void scalesSizes(float factor, float width, float height, int physicalWidth, int physicalHeight) {
        var scale = new DisplayScale(factor);

        assertEquals(
                new PhysicalSize(physicalWidth, physicalHeight),
                scale.toPhysical(new LogicalSize(width, height)));
    }

    @ParameterizedTest
    @CsvSource({
        // The rule is round-half-away-from-zero, applied once, at the boundary.
        "1.5, 1,  2",
        "1.5, 3,  5",
        "1.5, 5,  8",
        "1.25, 1, 1",
        "1.25, 2, 3",
        "1.25, 10, 13",
        "1.75, 1, 2",
    })
    @DisplayName("fractional scales round rather than truncate")
    void roundsRatherThanTruncates(float factor, float logical, int expected) {
        // Truncation is the tempting implementation and it loses a pixel per
        // conversion, which is how a 1px border at 150% becomes invisible.
        assertEquals(expected, new DisplayScale(factor).toPhysical(logical));
    }

    @Test
    @DisplayName("converting back is approximate, and says so")
    void backConversionIsApproximate() {
        var scale = new DisplayScale(1.5f);

        // 3 logical -> 5 physical -> 3.33 logical. Rounding already discarded
        // information; the API does not pretend otherwise.
        assertEquals(5, scale.toPhysical(3f));
        assertEquals(3.3333333f, scale.toLogical(5), 0.0001f);
    }

    @Test
    @DisplayName("a round trip through physical space stays within a pixel")
    void roundTripStaysClose() {
        var scale = new DisplayScale(1.25f);
        for (var i = 1; i <= 500; i++) {
            final var logical = i;
            var back = scale.toLogical(scale.toPhysical(logical));

            assertTrue(
                    Math.abs(back - logical) <= 1f,
                    () -> "round trip of " + logical + " drifted to " + back);
        }
    }

    @ParameterizedTest
    @ValueSource(floats = {0f, -1f, -0.5f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY})
    @DisplayName("an unusable scale factor is rejected")
    void rejectsUnusableFactors(float factor) {
        assertThrows(IllegalArgumentException.class, () -> new DisplayScale(factor));
    }

    @Test
    @DisplayName("integral scales are recognised")
    void detectsIntegralScales() {
        assertTrue(DisplayScale.ONE.isIntegral());
        assertTrue(new DisplayScale(2f).isIntegral());
        assertFalse(new DisplayScale(1.5f).isIntegral());
        assertFalse(new DisplayScale(1.25f).isIntegral());
    }

    @Test
    @DisplayName("reads as a percentage")
    void printsAsPercentage() {
        assertEquals("150%", new DisplayScale(1.5f).toString());
        assertEquals("100%", DisplayScale.ONE.toString());
    }

    @Test
    @DisplayName("a size that cannot fit in pixels is rejected, not silently wrapped")
    void rejectsOverflow() {
        var scale = new DisplayScale(2f);

        assertThrows(
                IllegalArgumentException.class, () -> scale.toPhysical(Float.MAX_VALUE));
    }
}
