package dev.goldberry.text.bidi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.layout.Direction;

/// The levels and the line order, without a font: what the bidirectional
/// algorithm says about a string is a fact about its characters.
class BidiLevelsTest {

    /// Hebrew, so that every character is a strong right-to-left one: "shalom".
    private static final String SHALOM = "שלום";

    /// The order a line's runs are placed in, as text ranges.
    private static List<String> order(BidiLevels levels, String text, int start, int end) {
        return levels.visualRuns(start, end).stream()
                .map(run -> text.substring(run.start(), run.end()) + (run.isRightToLeft() ? "<" : ">"))
                .toList();
    }

    @Nested
    @DisplayName("left to right throughout")
    class Plain {

        @Test
        @DisplayName("Latin under a left-to-right base has no levels at all")
        void latin() {
            var levels = BidiLevels.of("Goldberry, 2026!", Directionality.LTR);

            assertTrue(levels.isLeftToRight());
            assertEquals(0, levels.levelAt(3));
            assertEquals(Direction.LTR, levels.directionAt(0));
            assertEquals(List.of(new LevelRun(0, 16, 0)), levels.visualRuns(0, 16));
        }

        @Test
        @DisplayName("and so does Latin whose first strong character is found, and an empty text")
        void firstStrongLatin() {
            assertTrue(BidiLevels.of("hello\nworld", Directionality.AUTO).isLeftToRight());
            assertTrue(BidiLevels.of("", Directionality.AUTO).isLeftToRight());
            assertTrue(
                    BidiLevels.of("hello", new Directionality(Direction.RTL, Directionality.Source.FIRST_STRONG))
                            .isLeftToRight(),
                    "a strong left-to-right character wins over the fallback");
        }

        @Test
        @DisplayName("Hebrew forced left to right is read as Latin")
        void overrideLeftToRight() {
            var levels = BidiLevels.of(SHALOM, new Directionality(Direction.LTR, Directionality.Source.OVERRIDE));

            assertTrue(levels.isLeftToRight());
        }
    }

    @Nested
    @DisplayName("the levels")
    class Levels {

        @Test
        @DisplayName("a right-to-left word in a left-to-right sentence is one odd run, and its digits are even")
        void mixed() {
            var text = "abc " + "מה" + " 12 !";
            var levels = BidiLevels.of(text, Directionality.LTR);

            assertFalse(levels.isLeftToRight());
            assertEquals(0, levels.levelAt(0));
            assertEquals(1, levels.levelAt(4));
            assertEquals(2, levels.levelAt(7), "a number inside right-to-left text reads left to right");
            assertEquals(0, levels.levelAt(10));
            assertEquals(
                    List.of(
                            new LevelRun(0, 4, 0),
                            new LevelRun(4, 7, 1),
                            new LevelRun(7, 9, 2),
                            new LevelRun(9, 11, 0)),
                    levels.runs(0, text.length()));
        }

        @Test
        @DisplayName("Latin under a right-to-left base is even inside an odd paragraph")
        void latinRightToLeft() {
            var levels = BidiLevels.of("abc!", Directionality.RTL);

            assertFalse(levels.isLeftToRight(), "the base is right to left, so the punctuation is");
            assertEquals(2, levels.levelAt(0));
            assertEquals(1, levels.levelAt(3));
            assertEquals(Direction.RTL, levels.directionAt(4));
        }

        @Test
        @DisplayName("every character forced right to left is at level one")
        void overrideRightToLeft() {
            var levels = BidiLevels.of("abc", new Directionality(Direction.RTL, Directionality.Source.OVERRIDE));

            for (var i = 0; i < 3; i++) {
                assertEquals(1, levels.levelAt(i));
            }
            assertEquals(List.of(new LevelRun(0, 3, 1)), levels.visualRuns(0, 3));
        }

        @Test
        @DisplayName("an offset outside the text is refused")
        void outside() {
            var levels = BidiLevels.of(SHALOM, Directionality.LTR);

            assertThrows(IndexOutOfBoundsException.class, () -> levels.levelAt(4));
            assertThrows(IndexOutOfBoundsException.class, () -> levels.directionAt(5));
            assertThrows(IndexOutOfBoundsException.class, () -> levels.visualRuns(2, 5));
        }
    }

    @Nested
    @DisplayName("the base direction")
    class Base {

