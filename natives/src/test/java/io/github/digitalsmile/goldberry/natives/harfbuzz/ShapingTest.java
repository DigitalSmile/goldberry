package io.github.digitalsmile.goldberry.natives.harfbuzz;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.natives.NativeLibraryRequirement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Shaping, checked without a font file.
///
/// Every test here runs against HarfBuzz's **empty face**, which has no glyphs
/// and gives every character a `.notdef` with a zero advance. That sounds like
/// it proves nothing, and it proves most of what can go wrong in a binding: how
/// many glyphs came back, which characters each one maps to, which direction was
/// used, and whether the two parallel arrays were read at the right stride. None
/// of that depends on the font having outlines.
///
/// What it cannot check is the shaping itself — ligatures, kerning, contextual
/// forms — because those need a real font. Goldberry bundles none yet
/// (`licenses/` is still placeholders), so that check waits for one rather than
/// depending on whatever happens to be installed on the machine running the
/// tests.
class ShapingTest {

    @BeforeAll
    static void requireNativeLibrary() {
        NativeLibraryRequirement.enforce();
    }

    @Test
    @DisplayName("every character produces a glyph, and each maps back to its index")
    void clustersMapGlyphsBackToText() {
        try (var font = ShapedFont.empty();
                var buffer = ShapingBuffer.create()) {

            buffer.addText("Hello");
            buffer.guessSegmentProperties();
            var run = buffer.shape(font);

            assertEquals(5, run.length(), "one glyph per character in a font with no ligatures");
            for (var i = 0; i < run.length(); i++) {
                // The cluster is the index into the original String -- which is
                // what makes a caret position meaningful, and what would be
                // quietly wrong if the info array were read at the wrong stride.
                assertEquals(i, run.cluster(i), "cluster " + i);
            }
        }
    }

    @Test
    @DisplayName("the glyph and position arrays are read at the right stride")
    void parallelArraysAreReadAtTheRightStride() {
        // The failure this guards against is specific and nasty: with a stride
        // that is wrong by four bytes, glyph 0 is perfect and every glyph after
        // it is read from the middle of its neighbour. A short run would look
        // fine. So this uses a long one and checks the last entry, which is
        // where a wrong stride has drifted furthest.
        var text = "The quick brown fox jumps over the lazy dog";
        try (var font = ShapedFont.empty();
                var buffer = ShapingBuffer.create()) {

            buffer.addText(text);
            buffer.guessSegmentProperties();
            var run = buffer.shape(font);

            assertEquals(text.length(), run.length());
            assertEquals(text.length() - 1, run.cluster(run.length() - 1), "the last cluster");
            // Reading past the end of HarfBuzz's array would give whatever
            // follows it in memory rather than a clean zero.
            assertEquals(0, run.xAdvance(run.length() - 1), "the empty face advances nothing");
        }
    }

    @Test
    @DisplayName("a surrogate pair is one character, not two")
    void astralCharactersAreOneGlyph() {
        // "𝄞" is U+1D11E — two UTF-16 code units, one character. Getting this
        // wrong produces two .notdef boxes where there should be one, and it is
        // the first thing a UTF-16 binding gets wrong.
        var clef = "𝄞";
        try (var font = ShapedFont.empty();
                var buffer = ShapingBuffer.create()) {

            buffer.addText(clef);
            buffer.guessSegmentProperties();
            var run = buffer.shape(font);

            assertEquals(1, run.length(), "one glyph for one character");
            assertEquals(0, run.cluster(0), "starting at code unit 0");
        }
    }

    @Test
    @DisplayName("direction is guessed from the text, and can be overridden")
    void directionIsGuessedAndOverridable() {
        try (var buffer = ShapingBuffer.create()) {
            buffer.addText("Hello");
            buffer.guessSegmentProperties();
            assertEquals(TextDirection.LTR, buffer.direction(), "Latin guesses left-to-right");

            buffer.reset();
            // Arabic. HarfBuzz reads the first strong character and decides.
            buffer.addText("مرحبا");
            buffer.guessSegmentProperties();
            assertEquals(TextDirection.RTL, buffer.direction(), "Arabic guesses right-to-left");

            buffer.reset();
            buffer.addText("Hello");
            buffer.setDirection(TextDirection.RTL);
            assertEquals(TextDirection.RTL, buffer.direction(), "and an explicit choice wins");
        }
    }

