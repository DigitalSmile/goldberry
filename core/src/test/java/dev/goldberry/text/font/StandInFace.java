package dev.goldberry.text.font;

import java.util.HashMap;
import java.util.Map;

import dev.goldberry.assets.BundledAssets;
import dev.goldberry.assets.BundledFont;
import dev.goldberry.text.font.sfnt.SyntheticFont;

/// A fallback face for tests: Inter's outlines behind a `cmap` that claims
/// characters Inter does not have.
///
/// `:core` ships no face with Han or Arabic in it and a test must not read the
/// system's, so the face is assembled: every table of Inter, with a `cmap` that
/// maps `中` or `م` to one of Inter's own letters. The shaper and the rasterizer
/// read real outlines and real metrics, and what a test learns is which face a
/// character was routed to and where its glyph went, not what Han looks like.
/// A grid other than Inter's 2048 to the em makes the rescaling between two
/// faces' units visible in every width.
public final class StandInFace {

    /// Han, as a name is written in it.
    public static final String HAN = "中文名字";

    /// Arabic: Muhammad, and Ali.
    public static final String ARABIC = "محمد علي";

    /// Mathematical bold Fraktur: Hessa, as a display name spells it.
    public static final String FRAKTUR = "𝕳𝖊𝖘𝖘𝖆";

    /// U+064E, the Arabic fatha: a combining mark above a letter.
    public static final char FATHA = '\u064E';

    private StandInFace() {}

    /// The characters a stand-in claims, each drawn with the glyph of the Latin
    /// letter beside it.
    public static Map<Integer, Character> scripts() {
        var claims = new HashMap<Integer, Character>();
        claim(claims, HAN, "ZWNX");
        claim(claims, "محدعلي", "mhdaly");
        claims.put((int) FATHA, '\u0301');
        claim(claims, FRAKTUR, "Hessa");
        return claims;
    }

    private static void claim(Map<Integer, Character> claims, String characters, String drawnAs) {
        var at = 0;
        var letter = 0;
        while (at < characters.length()) {
            var codePoint = characters.codePointAt(at);
            claims.put(codePoint, drawnAs.charAt(letter++));
            at += Character.charCount(codePoint);
        }
    }

    /// A face claiming `claims`, and also printable ASCII when `ascii` is set, as
    /// every real CJK face does, on a grid of `unitsPerEm`.
    ///
    /// @param claims each claimed code point, and the character of Inter whose
    ///               glyph draws it
    public static byte[] of(Map<Integer, Character> claims, boolean ascii, int unitsPerEm) {
        var inter = BundledAssets.font(BundledFont.UI);
        var glyphs = new HashMap<Integer, Integer>();
        try (var font = Font.of(inter, 16)) {
            for (var claim : claims.entrySet()) {
                glyphs.put(claim.getKey(), glyphOf(font, claim.getValue()));
            }
            if (ascii) {
                for (var c = 0x20; c < 0x7F; c++) {
                    glyphs.put(c, glyphOf(font, (char) c));
                }
            }
        }
        return SyntheticFont.splice(
                inter,
                Map.of(
                        "cmap", SyntheticFont.cmap(glyphs),
                        "head", SyntheticFont.headWithUnitsPerEm(inter, unitsPerEm)));
    }

    /// Inter's glyph for one character.
    public static int glyphOf(Font inter, char character) {
        var run = inter.shape(String.valueOf(character));
        var glyph = run.glyphId(0);
        if (glyph == 0) {
            throw new IllegalArgumentException("Inter has no glyph for U+" + Integer.toHexString(character));
        }
        return glyph;
    }
}
