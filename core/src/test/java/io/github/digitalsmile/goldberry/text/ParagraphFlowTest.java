package io.github.digitalsmile.goldberry.text;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalInt;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.assets.BundledFont;
import io.github.digitalsmile.goldberry.natives.yoga.measure.MeasureMode;
import io.github.digitalsmile.goldberry.paint.TestFrames;
import io.github.digitalsmile.goldberry.text.flow.TextAlign;
import io.github.digitalsmile.goldberry.text.flow.TextFlow;
import io.github.digitalsmile.goldberry.text.flow.TextOverflow;
import io.github.digitalsmile.goldberry.text.flow.WhiteSpace;
import io.github.digitalsmile.goldberry.text.font.Font;

/// `white-space` and `text-overflow`, measured and then drawn.
///
/// Two halves that have to be tested separately because they fail separately.
/// **The measurement** is what makes a cut label possible at all: a box with text
/// is a measured leaf, so a paragraph that answers "as wide as you offered" can
/// never overflow anything, and three attempts at clipping one failed on exactly
/// that ([ADR-0235]). **The ink** is what says the cut happened where it should —
/// inside the width for an ellipsis, past it for a plain `nowrap` — and a
/// truncation that is off by a glyph renders perfectly and is wrong
/// ([ADR-0255]).
class ParagraphFlowTest {

    private static final int BACKGROUND = 0xFF000000;
    private static final int INK = 0xFFFFFFFF;

    /// Long enough that it wraps to several lines in any box a label sits in, and
    /// made of ordinary words so the break points are unremarkable.
    private static final String LONG = "Export the current selection as a document";

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

    // --- the measurement ------------------------------------------------------

    @Test
    @DisplayName("nowrap reports the width the text wants, not the width it was offered")
    void nowrapMeasuresNatural() {
        var paragraph = Paragraph.of(font, LONG);
        var natural = font.widthOf(LONG);
        var offered = 80f;
        assertTrue(natural > offered, "the fixture has to be wider than the box for this to mean anything");

        var wrapping = paragraph
                .measureFunction(TextFlow.NORMAL)
                .measure(offered, MeasureMode.AT_MOST, Float.NaN, MeasureMode.UNDEFINED);
        var nowrap = paragraph
                .measureFunction(TextFlow.ELLIPSIS)
                .measure(offered, MeasureMode.AT_MOST, Float.NaN, MeasureMode.UNDEFINED);

        assertTrue(wrapping.width() <= offered, "a wrapping paragraph fits the offer by construction");
        assertTrue(wrapping.height() > font.lineHeight() * 1.5, "and pays for it in lines");

        assertEquals(natural, nowrap.width(), 0.5, "nowrap ignores the offer");
        assertEquals(font.lineHeight(), nowrap.height(), 0.01, "and stays one line");
    }

    @Test
    @DisplayName("text-overflow does not change the measurement, so the ellipsis cannot decide the width")
    void ellipsisIsNotMeasured() {
        // The rule the whole design rests on. A paragraph whose measurement
        // shrank because it had been truncated would be a box that shrank
        // because it was too narrow -- which either settles somewhere nobody
        // asked for or oscillates for ever.
        var paragraph = Paragraph.of(font, LONG);
        var cut = paragraph
                .measureFunction(TextFlow.ELLIPSIS)
                .measure(80, MeasureMode.AT_MOST, Float.NaN, MeasureMode.UNDEFINED);
        var uncut = paragraph
                .measureFunction(new TextFlow(WhiteSpace.NOWRAP, TextOverflow.CLIP))
                .measure(80, MeasureMode.AT_MOST, Float.NaN, MeasureMode.UNDEFINED);

        assertEquals(uncut.width(), cut.width(), 0.001);
        assertEquals(uncut.height(), cut.height(), 0.001);
    }

