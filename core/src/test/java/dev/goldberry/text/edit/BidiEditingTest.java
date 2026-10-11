package dev.goldberry.text.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.assets.BundledFont;
import dev.goldberry.input.event.KeyEvent;
import dev.goldberry.input.key.Key;
import dev.goldberry.input.key.Modifiers;
import dev.goldberry.layout.Direction;
import dev.goldberry.text.Paragraph;
import dev.goldberry.text.document.TextDocument;
import dev.goldberry.text.edit.keys.Motion;
import dev.goldberry.text.flow.TextAlign;
import dev.goldberry.text.font.Font;

/// Editing text with right-to-left runs in it: where the caret is drawn, what a
/// selection covers, and which way the arrow keys go.
///
/// Inter has no Hebrew, so every Hebrew letter is the same `.notdef` box; what
/// the assertions read is where the boxes went, not what they look like.
class BidiEditingTest {

    /// Hebrew, "shalom": four strong right-to-left letters.
    private static final String SHALOM = "שלום";

    private Font font;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        font = Font.bundled(BundledFont.UI, 13);
    }

    @AfterEach
    void tearDown() {
        if (font != null) {
            font.close();
        }
    }

    private static KeyEvent key(Key which) {
        return new KeyEvent(KeyEvent.Kind.PRESSED, which, Modifiers.NONE, false, null);
    }

    @Nested
    @DisplayName("the geometry")
    class Geometry {

        @Test
        @DisplayName("the caret before a right-to-left paragraph's first letter is at the right edge of the box")
        void caretStartsAtTheRight() {
            var paragraph = Paragraph.of(font, SHALOM);
            var layout = paragraph.layout(200);

            var start = TextGeometry.caretAt(paragraph, layout, 0, 200, TextAlign.START);
            var end = TextGeometry.caretAt(paragraph, layout, 4, 200, TextAlign.START);

            assertEquals(200, start.x(), 1e-6);
            assertEquals(200 - paragraph.widthBetween(0, 4), end.x(), 1e-6);
            assertEquals(
                    2,
                    TextGeometry.offsetAt(paragraph, layout, (start.x() + end.x()) / 2, 1, 200, TextAlign.START),
                    "a click in the middle is the middle");
        }

        @Test
        @DisplayName("a selection across the two directions is a rectangle on each side of the run")
        void selectionSplits() {
            var text = "abc " + SHALOM;
            var paragraph = Paragraph.of(font, text);
            var layout = paragraph.layout(Paragraph.UNCONSTRAINED);

            var rects = TextGeometry.selectionRects(paragraph, layout, 2, 6);

            assertEquals(2, rects.size(), rects.toString());
            assertEquals(paragraph.widthBetween(0, 2), rects.get(0).left(), 1e-3);
            assertEquals(paragraph.widthBetween(0, 8), rects.get(1).right(), 1e-3);
            assertEquals(
                    1,
                    TextGeometry.selectionRects(paragraph, layout, 0, 3).size(),
                    "a selection in one run is one rectangle");
        }

        @Test
        @DisplayName("a selected line break in a right-to-left paragraph is drawn at its left")
        void selectedBreak() {
            var paragraph = Paragraph.of(font, SHALOM + "\n" + SHALOM);
            var layout = paragraph.layout(200);

            var rects = TextGeometry.selectionRects(paragraph, layout, 0, 7, 200, TextAlign.START);
            var first = rects.getFirst();
            var space = font.widthOf(" ");

            assertEquals(200 - paragraph.widthBetween(0, 4) - space, first.left(), 1e-3);
            assertEquals(200, first.right(), 1e-3);
        }

        @Test
        @DisplayName("a document places each hard line by its own direction")
        void document() {
            var document = TextDocument.of(font, "abc\n" + SHALOM, null, line -> Paragraph.of(font, line));
            var lines = document.lines(200);

            assertEquals(Direction.LTR, document.directionAt(0));
            assertEquals(Direction.RTL, document.directionAt(5));
            assertEquals(
                    0,
                    TextGeometry.caretAt(document, lines, 0, 200, TextAlign.START)
                            .x(),
                    1e-6);
            assertEquals(
                    200,
                    TextGeometry.caretAt(document, lines, 4, 200, TextAlign.START)
                            .x(),
                    1e-6);
            var rects = TextGeometry.selectionRects(document, lines, 4, 6, 200, TextAlign.START);
            assertEquals(1, rects.size());
            assertEquals(200, rects.getFirst().right(), 1e-3, "the first two letters, at the right");
        }
    }

    @Nested
    @DisplayName("the arrow keys")
    class Keys {

        @Test
        @DisplayName("Left goes forward in a right-to-left paragraph, and back in a left-to-right one")
        void mirrored() {
            var editor = new Editor(font).text("abc\n" + SHALOM);

            editor.caretTo(6, false);
            assertTrue(editor.onKey(key(Key.LEFT)));
            assertEquals(7, editor.edit().caret(), "on the Hebrew line, Left reads on");
            assertTrue(editor.onKey(key(Key.RIGHT)));
            assertEquals(6, editor.edit().caret());

            editor.caretTo(2, false);
            editor.onKey(key(Key.LEFT));
            assertEquals(1, editor.edit().caret(), "on the Latin line, Left goes back");
        }

        @Test
        @DisplayName("a motion is mirrored right to left, and only the two that have a side")
        void motion() {
            assertEquals(Motion.RIGHT, Motion.LEFT.in(Direction.RTL));
            assertEquals(Motion.LEFT, Motion.RIGHT.in(Direction.RTL));
            assertEquals(Motion.LEFT, Motion.LEFT.in(Direction.LTR));
            assertEquals(Motion.LINE_START, Motion.LINE_START.in(Direction.RTL));
            assertEquals(Motion.DOCUMENT_END, Motion.DOCUMENT_END.in(Direction.RTL));
        }
    }
}
