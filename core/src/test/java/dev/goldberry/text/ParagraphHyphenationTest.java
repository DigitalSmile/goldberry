package dev.goldberry.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.BitSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.assets.BundledFont;
import dev.goldberry.css.Decoration;
import dev.goldberry.layout.Direction;
import dev.goldberry.paint.InlineRanges;
import dev.goldberry.paint.TestFrames;
import dev.goldberry.render.Cursor;
import dev.goldberry.text.bidi.Directionality;
import dev.goldberry.text.flow.TextAlign;
import dev.goldberry.text.flow.TextFlow;
import dev.goldberry.text.flow.WordBreak;
import dev.goldberry.text.font.Font;

class ParagraphHyphenationTest {
    private Font font;

    @BeforeEach
    void openFont() {
        RendererRequirement.enforce();
        font = Font.bundled(BundledFont.CODE, 18);
    }

    @AfterEach
    void closeFont() {
        if (font != null) {
            font.close();
        }
    }

    private static BitSet breaks(int... offsets) {
        var bits = new BitSet();
        for (var offset : offsets) {
            bits.set(offset);
        }
        return bits;
    }

    @Test
    void longestPrefixThatFitsIncludesTheMarkAndResizesWithoutChangingTheSource() {
        var source = Paragraph.of(font, "a hyphenation");
        var points = breaks(4, 8);
        var paragraph = source.withHyphenation(points);
        assertSame(paragraph, source.withHyphenation(breaks(4, 8)));
        points.clear();
        var width = paragraph.widthBetween(0, 8) + font.widthOf(font.hyphen());
        var fit = paragraph.layout(width).lines().getFirst();
        assertEquals("a hyphen", fit.textIn(source.text()));
        assertEquals(width, fit.width(), 0.001);
        assertEquals(4, paragraph.layout(width - 1).lines().getFirst().end());
        assertEquals("a hyphenation", paragraph.text());
        var wide = paragraph.layout(1000);
        assertEquals(1, wide.lineCount());
        assertEquals(0, paragraph.hyphenWidthOf(wide.lines().getFirst()));
        assertEquals("a ", source.layout(width).lines().getFirst().textIn(source.text()));
    }

    @Test
    void aSoftHyphenDrawsOnlyWhenChosenAndCanBeSuppressed() {
        var text = "hy\u00ADphenation";
        var source = Paragraph.of(font, text);
        var paragraph = source.withHyphenation(breaks(3));
        var width = paragraph.widthBetween(0, 3) + font.widthOf(font.hyphen());
        var line = paragraph.layout(width).lines().getFirst();
        assertEquals(3, line.end());
        assertEquals(width, line.width(), 0.001);
        assertEquals(1, source.withHyphenation(breaks()).layout(width).lineCount());
        assertEquals(font.widthOf("hyphenation"), paragraph.layout(1000).width(), 0.01);
        var arbitrary = paragraph.layout(width, TextFlow.NORMAL.wordBreak(WordBreak.BREAK_ALL));
        assertTrue(arbitrary.lines().stream().allMatch(row -> paragraph.hyphenWidthOf(row) == 0));
    }

    @Test
    void aMarkUsesTheEndingSpansFontAndInkAndTakesPartInTheLinkRectangle() {
        try (var large = Font.bundled(BundledFont.CODE, 29)) {
            var paragraph = Paragraph.join(List.of(Paragraph.of(font, "a "), Paragraph.of(large, "hyphenation")))
                    .withHyphenation(breaks(4, 8));
            var width = paragraph.widthBetween(0, 8) + large.widthOf(large.hyphen()) + 2;
            var flow = TextFlow.NORMAL.textAlign(TextAlign.JUSTIFY);
            var first = paragraph.layout(width, flow).lines().getFirst();
            assertEquals(8, first.end());
            assertEquals(large.widthOf(large.hyphen()), paragraph.hyphenWidthOf(first), 0.001);
            var link = new InlineRanges(
                    List.of(new InlineRanges.Range(2, 13, new Object(), Cursor.DEFAULT, Decoration.NONE)));
            var piece = link.place(paragraph, flow, width).getFirst().getFirst();
            assertEquals(6, piece.end());
            assertEquals(width, piece.bounds().right(), 0.01);
            assertEquals(paragraph.hyphenWidthOf(first), piece.gapAfter(), 0.001);

            var target = TestFrames.of(240, 160, 1);
            var red = new SpanPaint(2, 13, 0xFFFF0000, Set.of());
            paragraph.paint(target.frame(), 0, 0, width, 0xFF000000, flow, List.of(red));
            target.end();
            boolean ink = false;
            for (int y = 0; y < paragraph.heightOf(first); y++) {
                for (int x = (int) Math.ceil(width - paragraph.hyphenWidthOf(first)); x < width; x++) {
                    ink |= (target.pixel(x, y) & 0xFF0000) != 0;
                }
            }
            assertTrue(ink, "the red hyphen is painted in the mark's part of the link");
        }
    }

    @Test
    void rightToLeftWordOwnsTheMarkAtItsLeftEdge() {
        var rtl = new Directionality(Direction.RTL, Directionality.Source.OVERRIDE);
        var paragraph = Paragraph.of(font, "hyphenation", 0, 0, rtl).withHyphenation(breaks(2, 6));
        var width = paragraph.widthBetween(0, 6) + font.widthOf(font.hyphen());
        var ranges = new InlineRanges(List.of(
                new InlineRanges.Range(0, 2, null, Cursor.DEFAULT, Decoration.NONE),
                new InlineRanges.Range(2, 11, null, Cursor.DEFAULT, Decoration.NONE)));
        var pieces = ranges.place(paragraph, TextFlow.NORMAL, width);
        var split = pieces.get(1).getFirst();
        assertTrue(split.rtl());
        assertEquals(0, split.bounds().left(), 0.001);
        assertEquals(4, split.end());
        assertEquals(font.widthOf(font.hyphen()), split.gapAfter(), 0.001);
        assertEquals(width, pieces.getFirst().getFirst().bounds().right(), 0.001);
    }

    @Test
    void aGraphemeAndAnExistingHyphenCannotGainAnotherHyphen() {
        var accent = Paragraph.of(font, "a\u0301bcdef").withHyphenation(breaks(1));
        assertEquals(1, accent.layout(5).lineCount());
        var literal = Paragraph.of(font, "half-way").withHyphenation(breaks(5));
        var line = literal.layout(font.widthOf("half-") + .5).lines().getFirst();
        assertEquals("half-", line.textIn(literal.text()));
        assertEquals(0, literal.hyphenWidthOf(line));
    }
}
