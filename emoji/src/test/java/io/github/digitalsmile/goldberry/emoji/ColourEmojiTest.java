package io.github.digitalsmile.goldberry.emoji;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import io.github.digitalsmile.goldberry.text.font.sfnt.ColorPaint;
import io.github.digitalsmile.goldberry.text.font.sfnt.ColorPaints;
import io.github.digitalsmile.goldberry.text.font.sfnt.CompositeMode;
import io.github.digitalsmile.goldberry.text.font.sfnt.GlyphOutlines;
import io.github.digitalsmile.goldberry.text.font.sfnt.OutlineSink;

/// The half of [ADR-0393] and [ADR-0456] that can only be checked against the
/// real face: that the shipped build's colour is readable, and that the pixels
/// come out coloured.
///
/// Every assertion about drawing is about **pixels**, not about calls. A paint
/// graph drawn with every fill in the text colour would satisfy any test that
/// counted draws, and would look exactly like a silhouette.
class ColourEmojiTest {

    /// Big enough that a glyph's interior is several pixels of each colour, and
    /// small enough to scan in a test.
    private static final int SIZE = 96;

    private static byte[] face() {
        return BundledAssets.font(BundledFont.EMOJI);
    }

    @Test
    @DisplayName("the shipped face is COLRv1, with a palette, and has no version 0 layers to fall back on")
    void theFaceIsVersionOne() {
        var paints = ColorPaints.read(face());

        assertTrue(paints.size() > 3000, () -> "only " + paints.size() + " colour glyphs");
        assertTrue(paints.paletteSize() > 1000, () -> "a " + paints.paletteSize() + "-colour palette");
        // Which is why reading version 1 was not optional: the version 0 reader
        // alone finds nothing here, and every emoji would be its bare outline.
        assertTrue(ColorLayers.read(face()).isEmpty(), "Noto ships no version 0 records");
    }

    @Test
    @DisplayName("every graph in the face is read, and every outline one of them clips to exists")
    void everyGraphReads() {
        var bytes = face();
        var paints = ColorPaints.read(bytes);
        var outlines = GlyphOutlines.read(bytes);
        assertEquals(1024, outlines.unitsPerEm(), "Noto is 1024 units to the em");

        // A malformed graph answers null and is drawn as its outline, which is
        // correct for a broken font and a silent regression for this one -- so
        // every base glyph in the shipped face must come back as a graph.
        var read = 0;
        var clips = new HashSet<Integer>();
        for (var glyph = 0; glyph < outlines.glyphCount(); glyph++) {
            if (!paints.has(glyph)) {
                continue;
            }
            var graph = paints.paint(glyph);
            assertNotNull(graph, "glyph " + glyph + " has a graph that did not read");
            read++;
            collectGlyphs(graph, clips);
        }
        assertEquals(paints.size(), read, "every graph the index lists");

        for (var glyph : clips) {
            var sink = new CountingSink();
            assertTrue(outlines.outline(glyph, sink), "the outline of glyph " + glyph + " reads");
            assertTrue(sink.segments > 0, "and is a shape: glyph " + glyph);
        }
    }

    @Test
    @DisplayName("a flag is a composite, soft-lit, which is the one node drawn offscreen")
    void aFlagIsAComposite() {
        RendererRequirement.enforce();

        try (var face = FontFace.bundled(BundledFont.EMOJI);
                var font = Font.on(face, 16)) {
            var flag = font.shape("🇺🇸");
            assertEquals(1, flag.length(), "two regional indicators ligate into one flag");

            var graph = ColorPaints.read(face()).paint(flag.glyphId(0));
            var composite = assertInstanceOf(ColorPaint.Composite.class, graph);
            assertEquals(CompositeMode.SOFT_LIGHT, composite.mode());
        }
    }

    @Test
    @DisplayName("a flag paints in its own colours, through the composite")
    void aFlagIsColoured() {
        RendererRequirement.enforce();

        try (var face = FontFace.bundled(BundledFont.EMOJI);
                var font = Font.on(face, 64)) {

            var hues = paint(frame -> font.draw(frame, 8, 72, "🇺🇸", 0xFF000000));

            assertTrue(hues.size() > 3, () -> "a flag came out in " + hues.size() + " hues");
        }
    }

    @Test
    @DisplayName("a face is shaded with gradients, not filled flat")
    void gradientsAreDrawn() {
        RendererRequirement.enforce();

        // The grinning face is a radial gradient from yellow to orange. Flat
        // fills of its layers give a handful of hues; a gradient gives a ramp of
        // them. Forty is well above what the flat layers alone produce.
        try (var face = FontFace.bundled(BundledFont.EMOJI);
                var font = Font.on(face, 80)) {

            var hues = paint(frame -> font.draw(frame, 4, 76, "😀", 0xFF000000));

            assertTrue(hues.size() > 40, () -> "the face came out in " + hues.size() + " hues, which is flat");
        }
    }

    /// Every glyph id a graph clips to, following layers, transforms and
    /// composites but not references to other graphs.
    private static void collectGlyphs(ColorPaint node, Set<Integer> into) {
        switch (node) {
            case ColorPaint.Layers layers -> layers.layers().forEach(layer -> collectGlyphs(layer, into));
            case ColorPaint.Glyph glyph -> {
                into.add(glyph.glyphId());
                collectGlyphs(glyph.paint(), into);
            }
            case ColorPaint.Transform transform -> collectGlyphs(transform.paint(), into);
            case ColorPaint.Composite composite -> {
                collectGlyphs(composite.source(), into);
                collectGlyphs(composite.backdrop(), into);
            }
            case ColorPaint.ColrGlyph _,
                    ColorPaint.Solid _,
                    ColorPaint.LinearGradient _,
                    ColorPaint.RadialGradient _,
                    ColorPaint.SweepGradient _ -> {}
        }
    }

    /// Counts what an outline sends, for "is this a shape at all".
    private static final class CountingSink implements OutlineSink {

        private int segments;

        @Override
        public void moveTo(double x, double y) {}

        @Override
        public void lineTo(double x, double y) {
            segments++;
        }

        @Override
        public void quadTo(double cx, double cy, double x, double y) {
            segments++;
        }

        @Override
        public void close() {}
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
        // units to the em and Noto is 1024, so appending one face's advances
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
