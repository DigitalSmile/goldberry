package io.github.digitalsmile.goldberry.paint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.assets.BundledAssets;
import io.github.digitalsmile.goldberry.assets.BundledFont;
import io.github.digitalsmile.goldberry.natives.blend2d.enums.BlendCompOp;
import io.github.digitalsmile.goldberry.text.ShapedRun;
import io.github.digitalsmile.goldberry.text.font.Font;
import io.github.digitalsmile.goldberry.text.font.FontFace;
import io.github.digitalsmile.goldberry.text.font.sfnt.CompositeMode;
import io.github.digitalsmile.goldberry.text.font.sfnt.SyntheticFont;
import io.github.digitalsmile.goldberry.text.font.sfnt.SyntheticFont.Line;
import io.github.digitalsmile.goldberry.text.font.sfnt.SyntheticFont.Stop;

/// A `COLR` version 1 graph, drawn — [ADR-0456].
///
/// `:core` ships no colour face, so these tests make one: Inter's own tables,
/// with a `COLR` and a `CPAL` spliced in that give its `H` a paint graph. The
/// rasterizer and the shaper read a real font and real outlines, and the graph
/// is the only thing that is synthetic — which is exactly the part under test.
///
/// Every assertion is about **pixels** and their hue. A painter that filled the
/// outline in the text's colour, or dropped a transform, or blended a composite
/// the wrong way round, draws a plausible `H` in every case; what tells them
/// apart is which colour ended up where.
class ColourGlyphPainterTest {

    private static final int RED = 0xFFFF0000;
    private static final int GREEN = 0xFF00FF00;
    private static final int BLUE = 0xFF0000FF;

    private static final int SIZE = 64;
    private static final int WIDTH = 96;
    private static final int HEIGHT = 80;
    private static final int BASELINE = 64;

    private static int glyphH;
    private static int glyphI;
    private static int advanceH;

    @BeforeAll
    static void glyphs() {
        RendererRequirement.enforce();
        try (var font = Font.bundled(BundledFont.UI, SIZE)) {
            var h = font.shape("H");
            glyphH = h.glyphId(0);
            advanceH = h.xAdvance(0);
            glyphI = font.shape("i").glyphId(0);
        }
    }

    /// Inter, with `root` as the paint graph of its `H`.
    private static byte[] faceWith(SyntheticFont.Paint root) {
        var colr = SyntheticFont.colrV1(Map.of(glyphH, root), List.of(), List.of());
        return SyntheticFont.splice(
                BundledAssets.font(BundledFont.UI), Map.of("COLR", colr, "CPAL", SyntheticFont.cpal(RED, GREEN, BLUE)));
    }

    /// `text` drawn through a face whose `H` is `root`, black on white.
    private static Pixels draw(SyntheticFont.Paint root, String text) {
        var bytes = faceWith(root);
        try (var face = GlyphFace.of("colour", bytes);
                var pen = GlyphPen.on(face, SIZE);
                var shaper = FontFace.of("colour", bytes);
                var font = Font.on(shaper, SIZE)) {
            ShapedRun run = font.shape(text);
            var target = TestFrames.of(WIDTH, HEIGHT, 1.0f);
            try {
                target.frame().fill(0xFFFFFFFF);
                pen.draw(target.frame(), 8, BASELINE, run, 0, run.length(), 0xFF000000);
            } finally {
                target.end();
            }
            return new Pixels(target);
        }
    }

    private static SyntheticFont.Paint solid(int palette) {
        return new SyntheticFont.Solid(palette, 1, false);
    }

    private static SyntheticFont.Paint h(SyntheticFont.Paint fill) {
        return new SyntheticFont.Glyph(glyphH, fill);
    }

    /// Red at the left of the `H`'s advance, blue at the right.
    private static Line redToBlue() {
        return new Line(0, new Stop(0, 0, 1), new Stop(1, 2, 1));
    }

    @Nested
    @DisplayName("a glyph filled with")
    class Fills {

        @Test
        @DisplayName("a solid is that colour and not the text's")
        void solid() {
            var pixels = draw(h(ColourGlyphPainterTest.solid(0)), "H");

            assertTrue(pixels.count(Pixels::isRed) > 50, "the H is red: " + pixels.summary());
            assertEquals(0, pixels.count(Pixels::isDark), "and none of it is the text's black");
        }

        @Test
        @DisplayName("the foreground entry is the text's colour")
        void foreground() {
            var pixels = draw(h(ColourGlyphPainterTest.solid(0xFFFF)), "H");

            assertTrue(pixels.count(Pixels::isDark) > 50, "drawn in the text's black: " + pixels.summary());
            assertEquals(0, pixels.count(Pixels::isRed));
        }