    @Test
    @DisplayName("a parent that has already decided still wins under nowrap")
    void exactlyStillWins() {
        // EXACTLY is not a question. Answering it with the natural width would
        // report a size the parent had already ruled out, which Yoga believes.
        var measured = Paragraph.of(font, LONG)
                .measureFunction(TextFlow.ELLIPSIS)
                .measure(120, MeasureMode.EXACTLY, Float.NaN, MeasureMode.UNDEFINED);

        assertEquals(120f, measured.width(), 0.001);
    }

    @Test
    @DisplayName("a hard newline still breaks under nowrap")
    void nowrapKeepsHardLines() {
        // A deliberate difference from CSS, where `nowrap` collapses newlines
        // into spaces. A paragraph here draws the string it was handed, and only
        // *soft* wrapping is what nowrap turns off.
        var paragraph = Paragraph.of(font, "first\nsecond");
        var measured = paragraph
                .measureFunction(TextFlow.ELLIPSIS)
                .measure(4, MeasureMode.AT_MOST, Float.NaN, MeasureMode.UNDEFINED);

        assertEquals(font.lineHeight() * 2, measured.height(), 0.01);
    }

    @Test
    @DisplayName("the no-argument measure function still wraps, so nothing that never asked has changed")
    void defaultIsUnchanged() {
        var paragraph = Paragraph.of(font, LONG);
        var explicit = paragraph
                .measureFunction(TextFlow.NORMAL)
                .measure(80, MeasureMode.AT_MOST, Float.NaN, MeasureMode.UNDEFINED);
        var implicit = paragraph.measureFunction().measure(80, MeasureMode.AT_MOST, Float.NaN, MeasureMode.UNDEFINED);

        assertEquals(explicit.width(), implicit.width(), 0.001);
        assertEquals(explicit.height(), implicit.height(), 0.001);
    }

    // --- offsetFitting --------------------------------------------------------

    @Test
    @DisplayName("offsetFitting never rounds up, which is what offsetAt does")
    void offsetFittingFloors() {
        var text = "Hamburgefonstiv";
        var paragraph = Paragraph.of(font, text);
        // Three characters plus most of a fourth: `offsetAt` rounds to the
        // nearest caret and would answer 4, which is a character more than there
        // is room to draw.
        var width = paragraph.widthBetween(0, 3) + (paragraph.widthBetween(3, 4) * 0.8);

        var fitting = paragraph.offsetFitting(0, text.length(), width);

        assertEquals(3, fitting);
        assertTrue(paragraph.widthBetween(0, fitting) <= width);
        assertEquals(4, paragraph.offsetAt(0, text.length(), width), "the sibling that rounds");
    }

    @Test
    @DisplayName("nothing fits in no room, and that is an offset rather than a failure")
    void offsetFittingWithNoRoom() {
        var paragraph = Paragraph.of(font, "Goldberry");

        assertEquals(0, paragraph.offsetFitting(0, 9, 0));
        assertEquals(0, paragraph.offsetFitting(0, 9, -5));
        assertEquals(0, paragraph.offsetFitting(0, 9, Double.NaN));
    }

    @Test
    @DisplayName("room for everything is the whole line")
    void offsetFittingWithRoomToSpare() {
        var text = "Goldberry";
        var paragraph = Paragraph.of(font, text);

        assertEquals(text.length(), paragraph.offsetFitting(0, text.length(), 10_000));
    }

    // --- the ink --------------------------------------------------------------

    @Test
    @DisplayName("nowrap draws one line past the edge; wrapping draws two inside it")
    void nowrapOverflowsAndWrappingDoesNot() {
        var box = 80;
        var wrapped = paint(LONG, box, TextFlow.NORMAL);
        var overflowing = paint(LONG, box, new TextFlow(WhiteSpace.NOWRAP, TextOverflow.CLIP));

        assertTrue(
                lastInkedColumn(wrapped).orElseThrow() <= box + 2,
                "a wrapped paragraph stays inside the width it was wrapped to");
        assertTrue(
                lastInkedColumn(overflowing).orElseThrow() > box + 10,
                "nowrap is what puts ink past the box, for an ancestor's `overflow` to cut");

        assertTrue(inkedRows(wrapped) > inkedRows(overflowing), "and the wrapped one is the taller of the two");
    }

