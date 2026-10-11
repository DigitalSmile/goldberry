package dev.goldberry.text.font;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.assets.BundledFont;

/// `ch` and `ex` measured in the bundled faces.
class FontMeasuresTest {

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

    @Test
    @DisplayName("ch is the advance of 0, per em, in the face a family names")
    void chIsTheZero() {
        var mono = FontMeasures.of(fonts).face("JetBrains Mono", 400);
        var font = fonts.of(BundledFont.CODE, 20);
        assertEquals(font.widthOf("0") / 20, mono.ch(), 1e-3);
        // A monospaced face: every advance is the zero's.
        assertEquals(font.widthOf("M") / 20, mono.ch(), 1e-3);
    }

    @Test
    @DisplayName("ex is the face's x-height, per em, and less than an em")
    void exIsTheXHeight() {
        var inter = FontMeasures.of(fonts).face("Inter", 400);
        assertTrue(inter.ex() > 0.3 && inter.ex() < 0.8, () -> "ex " + inter.ex());
        assertEquals(fonts.of(BundledFont.UI, 64).xHeight() / 64, inter.ex(), 1e-3);
    }

    @Test
    @DisplayName("two faces measure apart, and the same face is the same measures")
    void perFace() {
        var measures = FontMeasures.of(fonts);
        var inter = measures.face("Inter", 400);
        var mono = inter.face("JetBrains Mono", 400);
        assertNotEquals(inter.ch(), mono.ch());
        assertSame(mono, mono.face("JetBrains Mono", 400));
        assertEquals(inter, measures.face("Inter", 400));
    }

    @Test
    @DisplayName("a renderer with one font measures every face in it")
    void singleFont() {
        var font = fonts.of(BundledFont.CODE, 13);
        var units = FontMeasures.of(font);
        assertEquals(font.widthOf("0") / 13, units.ch(), 1e-3);
        assertSame(units, units.face("Inter", 700));
    }
}
