package dev.goldberry.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.assets.BundledFont;
import dev.goldberry.paint.TestFrames;
import dev.goldberry.text.flow.TextDecoration;
import dev.goldberry.text.flow.TextFlow;
import dev.goldberry.text.font.Font;

/// Paragraphs joined end to end: one text, several fonts, wrapped as one.
///
/// The assertions are the ones [ParagraphTest] makes of a plain paragraph,
/// asked across a seam: each span measures what its own font says, the line
/// breaker sees the whole text, and a line is as tall as the tallest font on
/// it.
@DisplayName("a joined paragraph")
class ParagraphJoinTest {

    private static final String GIVE = "Give it ";
    private static final String BLEEDING = "Bleeding";
    private static final String REST = " equal to the amount of boost it lost, then reset it.";

    private Font regular;
    private Font strong;
    private Font large;

    @BeforeEach
    void openFonts() {
        RendererRequirement.enforce();
        regular = Font.bundled(BundledFont.UI, 16);
        strong = Font.bundled(BundledFont.UI_STRONG, 16);
        large = Font.bundled(BundledFont.UI, 30);
    }

    @AfterEach
    void closeFonts() {
        for (var font : new Font[] {regular, strong, large}) {
            if (font != null) {
                font.close();
            }
        }
    }

    private Paragraph sentence(Font keyword) {
        return Paragraph.join(
                List.of(Paragraph.of(regular, GIVE), Paragraph.of(keyword, BLEEDING), Paragraph.of(regular, REST)));
    }

    @Nested
    @DisplayName("offsets")
    class Offsets {

        @Test
        @DisplayName("its text is the spans' text end to end, and it is measured in the first span's font")
        void textIsConcatenated() {
            var joined = sentence(strong);

            assertEquals(GIVE + BLEEDING + REST, joined.text());
            assertSame(regular, joined.font());
        }

        @Test
        @DisplayName("each span is as wide as its own font says, at its own offsets")
        void eachSpanKeepsItsWidth() {
            var joined = sentence(strong);
            var keywordStart = GIVE.length();
            var keywordEnd = keywordStart + BLEEDING.length();

            assertEquals(regular.widthOf(GIVE), joined.widthBetween(0, keywordStart), 0.01);
            assertEquals(strong.widthOf(BLEEDING), joined.widthBetween(keywordStart, keywordEnd), 0.01);
            assertEquals(
                    regular.widthOf(REST),
                    joined.widthBetween(keywordEnd, joined.text().length()),
                    0.01);
            assertTrue(
                    strong.widthOf(BLEEDING) > regular.widthOf(BLEEDING),
                    "the bold word is wider, so the test can tell the fonts apart");
        }

        @Test
        @DisplayName("a span at another size is rescaled into the first span's units")
        void anotherSizeIsRescaled() {
            var joined = sentence(large);
            var keywordStart = GIVE.length();

            assertEquals(
                    large.widthOf(BLEEDING), joined.widthBetween(keywordStart, keywordStart + BLEEDING.length()), 0.05);
        }

        @Test
        @DisplayName("a caret lands on the seam between two spans")
        void caretFindsTheSeam() {
            var joined = sentence(strong);
            var seam = joined.widthBetween(0, GIVE.length());

            assertEquals(GIVE.length(), joined.offsetAt(0, joined.text().length(), seam));
        }

        @Test
        @DisplayName("one span is that span, and none is refused")
        void oneAndNone() {
            var only = Paragraph.of(regular, GIVE);

            assertSame(only, Paragraph.join(List.of(only)));
            assertThrows(IllegalArgumentException.class, () -> Paragraph.join(List.of()));
        }

        @Test
        @DisplayName("a joined span is flattened into the join")
        void joinedSpansFlatten() {
            var head = Paragraph.join(List.of(Paragraph.of(regular, GIVE), Paragraph.of(strong, BLEEDING)));
            var joined = Paragraph.join(List.of(head, Paragraph.of(regular, REST)));
            var direct = sentence(strong);

            assertEquals(direct.text(), joined.text());
            assertEquals(direct.layout(200).height(), joined.layout(200).height(), 0.001);
            assertEquals(
                    direct.widthBetween(0, direct.text().length()),
                    joined.widthBetween(0, joined.text().length()),
                    0.001);
        }
    }

    @Nested
    @DisplayName("line breaking")
    class LineBreaking {

