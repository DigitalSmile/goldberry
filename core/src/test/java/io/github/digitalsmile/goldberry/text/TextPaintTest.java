package io.github.digitalsmile.goldberry.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.TestFrames;
import io.github.digitalsmile.goldberry.assets.BundledFont;
import java.util.OptionalInt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The ink itself: what reached the buffer when text was drawn.
///
/// The first glyph Goldberry ever put on a surface. Everything below it — the
/// export list, the layout table, the staging buffer, the design-unit invariant
/// — is machinery whose only observable consequence is these pixels, and a
/// mistake anywhere in it produces a frame that renders and is wrong. So the
/// assertions are about *where* the ink is, not merely that there is some.
class TextPaintTest {

    private static final int BACKGROUND = 0xFF000000;
    private static final int INK = 0xFFFFFFFF;

    @BeforeEach
    void requireRenderer() {
        RendererRequirement.enforce();
    }

    @Test
    @DisplayName("text drawn is text painted")
    void glyphsReachTheBuffer() {
        var target = TestFrames.of(200, 64, 1.0f);
        try (var font = Font.bundled(BundledFont.UI, 24)) {
            target.frame().fill(BACKGROUND);
            font.draw(target.frame(), 10, 40, "Goldberry", INK);
            target.end();
        }

        assertTrue(inkedColumns(target, 200, 64) > 0,
                "nothing was drawn — the glyph run never reached the rasterizer");
    }

    @Test
    @DisplayName("the ink is as wide as the shaped run says it is")
    void inkMatchesTheMeasuredWidth() {
        // The assertion the whole of ADR-0034 exists for. Blend2D multiplies
        // placements by size/units-per-em; Java computes the same product in
        // widthOf. If either side is wrong the two disagree by that factor --
        // 128x for Inter at 16pt -- and the text is either a single illegible
        // pile or entirely off the edge of the frame. Both still "render".
        var text = "Hamburgefonstiv";
        var target = TestFrames.of(400, 64, 1.0f);
        double expected;
        try (var font = Font.bundled(BundledFont.UI, 20)) {
            expected = font.widthOf(text);

            target.frame().fill(BACKGROUND);
            font.draw(target.frame(), 10, 44, text, INK);
            target.end();
        }

        var first = firstInkedColumn(target, 400, 64).orElseThrow(
                () -> new AssertionError("nothing was drawn at all"));
        var last = lastInkedColumn(target, 400, 64).orElseThrow();

        // The ink runs from the first glyph's left bearing to the last glyph's
        // right edge, which is a little narrower than the sum of the advances --
        // the pen keeps moving past the final letter's ink. A few points of
        // slack covers that; it does not come close to covering a factor of 128.
        var inked = last - first + 1;
        assertTrue(inked > expected - 8 && inked < expected + 4,
                () -> "ink spans " + inked + " pixels, but the run measures " + expected);
    }

    @Test
    @DisplayName("the origin is the baseline, so the letters sit above it")
    void theOriginIsTheBaseline() {
        var baseline = 40;
        var target = TestFrames.of(200, 64, 1.0f);
        try (var font = Font.bundled(BundledFont.UI, 24)) {
            target.frame().fill(BACKGROUND);
            // No descenders in "HEIL" -- every one of these letters sits
            // entirely on the baseline, so anything below it is a bug and not
            // typography.
            font.draw(target.frame(), 10, baseline, "HEIL", INK);
            target.end();
        }

        assertTrue(rowIsInked(target, 200, baseline - 8), "the letters are above the baseline");
        // Two rows of slack for the antialiasing of the baseline itself.
        for (var y = baseline + 2; y < 64; y++) {
            var row = y;
            assertTrue(!rowIsInked(target, 200, row),
                    () -> "row " + row + " is below the baseline and should be empty");
        }
    }