    @Test
    @DisplayName("an ellipsis keeps the ink inside the box the plain nowrap ran out of")
    void ellipsisStaysInside() {
        var box = 80;
        var cut = paint(LONG, box, TextFlow.ELLIPSIS);

        var last = lastInkedColumn(cut).orElseThrow();
        assertTrue(last <= box, () -> "the ellipsis drew to column " + last + ", past the " + box + "px box");
        // And it used the room: a cut that stopped well short would be a label
        // truncated more than it had to be.
        assertTrue(last > box - 12, () -> "the ellipsis stopped at column " + last + ", well short of " + box);
        assertEquals(1, inkedLines(cut), "still one line");
    }

    @Test
    @DisplayName("the mark is drawn even where there is no room for a single letter")
    void aVeryNarrowBoxStillSaysSomething() {
        // A cell that went blank as it narrowed would read as a missing value
        // rather than as a truncated one.
        var cut = paint(LONG, 4, TextFlow.ELLIPSIS);

        assertTrue(firstInkedColumn(cut).isPresent(), "something was drawn");
    }

    @Test
    @DisplayName("text that fits is drawn whole, with no mark on the end of it")
    void shortTextIsNotMarked() {
        var text = "OK";
        var wide = 200;
        var cut = paint(text, wide, TextFlow.ELLIPSIS);
        var plain = paint(text, wide, TextFlow.NORMAL);

        assertEquals(
                lastInkedColumn(plain).orElseThrow(),
                lastInkedColumn(cut).orElseThrow(),
                "a label that fits is the same pixels either way");
    }

    @Test
    @DisplayName("the cut is at a different place from the plain overflow, and is not the whole text")
    void theCutIsRealAndNotTheWholeString() {
        var box = 80;
        var cut = paint(LONG, box, TextFlow.ELLIPSIS);
        var overflowing = paint(LONG, box, new TextFlow(WhiteSpace.NOWRAP, TextOverflow.CLIP));

        assertNotEquals(
                lastInkedColumn(overflowing).orElseThrow(),
                lastInkedColumn(cut).orElseThrow(),
                "an ellipsis that drew the whole run would be `clip` with extra steps");
    }

    // --- text-align -----------------------------------------------------------

    @Test
    @DisplayName("a line narrower than its box sits where text-align says")
    void alignmentPlacesTheLine() {
        var text = "9%";
        var box = 200;
        var natural = font.widthOf(text);

        var start = firstInkedColumn(paint(text, box, TextFlow.NORMAL)).orElseThrow();
        var centre = firstInkedColumn(paint(text, box, TextFlow.NORMAL.textAlign(TextAlign.CENTER)))
                .orElseThrow();
        var end = firstInkedColumn(paint(text, box, TextFlow.NORMAL.textAlign(TextAlign.END)))
                .orElseThrow();

        assertTrue(start < 3, () -> "start-aligned text begins at the box's edge, not column " + start);
        // Two pixels of slack for the first glyph's left bearing, which is real
        // ink offset from the pen and is not what is being measured.
        assertEquals((box - natural) / 2, centre, 2.0, "centred");
        assertEquals(box - natural, end, 2.0, "against the trailing edge");
    }

    @Test
    @DisplayName("a line wider than its box is never pulled left by the alignment")
    void alignmentDoesNotHideTheStart() {
        // `text-align: end` on an overflowing `nowrap` line would otherwise move
        // the *beginning* of the text off the leading edge — hiding the half a
        // reader needs to keep the half they can already guess.
        var box = 40;
        var nowrapEnd = new TextFlow(WhiteSpace.NOWRAP, TextOverflow.CLIP, TextAlign.END);

        var first = firstInkedColumn(paint(LONG, box, nowrapEnd)).orElseThrow();

        assertTrue(first < 3, () -> "the text starts at column " + first + " rather than at the box's edge");
    }