        @Test
        @DisplayName("runs over the whole text, so a line holds words of two spans")
        void breaksAcrossTheSeam() {
            var joined = sentence(strong);
            var keywordEnd = GIVE.length() + BLEEDING.length();
            var layout = joined.layout(220);

            assertTrue(layout.lineCount() > 1, "the sentence wraps at 220");
            var first = layout.lines().getFirst();
            assertEquals(0, first.start());
            assertTrue(
                    first.end() > keywordEnd,
                    () -> "the first line goes on past the keyword into the rest: " + first.textIn(joined.text()));
            for (var line : layout.lines()) {
                assertTrue(line.width() <= 220, () -> "a line wider than its width: " + line.textIn(joined.text()));
            }
            // Every character is on exactly one line, in order.
            for (var i = 1; i < layout.lineCount(); i++) {
                assertEquals(
                        layout.lines().get(i - 1).end(), layout.lines().get(i).start());
            }
            assertEquals(joined.text().length(), layout.lines().getLast().end());
        }

        @Test
        @DisplayName("breaks a line where one plain paragraph of the same widths would")
        void breaksWhereOneFontWould() {
            // The same font in every span: the join must change nothing about
            // where the lines fall.
            var joined = sentence(regular);
            var plain = Paragraph.of(regular, GIVE + BLEEDING + REST);

            var a = joined.layout(180);
            var b = plain.layout(180);
            assertEquals(b.lineCount(), a.lineCount());
            for (var i = 0; i < a.lineCount(); i++) {
                assertEquals(b.lines().get(i).start(), a.lines().get(i).start());
                assertEquals(b.lines().get(i).end(), a.lines().get(i).end());
            }
            assertEquals(b.height(), a.height(), 0.0);
        }

        @Test
        @DisplayName("keeps a newline inside a span a hard break")
        void newlineInsideASpan() {
            var joined = Paragraph.join(List.of(Paragraph.of(regular, "one\ntwo "), Paragraph.of(strong, "three")));

            var layout = joined.layout(Paragraph.UNCONSTRAINED);
            assertEquals(2, layout.lineCount());
            assertEquals("two three", layout.lines().get(1).textIn(joined.text()));
        }
    }

    @Nested
    @DisplayName("line height")
    class LineHeight {

        @Test
        @DisplayName("is the font's when every span shares it")
        void oneFont() {
            var joined = sentence(regular);
            var layout = joined.layout(180);

            assertEquals(layout.lineCount() * regular.lineHeight(), layout.height(), 0.0);
            assertEquals(regular.ascent(), joined.ascentOf(layout.lines().getFirst()), 0.0);
        }

        @Test
        @DisplayName("is the tallest font's on a line that holds it, and only there")
        void tallestOnTheLine() {
            var joined = sentence(large);
            var layout = joined.layout(240);
            var keywordStart = GIVE.length();

            assertTrue(layout.lineCount() > 2, "enough lines for one without the large word");
            var holding = layout.lines().getFirst();
            assertTrue(holding.end() > keywordStart, "the large word is on the first line");
            assertEquals(large.lineHeight(), joined.heightOf(holding), 0.01);
            assertEquals(large.ascent(), joined.ascentOf(holding), 0.01);

            var last = layout.lines().getLast();
            assertEquals(regular.lineHeight(), joined.heightOf(last), 0.01);
            assertEquals(regular.ascent(), joined.ascentOf(last), 0.01);

            var sum = 0.0;
            for (var line : layout.lines()) {
                sum += joined.heightOf(line);
            }
            assertEquals(sum, layout.height(), 0.001, "the layout is as tall as its lines");
            assertTrue(layout.height() > layout.lineCount() * regular.lineHeight());
        }
    }

    @Nested
    @DisplayName("the cache")
    class Cache {

        @Test
        @DisplayName("keeps one join for the same spans, and shapes nothing to make it")
        void sameSpansSameJoin() {
            var cache = ParagraphCache.create();
            var spans = List.of(
                    cache.paragraph(regular, GIVE), cache.paragraph(strong, BLEEDING), cache.paragraph(regular, REST));
            var shaped = cache.shapedCharacters();

            var first = cache.join(spans);
            var again = cache.join(List.of(
                    cache.paragraph(regular, GIVE), cache.paragraph(strong, BLEEDING), cache.paragraph(regular, REST)));

            assertSame(first, again);
            assertEquals(shaped, cache.shapedCharacters(), "joining shapes nothing");
            assertNotSame(first, cache.join(List.of(spans.get(0), spans.get(2))), "other spans, another join");
        }
    }

