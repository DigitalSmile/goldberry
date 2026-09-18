package io.github.digitalsmile.goldberry.text.font.sfnt;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/// A face's colour glyphs — its `COLR` version 0 and `CPAL` tables, read.
///
/// ## What a colour glyph is, in this format
///
/// Nothing new. A colour glyph is a **list of ordinary glyphs** drawn one over
/// the next, each filled with one colour out of a palette the font carries. The
/// outlines are in `glyf` beside every other outline in the face, so the
/// rasterizer that draws a letter draws these too — the only thing missing was
/// somebody to read the list and set the colour between the layers
/// ([ADR-0393]).
///
/// That is why this format and not another. The same OpenMoji release ships the
/// same pictures as SVG documents inside the font and as bitmap strikes; both
/// would need a second renderer, and the strikes blur at 150%. Layered outlines
/// need arithmetic.
///
/// ## Why it is Java
///
/// [io.github.digitalsmile.goldberry.text.font.FaceCoverage]'s argument, and the
/// GIF decoder's before it: `COLR` version 0 is two flat arrays and `CPAL` is a
/// third, the whole reader fits on a page, and binding a native equivalent would
/// put a handle and its lifetime across FFM for a question asked once per face.
///
/// ## Version 1 is read as version 0
///
/// `COLR` version 1 adds gradients and transforms in fields **after** the
/// version 0 ones, which stay where they are and keep meaning what they meant.
/// So a version 1 face is read for its version 0 records and draws its
/// non-gradient glyphs correctly; what it does not do is draw a gradient. That is
/// a smaller wrong than refusing the face, and it is written here rather than
/// discovered.
public final class ColorLayers {

    private static final int COLR = TableDirectory.tag('C', 'O', 'L', 'R');
    private static final int CPAL = TableDirectory.tag('C', 'P', 'A', 'L');

    /// The palette index that means "whatever colour the text is".
    ///
    /// A face uses it for the parts of a glyph that should follow the surrounding
    /// prose rather than the palette — the strokes of a monochrome fallback
    /// inside an otherwise coloured face.
    private static final int FOREGROUND = 0xFFFF;

    /// A face with no colour glyphs at all, which is nearly every face.
    public static final ColorLayers NONE =
            new ColorLayers(new int[0], new int[0], new int[0], new int[0], new int[0], new boolean[0], 0);

    /// The base glyphs that have layers, ascending — what [#find] searches.
    private final int[] baseGlyphs;

    private final int[] firstLayer;
    private final int[] layerCounts;

    /// The layer glyphs, all of them, indexed by `firstLayer[record] + index`.
    private final int[] layerGlyphs;

    private final int[] layerArgb;

    /// Which layers take the text's colour instead of `layerArgb`'s.
    private final boolean[] layerFollowsText;

    private final int paletteSize;

    private ColorLayers(
            int[] baseGlyphs,
            int[] firstLayer,
            int[] layerCounts,
            int[] layerGlyphs,
            int[] layerArgb,
            boolean[] layerFollowsText,
            int paletteSize) {

        this.baseGlyphs = baseGlyphs;
        this.firstLayer = firstLayer;
        this.layerCounts = layerCounts;
        this.layerGlyphs = layerGlyphs;
        this.layerArgb = layerArgb;
        this.layerFollowsText = layerFollowsText;
        this.paletteSize = paletteSize;
    }

    /// The colour glyphs in `font`, or [#NONE] when it has none.
    ///
    /// **[#NONE] rather than an exception** for a face this cannot read: no
    /// `COLR`, no `CPAL`, a version beyond what is described above, records out of
    /// order, or bytes that are not a font. The caller is asking whether there is
    /// colour in here, and "no" is an answer it can draw with — the glyphs come
    /// out as ordinary outlines, which is what they were before this existed.
    ///
    /// @param font the face's bytes
    public static ColorLayers read(byte[] font) {
        Objects.requireNonNull(font, "font");
        var colr = TableDirectory.table(font, COLR);
        var cpal = TableDirectory.table(font, CPAL);
        if (colr == null || cpal == null) {
            return NONE;
        }
        try {
            var palette = palette(cpal);
            return palette == null ? NONE : layers(colr, palette);
        } catch (RuntimeException e) {
            // Bounds-checked by the buffer, so a malformed table lands here rather
            // than drawing a glyph the font never described.
            return NONE;
        }
    }

