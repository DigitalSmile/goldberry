package io.github.digitalsmile.goldberry.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.assets.BundledFont;
import io.github.digitalsmile.goldberry.paint.TestFrames;
import io.github.digitalsmile.goldberry.text.flow.TextAlign;
import io.github.digitalsmile.goldberry.text.flow.TextDecoration;
import io.github.digitalsmile.goldberry.text.flow.TextFlow;
import io.github.digitalsmile.goldberry.text.font.Font;

/// `text-decoration`, drawn — `docs/gaps.md` G27, ADR-0321.
///
/// The half that has to be measured in **ink**, like [ParagraphFlowTest]: a rule
/// one pixel out of place renders perfectly and is wrong. What a rule is, in
/// pixels, is a row of the frame that is inked all the way across the text — which
/// glyphs never are, since a line of type has gaps in it. Every assertion here is
/// built on that one observation.
class ParagraphDecorationTest {

    private static final int BACKGROUND = 0xFF000000;
    private static final int INK = 0xFFFFFFFF;

    private static final int WIDTH = 400;
    private static final int HEIGHT = 96;

    /// Wide enough to have a long run of ink under it, and made of ordinary letters
    /// so nothing descends far.
    private static final String TEXT = "underline";

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
    @DisplayName("an undecorated paragraph draws no rule at all")
    void nothingByDefault() {
        var plain = paint(TEXT, 200, TextFlow.NORMAL);

        assertEquals(List.of(), rules(plain, TEXT), "a rule appeared under text that never asked for one");
    }

    @Test
    @DisplayName("an underline is a rule below the baseline")
    void underlineIsBelowTheBaseline() {
        var underlined = paint(TEXT, 200, TextFlow.NORMAL.decorations(TextDecoration.UNDERLINE));

        var rules = rules(underlined, TEXT);
        assertFalse(rules.isEmpty(), "nothing was drawn under the text");
        // The baseline is the font's ascent below the top, which is where the
        // paragraph was drawn from.
        var baseline = font.ascent();
        assertTrue(
                rules.getFirst() >= baseline,
                () -> "the rule is at row " + rules.getFirst() + ", above the baseline at " + baseline);
        assertTrue(
                rules.getLast() < baseline + font.descent() + 2,
                () -> "the rule is at row " + rules.getLast() + ", below the font's own descender");
    }

    @Test
    @DisplayName("a strikethrough is a rule through it")
    void lineThroughIsAboveTheBaseline() {
        var struck = paint(TEXT, 200, TextFlow.NORMAL.decorations(TextDecoration.LINE_THROUGH));

        var rules = rules(struck, TEXT);
        assertFalse(rules.isEmpty(), "nothing was drawn through the text");
        var baseline = font.ascent();
        assertTrue(
                rules.getLast() < baseline, () -> "the rule is at row " + rules.getLast() + ", not through the text");
        assertTrue(
                rules.getFirst() > baseline / 3, () -> "the rule is at row " + rules.getFirst() + ", above the caps");
    }

    @Test
    @DisplayName("both at once are two rules")
    void bothAreTwoRules() {
        var both = paint(TEXT, 200, TextFlow.NORMAL.decorations(TextDecoration.UNDERLINE, TextDecoration.LINE_THROUGH));

        assertEquals(2, runs(rules(both, TEXT)), "two decorations have to be two rules and not one thick one");
    }

    /// The rule is as long as the **text**, not as wide as the box: a label
    /// underlined to the end of a 200-point cell would look like a form field.
    @Test
    @DisplayName("the rule is as long as the line, not as wide as the box")
    void theRuleStopsWithTheText() {
        var underlined = paint(TEXT, 200, TextFlow.NORMAL.decorations(TextDecoration.UNDERLINE));
        var row = rules(underlined, TEXT).getFirst();

        var width = font.widthOf(TEXT);
        var last = lastInkedColumnOfRow(underlined, row);
        assertEquals(width, last + 1, 2.0, "the rule ends at column " + last + " and the text at " + width);
    }

    @Test
    @DisplayName("it follows the alignment, because it follows the glyphs")
    void theRuleIsAligned() {
        var box = 200;
        var centred = paint(
                TEXT, box, TextFlow.NORMAL.decorations(TextDecoration.UNDERLINE).textAlign(TextAlign.CENTER));

        // Scanned where a centred line *should* be, which is the assertion: a rule
        // left at the box's edge produces no solid row in this window at all.
        var indent = (int) Math.round((box - font.widthOf(TEXT)) / 2);
        var row = rulesBetween(centred, indent + 3, indent + (int) font.widthOf(TEXT) - 3)
                .getFirst();
        var first = firstInkedColumnOfRow(centred, row);
        assertEquals(indent, first, 2.0, "the rule stayed at the box's edge");
    }

