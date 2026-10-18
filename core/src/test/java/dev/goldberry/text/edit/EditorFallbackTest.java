package dev.goldberry.text.edit;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.assets.BundledFont;
import dev.goldberry.input.event.KeyEvent;
import dev.goldberry.input.key.Key;
import dev.goldberry.input.key.Modifiers;
import dev.goldberry.text.font.FallbackSource;
import dev.goldberry.text.font.Font;
import dev.goldberry.text.font.FontSource;
import dev.goldberry.text.font.Fonts;
import dev.goldberry.text.font.StandInFace;

/// The caret when a fallback face arrives mid-edit: Latin typed first in the
/// field's own face, then Han or Arabic that only a fallback has.
///
/// A restyle reshapes and re-places the caret in the same `render`, and so does
/// a keystroke. What no test could provoke before per-character fallback was a
/// keystroke that changes *which face* draws part of the line. The caret has to
/// stand where the new glyphs end, and a click on it has to land back on the
/// same offset.
@DisplayName("an editor whose text falls back to another face as it is typed")
class EditorFallbackTest {

    private static final double SIZE = 14;

    private Fonts fonts;
    private Fonts plain;
    private Font font;
    private Font tofu;

    @BeforeEach
    void openBooks() {
        RendererRequirement.enforce();
        // Half Inter's grid, so a borrowed glyph is twice as wide in the field's
        // units and a caret placed in the wrong face's units is visibly off.
        var standIn = FontSource.of(
                "Stand In", 400, BundledFont.Style.UPRIGHT, StandInFace.of(StandInFace.scripts(), true, 1024));
        fonts = Fonts.bundled(List.of(), List.of(FallbackSource.of(standIn)));
        plain = Fonts.bundled();
        font = fonts.of(BundledFont.UI, SIZE);
        tofu = plain.of(BundledFont.UI, SIZE);
    }

    @AfterEach
    void closeBooks() {
        if (fonts != null) {
            fonts.close();
        }
        if (plain != null) {
            plain.close();
        }
    }

    /// Types `text` one code point at a time, as keystrokes arrive.
    private static void type(Editor editor, String text) {
        text.codePoints().forEach(codePoint -> editor.onText(Character.toString(codePoint)));
    }

    /// Clicks on the caret's own position and answers where the caret went.
    private static int clickOnCaret(Editor editor) {
        var caret = editor.caret();
        editor.pointerAt(caret.x(), caret.top() + caret.height() / 2, false, 1);
        return editor.edit().caret();
    }

    @Test
    @DisplayName("Han typed after Latin: the caret stands where each borrowed glyph ends")
    void hanAfterLatin() {
        var editor = new Editor(font);
        type(editor, "Ann ");
        var latin = editor.caret().x();
        assertEquals(tofu.widthOf("Ann "), latin, 1e-3, "the field's own face, before any fallback");

        var previous = latin;
        var typed = 0;
        for (var i = 0; i < StandInFace.HAN.length(); i++) {
            type(editor, StandInFace.HAN.substring(i, i + 1));
            typed++;
            var x = editor.caret().x();
            var step = x - previous;
            // Each Han stand-in borrows one of Inter's capitals on a grid half
            // the size, so it is wider than any Latin letter of the same size and
            // never zero, which is what `.notdef` in the wrong face would give.
            assertTrue(step > tofu.widthOf("Z"), "character " + typed + " moved the caret only " + step);
            assertEquals(4 + typed, editor.edit().caret());
            assertEquals(4 + typed, clickOnCaret(editor), "a click on the caret after character " + typed);
            previous = x;
        }
        assertEquals(2 * tofu.widthOf("ZWNX"), previous - latin, 1e-2, "four borrowed glyphs, rescaled");
    }

    @Test
    @DisplayName("a borrowed character inserted inside Latin moves the caret and what follows it")
    void insertedInside() {
        var editor = new Editor(font).text("Anne");
        editor.caretTo(3, false);
        var before = editor.caret().x();

        type(editor, "中");

        assertAll(
                () -> assertEquals("Ann中e", editor.text()),
                () -> assertEquals(4, editor.edit().caret()),
                () -> assertEquals(
                        before + 2 * tofu.widthOf("Z"), editor.caret().x(), 1e-2),
                () -> assertEquals(4, clickOnCaret(editor)));
    }

    @Test
    @DisplayName("deleting the borrowed character puts the caret back in the field's own face")
    void deletedAgain() {
        var editor = new Editor(font);
        type(editor, "Ann中");

        editor.onKey(new KeyEvent(KeyEvent.Kind.PRESSED, Key.BACKSPACE, Modifiers.NONE, false, null));

        assertAll(
                () -> assertEquals("Ann", editor.text()),
                () -> assertEquals(tofu.widthOf("Ann"), editor.caret().x(), 1e-3));
    }

    /// Arabic runs right to left inside a left-to-right line. Where two runs
    /// meet, two offsets share one position, so the round trip asks for the
    /// same *place* rather than the same number.
    @Test
    @DisplayName("Arabic typed after Latin: every caret is a place a click finds again")
    void arabicAfterLatin() {
        var editor = new Editor(font);
        type(editor, "Ann ");
        var latin = editor.caret().x();

        var word = "محمد";
        for (var i = 0; i < word.length(); i++) {
            type(editor, word.substring(i, i + 1));
            var x = editor.caret().x();
            var offset = editor.edit().caret();
            assertEquals(5 + i, offset);
            assertTrue(x >= latin - 1e-3, "the caret stays right of the Latin it follows, at " + offset);

            var landed = clickOnCaret(editor);
            assertEquals(x, editor.caret().x(), 1e-3, "a click on the caret at " + offset + " landed at " + landed);
        }
        // The caret before the first Arabic letter stands at the run's right
        // edge, past all four borrowed glyphs at twice their Latin width.
        assertEquals(
                latin + 2 * tofu.widthOf("mhmd"),
                editor.document().caretX(0, editor.text().length(), 4),
                1e-2);
    }
}
