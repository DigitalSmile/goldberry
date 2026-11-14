package dev.goldberry.text.font;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.RendererRequirement;
import dev.goldberry.css.Typography;
import dev.goldberry.text.font.FontFeatures.Setting;

/// OpenType features reaching the shaper: a font of their own over the same
/// face, shaped with them.
class FontFeaturesTest {

    private static final FontFeatures TABULAR = FontFeatures.NONE.numeric(List.of(new Setting("tnum", 1)));

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

    private static Typography inter() {
        return Typography.INITIAL.size(20);
    }

    @Test
    @DisplayName("tabular figures are all one width, where Inter's own are not")
    void tabular() {
        var proportional = fonts.of(inter());
        assertNotEquals(proportional.widthOf("1111"), proportional.widthOf("0000"), "Inter's figures are proportional");

        var tabular = fonts.of(inter().features(TABULAR));
        assertEquals(tabular.widthOf("1111"), tabular.widthOf("0000"), 1e-9);
    }

    @Test
    @DisplayName("a feature turned off undoes one the numeric variant turned on")
    void off() {
        var undone = inter().features(TABULAR.settings(List.of(new Setting("tnum", 0))));
        var font = fonts.of(undone);
        assertNotEquals(font.widthOf("1111"), font.widthOf("0000"));
    }

    @Test
    @DisplayName("a slashed zero is another glyph")
    void slashedZero() {
        var plain = fonts.of(inter()).shape("0");
        var slashed = fonts.of(inter().features(FontFeatures.NONE.numeric(List.of(new Setting("zero", 1)))))
                .shape("0");
        assertNotEquals(plain.glyphId(0), slashed.glyphId(0));
    }

    @Test
    @DisplayName("each set of features is a font of its own, and the same set is the same font")
    void identity() {
        assertNotSame(fonts.of(inter()), fonts.of(inter().features(TABULAR)));
        assertSame(fonts.of(inter().features(TABULAR)), fonts.of(inter().features(TABULAR)));
        assertEquals(
                List.of(new Setting("tnum", 1)),
                fonts.of(inter().features(TABULAR)).features());
        assertEquals(List.of(), fonts.of(inter()).features());
    }

    @Test
    @DisplayName("a feature the face lacks changes nothing")
    void unknown() {
        var unknown = inter().features(FontFeatures.NONE.settings(List.of(new Setting("XXXX", 1))));
        assertEquals(
                fonts.of(inter()).widthOf("Wide wolves 1209"), fonts.of(unknown).widthOf("Wide wolves 1209"));
    }

    @Test
    @DisplayName("a tag is four printable characters, and a value is not negative")
    void validation() {
        assertThrows(IllegalArgumentException.class, () -> new Setting("tnu", 1));
        assertThrows(IllegalArgumentException.class, () -> new Setting("tnum", -1));
    }
}
