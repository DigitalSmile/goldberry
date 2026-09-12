package io.github.digitalsmile.goldberry.text;

import java.util.Arrays;

/// The glyphs a shaper produced, and where they go.
///
/// ## Why the toolkit owns this
///
/// It is what `Font.shape` returns and what `Paragraph.glyphs()` hands back, so
/// before ADR-0282 reading either meant reading a `:natives` class. Nothing about
/// it is native: it is six `int` arrays, with no foreign memory, no lifetime and
/// nothing to close — HarfBuzz's own buffers are read and copied out at the
/// moment shaping finishes, and this is what is left (ADR-0282).
///
/// ## What is in it
///
/// One entry per **glyph**, which is not one per character. A ligature is one
/// glyph for several characters and a decomposed accent is two glyphs for one, so
/// the only honest mapping back to the text is [#cluster], which is the byte
/// index of the first character a glyph belongs to. Two glyphs sharing a cluster
/// are inseparable — that mapping is what a caret and a selection are built on.
///
/// Advances move the pen; offsets move the glyph without moving the pen, which is
/// how a mark sits over a base. Both are in the font's **design units**, so a run
/// is correct at any size: it is the face's shaping of that string rather than any
/// one size's.
public final class ShapedRun {

    /// A run with no glyphs. Shaping empty text produces this rather than null.
    public static final ShapedRun EMPTY =
            new ShapedRun(new int[0], new int[0], new int[0], new int[0], new int[0], new int[0]);

    private final int[] glyphIds;
    private final int[] clusters;
    private final int[] xAdvances;
    private final int[] yAdvances;
    private final int[] xOffsets;
    private final int[] yOffsets;

    /// Takes the arrays as given rather than copying them.
    ///
    /// Package-private, and the reason this class is not a record: the six arrays
    /// are built once by the shaper and never touched again, and a record would
    /// have handed them out through its accessors for anyone to write into.
    ShapedRun(int[] glyphIds, int[] clusters, int[] xAdvances, int[] yAdvances, int[] xOffsets, int[] yOffsets) {
        this.glyphIds = glyphIds;
        this.clusters = clusters;
        this.xAdvances = xAdvances;
        this.yAdvances = yAdvances;
        this.xOffsets = xOffsets;
        this.yOffsets = yOffsets;
    }

    /// A run over copies of these six arrays.
    ///
    /// Copied rather than taken, because a caller that kept a reference could
    /// write into a run after it was built — and a shaped run is a value that
    /// a paragraph cache hands out repeatedly.
    ///
    /// @throws IllegalArgumentException if the six are not the same length
    public static ShapedRun of(
            int[] glyphIds, int[] clusters, int[] xAdvances, int[] yAdvances, int[] xOffsets, int[] yOffsets) {

        var length = glyphIds.length;
        if (clusters.length != length
                || xAdvances.length != length
                || yAdvances.length != length
                || xOffsets.length != length
                || yOffsets.length != length) {
            throw new IllegalArgumentException("a shaped run has one of each number per glyph, and these do not agree");
        }
        return length == 0
                ? EMPTY
                : new ShapedRun(
                        glyphIds.clone(),
                        clusters.clone(),
                        xAdvances.clone(),
                        yAdvances.clone(),
                        xOffsets.clone(),
                        yOffsets.clone());
    }

    /// How many glyphs the run produced. Not the number of characters shaped.
    public int length() {
        return glyphIds.length;
    }

    /// Whether shaping produced nothing.
    public boolean isEmpty() {
        return glyphIds.length == 0;
    }

    /// The face's own index for the glyph at `index` — not a character.
    public int glyphId(int index) {
        return glyphIds[index];
    }

    /// Which character this glyph came from, as an index into the shaped text.
    public int cluster(int index) {
        return clusters[index];
    }

    /// How far the pen moves after this glyph, across.
    public int xAdvance(int index) {
        return xAdvances[index];
    }

    /// How far the pen moves after this glyph, down.
    public int yAdvance(int index) {
        return yAdvances[index];
    }

    /// How far this glyph is drawn from the pen, across, without moving it.
    public int xOffset(int index) {
        return xOffsets[index];
    }

    /// How far this glyph is drawn from the pen, down, without moving it.
    public int yOffset(int index) {
        return yOffsets[index];
    }

    /// The sum of the advances — how wide the run is, in design units.
    ///
    /// The sum of the *advances* and not the extent of the ink: a trailing space
    /// moves the pen and draws nothing, and a layout pass has to account for it.
    public long totalXAdvance() {
        var total = 0L;
        for (var advance : xAdvances) {
            total += advance;
        }
        return total;
    }

    @Override
    public String toString() {
        return "ShapedRun[" + glyphIds.length + " glyphs, " + totalXAdvance() + " units]";
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ShapedRun run
                && Arrays.equals(glyphIds, run.glyphIds)
                && Arrays.equals(clusters, run.clusters)
                && Arrays.equals(xAdvances, run.xAdvances)
                && Arrays.equals(yAdvances, run.yAdvances)
                && Arrays.equals(xOffsets, run.xOffsets)
                && Arrays.equals(yOffsets, run.yOffsets);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(glyphIds) * 31 + Arrays.hashCode(clusters);
    }
}