    @Nested
    @DisplayName("painting")
    class Painting {

        private static final int BLACK = 0xFF000000;
        private static final int WHITE = 0xFFFFFFFF;
        private static final int RED = 0xFFFF0000;

        private TestFrames.Target paint(Paragraph paragraph, List<SpanPaint> spans) {
            var target = TestFrames.of(600, 40, 1.0f);
            target.frame().fill(BLACK);
            paragraph.paint(target.frame(), 0, 4, 600, WHITE, TextFlow.NORMAL, spans);
            target.end();
            return target;
        }

        /// The reddest and the greenest channel inked in columns `[from, to)`.
        private static int[] inks(TestFrames.Target target, int from, int to) {
            var red = 0;
            var green = 0;
            for (var x = from; x < to; x++) {
                for (var y = 0; y < 40; y++) {
                    var pixel = target.pixel(x, y);
                    red = Math.max(red, (pixel >>> 16) & 0xFF);
                    green = Math.max(green, (pixel >>> 8) & 0xFF);
                }
            }
            return new int[] {red, green};
        }

        @Test
        @DisplayName("draws a span in its own colour and the rest in the paragraph's")
        void spanHasItsColour() {
            var joined = sentence(strong);
            var start = GIVE.length();
            var end = start + BLEEDING.length();
            var target = paint(joined, List.of(new SpanPaint(start, end, RED, TextDecoration.NONE)));

            var left = (int) Math.ceil(joined.widthBetween(0, start)) + 1;
            var right = (int) Math.floor(joined.widthBetween(0, end)) - 1;
            var keyword = inks(target, left, right);
            assertTrue(
                    keyword[0] > 200 && keyword[1] == 0, () -> "the keyword is red: " + keyword[0] + "/" + keyword[1]);
            var before = inks(target, 0, left - 2);
            assertTrue(before[1] > 200, "the words before it are white");
        }

        @Test
        @DisplayName("draws a plain paragraph with no spans exactly as the plain form does")
        void noSpansIsThePlainPath() {
            var plain = Paragraph.of(regular, GIVE + BLEEDING + REST);
            var through = paint(plain, List.of());
            var direct = TestFrames.of(600, 40, 1.0f);
            direct.frame().fill(BLACK);
            plain.paint(direct.frame(), 0, 4, 600, WHITE, TextFlow.NORMAL);
            direct.end();

            for (var x = 0; x < 600; x++) {
                for (var y = 0; y < 40; y++) {
                    assertEquals(direct.pixel(x, y), through.pixel(x, y), "pixel " + x + "," + y);
                }
            }
        }

        @Test
        @DisplayName("underlines a span by its own rules, and nothing else")
        void spanHasItsRules() {
            var joined = sentence(regular);
            var start = GIVE.length();
            var end = start + BLEEDING.length();
            var ruled = paint(joined, List.of(new SpanPaint(start, end, WHITE, Set.of(TextDecoration.UNDERLINE))));
            var bare = paint(joined, List.of());

            var left = (int) Math.ceil(joined.widthBetween(0, start)) + 1;
            var right = (int) Math.floor(joined.widthBetween(0, end)) - 1;
            var baseline = (int) Math.ceil(4 + regular.ascent());
            // A rule is a row inked from one end of the word to the other, which
            // no row of the letters themselves is.
            var row = -1;
            for (var y = baseline; y < 40 && row < 0; y++) {
                if (inkedAcross(ruled, y, left, right)) {
                    row = y;
                }
            }
            assertTrue(row >= 0, "the keyword is underlined");
            assertTrue(!inkedAcross(bare, row, left, right), "and not without its span");
            var lead = (int) Math.floor(joined.widthBetween(0, start)) - 2;
            for (var x = 0; x < lead; x++) {
                assertEquals(bare.pixel(x, row), ruled.pixel(x, row), "the words before it are not underlined");
            }
        }

        private static boolean inkedAcross(TestFrames.Target target, int y, int from, int to) {
            for (var x = from; x < to; x++) {
                if (((target.pixel(x, y) >>> 8) & 0xFF) < 100) {
                    return false;
                }
            }
            return true;
        }
    }
}
