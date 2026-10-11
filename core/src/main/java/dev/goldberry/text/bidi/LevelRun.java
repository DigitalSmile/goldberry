package dev.goldberry.text.bidi;

/// A stretch of text at one embedding level: one direction, shaped as one run
/// and placed as one block.
///
/// @param start first character, as an offset into the text
/// @param end   one past the last
/// @param level its embedding level; odd runs right to left
public record LevelRun(int start, int end, int level) {

    public LevelRun {
        if (start > end) {
            throw new IllegalArgumentException("a run cannot end before it starts: " + start + ".." + end);
        }
        if (level < 0) {
            throw new IllegalArgumentException("an embedding level is not negative: " + level);
        }
    }

    /// Whether the run reads right to left.
    public boolean isRightToLeft() {
        return (level & 1) == 1;
    }
}
