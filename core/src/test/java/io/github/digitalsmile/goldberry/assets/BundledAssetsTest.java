package io.github.digitalsmile.goldberry.assets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.natives.harfbuzz.ShapedFont;
import io.github.digitalsmile.goldberry.natives.harfbuzz.ShapingBuffer;

/// The bundled fonts and icons, and the first shaping that uses real outlines.
///
/// [io.github.digitalsmile.goldberry.natives.harfbuzz.ShapingTest] can only
/// check the shape of the binding, because `:natives` has no font to shape with.
/// The fonts live here, so this is where ligatures, kerning and glyph ids stop
/// being untested — the gap ADR-0032 wrote down.
class BundledAssetsTest {

    @Test
    @DisplayName("the icon table loads and the icons are real path data")
    void iconsAreLoadable() {
        // 1544 icons in Lucide 0.469.0. An exact count would break on every
        // upstream bump for no benefit; an order of magnitude catches a table
        // that failed to compile.
        assertTrue(
                BundledAssets.iconNames().size() > 1000,
                () -> "only " + BundledAssets.iconNames().size() + " icons loaded");

        var check = BundledAssets.icon("check").orElseThrow();
        assertEquals("M20 6 9 17l-5-5", check, "Lucide's check mark, verbatim from the SVG");

        // A circle has to have become two arcs, or it would render as nothing.
        var circle = BundledAssets.icon("circle").orElseThrow();
        assertEquals(2, circle.chars().filter(c -> c == 'A').count(), circle);
    }

    @Test
    @DisplayName("every icon starts with a moveto, so they can be concatenated")
    void everyIconIsWellFormed() {
        for (var name : BundledAssets.iconNames()) {
            var path = BundledAssets.icon(name).orElseThrow();
            assertTrue(
                    path.startsWith("M") || path.startsWith("m"),
                    () -> name + " does not begin with a moveto: " + path);
        }
    }

    @Test
    @DisplayName("an icon nobody bundled is an absence, not an exception")
    void unknownIconsAreEmpty() {
        assertTrue(BundledAssets.icon("definitely-not-an-icon").isEmpty());
    }