        @Test
        @DisplayName("a linear gradient runs across the glyph, one stem to the other")
        void linear() {
            var pixels = draw(h(new SyntheticFont.Linear(redToBlue(), 0, 0, advanceH, 0, 0, 1000)), "H");

            var left = pixels.firstInkedColumn();
            var right = pixels.lastInkedColumn();
            assertTrue(Pixels.isReddish(pixels.inkIn(left)), "the left stem leans red: " + pixels.summary());
            assertTrue(Pixels.isBluish(pixels.inkIn(right)), "the right stem leans blue: " + pixels.summary());
        }

        @Test
        @DisplayName("a radial gradient puts its first stop at the focal circle")
        void radial() {
            // Centred on the H's left edge at half its cap height: the middle of
            // the left stem is near the first stop and the right one is near
            // the last.
            var pixels = draw(h(new SyntheticFont.Radial(redToBlue(), 0, 745, 0, 0, 745, advanceH)), "H");

            assertTrue(Pixels.isReddish(pixels.inkIn(pixels.firstInkedColumn())), pixels.summary());
            assertTrue(Pixels.isBluish(pixels.inkIn(pixels.lastInkedColumn())), pixels.summary());
        }

        @Test
        @DisplayName("stops outside zero to one stretch the geometry rather than being dropped")
        void stopsOutsideTheUnitRange() {
            // Red at -1 and blue at 2 over 0..advance: at the left stem the ramp
            // is a third of the way along, so it is a mix and not pure red.
            var line = new Line(0, new Stop(-1, 0, 1), new Stop(1.99, 2, 1));
            var pixels = draw(h(new SyntheticFont.Linear(line, 0, 0, advanceH, 0, 0, 1000)), "H");

            var left = pixels.inkIn(pixels.firstInkedColumn());
            assertTrue(red(left) > 80 && blue(left) > 40, "a mix at the left: #" + Integer.toHexString(left));
        }
    }

    @Nested
    @DisplayName("a transform")
    class Transforms {

        @Test
        @DisplayName("between the glyph and its gradient moves the ramp and not the outline")
        void underTheGlyphMovesTheFill() {
            var plain = draw(h(ColourGlyphPainterTest.solid(0)), "H");
            // The ramp moved right by a whole advance: PAD holds the first stop
            // over the whole H.
            var moved = draw(
                    h(new SyntheticFont.Translate(
                            advanceH, 0, new SyntheticFont.Linear(redToBlue(), 0, 0, advanceH, 0, 0, 1000))),
                    "H");

            assertEquals(plain.firstInkedColumn(), moved.firstInkedColumn(), "the outline stayed put");
            assertTrue(Pixels.isReddish(moved.inkIn(moved.lastInkedColumn())), "red all the way across");
        }

        @Test
        @DisplayName("above the glyph moves the glyph")
        void aboveTheGlyphMovesTheGlyph() {
            var plain = draw(h(ColourGlyphPainterTest.solid(0)), "H");
            // 512 design units at 64 px to a 2048-unit em is 16 pixels.
            var moved = draw(new SyntheticFont.Translate(512, 0, h(ColourGlyphPainterTest.solid(0))), "H");

            assertEquals(plain.firstInkedColumn() + 16, moved.firstInkedColumn(), 1);
        }
    }

    @Nested
    @DisplayName("a composite")
    class Composites {

        @Test
        @DisplayName("SRC_IN keeps the source only where the backdrop is")
        void sourceIn() {
            // An unbounded blue, kept inside a red H: a blue H, and no blue
            // rectangle around it.
            var pixels = draw(new SyntheticFont.Composite(solid(2), 5, h(solid(0))), "H");

            assertTrue(pixels.count(Pixels::isBlue) > 50, "the H is blue: " + pixels.summary());
            assertEquals(0, pixels.count(Pixels::isRed), "none of the backdrop's own red shows");
            assertEquals(0xFFFFFFFF, pixels.at(2, 2), "and outside the H the page is untouched");
        }

        @Test
        @DisplayName("SRC_OVER of two glyphs draws the source on top")
        void sourceOver() {
            var pixels = draw(new SyntheticFont.Composite(h(solid(2)), 3, h(solid(0))), "H");

            assertTrue(pixels.count(Pixels::isBlue) > 50, pixels.summary());
            assertEquals(0, pixels.count(Pixels::isRed), "the backdrop is entirely under the source");
        }

        @Test
        @DisplayName("a glyph whose fill is itself a picture is that picture, cut to the glyph")
        void glyphOverAPicture() {
            // The H's fill is a layered picture rather than a brush, which is
            // drawn offscreen and masked by the outline.
            var pixels = draw(h(new SyntheticFont.Composite(solid(1), 3, solid(0))), "H");

            assertTrue(pixels.count(Pixels::isGreen) > 50, "green inside: " + pixels.summary());
            assertEquals(0xFFFFFFFF, pixels.at(2, 2), "and nothing outside the outline");
        }
    }

