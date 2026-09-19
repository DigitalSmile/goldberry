package io.github.digitalsmile.goldberry.text.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.assets.BundledFont;
import io.github.digitalsmile.goldberry.input.event.PreeditEvent;
import io.github.digitalsmile.goldberry.text.font.Font;

/// `docs/gaps.md` G15: what an editor does with a composition it has not been
/// given permission to keep.
///
/// The claim under all of it is one sentence — **a composition is not an edit**
/// — and every test here is a way for that to be false: the text changing, the
/// undo history growing, a ghost left on screen after `Escape` (ADR-0289).
class EditorPreeditTest {

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

    private Editor editor(String text) {
        return new Editor(font).text(text);
    }

    private static PreeditEvent preedit(String text) {
        return new PreeditEvent(text, -1, -1, null);
    }

    private static PreeditEvent preedit(String text, int start, int length) {
        return new PreeditEvent(text, start, length, null);
    }

    @Test
    @DisplayName("does not put the composition in the text")
    void doesNotEdit() {
        var editor = editor("ab").caretTo(1, false);

        assertTrue(editor.onPreedit(preedit("にほんご")));

        assertEquals("ab", editor.text(), "a composition the user has not accepted is not in the document");
        assertEquals(1, editor.edit().caret(), "the document caret does not move for a composition");
    }

    @Test
    @DisplayName("draws it inside the text, so the words after it move along")
    void splicesItIntoWhatIsDrawn() {
        var editor = editor("ab").caretTo(1, false);

        editor.onPreedit(preedit("XY"));

        assertEquals("aXYb", editor.displayText());
        assertTrue(editor.isComposing());
        assertEquals("XY", editor.composing());
    }

    @Test
    @DisplayName("puts the caret inside the composition, where the input method has it")
    void caretIsInsideTheComposition() {
        var editor = editor("ab").caretTo(1, false);
        var before = editor.caret().x();

        // start=2, length=0 is "the caret is after two characters of what I am
        // composing", which is what an input method reports as it grows.
        editor.onPreedit(preedit("XY", 2, 0));

        assertTrue(editor.caret().x() > before, "a caret drawn before the composition would sit behind the text");
    }

    @Test
    @DisplayName("leaves the undo history alone, however long the composition gets")
    void doesNotTouchUndo() {
        var editor = editor("");

        editor.onPreedit(preedit("に"));
        editor.onPreedit(preedit("にほ"));
        editor.onPreedit(preedit("にほん"));

        assertFalse(editor.canUndo(), "three keystrokes of composing is nothing to undo yet");
    }

    @Test
    @DisplayName("the accepted candidate is one edit, and one undo step")
    void commitIsOneEdit() {
        var editor = editor("");
        editor.onPreedit(preedit("にほんご"));

        // What the platform sends when the user accepts: the result as committed
        // text. The empty TEXT_EDITING may come before or after, and must not be
        // needed for this to be right.
        assertTrue(editor.onText("日本語"));

        assertEquals("日本語", editor.text());
        assertFalse(editor.isComposing(), "an accepted candidate must not stay on screen underlined as well");
        assertTrue(editor.canUndo());
        assertTrue(editor.undo());
        assertEquals("", editor.text(), "one undo takes back the whole conversion, not one keystroke of it");
    }

    @Test
    @DisplayName("an abandoned composition leaves nothing behind")
    void abandonedCompositionClears() {
        var editor = editor("ab").caretTo(1, false);
        editor.onPreedit(preedit("にほんご"));

        assertTrue(editor.onPreedit(preedit("")), "the empty composition is what Escape produces");

        assertFalse(editor.isComposing());
        assertEquals("ab", editor.displayText(), "a ghost left on screen is what forgetting this looks like");
        assertEquals("ab", editor.text());
    }

    @Test
    @DisplayName("hides the selection while composing, because the composition replaces it")
    void hidesTheSelectionWhileComposing() {
        var editor = editor("abcd").caretTo(0, false).caretTo(3, true);
        assertFalse(editor.selectionRects().isEmpty());

        editor.onPreedit(preedit("XY"));

        assertTrue(
                editor.selectionRects().isEmpty(),
                "a highlight over text that is about to be replaced is a highlight nobody asked for");
        assertFalse(editor.composingRects().isEmpty(), "the composition has an underline of its own");
    }