    @Test
    @DisplayName("a right-to-left run keeps its direction and its cluster mapping")
    void rightToLeftIsShapedWithoutLosingClusters() {
        try (var font = ShapedFont.empty();
                var buffer = ShapingBuffer.create()) {

            buffer.addText("abc");
            buffer.setDirection(TextDirection.RTL);
            buffer.setScript("Arab");
            var run = buffer.shape(font);

            assertEquals(3, run.length());
            assertEquals(TextDirection.RTL, buffer.direction(), "the direction survived shaping");
            // Every glyph still maps to a distinct character, which is what a
            // caret and a selection are built on.
            assertEquals(
                    List.of(0, 1, 2),
                    List.of(run.cluster(0), run.cluster(1), run.cluster(2)).stream().sorted().toList(),
                    "each character accounted for exactly once");
        }
    }

    @Test
    @DisplayName("glyph reordering needs a real font, and is not checked here")
    void reorderingIsNotCheckedWithoutAFont() {
        // Worth writing down rather than leaving as a silent absence. A real
        // shaper emits right-to-left glyphs in visual order, so the first glyph
        // is the last character. HarfBuzz's fallback path over the empty face
        // does not reorder, so this file cannot tell the two apart — asserting
        // either way would be asserting a property of the fallback rather than
        // of shaping. It waits for a bundled font.
        try (var font = ShapedFont.empty();
                var buffer = ShapingBuffer.create()) {

            buffer.addText("abc");
            buffer.setDirection(TextDirection.RTL);
            var run = buffer.shape(font);

            assertEquals(3, run.length(), "the count is checkable; the order is not");
        }
    }

    @Test
    @DisplayName("only the requested range produces glyphs; the rest is context")
    void contextIsShapedButNotOutput() {
        try (var font = ShapedFont.empty();
                var buffer = ShapingBuffer.create()) {

            // The shaper sees all of "abcdef" -- which matters for scripts where
            // a letter's form depends on its neighbours -- but produces glyphs
            // only for "cd".
            buffer.addText("abcdef", 2, 4);
            buffer.guessSegmentProperties();
            var run = buffer.shape(font);

            assertEquals(2, run.length(), "two glyphs, not six");
            assertEquals(2, run.cluster(0), "and the clusters index the whole string");
            assertEquals(3, run.cluster(1));
        }
    }

    @Test
    @DisplayName("empty text shapes to no glyphs rather than to null")
    void emptyTextIsAnEmptyRun() {
        try (var font = ShapedFont.empty();
                var buffer = ShapingBuffer.create()) {

            buffer.addText("");
            buffer.guessSegmentProperties();
            var run = buffer.shape(font);

            assertTrue(run.isEmpty());
            assertEquals(0, run.length());
            assertEquals(0L, run.totalXAdvance());
            assertSame(GlyphRun.EMPTY, run, "the shared empty run, not a fresh allocation");
        }
    }

    @Test
    @DisplayName("a buffer can be reused for run after run")
    void buffersAreReusable() {
        // The reason ShapingBuffer exists as an object at all: a layout pass
        // shapes the same paragraph at several widths, and allocating native
        // memory per attempt would put that on the measure callback's path.
        var lengths = new ArrayList<Integer>();
        try (var font = ShapedFont.empty();
                var buffer = ShapingBuffer.create()) {

            for (var text : List.of("a", "bb", "ccc", "dddd")) {
                buffer.reset();
                buffer.addText(text);
                buffer.guessSegmentProperties();
                lengths.add(buffer.shape(font).length());
            }
        }

        assertEquals(List.of(1, 2, 3, 4), lengths, "each run independent of the last");
    }

    @Test
    @DisplayName("a run reports its total advance, which is what layout asks for")
    void totalAdvanceIsTheSumOfAdvances() {
        try (var font = ShapedFont.empty();
                var buffer = ShapingBuffer.create()) {

            buffer.addText("iiii");
            buffer.guessSegmentProperties();
            var run = buffer.shape(font);

            // The empty face advances nothing, so the sum is zero -- but it is
            // the *sum*, computed over the array that was read at a stride.
            assertEquals(0L, run.totalXAdvance());
            assertEquals(4, run.length());
        }
    }

