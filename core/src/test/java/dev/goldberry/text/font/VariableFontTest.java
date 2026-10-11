package dev.goldberry.text.font;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.assets.BundledAssets;
import dev.goldberry.assets.BundledFont;
import dev.goldberry.css.Typography;
import dev.goldberry.natives.harfbuzz.Instancer;
import dev.goldberry.text.font.sfnt.VariationAxes;

/// A variable face set at a weight, a width or an axis a stylesheet names:
/// pinned into a static face of its own, which the shaper and the rasterizer
/// both read.
class VariableFontTest {

    private static final String SAMPLE = "Wide wolves wander westward";

    private Fonts fonts;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        fonts = Fonts.bundled();
    }

    @AfterEach
    void tearDown() {
        if (fonts != null) {
            fonts.close();
        }
    }

    private static Typography inter(int weight) {
        return Typography.INITIAL.weight(weight).size(20);
    }

    private double width(Typography typography) {
        return fonts.of(typography).widthOf(SAMPLE);
    }

    @Test
    @DisplayName("the upright UI face is variable along wght, and its static siblings are not")
    void axes() {
        var axes = VariationAxes.of(BundledAssets.font(BundledFont.UI));
        var weight = VariationAxes.find(axes, "wght").orElseThrow();
        assertTrue(weight.min() <= 100 && weight.max() >= 900, weight::toString);
        assertEquals(400, weight.defaultValue());
        assertEquals(List.of(), VariationAxes.of(BundledAssets.font(BundledFont.UI_STRONG)));
    }

    @Test
    @DisplayName("an instance is a static face, set where it was pinned")
    void instance() {
        var pinned = Instancer.instance(BundledAssets.font(BundledFont.UI), List.of(new Instancer.Axis("wght", 700)))
                .orElseThrow();
        assertEquals(List.of(), VariationAxes.of(pinned), "no variation is left in it");
        try (var regular = FontFace.bundled(BundledFont.UI);
                var bold = FontFace.of("Inter", pinned);
                var regularFont = Font.on(regular, 20);
                var boldFont = Font.on(bold, 20)) {
            assertTrue(boldFont.widthOf(SAMPLE) > regularFont.widthOf(SAMPLE), "a bolder Inter is wider");
        }
    }

    @Test
    @DisplayName("a weight between the faces is drawn at that weight, from the variable face")
    void between() {
        var w400 = width(inter(400));
        var w500 = width(inter(500));
        var w600 = width(inter(600));
        var w700 = width(inter(700));
        assertTrue(
                w400 < w500 && w500 < w600 && w600 < w700,
                () -> List.of(w400, w500, w600, w700).toString());
    }

    @Test
    @DisplayName("the two weights a face exists for are those faces, as before")
    void exactFaces() {
        assertSame(fonts.of(BundledFont.UI, 20), fonts.of(inter(400)));
        assertSame(fonts.of(BundledFont.UI_STRONG, 20), fonts.of(inter(600)));
    }

    @Test
    @DisplayName("each setting is pinned once, and the font is kept")
    void cached() {
        var first = fonts.of(inter(700));
        var faces = fonts.openFaces();
        assertSame(first, fonts.of(inter(700)));
        assertEquals(faces, fonts.openFaces());
        assertNotSame(first, fonts.of(inter(700).size(24)));
        assertEquals(faces, fonts.openFaces(), "another size over the same instance opens no face");
    }

    @Test
    @DisplayName("font-variation-settings has the last word on an axis the face has")
    void settings() {
        var light = inter(400).variation(FontVariation.NONE.settings(List.of(new FontVariation.Setting("wght", 300))));
        assertTrue(width(light) < width(inter(400)));
        var unknown = inter(400).variation(FontVariation.NONE.settings(List.of(new FontVariation.Setting("XXXX", 3))));
        assertSame(fonts.of(inter(400)), fonts.of(unknown), "an axis the face lacks is not written");
    }

    @Test
    @DisplayName("a stretch on a face with no wdth axis changes nothing")
    void stretch() {
        assertSame(fonts.of(inter(400)), fonts.of(inter(400).variation(FontVariation.NONE.stretch(75))));
    }

    @Test
    @DisplayName("an italic, which is static, draws the nearest of its two weights")
    void italic() {
        var italic = inter(500).style(BundledFont.Style.ITALIC);
        assertSame(fonts.of(BundledFont.UI_ITALIC, 20), fonts.of(italic));
    }
}
