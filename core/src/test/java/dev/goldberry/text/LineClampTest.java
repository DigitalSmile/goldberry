package dev.goldberry.text;

import static dev.goldberry.css.TestElement.element;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.RendererRequirement;
import dev.goldberry.assets.BundledFont;
import dev.goldberry.css.ComputedStyle;
import dev.goldberry.css.Stylesheet;
import dev.goldberry.css.cascade.CascadeLayer;
import dev.goldberry.css.cascade.StyleResolver;
import dev.goldberry.css.parse.CssTokenizer;
import dev.goldberry.css.parse.TokenType;
import dev.goldberry.css.value.CssLength;
import dev.goldberry.layout.Length;
import dev.goldberry.layout.MeasureMode;
import dev.goldberry.paint.Box;
import dev.goldberry.paint.BoxPainter;
import dev.goldberry.paint.TestFrames;
import dev.goldberry.text.flow.TextFlow;
import dev.goldberry.text.font.Font;

/// `line-clamp`: a paragraph that keeps its first lines and marks the last.
class LineClampTest {

    @Nested
    @DisplayName("in the cascade")
    class Cascade {

        private ComputedStyle child(String css) {
            var sheet = Stylesheet.parse(CascadeLayer.APPLICATION, css);
            var root = element("card");
            root.with(element("text"));
            var resolver = new StyleResolver(List.of(sheet));
            var context = new CssLength.Context(16, 16);
            var parent = ComputedStyle.of(resolver.resolve(root), context, null);
            return ComputedStyle.of(resolver.resolve(root.descend(1)), context, parent);
        }

        @Test
        @DisplayName("a whole number of lines, in either spelling, and none for all of them")
        void reads() {
            assertEquals(2, child("text { line-clamp: 2 }").lineClamp());
            assertEquals(3, child("text { -webkit-line-clamp: 3 }").lineClamp());
            assertEquals(0, child("text { line-clamp: 2; line-clamp: none }").lineClamp());
            assertEquals(2, child("text { line-clamp: 2 }").textFlow().maxLines());
        }

        @Test
        @DisplayName("is not inherited")
        void notInherited() {
            assertEquals(0, child("card { line-clamp: 2 }").lineClamp());
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"0", "1.5", "-1", "2px"})
        @DisplayName("refuses what is not a count of lines")
        void refuses(String value) {
            var tokens = CssTokenizer.tokenize(value).stream()
                    .filter(t -> !t.is(TokenType.EOF))
                    .toList();
            assertFalse(ComputedStyle.applies("line-clamp", tokens, CssLength.Context.DEFAULT), value);
        }
    }

    @Nested
    @DisplayName("in a paragraph")
    class Shaped {

        private static final String TEXT = "one two three four five six seven eight nine ten eleven twelve";

        private Font font;

        @BeforeEach
        void setUp() {
            RendererRequirement.enforce();
            font = Font.bundled(BundledFont.UI, 16);
        }

        @AfterEach
        void tearDown() {
            if (font != null) {
                font.close();
            }
        }

        @Test
        @DisplayName("is measured at the lines it keeps")
        void measured() {
            var paragraph = Paragraph.of(font, TEXT);
            var all = paragraph.layout(80);
            assertTrue(all.lines().size() > 3, "the text wraps to several lines");
            var clamped = paragraph
                    .measureFunction(TextFlow.NORMAL.maxLines(2))
                    .measure(80, MeasureMode.AT_MOST, Float.NaN, MeasureMode.UNDEFINED);
            assertEquals(2 * font.lineHeight(), clamped.height(), 0.01);
            assertEquals(all.height(), paragraph.keptHeight(all, TextFlow.NORMAL), 1e-9);
            assertEquals(all.height(), paragraph.keptHeight(all, TextFlow.NORMAL.maxLines(99)), 1e-9);
        }

        @Test
        @DisplayName("draws nothing below the last line it keeps, and marks that line")
        void painted() {
            var paragraph = Paragraph.of(font, TEXT);
            var lineHeight = (int) Math.ceil(font.lineHeight());
            var clamped = paint(paragraph, TextFlow.NORMAL.maxLines(1));
            var whole = paint(paragraph, TextFlow.NORMAL);
            assertTrue(inkIn(whole, lineHeight, 3 * lineHeight) > 0, "the second line is drawn unclamped");
            assertEquals(0, inkIn(clamped, lineHeight + 1, 3 * lineHeight), "and not under a clamp of one");
            assertTrue(inkIn(clamped, 0, lineHeight) > 0, "the first line is kept");
        }

        private TestFrames.Target paint(Paragraph paragraph, TextFlow flow) {
            var target = TestFrames.of(100, 100, 1f);
            BoxPainter.paint(
                    target.frame(),
                    Box.of().size(Length.points(80), Length.AUTO).text(new Box.Text(paragraph, 0xFF000000, flow)));
            target.end();
            return target;
        }

        private static int inkIn(TestFrames.Target target, int fromY, int toY) {
            var count = 0;
            for (var y = fromY; y < toY; y++) {
                for (var x = 0; x < 100; x++) {
                    if (target.alphaAt(x, y) > 0) {
                        count++;
                    }
                }
            }
            return count;
        }
    }
}
