package io.github.digitalsmile.goldberry.text.font.sfnt;

import static io.github.digitalsmile.goldberry.text.font.sfnt.SyntheticFont.compositeGlyph;
import static io.github.digitalsmile.goldberry.text.font.sfnt.SyntheticFont.outlineFace;
import static io.github.digitalsmile.goldberry.text.font.sfnt.SyntheticFont.simpleGlyph;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.text.font.sfnt.SyntheticFont.RecordingSink;

/// The `glyf` reader — the shapes a COLRv1 graph clips its fills to
/// ([ADR-0456]).
///
/// Every expectation is a list of path commands a reader can check against the
/// points by hand. The one rule that makes TrueType outlines tricky — an
/// on-curve point implied halfway between two off-curve ones — is where a
/// reader that looks right draws a lumpy circle, so it has a test of its own.
class GlyphOutlinesTest {

    private static final int[][] SQUARE = {{0, 0, 1}, {100, 0, 1}, {100, 100, 1}, {0, 100, 1}};

    private static List<String> outline(GlyphOutlines outlines, int glyph) {
        var sink = new RecordingSink();
        assertTrue(outlines.outline(glyph, sink), "glyph " + glyph + " was read");
        return sink.calls;
    }

    @Test
    @DisplayName("a face with no glyf table has no outlines, and says so without an exception")
    void noTable() {
        assertSame(GlyphOutlines.NONE, GlyphOutlines.read(SyntheticFont.of(Map.of())));
        assertFalse(GlyphOutlines.NONE.outline(0, new RecordingSink()));
        assertEquals(0, GlyphOutlines.NONE.glyphCount());
    }

    @Test
    @DisplayName("the units per em are the head table's")
    void unitsPerEm() {
        var outlines = GlyphOutlines.read(outlineFace(2048, simpleGlyph(SQUARE)));
        assertEquals(2048, outlines.unitsPerEm());
        assertEquals(1, outlines.glyphCount());
    }

    @Test
    @DisplayName("on-curve points are straight lines, and the contour is closed")
    void straightLines() {
        var outlines = GlyphOutlines.read(outlineFace(1000, simpleGlyph(SQUARE)));
        assertEquals(List.of("M 0 0", "L 100 0", "L 100 100", "L 0 100", "Z"), outline(outlines, 0));
    }

    @Test
    @DisplayName("an off-curve point is a quadratic's control, and two in a row imply the point between them")
    void impliedPoints() {
        // A diamond of four off-curve controls: every on-curve point is
        // implied, and the contour starts at the midpoint of the last and first.
        int[][] round = {{50, 0, 0}, {100, 50, 0}, {50, 100, 0}, {0, 50, 0}};
        var outlines = GlyphOutlines.read(outlineFace(1000, simpleGlyph(round)));

        assertEquals(
                List.of("M 25 25", "Q 50 0 75 25", "Q 100 50 75 75", "Q 50 100 25 75", "Q 0 50 25 25", "Z"),
                outline(outlines, 0));
    }

    @Test
    @DisplayName("a contour that starts off the curve starts at its last point when that one is on it")
    void startsAtTheLastOnCurvePoint() {
        int[][] contour = {{50, 100, 0}, {100, 0, 1}, {0, 0, 1}};
        var outlines = GlyphOutlines.read(outlineFace(1000, simpleGlyph(contour)));

        assertEquals(List.of("M 0 0", "Q 50 100 100 0", "Z"), outline(outlines, 0));
    }

    @Test
    @DisplayName("several contours are several closed sub-paths")
    void severalContours() {
        int[][] inner = {{25, 25, 1}, {75, 25, 1}, {75, 75, 1}};
        var calls = outline(GlyphOutlines.read(outlineFace(1000, simpleGlyph(SQUARE, inner))), 0);

        assertEquals(2, calls.stream().filter("Z"::equals).count());
        assertEquals("M 25 25", calls.get(5), "the second starts after the first closes");
    }

    @Test
    @DisplayName("a composite places each component by its offset and scale")
    void composite() {
        var outlines = GlyphOutlines.read(outlineFace(
                1000, simpleGlyph(SQUARE), compositeGlyph(0.5, new int[] {0, 10, 20}, new int[] {0, 200, 0})));
        var calls = outline(outlines, 1);

        assertEquals(10, calls.size(), "two squares");
        // Scaled by a half, then offset — the component's own matrix first.
        assertEquals("M 10 20", calls.get(0));
        assertEquals("L 60 20", calls.get(1));
        assertEquals("M 200 0", calls.get(5));
        assertEquals("L 250 50", calls.get(7));
    }

    @Test
    @DisplayName("a glyph with no data is legal and is nothing")
    void emptyGlyph() {
        var outlines = GlyphOutlines.read(outlineFace(1000, new byte[0], simpleGlyph(SQUARE)));
        assertEquals(List.of(), outline(outlines, 0));
    }

    @Test
    @DisplayName("a glyph out of range, or one that runs off the table, sends nothing at all")
    void allOrNothing() {
        var truncated = simpleGlyph(SQUARE);
        var outlines = GlyphOutlines.read(outlineFace(1000, Arrays.copyOf(truncated, truncated.length - 6)));

        var sink = new RecordingSink();
        assertFalse(outlines.outline(0, sink), "the coordinates are missing");
        assertEquals(List.of(), sink.calls, "and not half a square reached the sink");

        assertFalse(outlines.outline(1, sink), "past the last glyph");
        assertFalse(outlines.outline(-1, sink));
    }

    @Test
    @DisplayName("a composite that contains itself stops at the depth limit")
    void compositeCycle() {
        var outlines = GlyphOutlines.read(outlineFace(1000, compositeGlyph(1, new int[] {0, 0, 0})));
        assertFalse(outlines.outline(0, new RecordingSink()));
    }
}
