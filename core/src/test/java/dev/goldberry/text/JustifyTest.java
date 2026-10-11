package dev.goldberry.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.assets.BundledFont;
import dev.goldberry.paint.TestFrames;
import dev.goldberry.text.flow.TextAlign;
import dev.goldberry.text.flow.TextFlow;
import dev.goldberry.text.font.Font;

/// `text-align: justify`: every line but a paragraph's last reaches both
/// edges, its slack shared among its word gaps.
class JustifyTest {

    private static final int BACKGROUND = 0xFF000000;
    private static final int INK = 0xFFFFFFFF;
    private static final int WIDTH = 400;
    private static final int HEIGHT = 120;
    private static final double BOX = 180;

    /// Wraps to several lines at [#BOX], with a hard break before its last
    /// sentence.
    private static final String TEXT = "Export the current selection as a document and keep the history\nThen close it";

    private static final TextFlow JUSTIFIED = TextFlow.NORMAL.textAlign(TextAlign.JUSTIFY);

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

    private TestFrames.Target paint(TextFlow flow, List<SpanPaint> spans) {
        var target = TestFrames.of(WIDTH, HEIGHT, 1.0f);
        target.frame().fill(BACKGROUND);
        Paragraph.of(font, TEXT).paint(target.frame(), 0, 0, BOX, INK, flow, spans);
        target.end();
        return target;
    }

    /// The last inked column on each line, top to bottom.
    private List<Integer> lineEnds(TestFrames.Target target) {
        var lines = Paragraph.of(font, TEXT).layout(BOX).lines();
        var lineHeight = font.lineHeight();
        return IntStream.range(0, lines.size())
                .map(i -> lastInked(target, (int) Math.ceil(i * lineHeight), (int) Math.floor((i + 1) * lineHeight))
                        .orElse(-1))
                .boxed()
                .toList();
    }

    private static OptionalInt lastInked(TestFrames.Target target, int top, int bottom) {
        for (var x = WIDTH - 1; x >= 0; x--) {
            for (var y = top; y < Math.min(bottom, HEIGHT); y++) {
                if (target.pixel(x, y) != BACKGROUND) {
                    return OptionalInt.of(x);
                }
            }
        }
        return OptionalInt.empty();
    }

    @Test
    @DisplayName("a wrapped line reaches the far edge, and the last of each paragraph does not")
    void reachesBothEdges() {
        var layout = Paragraph.of(font, TEXT).layout(BOX);
        assertTrue(layout.lines().size() >= 4, "the fixture has to wrap for this to mean anything");
        var ends = lineEnds(paint(JUSTIFIED, List.of()));
        var natural = lineEnds(paint(TextFlow.NORMAL, List.of()));
        for (var i = 0; i < ends.size(); i++) {
            var line = layout.lines().get(i);
            var last = line.end() >= TEXT.length() || TEXT.charAt(line.end()) == '\n';
            if (last) {
                assertEquals(
                        natural.get(i), ends.get(i), "line " + i + " ends a paragraph and is placed, not respaced");
            } else {
                assertEquals(BOX, ends.get(i), 2.5, "line " + i + " reaches the box's far edge");
            }
        }
    }

    @Test
    @DisplayName("a styled paragraph is respaced the same way")
    void styled() {
        var spans = List.of(new SpanPaint(0, 6, 0xFFFF0000, Set.of()));
        assertEquals(lineEnds(paint(JUSTIFIED, List.of())), lineEnds(paint(JUSTIFIED, spans)));
    }

    @Test
    @DisplayName("measuring a justified paragraph gives its natural width")
    void measure() {
        var paragraph = Paragraph.of(font, TEXT);
        assertEquals(
                paragraph.layout(BOX).width(), paragraph.layout(BOX, JUSTIFIED).width());
    }

    @Test
    @DisplayName("an unjustified line keeps the start, as text-align: start does")
    void placesAtTheStart() {
        assertEquals(0, TextAlign.JUSTIFY.indentOf(50, 200));
    }
}
