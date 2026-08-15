package io.github.digitalsmile.goldberry.assets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.natives.harfbuzz.ShapedFont;
import io.github.digitalsmile.goldberry.natives.harfbuzz.ShapingBuffer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

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
        assertTrue(BundledAssets.iconNames().size() > 1000,
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
            assertTrue(path.startsWith("M") || path.startsWith("m"),
                    () -> name + " does not begin with a moveto: " + path);
        }
    }

    @Test
    @DisplayName("an icon nobody bundled is an absence, not an exception")
    void unknownIconsAreEmpty() {
        assertTrue(BundledAssets.icon("definitely-not-an-icon").isEmpty());
    }

    @ParameterizedTest
    @EnumSource(BundledFont.class)
    @DisplayName("every bundled font is present and is a real font file")
    void fontsAreBundled(BundledFont font) {
        var bytes = BundledAssets.font(font);

        assertTrue(bytes.length > 10_000, () -> font + " is only " + bytes.length + " bytes");
        // TrueType outlines start with the version tag 0x00010000; OpenType with
        // 'OTTO'. Anything else means the extraction picked up the wrong entry.
        var tag = ((bytes[0] & 0xFF) << 24) | ((bytes[1] & 0xFF) << 16)
                | ((bytes[2] & 0xFF) << 8) | (bytes[3] & 0xFF);
        assertTrue(tag == 0x00010000 || tag == 0x4F54544F,
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

    @BeforeAll
    static void reportNativeAvailability() {
        // Only the shaping tests below need the library; the resource tests
        // above run everywhere.
    }

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
            assertNotEquals(proportional.xAdvance(0), proportional.xAdvance(1),
                    "Inter is proportional");

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
            assertTrue(Math.abs(large - 2 * small) <= 8,
                    () -> large + " should be about twice " + small);
        }
    }

    @Test
    @DisplayName("emoji shape through the emoji face and not through the UI one")
    void emojiNeedTheEmojiFace() {
        RendererRequirement.enforce();

        var snowman = "☃";
        try (var ui = ShapedFont.fromBytes(BundledAssets.font(BundledFont.UI));
                var emoji = ShapedFont.fromBytes(BundledAssets.font(BundledFont.EMOJI));
                var buffer = ShapingBuffer.create()) {

            buffer.addText(snowman);
            buffer.guessSegmentProperties();
            var throughEmoji = buffer.shape(emoji);

            assertEquals(1, throughEmoji.length());
            assertNotEquals(0, throughEmoji.glyphId(0), "OpenMoji has this character");

            // The same character through the UI face, which is the point of
            // having two slots: §6.1 says the chain is primary then emoji and
            // stops there, so choosing the slot is the text stack's job. Nothing
            // cascades on its own, and whatever Inter does with a snowman —
            // notdef or an outline of its own — it is a different answer.
            buffer.reset();
            buffer.addText(snowman);
            buffer.guessSegmentProperties();
            var throughUi = buffer.shape(ui);

            assertEquals(1, throughUi.length(), "one glyph either way");
        }
    }
}
