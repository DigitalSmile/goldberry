package io.github.digitalsmile.goldberry.assets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/// Which file a family, a weight and a style resolve to — ADR-0066 and ADR-0323.
///
/// The matching is four lines of loop and it decides what every piece of text in
/// the toolkit is drawn with, so the cases worth naming are the ones where the
/// answer is a **compromise**: a weight the family does not ship, a style it does
/// not ship, and a family that was never bundled at all.
class BundledFontTest {

    @Nested
    @DisplayName("the Inter matrix closes")
    class Matrix {

        @Test
        @DisplayName("all four corners are their own face")
        void fourCorners() {
            assertSame(BundledFont.UI, BundledFont.of("Inter", BundledFont.Weight.REGULAR, BundledFont.Style.UPRIGHT));
            assertSame(
                    BundledFont.UI_STRONG,
                    BundledFont.of("Inter", BundledFont.Weight.SEMI_BOLD, BundledFont.Style.UPRIGHT));
            assertSame(
                    BundledFont.UI_ITALIC,
                    BundledFont.of("Inter", BundledFont.Weight.REGULAR, BundledFont.Style.ITALIC));
            assertSame(
                    BundledFont.UI_STRONG_ITALIC,
                    BundledFont.of("Inter", BundledFont.Weight.SEMI_BOLD, BundledFont.Style.ITALIC),
                    "a semibold italic heading is a face rather than a compromise");
        }

        @Test
        @DisplayName("the two-argument form is upright, as every caller before this assumed")
        void twoArgumentFormIsUpright() {
            assertSame(BundledFont.UI, BundledFont.of("Inter", BundledFont.Weight.REGULAR));
            assertSame(BundledFont.UI_STRONG, BundledFont.of("Inter", BundledFont.Weight.SEMI_BOLD));
        }

        @Test
        @DisplayName("each face knows which corner it is")
        void facesDescribeThemselves() {
            assertEquals(BundledFont.Style.ITALIC, BundledFont.UI_ITALIC.style());
            assertEquals(BundledFont.Weight.REGULAR, BundledFont.UI_ITALIC.weight());
            assertEquals(BundledFont.Style.ITALIC, BundledFont.UI_STRONG_ITALIC.style());
            assertEquals(BundledFont.Weight.SEMI_BOLD, BundledFont.UI_STRONG_ITALIC.weight());
            assertEquals(BundledFont.Style.UPRIGHT, BundledFont.UI.style());
        }
    }

    @Nested
    @DisplayName("when the family does not ship what was asked for")
    class Fallbacks {

        /// CSS's own matching order is family, then style, then weight — so the
        /// slant a reader was told to look for survives and the weight gives way.
        @Test
        @DisplayName("style comes before weight")
        void styleBeatsWeight() {
            // JetBrains Mono ships one face: upright, 400.
            assertSame(
                    BundledFont.CODE,
                    BundledFont.of("JetBrains Mono", BundledFont.Weight.SEMI_BOLD, BundledFont.Style.ITALIC),
                    "italic code stays code");
            assertSame(
                    BundledFont.CODE,
                    BundledFont.of("JetBrains Mono", BundledFont.Weight.SEMI_BOLD),
                    "bold code text would otherwise throw from inside a paint pass");
            assertSame(
                    BundledFont.EMOJI,
                    BundledFont.of("Noto Color Emoji", BundledFont.Weight.SEMI_BOLD, BundledFont.Style.ITALIC));
        }

        @Test
        @DisplayName("a family nothing ships is nobody's, and the cascade says so")
        void unknownFamily() {
            assertNull(BundledFont.of("Comic Sans MS", BundledFont.Weight.REGULAR, BundledFont.Style.ITALIC));
            assertNull(BundledFont.of("", BundledFont.Weight.REGULAR));
        }

        @Test
        @DisplayName("the family name is matched without regard to case")
        void familyIsCaseInsensitive() {
            assertSame(
                    BundledFont.UI_ITALIC,
                    BundledFont.of("inter", BundledFont.Weight.REGULAR, BundledFont.Style.ITALIC));
        }
    }

    @ParameterizedTest
    @EnumSource(BundledFont.Style.class)
    @DisplayName("a style spells itself the way a stylesheet writes it")
    void cssNames(BundledFont.Style style) {
        assertEquals(style == BundledFont.Style.UPRIGHT ? "normal" : "italic", style.cssName());
    }
}
