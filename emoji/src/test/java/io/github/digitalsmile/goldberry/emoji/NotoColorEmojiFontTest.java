package io.github.digitalsmile.goldberry.emoji;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.assets.BundledAssets;
import io.github.digitalsmile.goldberry.assets.BundledFont;
import io.github.digitalsmile.goldberry.assets.EmojiFont;
import io.github.digitalsmile.goldberry.natives.harfbuzz.ShapedFont;
import io.github.digitalsmile.goldberry.natives.harfbuzz.ShapingBuffer;

/// The face this artifact exists to carry — [ADR-0384], [ADR-0456].
///
/// The shaping half moved here from `:core`'s `BundledAssetsTest` with the font
/// itself: "emoji shape through the emoji face" is a test of the thing that
/// ships the face, and `:core` now tests the *absence* instead.
class NotoColorEmojiFontTest {

    @Test
    @DisplayName("the face is found through the service, which is what `:core` asks")
    void providerIsFound() {
        assertTrue(BundledAssets.hasEmojiFont(), "goldberry-emoji is on this test's module path");
        assertEquals(NotoColorEmojiFont.class, EmojiFont.provider().getClass());
    }

    @Test
    @DisplayName("and it is a real font file")
    void bytesAreAFont() {
        var bytes = BundledAssets.font(BundledFont.EMOJI);

        assertTrue(bytes.length > 10_000, () -> "only " + bytes.length + " bytes");
        var tag = ((bytes[0] & 0xFF) << 24) | ((bytes[1] & 0xFF) << 16) | ((bytes[2] & 0xFF) << 8) | (bytes[3] & 0xFF);
        assertTrue(
                tag == 0x00010000 || tag == 0x4F54544F,
                () -> "starts with 0x" + Integer.toHexString(tag) + ", which is not a font tag");
    }

    @Test
    @DisplayName("the bytes are a copy, so a caller cannot corrupt the next one")
    void bytesAreNotShared() {
        var first = BundledAssets.font(BundledFont.EMOJI);
        var second = BundledAssets.font(BundledFont.EMOJI);

        var untouched = second[0];
        first[0] = (byte) (untouched + 1);
        assertEquals(untouched, second[0], "mutating one must not reach the other");
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
            assertNotEquals(0, throughEmoji.glyphId(0), "Noto has this character");

            // The same character through the UI face, which is the point of
            // having two slots: §6.1 says the chain is primary then emoji and
            // stops there, so choosing the slot is the text stack's job. Nothing
            // cascades on its own.
            buffer.reset();
            buffer.addText(snowman);
            buffer.guessSegmentProperties();
            var throughUi = buffer.shape(ui);

            assertEquals(1, throughUi.length(), "one glyph either way");
        }
    }

    @Test
    @DisplayName("a jar assembled without its font says so")
    void missingResource() {
        // The failure nobody finds until a user does: the asset step did not
        // run, and the jar is a provider with nothing behind it.
        var thrown = org.junit.jupiter.api.Assertions.assertThrows(
                java.io.UncheckedIOException.class, () -> NotoColorEmojiFont.read("/no/such/font.ttf"));
        assertTrue(thrown.getMessage().contains("asset step"), thrown.getMessage());
    }

    @Test
    @DisplayName("the credit is a constant, and names the face and its licence")
    void credit() {
        // Optional under the OFL, unlike OpenMoji's CC BY-SA -- but an
        // application that puts one in an about box should not have to
        // transcribe it, and should not be handed the old face's.
        assertTrue(NotoColorEmojiFont.CREDIT.contains("Noto Color Emoji"));
        assertTrue(NotoColorEmojiFont.CREDIT.contains("Open Font License"));
    }

    @Test
    @DisplayName("the face is Noto's, by its own name table's family, which is what a stylesheet writes")
    void theFamilyIsNoto() {
        assertEquals("Noto Color Emoji", BundledFont.EMOJI.family());
    }
}
