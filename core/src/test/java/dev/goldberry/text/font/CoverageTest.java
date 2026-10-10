package dev.goldberry.text.font;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.assets.BundledAssets;
import dev.goldberry.assets.BundledFont;
import dev.goldberry.text.font.sfnt.SyntheticFont;

/// A face's coverage as the table a paragraph asks per character: the same
/// answer as [FaceCoverage], held as merged ranges.
@DisplayName("a face's coverage")
class CoverageTest {

    private static final Coverage INTER = Coverage.of(BundledAssets.font(BundledFont.UI));

    @Test
    @DisplayName("is the cmap's answer, character for character")
    void sameAnswer() {
        var inter = BundledAssets.font(BundledFont.UI);
        var points = FaceCoverage.codePoints(inter);
        assertEquals(points.length, INTER.size());
        for (var point : points) {
            assertTrue(INTER.covers(point), () -> "U+" + Integer.toHexString(point));
        }
        // A thousand-odd characters, as a few hundred ranges at most: what a
        // lookup bisects.
        assertTrue(INTER.ranges() < points.length / 2, INTER::toString);
    }

    @Test
    @DisplayName("has Latin, and has no Han, no Arabic and no mathematical Fraktur")
    void inter() {
        assertTrue(INTER.covers('A'));
        assertTrue(INTER.covers('é'));
        assertTrue(INTER.covers('Ж'));
        assertFalse(INTER.covers('中'));
        assertFalse(INTER.covers('م'));
        assertFalse(INTER.covers(StandInFace.FRAKTUR.codePointAt(0)));
        assertFalse(INTER.covers(-1));
        assertFalse(INTER.covers(Character.MAX_CODE_POINT));
    }

    @Test
    @DisplayName("covers text with controls and joiners in it, which no face draws a glyph for")
    void ignorables() {
        assertTrue(INTER.coversAll("a\nb\tc", 0, 5));
        assertTrue(INTER.coversAll("a\u200Db\u200Cc\u200F", 0, 6));
        assertTrue(INTER.coversAll("a\uFE0E", 0, 2));
        assertFalse(INTER.coversAll("ab中", 0, 3));
        assertTrue(INTER.coversAll("ab中", 0, 2), "the range is the question, not the string");
    }

    @Test
    @DisplayName("reads a format 12 table above the Basic Multilingual Plane")
    void supplementary() {
        var fraktur = StandInFace.FRAKTUR.codePointAt(0);
        var coverage = Coverage.of(SyntheticFont.of(Map.of("cmap", SyntheticFont.cmap(Map.of(fraktur, 3, 0x41, 4)))));
        assertTrue(coverage.covers(fraktur));
        assertTrue(coverage.covers('A'));
        assertFalse(coverage.covers(fraktur + 1));
        assertTrue(coverage.coversAll(StandInFace.FRAKTUR, 0, 2));
        assertEquals(2, coverage.size());
    }

    @Test
    @DisplayName("merges ranges that touch or overlap, from any order")
    void merges() {
        var coverage = Coverage.ofRanges(new int[] {20, 30, 0x100, 0x1FF, 10, 19, 25, 40, 0x200, 0x200});
        assertEquals(2, coverage.ranges());
        assertEquals(10, coverage.codePoints()[0]);
        assertTrue(coverage.covers(10) && coverage.covers(40) && coverage.covers(0x200));
        assertFalse(coverage.covers(9) || coverage.covers(41) || coverage.covers(0xFF) || coverage.covers(0x201));
        assertEquals(31 + 0x101, coverage.size());
    }

    @Test
    @DisplayName("is empty, not an exception, for bytes that are not a font")
    void notAFont() {
        assertTrue(Coverage.of(new byte[] {1, 2, 3}).isEmpty());
        assertTrue(Coverage.of(new byte[0]).isEmpty());
        assertFalse(Coverage.of(new byte[0]).covers('a'));
    }
}