    @Test
    @DisplayName("an ellipsised line fills its box, so the alignment has nothing to share out")
    void alignmentDoesNotMoveACutLine() {
        var box = 80;
        var plain = paint(LONG, box, TextFlow.ELLIPSIS);
        var aligned = paint(LONG, box, TextFlow.ELLIPSIS.textAlign(TextAlign.END));

        assertEquals(
                firstInkedColumn(plain).orElseThrow(),
                firstInkedColumn(aligned).orElseThrow(),
                "a cut line is as wide as the box by construction");
    }

    @Test
    @DisplayName("every line of a wrapped paragraph is aligned, not the block they make")
    void alignmentIsPerLine() {
        // What `text-align` means, and the difference shows only on a paragraph
        // whose lines are different lengths: centring the *block* would leave the
        // short last line hanging at the left of the others.
        var box = 140;
        var centred = paint(LONG, box, TextFlow.NORMAL.textAlign(TextAlign.CENTER));

        assertTrue(inkedLines(centred) > 1, "the fixture has to wrap for this to mean anything");
        for (var line = 0; line < 96; line++) {
            var y = line;
            var inked = firstInkedColumnOfRow(centred, y);
            if (inked.isPresent()) {
                assertTrue(inked.getAsInt() > 2, () -> "row " + y + " starts at the box's edge, so it was not centred");
            }
        }
    }

    // --- helpers --------------------------------------------------------------

    /// Draws `text` into a frame wide enough to catch an overflow, wrapped or cut
    /// at `maxWidth`.
    ///
    /// The frame is deliberately much wider than the box: the point of half these
    /// assertions is *where the ink stopped*, and a frame that clipped it would
    /// make `nowrap` and `ellipsis` produce the same picture.
    private TestFrames.Target paint(String text, double maxWidth, TextFlow flow) {
        var target = TestFrames.of(400, 96, 1.0f);
        target.frame().fill(BACKGROUND);
        Paragraph.of(font, text).paint(target.frame(), 0, 0, maxWidth, INK, flow);
        target.end();
        return target;
    }

    private static boolean isInk(TestFrames.Target target, int x, int y) {
        return target.pixel(x, y) != BACKGROUND;
    }

    private static OptionalInt firstInkedColumn(TestFrames.Target target) {
        for (var x = 0; x < 400; x++) {
            for (var y = 0; y < 96; y++) {
                if (isInk(target, x, y)) {
                    return OptionalInt.of(x);
                }
            }
        }
        return OptionalInt.empty();
    }

    private static OptionalInt firstInkedColumnOfRow(TestFrames.Target target, int y) {
        for (var x = 0; x < 400; x++) {
            if (isInk(target, x, y)) {
                return OptionalInt.of(x);
            }
        }
        return OptionalInt.empty();
    }

    private static OptionalInt lastInkedColumn(TestFrames.Target target) {
        for (var x = 399; x >= 0; x--) {
            for (var y = 0; y < 96; y++) {
                if (isInk(target, x, y)) {
                    return OptionalInt.of(x);
                }
            }
        }
        return OptionalInt.empty();
    }

    private static int inkedRows(TestFrames.Target target) {
        var count = 0;
        for (var y = 0; y < 96; y++) {
            for (var x = 0; x < 400; x++) {
                if (isInk(target, x, y)) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    /// How many runs of inked rows there are, which for text is how many lines
    /// were drawn — a blank row between two of them is the leading.
    private static int inkedLines(TestFrames.Target target) {
        var lines = 0;
        var inside = false;
        for (var y = 0; y < 96; y++) {
            var inked = false;
            for (var x = 0; x < 400; x++) {
                if (isInk(target, x, y)) {
                    inked = true;
                    break;
                }
            }
            if (inked && !inside) {
                lines++;
            }
            inside = inked;
        }
        return lines;
    }
}
