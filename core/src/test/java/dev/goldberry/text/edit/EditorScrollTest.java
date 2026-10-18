package dev.goldberry.text.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import dev.goldberry.paint.TestFrames;
import dev.goldberry.text.font.Font;

/// An editor told how tall its box is keeps the caret in it, and draws only
/// what is in it.
class EditorScrollTest {

    private static final int LINES = 40;

    private static final int SHOWN = 5;

    private static final int BLACK = 0xFF000000;

    private static final int WHITE = 0xFFFFFFFF;

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

    private static String lines(int count) {
        var out = new StringBuilder();
        for (var i = 0; i < count; i++) {
            if (i > 0) {
                out.append('\n');
            }
            out.append("line ").append(i);
        }
        return out.toString();
    }

    private double line() {
        return font.lineHeight();
    }

    /// Forty lines in a box five lines tall, the caret at the start.
    private Editor scrolled() {
        return new Editor(font)
                .multiline(true)
                .text(lines(LINES))
                .caretTo(0, false)
                .viewportHeight(SHOWN * line());
    }

    private static KeyEvent key(Key which) {
        return new KeyEvent(KeyEvent.Kind.PRESSED, which, Modifiers.NONE, false, null);
    }

    @Nested
    @DisplayName("the scroll follows the caret")
    class Following {

        @Test
        @DisplayName("an editor told no height is never scrolled")
        void noHeightNoScroll() {
            var editor = new Editor(font).multiline(true).text(lines(LINES));

            assertEquals(0, editor.scrollTop());
            assertFalse(editor.scrollBy(100));
            assertEquals(0, editor.scrollTop());
        }

        @Test
        @DisplayName("at the start it is not scrolled, and Down inside the box does not scroll it")
        void insideTheBox() {
            var editor = scrolled();
            assertEquals(0, editor.scrollTop());

            for (var i = 0; i < SHOWN - 1; i++) {
                editor.onKey(key(Key.DOWN));
            }

            assertEquals(0, editor.scrollTop(), 1e-9);
        }

        @Test
        @DisplayName("one Down past the bottom edge scrolls by one line, the least that shows the caret")
        void pastTheBottom() {
            var editor = scrolled();

            for (var i = 0; i < SHOWN; i++) {
                editor.onKey(key(Key.DOWN));
            }

            assertEquals(line(), editor.scrollTop(), 1e-6);
        }

        @Test
        @DisplayName("the end of the text puts the last line at the bottom edge, and the start back at the top")
        void endAndStart() {
            var editor = scrolled();

            editor.caretTo(editor.text().length(), false);
            assertEquals((LINES - SHOWN) * line(), editor.scrollTop(), 1e-6);
            assertEquals(LINES * line(), editor.contentHeight(), 1e-6);

            editor.caretTo(0, false);
            assertEquals(0, editor.scrollTop(), 1e-9);
        }

        @Test
        @DisplayName("a drag past the bottom edge carries the caret there, and the scroll follows it")
        void dragPastTheEdge() {
            var editor = scrolled();

            editor.pointerAt(10, (SHOWN + 2.5) * line(), true, 1);

            assertTrue(editor.edit().hasSelection());
            assertEquals(3 * line(), editor.scrollTop(), 1e-6);
        }

        @Test
        @DisplayName("typing a new line at the bottom edge scrolls with it")
        void typing() {
            var editor = scrolled();
            editor.caretTo(lines(SHOWN).length(), false);
            assertEquals(0, editor.scrollTop(), 1e-9);

            editor.onKey(key(Key.ENTER));

            assertEquals(line(), editor.scrollTop(), 1e-6);
        }
    }

    @Nested
    @DisplayName("the caller can move the view")
    class Wheel {

        @Test
        @DisplayName("a wheel moves the view and not the caret, and the view stays until the caret moves")
        void wheelThenKey() {
            var editor = scrolled();

            assertTrue(editor.scrollBy(10 * line()));
            assertEquals(10 * line(), editor.scrollTop(), 1e-6);
            assertEquals(0, editor.edit().caret(), "the caret did not move");

            editor.onKey(key(Key.DOWN));
            assertEquals(line(), editor.scrollTop(), 1e-6, "the caret's move brought the view back to it");
        }

        @Test
        @DisplayName("it is clamped at both ends, and a wheel at an end says it did nothing")
        void clamped() {
            var editor = scrolled();

            assertFalse(editor.scrollBy(-50));
            editor.scrollTop(1e6);
            assertEquals((LINES - SHOWN) * line(), editor.scrollTop(), 1e-6);
            assertFalse(editor.scrollBy(1));
        }

        @Test
        @DisplayName("text shorter than the box is never scrolled")
        void shortText() {
            var editor = new Editor(font).multiline(true).text(lines(2)).viewportHeight(SHOWN * line());

            assertFalse(editor.scrollBy(100));
            assertEquals(0, editor.scrollTop());
        }
    }

    @Nested
    @DisplayName("it draws only what is in the box")
    class Painting {

        private static final int TOP = 40;

        /// Whether anything brighter than the black ground was drawn in rows
        /// `[from, to)`.
        private static boolean inked(TestFrames.Target target, int from, int to) {
            for (var y = from; y < to; y++) {
                for (var x = 0; x < 200; x++) {
                    if (((target.pixel(x, y) >>> 16) & 0xFF) > 0x40) {
                        return true;
                    }
                }
            }
            return false;
        }

        @Test
        @DisplayName("scrolled to the middle, nothing is drawn above or below the box, and the box is drawn")
        void clipped() {
            var editor = scrolled();
            editor.scrollTop(10.5 * line());
            var bottom = (int) Math.ceil(TOP + SHOWN * line());
            var target = TestFrames.of(200, bottom + 60, 1.0f);
            target.frame().fill(BLACK);
            editor.paint(target.frame(), 4, TOP, Editor.Ink.of(WHITE, 0xFF444444), false);
            target.end();

            assertFalse(inked(target, 0, TOP - 1), "a line was drawn above the box");
            assertFalse(inked(target, bottom + 1, bottom + 60), "a line was drawn below the box");
            assertTrue(inked(target, TOP, bottom), "the box is empty");
        }
    }
}