    @ParameterizedTest
    @EnumSource(value = BundledFont.class, names = "EMOJI", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("every bundled font is present and is a real font file")
    void fontsAreBundled(BundledFont font) {
        // Every one but the emoji face, which is not in this jar: OpenMoji is
        // CC BY-SA and ships as `goldberry-emoji`, so the face and the test that
        // shapes with it both live there ([ADR-0384]).
        var bytes = BundledAssets.font(font);

        assertTrue(bytes.length > 10_000, () -> font + " is only " + bytes.length + " bytes");
        // TrueType outlines start with the version tag 0x00010000; OpenType with
        // 'OTTO'. Anything else means the extraction picked up the wrong entry.
        var tag = ((bytes[0] & 0xFF) << 24) | ((bytes[1] & 0xFF) << 16) | ((bytes[2] & 0xFF) << 8) | (bytes[3] & 0xFF);
        assertTrue(
                tag == 0x00010000 || tag == 0x4F54544F,
                () -> font + " starts with 0x" + Integer.toHexString(tag) + ", not a font tag");
    }

    @Test
    @DisplayName("the font bytes are a copy, so a caller cannot corrupt the next one")
    void fontBytesAreNotShared() {
        var first = BundledAssets.font(BundledFont.UI);
        var second = BundledAssets.font(BundledFont.UI);

        assertNotEquals(System.identityHashCode(first), System.identityHashCode(second));

        // Changed to something it demonstrably was not: a TrueType file starts
        // with 0x00, so writing a zero here would have proved nothing.
        var untouched = second[0];
        first[0] = (byte) (untouched + 1);

        assertEquals(untouched, second[0], "mutating one must not reach the other");
    }

    // --- shaping with real outlines -----------------------------------------

    // Only the shaping tests below need the library; the resource tests above run
    // everywhere. That used to be said by an empty `@BeforeAll` named
    // `reportNativeAvailability`, which reported nothing (the 2026-09-18 review,
    // §6); it is a comment now, which is what it always was.

    @Test
    @DisplayName("shaping with Inter produces real glyph ids and advances")
    void realFontProducesRealGlyphs() {
        RendererRequirement.enforce();

        try (var font = ShapedFont.fromBytes(BundledAssets.font(BundledFont.UI));
                var buffer = ShapingBuffer.create()) {

            font.setScale(16 * 64, 16 * 64);
            buffer.addText("Hi");
            buffer.guessSegmentProperties();
            var run = buffer.shape(font);

            assertEquals(2, run.length());
            // Against the empty face every glyph id was 0. Real outlines mean
            // real ids, and no two different letters share one.
            assertNotEquals(0, run.glyphId(0), "H mapped to .notdef — the font did not load");
            assertNotEquals(run.glyphId(0), run.glyphId(1), "H and i are different glyphs");
            assertTrue(run.xAdvance(0) > 0, "a glyph with no advance would stack on the next");
            assertTrue(run.totalXAdvance() > 0);
        }
    }

    @Test
    @DisplayName("a wider string advances further, which is what layout measures")
    void advancesGrowWithText() {
        RendererRequirement.enforce();

        try (var font = ShapedFont.fromBytes(BundledAssets.font(BundledFont.UI));
                var buffer = ShapingBuffer.create()) {

            font.setScale(16 * 64, 16 * 64);

            buffer.addText("i");
            buffer.guessSegmentProperties();
            var narrow = buffer.shape(font).totalXAdvance();

            buffer.reset();
            buffer.addText("Wi");
            buffer.guessSegmentProperties();
            var wider = buffer.shape(font).totalXAdvance();

            // This is precisely the number a Yoga measure function reports.
            assertTrue(wider > narrow, () -> wider + " should exceed " + narrow);
        }
    }

    @Test
    @DisplayName("a proportional font kerns; a monospace one does not")
    void metricsDifferBetweenTheFaces() {
        RendererRequirement.enforce();

        try (var ui = ShapedFont.fromBytes(BundledAssets.font(BundledFont.UI));
                var code = ShapedFont.fromBytes(BundledAssets.font(BundledFont.CODE));
                var buffer = ShapingBuffer.create()) {

            ui.setScale(16 * 64, 16 * 64);
            code.setScale(16 * 64, 16 * 64);

            // In a monospace face every advance is identical by definition. In
            // Inter, 'i' is far narrower than 'W'. That difference is the whole
            // reason shaping exists rather than multiplying by a character count.
            buffer.addText("iW");
            buffer.guessSegmentProperties();
            var proportional = buffer.shape(ui);
            assertNotEquals(proportional.xAdvance(0), proportional.xAdvance(1), "Inter is proportional");

            buffer.reset();
            buffer.addText("iW");
            buffer.guessSegmentProperties();
            var mono = buffer.shape(code);
            assertEquals(mono.xAdvance(0), mono.xAdvance(1), "JetBrains Mono is monospace");
        }
    }

    @Test
    @DisplayName("the scale is what the advances are measured in")
    void scaleChangesTheUnits() {
        RendererRequirement.enforce();

        try (var font = ShapedFont.fromBytes(BundledAssets.font(BundledFont.UI));
                var buffer = ShapingBuffer.create()) {

            font.setScale(16 * 64, 16 * 64);
            buffer.addText("Hello");
            buffer.guessSegmentProperties();
            var small = buffer.shape(font).totalXAdvance();

            font.setScale(32 * 64, 32 * 64);
            buffer.reset();
            buffer.addText("Hello");
            buffer.guessSegmentProperties();
            var large = buffer.shape(font).totalXAdvance();

            // Twice the size, twice the width, within the rounding that integer
            // units force.
            assertTrue(Math.abs(large - 2 * small) <= 8, () -> large + " should be about twice " + small);
        }
    }

    @Test
    @DisplayName("the emoji face is not in this jar, and says which artifact it is in")
    void emojiIsItsOwnArtifact() {
        // The obligation is the reason: CC BY-SA wants credit where the work is
        // seen, which a notice file cannot give — so an application that draws
        // emoji adds the artifact and meets it on purpose ([ADR-0384]). What is
        // asserted here is that the failure *says* so: a missing resource would
        // be a puzzle, and this is an instruction.
        assertFalse(BundledAssets.hasEmojiFont(), "goldberry-emoji is not on this test's path");

        var thrown = assertThrows(MissingEmojiFontException.class, () -> BundledAssets.font(BundledFont.EMOJI));
        assertTrue(thrown.getMessage().contains("goldberry-emoji"), thrown.getMessage());
        assertTrue(thrown.getMessage().contains("CC BY-SA"), thrown.getMessage());
    }

    @Test
    @DisplayName("and the slot is still named here, because the text stack chooses it")
    void theSlotSurvives() {
        // §6.1's chain is primary then emoji, and which of the two a run of text
        // goes through is the text stack's decision — so the *name* stays in the
        // catalogue of faces even when the file is somebody else's.
        assertEquals("OpenMoji", BundledFont.EMOJI.family());
    }
}
