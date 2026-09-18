package io.github.digitalsmile.goldberry.emoji;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.assets.BundledAssets;
import io.github.digitalsmile.goldberry.assets.BundledFont;
import io.github.digitalsmile.goldberry.paint.Frame;
import io.github.digitalsmile.goldberry.render.PixelBuffer;
import io.github.digitalsmile.goldberry.render.model.DisplayScale;
import io.github.digitalsmile.goldberry.render.model.PhysicalSize;
import io.github.digitalsmile.goldberry.render.model.PixelFormat;
import io.github.digitalsmile.goldberry.text.Paragraph;
import io.github.digitalsmile.goldberry.text.font.Font;
import io.github.digitalsmile.goldberry.text.font.FontFace;
import io.github.digitalsmile.goldberry.text.font.sfnt.ColorLayers;

/// The half of [ADR-0393] that can only be checked against the real face: that
/// the shipped build has colour in it, and that the pixels come out coloured.
///
/// Every assertion here is about **pixels**, not about calls. A layered glyph
/// that drew every layer in the text colour would satisfy any test that counted
/// draws, and would look exactly like the silhouette this replaced.
class ColourEmojiTest {

    /// Big enough that a glyph's interior is several pixels of each colour, and
    /// small enough to scan in a test.
    private static final int SIZE = 96;

    @Test
    @DisplayName("the shipped face is the COLRv0 build, with a palette in it")
    void theFaceHasColourInIt() {
        var layers = ColorLayers.read(BundledAssets.font(BundledFont.EMOJI));

        assertFalse(layers.isEmpty(), "the monochrome build would read as no layers at all");
        assertTrue(layers.size() > 1000, () -> "only " + layers.size() + " colour glyphs");
        assertEquals(35, layers.paletteSize(), "OpenMoji draws everything from one 35-colour palette");
    }

    @Test
    @DisplayName("every layer of a glyph is an ordinary glyph in the same face")
    void layersAreGlyphs() {
        var layers = ColorLayers.read(BundledAssets.font(BundledFont.EMOJI));
        RendererRequirement.enforce();

        try (var face = FontFace.bundled(BundledFont.EMOJI);
                var font = Font.on(face, 16)) {

            var popper = font.shape("🎉");
            assertEquals(1, popper.length(), "one glyph for one code point");

            var record = layers.find(popper.glyphId(0));
            assertNotEquals(-1, record, "the party popper is drawn as layers");
            assertTrue(layers.layerCount(record) > 1, "and as more than one of them");

            // The point of the format: a layer is a glyph id the rasterizer
            // already knows how to draw, so nothing new rasterizes anything.
            for (var i = 0; i < layers.layerCount(record); i++) {
                assertTrue(layers.layerGlyph(record, i) > 0, "layer " + i + " is a real glyph");
            }
        }
    }

    @Test
    @DisplayName("an emoji paints in several colours, which is the whole gap")
    void anEmojiIsColoured() {
        RendererRequirement.enforce();

        try (var face = FontFace.bundled(BundledFont.EMOJI);
                var font = Font.on(face, 64)) {

            var hues = paint(frame -> font.draw(frame, 8, 72, "🎉", 0xFF000000));

            assertTrue(
                    hues.size() > 3,
                    () -> "a party popper came out in " + hues.size() + " hues, which is a silhouette");
        }
    }

    @Test
    @DisplayName("and does so through a paragraph of prose, which is how an application draws one")
    void anEmojiInASentenceIsRouted() {
        RendererRequirement.enforce();

        try (var uiFace = FontFace.bundled(BundledFont.UI);
                var emojiFace = FontFace.bundled(BundledFont.EMOJI);
                var plain = Font.on(uiFace, 48);
                var emoji = Font.on(emojiFace, 48)) {

            var sentence = "hi 🎉";

            // The same font, the same string, twice: once with nowhere to route
            // emoji to and once with the face attached. The first is what
            // `docs/gaps.md` G49 reported.
            var unrouted = paint(frame -> Paragraph.of(plain, sentence).paint(frame, 2, 2, SIZE, 0xFF000000));

            plain.emoji(emoji);
            var routed = paint(frame -> Paragraph.of(plain, sentence).paint(frame, 2, 2, SIZE, 0xFF000000));

            assertTrue(unrouted.isEmpty(), () -> "black text on white has no hue, and this had " + unrouted);
            assertTrue(routed.size() > 3, () -> "routed came out in " + routed.size() + " hues");
        }
    }

