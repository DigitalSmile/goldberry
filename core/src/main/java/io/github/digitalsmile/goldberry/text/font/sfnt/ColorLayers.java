package io.github.digitalsmile.goldberry.text.font.sfnt;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

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
/// That is why this format was the first one read. It is no longer the one the
/// shipped emoji face uses: Noto Color Emoji is `COLR` version 1, a paint graph
/// with gradients and transforms, and [ColorPaints] reads it. This reader stays
/// for every face that is still version 0 — and a face may carry both, version 0
/// records for the renderers that know no better beside version 1 ones for those
/// that do ([ADR-0456]).
///
/// ## Why it is Java
///
/// [io.github.digitalsmile.goldberry.text.font.FaceCoverage]'s argument, and the
/// GIF decoder's before it: `COLR` version 0 is two flat arrays and `CPAL` is a
/// third, the whole reader fits on a page, and binding a native equivalent would
/// put a handle and its lifetime across FFM for a question asked once per face.
///
/// ## Version 1 is read for its version 0 records
///
/// `COLR` version 1 adds its paint graph in fields **after** the version 0 ones,
/// which stay where they are and keep meaning what they meant. So this reads a
/// version 1 table's layer records, if it has any, and [ColorPaints] reads the
/// graph. A painter asks [ColorPaints] first: where a glyph has both, the graph
/// is the picture the font means.
public final class ColorLayers {

    private static final int COLR = TableDirectory.tag('C', 'O', 'L', 'R');
    private static final int CPAL = TableDirectory.tag('C', 'P', 'A', 'L');

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
            var palette = Palette.first(cpal);
            return palette == null ? NONE : layers(colr, palette);
        } catch (RuntimeException e) {
            // Bounds-checked by the buffer, so a malformed table lands here rather
            // than drawing a glyph the font never described.
            return NONE;
        }
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
            if (entry == Palette.FOREGROUND || entry >= palette.length) {
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
    /// [#layerArgb], rather than searched again per layer: a pen draws a dozen
    /// layers for a typical colour glyph and the search is the only part of this
    /// that is not an array read.
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
