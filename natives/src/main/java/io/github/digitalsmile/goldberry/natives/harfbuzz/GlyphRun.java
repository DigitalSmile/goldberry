package io.github.digitalsmile.goldberry.natives.harfbuzz;

/// The result of shaping one run of text.
///
/// Parallel arrays rather than an array of objects, deliberately. A paragraph is
/// thousands of glyphs and gets reshaped on every width Yoga proposes during a
/// layout pass, so one object per glyph would be an allocation storm on the
/// hot path. Six `int[]`s are six allocations however long the run is.
///
/// The arrays are copies of HarfBuzz's own, made because the buffer reuses its
/// memory: holding on to the native arrays across a reset would read the *next*
/// run's glyphs.
///
/// ## Reading it
///
/// `glyphIds[i]` is an index into the font, **not** a character — a ligature is
/// one glyph for several characters, and a single character can produce several
/// glyphs. `clusters[i]` is what maps back: it is the index into the original
/// text that this glyph came from, and glyphs sharing a cluster value are
/// inseparable. That mapping is what a caret and a selection are built on.
///
/// Advances and offsets are in the units the font's scale was set to
/// ([ShapedFont#setScale]). Advances move the pen; offsets move the glyph
/// without moving the pen, which is how a mark sits over a base.
public final class GlyphRun {

    /// A run with no glyphs. Shaping empty text produces this rather than null.
    public static final GlyphRun EMPTY =
            new GlyphRun(new int[0], new int[0], new int[0], new int[0], new int[0], new int[0]);

    private final int[] glyphIds;
    private final int[] clusters;
    private final int[] xAdvances;
    private final int[] yAdvances;
    private final int[] xOffsets;
    private final int[] yOffsets;

    GlyphRun(
            int[] glyphIds, int[] clusters,
            int[] xAdvances, int[] yAdvances,
            int[] xOffsets, int[] yOffsets) {
        this.glyphIds = glyphIds;
        this.clusters = clusters;
        this.xAdvances = xAdvances;
        this.yAdvances = yAdvances;
        this.xOffsets = xOffsets;
        this.yOffsets = yOffsets;
    }

    /// How many glyphs the run produced. Not the number of characters shaped.
    public int length() {
        return glyphIds.length;
    }

    /// Whether the run produced no glyphs at all.
    public boolean isEmpty() {
        return glyphIds.length == 0;
    }

    /// The font glyph id at `index`.
    public int glyphId(int index) {
        return glyphIds[index];
    }

    /// The index into the original text that the glyph at `index` came from.
    public int cluster(int index) {
        return clusters[index];
    }

    /// How far the pen moves along x after drawing the glyph at `index`.
    public int xAdvance(int index) {
        return xAdvances[index];
    }

    /// How far the pen moves along y. Zero for horizontal text.
    public int yAdvance(int index) {
        return yAdvances[index];
    }

    /// How far the glyph at `index` is drawn from the pen along x, without
    /// moving the pen.
    public int xOffset(int index) {
        return xOffsets[index];
    }

    /// How far the glyph at `index` is drawn from the pen along y.
    public int yOffset(int index) {
        return yOffsets[index];
    }

    /// The total width of the run, in the font's scale units.
    ///
    /// This is the number a measure function reports to Yoga: the sum of the
    /// advances, not the extent of the ink. A trailing space advances the pen
    /// and draws nothing, and layout has to account for it.
    public long totalXAdvance() {
        var total = 0L;
        for (var advance : xAdvances) {
            total += advance;
        }
        return total;
    }

    @Override
    public String toString() {
        return "GlyphRun[" + glyphIds.length + " glyphs, " + totalXAdvance() + " wide]";
    }
}