    @Test
    @DisplayName("an emoji measures the width the emoji face gives it, and not that width halved")
    void theRescaleIsRight() {
        RendererRequirement.enforce();

        // The one arithmetic mistake this design can make. Inter is 2048 design
        // units to the em and OpenMoji is 1024, so appending one face's advances
        // to the other's without rescaling them makes every emoji come out at
        // half its width — which is a *plausible* number, and would show up as
        // text that overlaps a picture rather than as anything obviously broken.
        try (var uiFace = FontFace.bundled(BundledFont.UI);
                var emojiFace = FontFace.bundled(BundledFont.EMOJI);
                var plain = Font.on(uiFace, 14);
                var emoji = Font.on(emojiFace, 14)) {

            plain.emoji(emoji);
            var prose =
                    Paragraph.of(plain, "hi").layout(Paragraph.UNCONSTRAINED).width();
            var both =
                    Paragraph.of(plain, "hi🎉").layout(Paragraph.UNCONSTRAINED).width();

            assertEquals(
                    emoji.widthOf("🎉"),
                    both - prose,
                    0.05,
                    "the emoji's share of the line is what the emoji face says it is");
        }
    }

    @Test
    @DisplayName("text with no emoji in it is shaped exactly as it was before any of this")
    void proseIsUntouched() {
        RendererRequirement.enforce();

        try (var uiFace = FontFace.bundled(BundledFont.UI);
                var emojiFace = FontFace.bundled(BundledFont.EMOJI);
                var plain = Font.on(uiFace, 16);
                var emoji = Font.on(emojiFace, 16)) {

            var before = Paragraph.of(plain, "Rolling to eu-2 at 14:00");
            plain.emoji(emoji);
            var after = Paragraph.of(plain, "Rolling to eu-2 at 14:00");

            assertEquals(before.glyphs(), after.glyphs(), "attaching an emoji face must not move a letter");
        }
    }

    /// The distinct **hues** a painter left behind.
    ///
    /// Hues and not colours, which is the difference between this test passing
    /// for the right reason and passing for the wrong one. Black text on white
    /// produces a dozen distinct *colours* — every shade of grey antialiasing
    /// puts along an edge — and not one of them has a hue. So a pixel counts here
    /// only when its channels disagree by more than antialiasing can explain,
    /// which is exactly the property "coloured emoji" means and "drawn at all"
    /// does not.
    ///
    /// Quantized to five bits a channel, so two neighbouring shades of one red
    /// are one answer rather than twenty.
    private static Set<Integer> paint(Consumer<Frame> painter) {
        var buffer = PixelBuffer.allocate(new PhysicalSize(SIZE, SIZE), PixelFormat.BGRA32_PREMULTIPLIED);
        var frame = Frame.over(buffer, DisplayScale.ONE);
        frame.fill(0xFFFFFFFF);
        painter.accept(frame);
        frame.end();

        var pixels = buffer.pixels();
        var found = new HashSet<Integer>();
        for (var y = 0; y < SIZE; y++) {
            for (var x = 0; x < SIZE; x++) {
                var at = y * buffer.stride() + x * 4;
                var blue = Byte.toUnsignedInt(pixels.get(at));
                var green = Byte.toUnsignedInt(pixels.get(at + 1));
                var red = Byte.toUnsignedInt(pixels.get(at + 2));
                // Quantized, so that two shades of the same red along an edge are
                // one answer rather than twenty.
                var chroma = Math.max(red, Math.max(green, blue)) - Math.min(red, Math.min(green, blue));
                if (chroma > 32) {
                    found.add(((red >> 5) << 10) | ((green >> 5) << 5) | (blue >> 5));
                }
            }
        }
        return found;
    }
}