    /// Palette zero, as `0xAARRGGBB`, or null when the table says nothing usable.
    ///
    /// The first palette and not a chosen one: `CPAL` allows several — a light
    /// set and a dark set — and choosing between them is a question about a
    /// theme that nothing has asked yet. When it is asked, this is where it goes.
    private static int @Nullable [] palette(ByteBuffer cpal) {
        var entries = Short.toUnsignedInt(cpal.getShort(2));
        var palettes = Short.toUnsignedInt(cpal.getShort(4));
        var records = Short.toUnsignedInt(cpal.getShort(6));
        var recordsOffset = cpal.getInt(8);
        if (entries == 0 || palettes == 0 || records == 0) {
            return null;
        }
        var first = Short.toUnsignedInt(cpal.getShort(12));
        if (first + entries > records) {
            return null;
        }
        var colours = new int[entries];
        for (var i = 0; i < entries; i++) {
            // BGRA in the file, in that order, one byte each.
            var at = recordsOffset + (first + i) * 4;
            var blue = Byte.toUnsignedInt(cpal.get(at));
            var green = Byte.toUnsignedInt(cpal.get(at + 1));
            var red = Byte.toUnsignedInt(cpal.get(at + 2));
            var alpha = Byte.toUnsignedInt(cpal.get(at + 3));
            colours[i] = (alpha << 24) | (red << 16) | (green << 8) | blue;
        }
        return colours;
    }

    private static ColorLayers layers(ByteBuffer colr, int[] palette) {
        var version = Short.toUnsignedInt(colr.getShort(0));
        if (version > 1) {
            // A version this reader has never seen may have moved the fields it
            // reads, and reading them anyway would draw something the font did
            // not describe.
            return NONE;
        }
        var bases = Short.toUnsignedInt(colr.getShort(2));
        var basesOffset = colr.getInt(4);
        var layersOffset = colr.getInt(8);
        var layerRecords = Short.toUnsignedInt(colr.getShort(12));
        if (bases == 0 || layerRecords == 0) {
            return NONE;
        }

        var layerGlyphs = new int[layerRecords];
        var layerArgb = new int[layerRecords];
        var layerFollowsText = new boolean[layerRecords];
        for (var i = 0; i < layerRecords; i++) {
            var at = layersOffset + i * 4;
            layerGlyphs[i] = Short.toUnsignedInt(colr.getShort(at));
            var entry = Short.toUnsignedInt(colr.getShort(at + 2));
            if (entry == FOREGROUND || entry >= palette.length) {
                // Out of range is treated as the foreground too: the alternative
                // is an index into somebody else's colour, and a glyph in the
                // text's own colour is at least legible.
                layerFollowsText[i] = true;
            } else {
                layerArgb[i] = palette[entry];
            }
        }

        var baseGlyphs = new int[bases];
        var firstLayer = new int[bases];
        var layerCounts = new int[bases];
        for (var i = 0; i < bases; i++) {
            var at = basesOffset + i * 6;
            baseGlyphs[i] = Short.toUnsignedInt(colr.getShort(at));
            firstLayer[i] = Short.toUnsignedInt(colr.getShort(at + 2));
            layerCounts[i] = Short.toUnsignedInt(colr.getShort(at + 4));
            if (i > 0 && baseGlyphs[i] <= baseGlyphs[i - 1]) {
                // The specification requires ascending order, and [#find] is a
                // binary search over it. A file that broke the order would answer
                // "no layers" for glyphs that have them, at random, which is
                // worse to debug than a face that draws as outlines.
                return NONE;
            }
            if (firstLayer[i] + layerCounts[i] > layerRecords) {
                return NONE;
            }
        }
        return new ColorLayers(
                baseGlyphs, firstLayer, layerCounts, layerGlyphs, layerArgb, layerFollowsText, palette.length);
    }

    /// Whether this face has any colour glyphs.
    ///
    /// The question a pen asks once, so that a face of letters pays nothing at
    /// all for this existing.
    public boolean isEmpty() {
        return baseGlyphs.length == 0;
    }

    /// How many colours the face's first palette holds — for a diagnostic, and
    /// for a test that wants to know the palette was read rather than invented.
    public int paletteSize() {
        return paletteSize;
    }

    /// How many base glyphs have layers.
    public int size() {
        return baseGlyphs.length;
    }

    /// The record for `glyphId`, or `-1` when that glyph is an ordinary outline.
    ///
    /// Looked up once and then passed to [#layerCount], [#layerGlyph] and
    /// [#layerArgb], rather than searched again per layer: a pen draws fourteen
    /// layers for an OpenMoji glyph and the search is the only part of this that
    /// is not a array read.
    public int find(int glyphId) {
        var at = Arrays.binarySearch(baseGlyphs, glyphId);
        return at < 0 ? -1 : at;
    }

    /// How many layers the record found by [#find] has.
    public int layerCount(int record) {
        return layerCounts[record];
    }

    /// The glyph to draw for one layer — an ordinary glyph id in the same face.
    public int layerGlyph(int record, int index) {
        Objects.checkIndex(index, layerCounts[record]);
        return layerGlyphs[firstLayer[record] + index];
    }

    /// The colour to fill that layer with, as `0xAARRGGBB`.
    ///
    /// @param textArgb what the surrounding text is drawn in, for the layers whose
    ///        palette entry says to follow it
    public int layerArgb(int record, int index, int textArgb) {
        Objects.checkIndex(index, layerCounts[record]);
        var at = firstLayer[record] + index;
        return layerFollowsText[at] ? textArgb : layerArgb[at];
    }

    @Override
    public String toString() {
        return "ColorLayers[" + baseGlyphs.length + " glyphs, " + layerGlyphs.length + " layers, " + paletteSize
                + " colours]";
    }
}