    @Test
    @DisplayName("a bigger size draws bigger text from the same shaping")
    void sizeChangesTheInkAndNotTheGlyphs() {
        var text = "Goldberry";
        var small = TestFrames.of(400, 80, 1.0f);
        var large = TestFrames.of(400, 80, 1.0f);

        try (var twelve = Font.bundled(BundledFont.UI, 12);
                var twentyFour = Font.bundled(BundledFont.UI, 24)) {

            small.frame().fill(BACKGROUND);
            twelve.draw(small.frame(), 10, 60, text, INK);
            small.end();

            large.frame().fill(BACKGROUND);
            twentyFour.draw(large.frame(), 10, 60, text, INK);
            large.end();
        }

        var narrow = inkedColumns(small, 400, 80);
        var wide = inkedColumns(large, 400, 80);

        // Twice the size is about twice the ink, and the glyph run behind both
        // was identical: only the font matrix differed.
        assertTrue(wide > narrow * 1.7 && wide < narrow * 2.3,
                () -> wide + " inked columns at 24pt against " + narrow + " at 12pt");
    }

    @Test
    @DisplayName("the display scale reaches the glyphs, not just the rectangles")
    void textIsScaledWithTheFrame() {
        var text = "Goldberry";
        var unscaled = TestFrames.of(400, 80, 1.0f);
        var scaled = TestFrames.of(400, 80, 2.0f);

        try (var font = Font.bundled(BundledFont.UI, 16)) {
            unscaled.frame().fill(BACKGROUND);
            font.draw(unscaled.frame(), 4, 40, text, INK);
            unscaled.end();

            // The same logical coordinates and the same logical size. At 2x
            // every one of them is worth two physical pixels, and the context's
            // transform is what has to carry that into the glyph outlines --
            // which it only does if the run went through the font matrix rather
            // than being pre-multiplied into pixels somewhere.
            scaled.frame().fill(BACKGROUND);
            font.draw(scaled.frame(), 4, 20, text, INK);
            scaled.end();
        }

        var atOne = inkedColumns(unscaled, 400, 80);
        var atTwo = inkedColumns(scaled, 400, 80);

        assertTrue(atTwo > atOne * 1.7 && atTwo < atOne * 2.3,
                () -> atTwo + " inked columns at 2x against " + atOne + " at 1x");
    }

    @Test
    @DisplayName("drawing nothing is not an error")
    void emptyTextDrawsNothing() {
        var target = TestFrames.of(64, 64, 1.0f);
        try (var font = Font.bundled(BundledFont.UI, 16)) {
            target.frame().fill(BACKGROUND);
            assertDoesNotThrow(() -> font.draw(target.frame(), 10, 40, "", INK));
            target.end();
        }

        assertEquals(0, inkedColumns(target, 64, 64));
    }

    @Test
    @DisplayName("a NaN baseline is refused rather than quietly drawing nothing")
    void nanBaselineIsRefused() {
        var target = TestFrames.of(64, 64, 1.0f);
        try (var font = Font.bundled(BundledFont.UI, 16)) {
            // Blend2D would rasterize this as nothing at all, and an arithmetic
            // bug in a layout pass would look like text that never loaded.
            assertThrows(
                    IllegalArgumentException.class,
                    () -> font.draw(target.frame(), 10, Double.NaN, "Goldberry", INK));
            target.end();
        }
    }

    // --- helpers -------------------------------------------------------------

    private static boolean isInk(TestFrames.Target target, int x, int y) {
        return target.pixel(x, y) != BACKGROUND;
    }

    private static boolean rowIsInked(TestFrames.Target target, int width, int y) {
        for (var x = 0; x < width; x++) {
            if (isInk(target, x, y)) {
                return true;
            }
        }
        return false;
    }

    private static int inkedColumns(TestFrames.Target target, int width, int height) {
        var count = 0;
        for (var x = 0; x < width; x++) {
            for (var y = 0; y < height; y++) {
                if (isInk(target, x, y)) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    private static OptionalInt firstInkedColumn(
            TestFrames.Target target, int width, int height) {
        for (var x = 0; x < width; x++) {
            for (var y = 0; y < height; y++) {
                if (isInk(target, x, y)) {
                    return OptionalInt.of(x);
                }
            }
        }
        return OptionalInt.empty();
    }

    private static OptionalInt lastInkedColumn(TestFrames.Target target, int width, int height) {
        for (var x = width - 1; x >= 0; x--) {
            for (var y = 0; y < height; y++) {
                if (isInk(target, x, y)) {
                    return OptionalInt.of(x);
                }
            }
        }
        return OptionalInt.empty();
    }
}
