package dev.goldberry.text.itemize;

import org.jspecify.annotations.Nullable;

/// What the itemizer asks whoever holds the fonts when it splits text by the
/// faces that have its characters: which face has a glyph for a cluster.
///
/// The itemizer reads the text and the holder of the fonts reads the faces.
/// [Itemizer#byCoverage] decides where a run starts and ends, keeping clusters
/// whole and runs of one script together, and asks this which faces can draw
/// what.
///
/// Read more: [Emoji](https://goldberry.dev/docs/guide/text.html#emoji).
///
/// @param <F> what a face is to the caller: a font, or a name in a test
public interface FaceChoice<F> {

    /// Whether `face` has a glyph for every character of `[start, end)` that
    /// needs one.
    boolean covers(F face, String text, int start, int end);

    /// The face that draws the cluster `[start, end)` when the base face does
    /// not, or null when none can.
    @Nullable
    F fallback(String text, int start, int end);
}
