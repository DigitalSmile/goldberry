package io.github.digitalsmile.goldberry.assets.svg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Anchoring a subpath so it can follow another one.
class SvgPathDataTest {

    @Test
    @DisplayName("data that already opens absolutely is untouched")
    void absoluteDataPassesThrough() {
        // Byte for byte: the numbers are upstream's and re-emitting them would
        // lose precision for nothing.
        assertEquals("M20 6 9 17l-5-5", SvgPathData.absoluteStart("M20 6 9 17l-5-5"));
        assertEquals("M3.5 13h6", SvgPathData.absoluteStart("M3.5 13h6"));
        assertEquals("", SvgPathData.absoluteStart(""));
    }

    @Test
    @DisplayName("a relative opening moveto becomes an absolute one")
    void relativeMoveToBecomesAbsolute() {
        // Inside its own <path> element the `m` is already measured from the
        // origin, so the numbers do not change -- only what they are relative to.
        assertEquals("M2 16", SvgPathData.absoluteStart("m2 16"));
        assertEquals("M2 16h4", SvgPathData.absoluteStart("m2 16h4"));
    }

    @Test
    @DisplayName("the implicit linetos after it stay relative")
    void trailingPairsBecomeAnExplicitRelativeLineTo() {
        // Lucide's `a-arrow-down`, second subpath. Turning only the letter
        // absolute would draw to (4.5,-9) and (4.5,9) instead of tracing the A.
        assertEquals("M2 16 l4.5-9 4.5 9", SvgPathData.absoluteStart("m2 16 4.5-9 4.5 9"));
    }

    @Test
    @DisplayName("commas and extra spaces separate as whitespace does")
    void separatorsAreAccepted() {
        assertEquals("M2 16 l4.5-9", SvgPathData.absoluteStart("m2,16,4.5-9"));
        assertEquals("M2 16", SvgPathData.absoluteStart("  m  2 , 16"));
    }

    @Test
    @DisplayName("a sign ends the number before it")
    void runTogetherNumbersSplitOnTheSign() {
        // `m-5-3` is two numbers, not one and a malformed one.
        assertEquals("M-5 -3", SvgPathData.absoluteStart("m-5-3"));
        assertEquals("M.5 -.25", SvgPathData.absoluteStart("m.5-.25"));
    }

    @Test
    @DisplayName("a second decimal point ends the number before it")
    void runTogetherDecimalsSplit() {
        assertEquals("M1.5 .5 l1 1", SvgPathData.absoluteStart("m1.5.5 1 1"));
    }

    @Test
    @DisplayName("an exponent is part of its number")
    void exponentsAreReadAsOneNumber() {
        assertEquals("M1e2 3", SvgPathData.absoluteStart("m1e2 3"));
        assertEquals("M1e-2 3", SvgPathData.absoluteStart("m1e-2 3"));
    }

    @Test
    @DisplayName("a bare e is the next command, not an exponent")
    void aTrailingLetterIsNotAnExponent() {
        // There is no `e` command in SVG, but `E` must not swallow a following
        // letter either -- the scanner has to stop when the digits run out.
        assertEquals("M1 3", SvgPathData.absoluteStart("m1 3"));
    }

    @Test
    @DisplayName("a moveto with no coordinates is refused")
    void aMoveToNeedsItsPair() {
        var thrown = assertThrows(IllegalArgumentException.class, () -> SvgPathData.absoluteStart("m"));

        assertTrue(thrown.getMessage().contains("opening moveto"), thrown.getMessage());
        assertThrows(IllegalArgumentException.class, () -> SvgPathData.absoluteStart("m5"));
    }

    @Test
    @DisplayName("startsAbsolutely answers what concatenation needs")
    void startsAbsolutelyIsAboutTheFirstCommand() {
        assertTrue(SvgPathData.startsAbsolutely("M0 0h1"));
        assertTrue(SvgPathData.startsAbsolutely("  M0 0"));
        assertFalse(SvgPathData.startsAbsolutely("m0 0h1"));
        assertFalse(SvgPathData.startsAbsolutely("h1"));
        assertFalse(SvgPathData.startsAbsolutely(""));
    }

    @Test
    @DisplayName("the rewrite is idempotent")
    void applyingItTwiceChangesNothing() {
        var once = SvgPathData.absoluteStart("m2 16 4.5-9 4.5 9");

        assertEquals(once, SvgPathData.absoluteStart(once));
    }
}
