package io.github.digitalsmile.goldberry.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.TestFrames;
import io.github.digitalsmile.goldberry.assets.BundledFont;
import io.github.digitalsmile.goldberry.natives.yoga.ComputedLayout;
import io.github.digitalsmile.goldberry.natives.yoga.FlexDirection;
import io.github.digitalsmile.goldberry.natives.yoga.StyleLength;
import io.github.digitalsmile.goldberry.text.Font;
import io.github.digitalsmile.goldberry.text.Paragraph;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Text taking part in layout, rather than being drawn over the top of it.
///
/// This is the M1 slice end to end: Yoga proposes a width from inside C, the
/// paragraph wraps at it and reports a height through the `YGSize` upcall
/// (ADR-0017), the flexbox algorithm sizes the box around that answer, and
/// Blend2D draws the lines that were measured. Each of those had been proven
/// alone; these are the assertions that they agree.
class BoxTextTest {

    private static final String TEXT =
            "Yoga proposes a width and the paragraph answers with a height,"
                    + " which is the only thing a flexbox algorithm needs to know about text.";

    private static final int INK = 0xFFFFFFFF;

    private Font font;

    @BeforeEach
    void openFont() {
        RendererRequirement.enforce();
        font = Font.bundled(BundledFont.UI, 14);
    }

    @AfterEach
    void closeFont() {
        if (font != null) {
            font.close();
        }
    }

    @Test
    @DisplayName("a text box is as tall as its text wrapped to the width it was given")
    void heightComesFromTheText() {
        var paragraph = Paragraph.of(font, TEXT);
        var target = TestFrames.of(400, 300, 1.0f);

        var box = Box.text(paragraph, INK);
        // A child, not the root. Yoga sizes the ROOT from the available space
        // it was handed, so a measured node at the top of the tree never gets
        // asked -- which is a real trap and the reason this says so here.
        var placed = layoutOf(target, column(box), box);

        // Stretched to the frame's width, so the paragraph wrapped at that, and
        // the height it reported is the height the box got. A leaf whose measure
        // function never ran comes out zero tall.
        var expected = paragraph.layout(placed.width()).height();
        assertTrue(placed.height() > 0, "the measure function ran");
        assertEquals(expected, placed.height(), 1.0);
        target.end();
    }

    @Test
    @DisplayName("a narrower column makes the same text taller")
    void narrowerIsTaller() {
        var paragraph = Paragraph.of(font, TEXT);

        var wide = TestFrames.of(400, 300, 1.0f);
        var wideBox = Box.text(paragraph, INK);
        var wideHeight = layoutOf(wide, column(wideBox), wideBox).height();

        var narrow = TestFrames.of(160, 300, 1.0f);
        var narrowBox = Box.text(paragraph, INK);
        var narrowHeight = layoutOf(narrow, column(narrowBox), narrowBox).height();

        assertTrue(narrowHeight > wideHeight,
                () -> narrowHeight + " at 160 wide should exceed " + wideHeight + " at 400");
        wide.end();
        narrow.end();
    }

    @Test
    @DisplayName("a sibling below the text starts where the text ended")
    void textPushesItsSiblingsDown() {
        var paragraph = Paragraph.of(font, TEXT);
        var text = Box.text(paragraph, INK);
        var footer = Box.filled(0xFF88C0D0).size(StyleLength.UNDEFINED, StyleLength.points(20));

        var root = Box.of().direction(FlexDirection.COLUMN).children(text, footer);
        var target = TestFrames.of(300, 400, 1.0f);

        var placedText = layoutOf(target, root, text);
        var placedFooter = layoutOf(target, root, footer);

        // The whole point of a measure function: the text's height is content,
        // and content is what everything after it is positioned against. A
        // paragraph drawn over the layout rather than inside it would leave the
        // footer at the top.
        assertTrue(placedText.height() > 0);
        assertEquals(placedText.top() + placedText.height(), placedFooter.top(), 1.0);
        target.end();
    }

    @Test
    @DisplayName("the lines are drawn inside the box that was measured for them")
    void inkLandsInsideTheBox() {
        var paragraph = Paragraph.of(font, TEXT);
        var target = TestFrames.of(300, 400, 1.0f);

        var text = Box.text(paragraph, INK);
        var root = Box.of()
                .direction(FlexDirection.COLUMN)
                .background(0xFF000000)
                .padding(StyleLength.points(10))
                .children(text);

        var placed = layoutOf(target, root, text);
        BoxPainter.paint(target.frame(), root);
        target.end();

        var inkTop = -1;
        var inkBottom = -1;
        for (var y = 0; y < 400; y++) {
            for (var x = 0; x < 300; x++) {
                if (target.pixel(x, y) != 0xFF000000) {
                    if (inkTop < 0) {
                        inkTop = y;
                    }
                    inkBottom = y;
                    break;
                }
            }
        }

        var firstInkedRow = inkTop;
        var lastInkedRow = inkBottom;

        assertTrue(firstInkedRow >= 0, "the text was drawn");
        // Inside the box Yoga computed, not merely somewhere on the frame. The
        // padding is what makes this a real check: ink at y=0 would mean the
        // paint step ignored the layout and drew at the frame's origin.
        assertTrue(firstInkedRow >= placed.top() - 1,
                () -> "ink starts at " + firstInkedRow + ", above the box at " + placed.top());
        assertTrue(lastInkedRow <= placed.top() + placed.height() + 1,
                () -> "ink ends at " + lastInkedRow + ", below the box ending at "
                        + (placed.top() + placed.height()));
    }

    @Test
    @DisplayName("a box may not have both text and children")
    void textAndChildrenAreRefused() {
        var paragraph = Paragraph.of(font, "Goldberry");

        // Yoga asks a measured node for its size and never lays its children
        // out, so this would silently lose them. Refused where the box is built,
        // not where the layout goes quiet.
        assertThrows(IllegalArgumentException.class,
                () -> Box.text(paragraph, INK).children(Box.filled(0xFF000000)));
    }

    // --- helpers -------------------------------------------------------------

    /// A column root holding one box, so the box's height is content rather than
    /// the frame's.
    private static Box column(Box child) {
        return Box.of().direction(FlexDirection.COLUMN).children(child);
    }

    /// Lays `root` out in `target` and returns where `wanted` ended up.
    private static ComputedLayout layoutOf(TestFrames.Target target, Box root, Box wanted) {
        var found = new ArrayList<ComputedLayout>();
        BoxPainter.forEachBox(target.frame(), root, (box, layout) -> {
            if (box == wanted) {
                found.add(layout);
            }
        });
        return single(found, wanted);
    }

    private static ComputedLayout single(List<ComputedLayout> found, Box wanted) {
        if (found.size() != 1) {
            throw new AssertionError(
                    "expected exactly one placement of " + wanted + ", got " + found.size());
        }
        return found.getFirst();
    }
}
