package io.github.digitalsmile.goldberry.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.assets.BundledFont;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The join between the shaper and the rasterizer, measured rather than drawn.
///
/// The invariant under test is the one in ADR-0034: shaping happens in font
/// design units and the size lives on the Blend2D font alone. Everything here is
/// a consequence of that, and each of these assertions fails loudly for a bug
/// that would otherwise render — wrongly, but without an error.
class FontTest {

    @BeforeEach
    void requireRenderer() {
        RendererRequirement.enforce();
    }

    @Test
    @DisplayName("a shaped run is in design units, so it does not depend on the size")
    void shapingIsSizeIndependent() {
        try (var small = Font.bundled(BundledFont.UI, 12);
                var large = Font.bundled(BundledFont.UI, 48)) {

            var fromSmall = small.shape("Hamburgefonstiv");
            var fromLarge = large.shape("Hamburgefonstiv");

            assertEquals(fromSmall.length(), fromLarge.length());
            // Identical, not merely proportional. This is what makes one shaping
            // result reusable at every size -- and it is only true because
            // neither Font scaled its shaper.
            for (var i = 0; i < fromSmall.length(); i++) {
                assertEquals(fromSmall.glyphId(i), fromLarge.glyphId(i), "glyph " + i);
                assertEquals(fromSmall.xAdvance(i), fromLarge.xAdvance(i), "advance " + i);
            }
            assertEquals(fromSmall.totalXAdvance(), fromLarge.totalXAdvance());
        }
    }

    @Test
    @DisplayName("the same run measures four times as wide at four times the size")
    void widthScalesWithSize() {
        try (var small = Font.bundled(BundledFont.UI, 12);
                var large = Font.bundled(BundledFont.UI, 48)) {

            var text = "Hamburgefonstiv";
            var narrow = small.widthOf(text);
            var wide = large.widthOf(text);

            assertTrue(narrow > 0, "a run of real text has a width");
            // Exact within floating point: both are the same integer advance
            // multiplied by size/upem, so the ratio is the ratio of the sizes.
            assertEquals(4.0, wide / narrow, 1e-9);
        }
    }

    @Test
    @DisplayName("a design-unit width is not a pixel width, and the difference is large")
    void designUnitsAreNotPixels() {
        try (var font = Font.bundled(BundledFont.UI, 16)) {
            var run = font.shape("Hamburgefonstiv");

            var pixels = font.widthOf(run);
            var designUnits = run.totalXAdvance();

            // The whole hazard in one assertion. Handing Blend2D a run that has
            // already been converted to pixels -- or reading design units as
            // pixels -- is off by upem/size, which for Inter at 16pt is 128x.
            // Text that wide still renders; it is simply not on the screen.
            assertTrue(designUnits > pixels * 100,
                    () -> designUnits + " design units against " + pixels + " logical units");
            assertEquals(designUnits * 16.0 / font.unitsPerEm(), pixels, 1e-9);
        }
    }

    @Test
    @DisplayName("the face reports the em grid its outlines are drawn on")
    void unitsPerEmIsTheFaces() {
        try (var font = Font.bundled(BundledFont.UI, 16)) {
            // Inter is TrueType-flavoured, so 2048. Asserted as a range rather
            // than a number: the value is the font's to change on an upgrade,
            // and what must hold is that it is a real em grid and not 0 or 1.
            assertTrue(font.unitsPerEm() >= 16,
                    () -> "an em grid of " + font.unitsPerEm() + " is not a font's");
            assertEquals(2048, font.unitsPerEm(), "Inter, at the pinned version");
        }
    }

    @Test
    @DisplayName("metrics come back in logical units, already scaled by the size")
    void metricsAreScaled() {
        try (var small = Font.bundled(BundledFont.UI, 16);
                var large = Font.bundled(BundledFont.UI, 32)) {

            assertTrue(small.ascent() > 0, "ascent is positive, measured upwards");
            assertTrue(small.descent() > 0, "descent is positive too, measured downwards");
            // A 16-point font reaches neither much more than 16 points above the
            // baseline nor a fraction of it. This catches design units leaking
            // through, which would be in the thousands.
            assertTrue(small.ascent() < 32, () -> "ascent of " + small.ascent() + " at 16pt");
            assertTrue(small.lineHeight() >= small.ascent() + small.descent());

            assertEquals(2.0, large.ascent() / small.ascent(), 1e-5, "twice the size");
        }
    }

    @Test
    @DisplayName("shaping empty text is an empty run, not a failure")
    void emptyTextShapesToNothing() {
        try (var font = Font.bundled(BundledFont.UI, 16)) {
            var run = font.shape("");

            assertTrue(run.isEmpty());
            assertEquals(0.0, font.widthOf(run));
        }
    }

    @Test
    @DisplayName("the buffer is reused, so shaping twice gives the same answer")
    void shapingIsRepeatable() {
        try (var font = Font.bundled(BundledFont.UI, 16)) {
            // The shaping buffer is deliberately kept and reset rather than
            // reallocated. A reset that missed something would show up as a
            // second run that inherited the first one's text or direction.
            var first = font.shape("Hello");
            var second = font.shape("Hello");
            var different = font.shape("Goodbye");
            var third = font.shape("Hello");

            assertEquals(first.length(), second.length());
            assertEquals(first.totalXAdvance(), second.totalXAdvance());
            assertEquals(first.totalXAdvance(), third.totalXAdvance(),
                    "and still, after a different string in between");
            assertNotEquals(first.totalXAdvance(), different.totalXAdvance());
        }
    }

    @Test
    @DisplayName("a monospace face measures every character the same, and Inter does not")
    void theFacesDiffer() {
        try (var ui = Font.bundled(BundledFont.UI, 16);
                var code = Font.bundled(BundledFont.CODE, 16)) {

            var proportional = ui.shape("iW");
            assertNotEquals(proportional.xAdvance(0), proportional.xAdvance(1), "Inter");

            var mono = code.shape("iW");
            assertEquals(mono.xAdvance(0), mono.xAdvance(1), "JetBrains Mono");
        }
    }

    @Test
    @DisplayName("an impossible size is refused")
    void impossibleSizesAreRefused() {
        assertThrows(
                IllegalArgumentException.class, () -> Font.bundled(BundledFont.UI, 0));
        assertThrows(
                IllegalArgumentException.class, () -> Font.bundled(BundledFont.UI, -16));
        assertThrows(
                IllegalArgumentException.class, () -> Font.bundled(BundledFont.UI, Double.NaN));
    }

    @Test
    @DisplayName("a closed font refuses to shape, and closing twice is a no-op")
    void closedFontIsUnusable() {
        var font = Font.bundled(BundledFont.UI, 16);
        font.close();

        assertTrue(font.isClosed());
        assertThrows(IllegalStateException.class, () -> font.shape("Hello"));
        assertThrows(IllegalStateException.class, font::ascent);
        font.close();
    }
}