        @Test
        @DisplayName("each hard line finds its own under the first strong character")
        void perParagraph() {
            var text = SHALOM + "\nhello\n123";
            var levels = BidiLevels.of(text, Directionality.AUTO);

            assertEquals(Direction.RTL, levels.directionAt(0));
            assertEquals(Direction.RTL, levels.directionAt(4), "the newline ends the first paragraph");
            assertEquals(Direction.LTR, levels.directionAt(5));
            assertEquals(Direction.LTR, levels.directionAt(text.length()), "no strong character: the fallback");
        }

        @Test
        @DisplayName("a paragraph with no strong character takes the direction given")
        void fallback() {
            var levels = BidiLevels.of("123", new Directionality(Direction.RTL, Directionality.Source.FIRST_STRONG));

            assertEquals(Direction.RTL, levels.directionAt(0));
            assertEquals(2, levels.levelAt(0));
        }

        @Test
        @DisplayName("an empty right-to-left paragraph is still right to left")
        void empty() {
            var levels = BidiLevels.of("", Directionality.RTL);

            assertFalse(levels.isLeftToRight());
            assertEquals(Direction.RTL, levels.directionAt(0));
            assertEquals(List.of(), levels.visualRuns(0, 0));
        }
    }

    @Nested
    @DisplayName("the order of a line")
    class Order {

        @Test
        @DisplayName("rule L2: a number inside right-to-left text stays left of the words before it")
        void reordered() {
            var text = "abc " + "מה" + " 12 !";
            var levels = BidiLevels.of(text, Directionality.LTR);

            assertEquals(List.of("abc >", "12>", "מה <", " !>"), order(levels, text, 0, text.length()));
        }

        @Test
        @DisplayName("a right-to-left paragraph places its first run at the right")
        void rightToLeftBase() {
            var text = SHALOM + " abc";
            var levels = BidiLevels.of(text, Directionality.AUTO);

            // The space between the two is neutral, and takes the paragraph's
            // direction: it belongs to the Hebrew.
            assertEquals(List.of("abc>", SHALOM + " <"), order(levels, text, 0, text.length()));
        }

        @Test
        @DisplayName("rule L1: trailing whitespace takes the paragraph's level and sits at its end")
        void trailingWhitespace() {
            var text = "abc  ";
            var levels = BidiLevels.of(text, Directionality.RTL);

            assertEquals(List.of("  <", "abc>"), order(levels, text, 0, text.length()));
        }

        @Test
        @DisplayName("rule L1: a tab and the whitespace before it take the paragraph's level")
        void tab() {
            var text = SHALOM + " \tabc";
            var levels = BidiLevels.of(text, Directionality.LTR);

            assertEquals(List.of(SHALOM + "<", " \tabc>"), order(levels, text, 0, text.length()));
        }

        @Test
        @DisplayName("a line is ordered on its own: the part of a run it holds")
        void partOfALine() {
            var text = "abc " + SHALOM;
            var levels = BidiLevels.of(text, Directionality.LTR);

            assertEquals(List.of("c >", "של<"), order(levels, text, 2, 6));
        }
    }

    @Nested
    @DisplayName("the CSS spelling")
    class Css {

        @Test
        @DisplayName("unicode-bidi's six keywords, three answers")
        void keywords() {
            assertEquals(UnicodeBidi.PLAINTEXT, UnicodeBidi.parse("PlainText"));
            assertNull(UnicodeBidi.parse("bidi"));
            assertEquals(Directionality.RTL, Directionality.of(Direction.RTL, UnicodeBidi.NORMAL));
            assertEquals(Directionality.LTR, Directionality.of(Direction.LTR, UnicodeBidi.ISOLATE));
            assertEquals(Directionality.LTR, Directionality.of(Direction.LTR, UnicodeBidi.EMBED));
            assertEquals(
                    new Directionality(Direction.RTL, Directionality.Source.OVERRIDE),
                    Directionality.of(Direction.RTL, UnicodeBidi.ISOLATE_OVERRIDE));
            assertEquals(
                    new Directionality(Direction.LTR, Directionality.Source.FIRST_STRONG),
                    Directionality.of(Direction.LTR, UnicodeBidi.PLAINTEXT));
            assertEquals(Directionality.AUTO, Directionality.of(Direction.LTR, UnicodeBidi.PLAINTEXT));
        }

        @Test
        @DisplayName("a run is a range at a level")
        void runs() {
            assertTrue(new LevelRun(0, 1, 3).isRightToLeft());
            assertFalse(new LevelRun(0, 1, 2).isRightToLeft());
            assertThrows(IllegalArgumentException.class, () -> new LevelRun(2, 1, 0));
            assertThrows(IllegalArgumentException.class, () -> new LevelRun(0, 1, -1));
        }
    }
}