    @Test
    @DisplayName("a script tag must be four characters")
    void scriptTagsAreValidated() {
        try (var buffer = ShapingBuffer.create()) {
            // hb_script_from_string takes a length and would happily accept a
            // shorter tag, producing a script nobody meant.
            var thrown = assertThrows(
                    IllegalArgumentException.class, () -> buffer.setScript("Lat"));
            assertTrue(thrown.getMessage().contains("four characters"), thrown.getMessage());

            assertDoesNotThrow(() -> buffer.setScript("Latn"));
        }
    }

    @Test
    @DisplayName("a language tag reaches HarfBuzz without being freed")
    void languageTagsAreAccepted() {
        try (var buffer = ShapingBuffer.create()) {
            // hb_language_from_string interns and owns the result forever. The
            // test is that setting one twice does not double-free anything.
            assertDoesNotThrow(() -> buffer.setLanguage("en"));
            assertDoesNotThrow(() -> buffer.setLanguage("ar-EG"));
            assertThrows(IllegalArgumentException.class, () -> buffer.setLanguage("  "));
        }
    }

    @Test
    @DisplayName("a range outside the text is refused")
    void rangesAreChecked() {
        try (var buffer = ShapingBuffer.create()) {
            assertThrows(
                    IndexOutOfBoundsException.class, () -> buffer.addText("abc", 0, 4));
            assertThrows(
                    IndexOutOfBoundsException.class, () -> buffer.addText("abc", 2, 1));
        }
    }

    @Test
    @DisplayName("a font with no bytes is refused rather than made into an empty face")
    void emptyFontDataIsRefused() {
        // HarfBuzz would produce a face with no glyphs, which is
        // indistinguishable from a font that failed to parse -- so the one case
        // that can be caught early is.
        var thrown = assertThrows(
                IllegalArgumentException.class, () -> ShapedFont.fromBytes(new byte[0]));

        assertTrue(thrown.getMessage().contains("not a font"), thrown.getMessage());
        assertThrows(IllegalArgumentException.class, () -> ShapedFont.fromBytes(new byte[4], -1));
    }

    @Test
    @DisplayName("bytes that are not a font give a face with no glyphs, not a crash")
    void unparseableFontDataIsSurvivable() {
        // This is HarfBuzz's design and worth pinning down: garbage in gives an
        // empty face rather than an error, so "the text came out as boxes" and
        // "the font failed to load" look the same from Java.
        var garbage = new byte[] {1, 2, 3, 4, 5, 6, 7, 8};
        try (var font = ShapedFont.fromBytes(garbage);
                var buffer = ShapingBuffer.create()) {

            buffer.addText("hi");
            buffer.guessSegmentProperties();
            var run = buffer.shape(font);

            assertEquals(2, run.length(), "still one glyph per character");
        }
    }

    @Test
    @DisplayName("the empty face is borrowed, so closing a font over it is safe")
    void closingAnEmptyFontDoesNotFreeTheSharedFace() {
        // hb_face_get_empty returns an immortal singleton. Destroying it would
        // decrement a reference count that was never ours, and the damage would
        // land on some later, unrelated font.
        for (var i = 0; i < 3; i++) {
            try (var font = ShapedFont.empty();
                    var buffer = ShapingBuffer.create()) {
                buffer.addText("x");
                buffer.guessSegmentProperties();
                assertEquals(1, buffer.shape(font).length(), "round " + i);
            }
        }
    }

    @Test
    @DisplayName("closed fonts and buffers are unusable, and close twice cleanly")
    void closedObjectsAreRefused() {
        var font = ShapedFont.empty();
        var buffer = ShapingBuffer.create();

        font.close();
        assertTrue(font.isClosed());
        assertThrows(IllegalStateException.class, () -> font.setScale(16, 16));
        assertDoesNotThrow(font::close);

        buffer.close();
        assertTrue(buffer.isClosed());
        assertThrows(IllegalStateException.class, () -> buffer.addText("x"));
        assertDoesNotThrow(buffer::close);
    }

    @Test
    @DisplayName("setting a scale is accepted and changes nothing about the empty face")
    void scaleIsSettable() {
        try (var font = ShapedFont.empty()) {
            assertFalse(font.isClosed());
            // 16px in 26.6 fixed point, which is the usual choice.
            assertDoesNotThrow(() -> font.setScale(16 * 64, 16 * 64));
        }
    }

    @Test
    @DisplayName("HarfBuzz reports which build is linked in")
    void versionIsReported() {
        var version = HarfBuzz.get().version();

        assertTrue(version.major() > 0, () -> "a real major version, got " + version);
        assertNotEquals("0.0.0", version.toString());
    }
}
