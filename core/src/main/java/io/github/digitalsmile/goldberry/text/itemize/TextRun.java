package io.github.digitalsmile.goldberry.text.itemize;

/// A stretch of one string that is shaped in one face.
///
/// Offsets are into the string the itemizer was handed — `[start, end)`, the
/// same half-open convention every other range in the toolkit uses, so
/// `text.subSequence(start, end)` is the run.
///
/// @param start the first character, inclusive
/// @param end   one past the last
/// @param slot  which face shapes it
public record TextRun(int start, int end, Slot slot) {

    public TextRun {
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("a run cannot end before it starts: " + start + ".." + end);
        }
    }

    /// How many characters the run covers. Not how many glyphs it shapes into,
    /// and not how many code points it holds.
    public int length() {
        return end - start;
    }
}