    @Test
    @DisplayName("the converting clause is a rectangle, and only when the platform reports one")
    void clauseRects() {
        var editor = editor("");

        editor.onPreedit(preedit("にほんご"));
        assertTrue(editor.composingClauseRects().isEmpty(), "no clause was reported, so none is drawn");

        editor.onPreedit(preedit("にほんご", 0, 2));
        assertFalse(editor.composingClauseRects().isEmpty());
    }

    @Test
    @DisplayName("repeating the same composition changes nothing, so a frame is not asked for")
    void repeatIsIdempotent() {
        var editor = editor("");
        assertTrue(editor.onPreedit(preedit("にほ", 0, 1)));

        assertTrue(editor.onPreedit(preedit("にほ", 0, 1)), "it is still composing, so the event is still consumed");
        assertEquals("にほ", editor.composing());
    }

    @Test
    @DisplayName("a read-only editor composes nothing")
    void readOnlyComposesNothing() {
        var editor = editor("ab").readOnly(true);

        assertFalse(editor.onPreedit(preedit("にほんご")));
        assertFalse(editor.isComposing());
    }

    @Test
    @DisplayName("a click during a composition lands in the document, not in the composition")
    void clickMapsBackToTheDocument() {
        var editor = editor("abcd").caretTo(2, false);
        editor.onPreedit(preedit("にほんご"));

        // Far to the right of everything: the last character of the document,
        // not an offset into a string the document does not contain.
        editor.pointerAt(10_000, 0, false, 1);

        assertTrue(editor.edit().caret() <= 4, "an offset past the document would be an index out of its own text");
    }

    /// The shaping is cached and the composition is spliced into it at
    /// `edit.caret()`, so **where the caret is is part of what was shaped**.
    /// Every mover — `caretTo`, `pointerAt`, an arrow key — changed `edit` and
    /// left the cache alone, so the paragraph went on holding the composition at
    /// the offset the caret had left.
    @Test
    @DisplayName("a caret moved mid-composition takes the composition with it")
    void movingTheCaretReshapesTheComposition() {
        var editor = editor("abcd").caretTo(0, false);
        editor.onPreedit(preedit("XY"));
        assertEquals("XYabcd", editor.document().text());

        editor.caretTo(4, false);

        assertEquals("abcdXY", editor.displayText());
        assertEquals(
                "abcdXY",
                editor.document().text(),
                "the shaping the caret and the underline are measured against is a frame behind the caret");
    }

    @Test
    @DisplayName("and so does a click, which is what a user does when the candidate list covers the line")
    void clickingReshapesTheComposition() {
        var editor = editor("abcd").caretTo(0, false);
        editor.onPreedit(preedit("XY"));
        assertEquals("XYabcd", editor.document().text());

        // Far to the right of everything: the end of the document.
        editor.pointerAt(10_000, 0, false, 1);

        assertEquals(4, editor.edit().caret());
        assertEquals("abcdXY", editor.document().text());
    }

    /// What the stale shaping actually looks like: the underline is drawn along
    /// whatever glyphs happen to sit at the composition's *new* offsets in the
    /// *old* string, which is neither the composition nor a word.
    @Test
    @DisplayName("the underline is where the composition now is, not where the old shaping put those offsets")
    void theUnderlineFollowsTheComposition() {
        var atStart = editor("abcd").caretTo(0, false);
        atStart.onPreedit(preedit("XY"));
        var before = atStart.composingRects().getFirst();

        var atEnd = editor("abcd").caretTo(4, false);
        atEnd.onPreedit(preedit("XY"));
        var expected = atEnd.composingRects().getFirst();
        assertTrue(
                expected.left() > before.left(), "a composition at the end starts further right than one at the start");

        atStart.caretTo(4, false);

        assertEquals(expected.left(), atStart.composingRects().getFirst().left(), 0.01);
        assertEquals(expected.width(), atStart.composingRects().getFirst().width(), 0.01);
    }

    @Test
    @DisplayName("the caret line is the line, not the caret — what a candidate window is kept clear of")
    void caretLineIsTheLine() {
        var editor = editor("a wide enough line of text");

        var line = editor.caretLine();

        assertTrue(line.width() > 0, "a zero-width area lets a candidate window sit over the text");
        assertEquals(editor.caret().height(), line.height(), 0.001);
        assertNotEquals(0, line.width());
    }
}