    @Test
    @DisplayName("a blank line in the middle is not underlined")
    void blankLinesAreNotDecorated() {
        var paragraph = paint("one\n\ntwo", 200, TextFlow.NORMAL.decorations(TextDecoration.UNDERLINE));

        // Three lines, two of them with text: the rules are under the first and the
        // third, and the run in between is the blank one.
        var lineHeight = font.lineHeight();
        var middle = rules(paragraph, "one").stream()
                .filter(row -> row > lineHeight && row < 2 * lineHeight)
                .toList();
        assertEquals(List.of(), middle, "a rule under a blank line is a rule nobody can explain");
    }

    /// The thickness is the face's, scaled — which is the whole reason this is the
    /// toolkit's job: an application drawing a rectangle would pick one number and
    /// be wrong at every other size.
    @Test
    @DisplayName("a bigger size draws a thicker rule")
    void thicknessFollowsTheFace() {
        var small = rules(paint(TEXT, 200, TextFlow.NORMAL.decorations(TextDecoration.UNDERLINE)), TEXT);
        List<Integer> large;
        try (var big = Font.bundled(BundledFont.UI, 40)) {
            var target = TestFrames.of(WIDTH, HEIGHT, 1.0f);
            target.frame().fill(BACKGROUND);
            Paragraph.of(big, TEXT)
                    .paint(target.frame(), 0, 0, 380, INK, TextFlow.NORMAL.decorations(TextDecoration.UNDERLINE));
            target.end();
            large = rules(target, TEXT, big);
        }

        assertTrue(
                large.size() > small.size(),
                "the rule is " + large.size() + " rows at 40pt and " + small.size() + " at 16pt");
    }

    @Test
    @DisplayName("an ellipsised line is ruled across the mark too")
    void aCutLineIsRuledToTheEnd() {
        var box = 60;
        var cut = paint(
                "Export the current selection as a document",
                box,
                TextFlow.ELLIPSIS.decorations(TextDecoration.UNDERLINE));

        var row = rules(cut, "Ex").getFirst();
        var last = lastInkedColumnOfRow(cut, row);
        assertEquals(box, last + 1, 2.0, "the rule stopped short of the line's own ellipsis");
    }

    // --- helpers --------------------------------------------------------------

    private TestFrames.Target paint(String text, double maxWidth, TextFlow flow) {
        var target = TestFrames.of(WIDTH, HEIGHT, 1.0f);
        target.frame().fill(BACKGROUND);
        Paragraph.of(font, text).paint(target.frame(), 0, 0, maxWidth, INK, flow);
        target.end();
        return target;
    }

    private static boolean isInk(TestFrames.Target target, int x, int y) {
        return target.pixel(x, y) != BACKGROUND;
    }

    /// The rows that are inked **all the way across** a run as wide as `sample` —
    /// which is what a rule is and what a line of glyphs never is.
    private List<Integer> rules(TestFrames.Target target, String sample) {
        return rules(target, sample, font);
    }

    private static List<Integer> rules(TestFrames.Target target, String sample, Font with) {
        // From a few pixels in, so the first glyph's left bearing and the rule's own
        // end do not have to agree to the pixel.
        return rulesBetween(target, 3, (int) Math.floor(with.widthOf(sample)) - 3);
    }

    /// The rows inked all the way across `[from, to)`.
    private static List<Integer> rulesBetween(TestFrames.Target target, int from, int to) {
        var rows = new ArrayList<Integer>();
        for (var y = 0; y < HEIGHT; y++) {
            var solid = true;
            for (var x = from; x < to; x++) {
                if (!isInk(target, x, y)) {
                    solid = false;
                    break;
                }
            }
            if (solid && to > from) {
                rows.add(y);
            }
        }
        return List.copyOf(rows);
    }

    /// How many separate rules `rows` describes — consecutive rows are one rule.
    private static int runs(List<Integer> rows) {
        var count = 0;
        var previous = Integer.MIN_VALUE;
        for (var row : rows) {
            if (row != previous + 1) {
                count++;
            }
            previous = row;
        }
        return count;
    }

    private static int firstInkedColumnOfRow(TestFrames.Target target, int y) {
        for (var x = 0; x < WIDTH; x++) {
            if (isInk(target, x, y)) {
                return x;
            }
        }
        return -1;
    }

    private static int lastInkedColumnOfRow(TestFrames.Target target, int y) {
        for (var x = WIDTH - 1; x >= 0; x--) {
            if (isInk(target, x, y)) {
                return x;
            }
        }
        return -1;
    }
}
