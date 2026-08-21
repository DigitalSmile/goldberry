package io.github.digitalsmile.goldberry.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.assets.BundledFont;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The two directions a caret needs: an offset to an x, and an x back to an
/// offset.
///
/// What is asserted is mostly that the pair **round-trips**. A caret is right
/// when clicking where it is drawn leaves it where it was, and that is a property
/// of the two functions together rather than of either one — a shared error in
/// both would pass every test that checked only one of them against a number
/// somebody typed.
class ParagraphCaretTest {

    private Font font;

    @BeforeEach
    void openFont() {
        RendererRequirement.enforce();
        font = Font.bundled(BundledFont.UI, 16);
    }

    @AfterEach
    void closeFont() {
        if (font != null) {
            font.close();
        }
    }

    @Test
    @DisplayName("a caret at the start is at zero and one at the end is at the width")
    void spansTheText() {
        var paragraph = Paragraph.of(font, "Goldberry");

        assertEquals(0, paragraph.widthBetween(0, 0), 0.001);
        assertEquals(font.widthOf("Goldberry"), paragraph.widthBetween(0, 9), 0.01);
    }

    @Test
    @DisplayName("a range's width is the same measured directly")
    void rangeMatchesTheFont() {
        var paragraph = Paragraph.of(font, "Goldberry");

        // "berry" measured as a range of the shaped paragraph against "berry"
        // shaped on its own. They can differ by a kern at the join -- the class
        // says so -- so this is the assertion that the difference stays small
        // rather than that there is none.
        assertEquals(font.widthOf("berry"), paragraph.widthBetween(4, 9), 1.0);
    }

    @Test
    @DisplayName("every caret position round-trips through its own x")
    void roundTripsEveryPosition() {
        var text = "Yoga laid this out.";
        var paragraph = Paragraph.of(font, text);

        for (var offset = 0; offset <= text.length(); offset++) {
            var x = paragraph.widthBetween(0, offset);

            assertEquals(offset, paragraph.offsetAt(0, text.length(), x),
                    "clicking exactly where the caret is drawn moved it, at offset " + offset);
        }
    }

    @Test
    @DisplayName("a click past the midpoint of a character lands after it")
    void nearestPositionWins() {
        var paragraph = Paragraph.of(font, "abc");

        var beforeB = paragraph.widthBetween(0, 1);
        var afterB = paragraph.widthBetween(0, 2);
        var midpoint = (beforeB + afterB) / 2;

        // A hair either side of the midpoint, which is the only place the answer
        // is allowed to change. This is what makes a click feel like it landed
        // where the pointer was rather than on the character it was over.
        assertEquals(1, paragraph.offsetAt(0, 3, midpoint - 0.5));
        assertEquals(2, paragraph.offsetAt(0, 3, midpoint + 0.5));
    }

    @Test
    @DisplayName("a click before the line is the start and one past it is the end")
    void clampsToTheLine() {
        var paragraph = Paragraph.of(font, "Goldberry");

        assertEquals(0, paragraph.offsetAt(0, 9, -100));
        assertEquals(0, paragraph.offsetAt(0, 9, 0));
        assertEquals(9, paragraph.offsetAt(0, 9, 10_000));
    }

    @Test
    @DisplayName("a click never lands inside a surrogate pair")
    void snapsToGraphemes() {
        // One code point, two chars. The pair spans offsets 1..3, so offset 2 is
        // a real index into the string and is not a place a caret can be: a
        // keystroke there would insert between the two halves of a character.
        // Offsets 1 and 3 are its edges and are both legal.
        var text = "a🎨b";
        var paragraph = Paragraph.of(font, text);

        for (var step = 0; step <= 40; step++) {
            var x = paragraph.widthBetween(0, text.length()) * step / 40;
            var offset = paragraph.offsetAt(0, text.length(), x);

            assertNotEquals(2, offset, "the caret landed inside the surrogate pair");
        }
    }

    @Test
    @DisplayName("a wrapped line's caret is measured from that line's own start")
    void worksPerLine() {
        var text = "Yoga laid this out, HarfBuzz shaped it, and Blend2D drew it.";
        var paragraph = Paragraph.of(font, text);
        var layout = paragraph.layout(120);

        assertTrue(layout.lineCount() > 1, "this text was supposed to wrap at 120");

        for (var line : layout.lines()) {
            // Zero at the left edge of every line, not a distance from the
            // paragraph's first character -- which is the whole reason the width
            // takes two offsets rather than one.
            assertEquals(0, paragraph.widthBetween(line.start(), line.start()), 0.001);
            assertEquals(line.start(), paragraph.offsetAt(line.start(), line.end(), 0));

            var middle = line.start() + (line.end() - line.start()) / 2;
            var x = paragraph.widthBetween(line.start(), middle);
            assertEquals(middle, paragraph.offsetAt(line.start(), line.end(), x));
        }
    }

    @Test
    @DisplayName("a click on an empty line is the only position it has")
    void emptyLineHasOnePosition() {
        var paragraph = Paragraph.of(font, "");

        assertEquals(0, paragraph.offsetAt(0, 0, 0));
        assertEquals(0, paragraph.offsetAt(0, 0, 500));
        assertEquals(0, paragraph.widthBetween(0, 0), 0.001);
    }

    @Test
    @DisplayName("an offset outside the text is refused rather than clamped")
    void refusesOffTheEnd() {
        var paragraph = Paragraph.of(font, "abc");

        // Clamping would turn a caller's off-by-one into a caret that quietly
        // stops one short of the end, which is worse than a stack trace naming
        // the line that computed it.
        assertThrows(IndexOutOfBoundsException.class, () -> paragraph.widthBetween(0, 4));
        assertThrows(IndexOutOfBoundsException.class, () -> paragraph.widthBetween(-1, 2));
        assertThrows(IllegalArgumentException.class, () -> paragraph.widthBetween(2, 1));
        assertThrows(IllegalArgumentException.class, () -> paragraph.offsetAt(2, 1, 0));
    }
}