    @Nested
    @DisplayName("the pen")
    class Pen {

        @Test
        @DisplayName("draws the glyphs with a graph in colour and the rest as the outline they are")
        void mixesTheTwo() {
            var pixels = draw(h(solid(0)), "Hi");

            assertTrue(pixels.count(Pixels::isRed) > 50, "the H has its graph");
            assertTrue(pixels.count(Pixels::isDark) > 10, "and the i is black text: " + pixels.summary());
        }

        @Test
        @DisplayName("a face with a graph says it has colour glyphs, and Inter alone does not")
        void theFaceKnows() {
            try (var colour = GlyphFace.of("colour", faceWith(h(solid(0))));
                    var plain = GlyphFace.of("plain", BundledAssets.font(BundledFont.UI))) {
                assertTrue(colour.hasColorGlyphs());
                assertFalse(plain.hasColorGlyphs());
                assertEquals(2048, colour.unitsPerEm(), "the head table's number, which scales the graph");
                assertFalse(colour.outline(glyphH).isEmpty(), "an outline to fill");
                assertSame(colour.outline(glyphI), colour.outline(glyphI), "kept, not rebuilt");
            }
        }
    }

    @ParameterizedTest
    @EnumSource(CompositeMode.class)
    @DisplayName("every composite mode has an operator, and the four HSL ones fall back to source-over")
    void everyModeHasAnOperator(CompositeMode mode) {
        var operator = ColourGlyphPainter.operator(mode);
        if (mode.name().startsWith("HSL_")) {
            assertEquals(BlendCompOp.SRC_OVER, operator);
        } else if (mode != CompositeMode.SRC_OVER) {
            assertNotEquals(BlendCompOp.SRC_OVER, operator, "a real operator for " + mode);
        }
    }

    // --- pixels ---------------------------------------------------------------

    private static int red(int argb) {
        return (argb >>> 16) & 0xFF;
    }

    private static int green(int argb) {
        return (argb >>> 8) & 0xFF;
    }

    private static int blue(int argb) {
        return argb & 0xFF;
    }

    /// A finished frame's pixels, read into an array once.
    private static final class Pixels {

        private final int[] argb = new int[WIDTH * HEIGHT];

        Pixels(TestFrames.Target target) {
            for (var y = 0; y < HEIGHT; y++) {
                for (var x = 0; x < WIDTH; x++) {
                    argb[y * WIDTH + x] = target.pixel(x, y);
                }
            }
        }

        int at(int x, int y) {
            return argb[y * WIDTH + x];
        }

        static boolean isInk(int pixel) {
            return pixel != 0xFFFFFFFF && Math.min(red(pixel), Math.min(green(pixel), blue(pixel))) < 128;
        }

        static boolean isRed(int pixel) {
            return red(pixel) > 200 && green(pixel) < 60 && blue(pixel) < 60;
        }

        static boolean isGreen(int pixel) {
            return green(pixel) > 200 && red(pixel) < 60 && blue(pixel) < 60;
        }

        static boolean isBlue(int pixel) {
            return blue(pixel) > 200 && red(pixel) < 60 && green(pixel) < 60;
        }

        static boolean isDark(int pixel) {
            return red(pixel) < 60 && green(pixel) < 60 && blue(pixel) < 60;
        }

        static boolean isReddish(int pixel) {
            return red(pixel) > blue(pixel) + 60;
        }

        static boolean isBluish(int pixel) {
            return blue(pixel) > red(pixel) + 60;
        }

        int count(IntPredicate test) {
            var count = 0;
            for (var pixel : argb) {
                if (test.test(pixel)) {
                    count++;
                }
            }
            return count;
        }

        int firstInkedColumn() {
            for (var x = 0; x < WIDTH; x++) {
                if (inkIn(x) != 0xFFFFFFFF) {
                    return x;
                }
            }
            return -1;
        }

        int lastInkedColumn() {
            for (var x = WIDTH - 1; x >= 0; x--) {
                if (inkIn(x) != 0xFFFFFFFF) {
                    return x;
                }
            }
            return -1;
        }

        /// The middle inked pixel of column `x` — the middle of a stem rather
        /// than its end or its antialiased edge — or white when there is none.
        int inkIn(int x) {
            var inked = new java.util.ArrayList<Integer>();
            for (var y = 0; y < HEIGHT; y++) {
                if (isInk(at(x, y))) {
                    inked.add(at(x, y));
                }
            }
            return inked.isEmpty() ? 0xFFFFFFFF : inked.get(inked.size() / 2);
        }

        String summary() {
            return "red=" + count(Pixels::isRed) + " green=" + count(Pixels::isGreen) + " blue=" + count(Pixels::isBlue)
                    + " dark=" + count(Pixels::isDark) + " columns=" + firstInkedColumn() + ".." + lastInkedColumn();
        }
    }
}
