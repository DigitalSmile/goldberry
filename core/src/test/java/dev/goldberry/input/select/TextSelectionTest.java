package dev.goldberry.input.select;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.assets.BundledFont;
import dev.goldberry.layout.Presence.UserSelect;
import dev.goldberry.paint.Box;
import dev.goldberry.text.Paragraph;
import dev.goldberry.text.edit.TextGeometry;
import dev.goldberry.text.font.Font;

/// The tree's one selection of plain text: registered by the renderer box by
/// box, driven by the router, and dropped when its box goes.
class TextSelectionTest {

    private static final String WORDS = "Deploy the orc board";

    private static final int HIGHLIGHT = 0x665E81AC;

    private Font font;

    private Box.Text text;

    private final Object element = new Object();

    private final TextSelection<Object> selection = new TextSelection<>();

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        font = Font.bundled(BundledFont.UI, 14);
        text = new Box.Text(Paragraph.of(font, WORDS), 0xFF000000);
    }

    @AfterEach
    void tearDown() {
        if (font != null) {
            font.close();
        }
    }

    /// One frame that drew `element` under `mode`.
    private Box.Text frame(UserSelect mode) {
        selection.beginFrame();
        var drawn = selection.rendered(element, text, mode, HIGHLIGHT);
        selection.endFrame();
        return drawn;
    }

    private double width() {
        return text.paragraph().layout(Paragraph.UNCONSTRAINED).width() + 10;
    }

    @Test
    @DisplayName("only text and all select; auto and none leave the box alone")
    void modes() {
        assertTrue(TextSelection.selects(UserSelect.TEXT));
        assertTrue(TextSelection.selects(UserSelect.ALL));
        assertFalse(TextSelection.selects(UserSelect.AUTO));
        assertFalse(TextSelection.selects(UserSelect.NONE));

        assertSame(text, frame(UserSelect.AUTO));
        assertFalse(selection.selectable(element), "auto registers nothing");
        assertFalse(selection.press(element, 1, 1, width(), 1));
    }

    @Test
    @DisplayName("a press and a drag select the stretch between them, and the next frame draws it highlighted")
    void dragSelects() {
        frame(UserSelect.TEXT);
        assertTrue(selection.press(element, 0, 5, width(), 1));
        assertTrue(selection.isDragging());
        assertTrue(selection.drag(width(), 5, width()));
        assertTrue(selection.release());
        assertEquals(WORDS, selection.selectedText());

        var drawn = frame(UserSelect.TEXT);
        var last = drawn.spans().getLast();
        assertEquals(0, last.start());
        assertEquals(WORDS.length(), last.end());
        assertEquals(HIGHLIGHT, last.background());
    }

    @Test
    @DisplayName("a double click takes the word, a triple click everything")
    void clicks() {
        frame(UserSelect.TEXT);
        var paragraph = text.paragraph();
        // The middle of `orc`.
        var orc = WORDS.indexOf("orc");
        var x = TextGeometry.caretAt(paragraph, paragraph.layout(Paragraph.UNCONSTRAINED), orc + 1)
                .x();
        selection.press(element, x, 5, width(), 2);
        assertEquals("orc", selection.selectedText());
        assertFalse(selection.isDragging(), "a double click is finished on its press");

        selection.press(element, x, 5, width(), 3);
        assertEquals(WORDS, selection.selectedText());
    }

    @Test
    @DisplayName("under user-select: all any click selects the lot")
    void all() {
        frame(UserSelect.ALL);
        selection.press(element, 2, 5, width(), 1);
        assertEquals(WORDS, selection.selectedText());
    }

    @Test
    @DisplayName("a selection whose box the next frame did not draw selectable is dropped")
    void droppedWithItsBox() {
        frame(UserSelect.TEXT);
        selection.press(element, 0, 5, width(), 3);
        assertFalse(selection.isEmpty());

        frame(UserSelect.NONE);
        assertTrue(selection.isEmpty());
        assertArrayEquals(new int[] {-1, -1}, selection.range());
    }

    @Test
    @DisplayName("clearing says whether there was anything to clear")
    void clear() {
        frame(UserSelect.TEXT);
        assertFalse(selection.clear());
        selection.press(element, 0, 5, width(), 3);
        assertTrue(selection.clear());
        assertEquals("", selection.selectedText());
    }

    @Test
    @DisplayName("the word around an offset, and nothing in an empty text")
    void wordAround() {
        assertArrayEquals(new int[] {7, 10}, TextSelection.wordAround(WORDS, 8));
        assertArrayEquals(new int[] {0, 6}, TextSelection.wordAround(WORDS, 0));
        assertArrayEquals(new int[] {0, 0}, TextSelection.wordAround("", 3));
    }
}
