package dev.goldberry.text.bidi;

import java.text.Bidi;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.layout.Direction;

/// The embedding levels of a text under a [Directionality], as the Unicode
/// bidirectional algorithm resolves them.
///
/// ```java
/// var levels = BidiLevels.of("Total: 120 شيكل", Directionality.LTR);
/// levels.levelAt(0);                  // 0: left to right
/// levels.levelAt(12);                 // 1: right to left
/// levels.visualRuns(0, 15);           // the runs in the order a line places them
/// ```
///
/// Each hard line, the text between two newlines, is a bidi paragraph of its
/// own with its own base level; a newline takes the level of the paragraph it
/// ends. The levels come from `java.text.Bidi`, one paragraph at a time.
///
/// [#isLeftToRight()] is the common case: every character at level 0 under a
/// left-to-right base, nothing to reorder. Text with no right-to-left character
/// under a left-to-right base is answered that way without running the
/// algorithm at all, which is what keeps a paragraph of prose costing what it
/// cost before bidi existed.
///
/// Immutable.
public final class BidiLevels {

    private final int length;

    /// Each character's level, or null when every one is 0.
    private final byte @Nullable [] levels;

    /// Where each hard line starts, and its base level, index for index. Null
    /// exactly when [#levels] is.
    private final int @Nullable [] paragraphStarts;

    private final byte @Nullable [] paragraphLevels;

    /// The whitespace rule L1 resets at the end of a line, read once per
    /// character: [Character#getDirectionality] is a table lookup, and a line
    /// asks for its runs on every paint.
    private final boolean @Nullable [] resetsAtLineEnd;

    /// The tabs and paragraph separators L1 resets, with the whitespace before
    /// them.
    private final boolean @Nullable [] separators;

    private BidiLevels(
            int length,
            byte @Nullable [] levels,
            int @Nullable [] paragraphStarts,
            byte @Nullable [] paragraphLevels,
            boolean @Nullable [] resetsAtLineEnd,
            boolean @Nullable [] separators) {
        this.length = length;
        this.levels = levels;
        this.paragraphStarts = paragraphStarts;
        this.paragraphLevels = paragraphLevels;
        this.resetsAtLineEnd = resetsAtLineEnd;
        this.separators = separators;
    }

    /// The levels of `text` under `directionality`.
    public static BidiLevels of(String text, Directionality directionality) {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(directionality, "directionality");
        var rtl = directionality.direction() == Direction.RTL;
        var mixed = Bidi.requiresBidi(text.toCharArray(), 0, text.length());
        var plain = switch (directionality.source()) {
            // Forced one way: right to left needs every level odd, left to
            // right needs none.
            case OVERRIDE -> !rtl;
            // With no right-to-left character there is no right-to-left run,
            // and a left-to-right base puts every neutral at 0.
            case DIRECTION -> !rtl && !mixed;
            // No right-to-left character means the first strong one, if any,
            // is left to right; a paragraph with none takes the fallback.
            case FIRST_STRONG -> !mixed && (!rtl || everyParagraphHasStrongLeft(text));
        };
        if (plain) {
            return new BidiLevels(text.length(), null, null, null, null, null);
        }
        return analysed(text, directionality);
    }

    /// Whether every hard line of `text`, which has no right-to-left
    /// character, has a left-to-right one.
    private static boolean everyParagraphHasStrongLeft(String text) {
        var found = false;
        for (var i = 0; i < text.length(); i++) {
            var c = text.charAt(i);
            if (c == '\n') {
                if (!found) {
                    return false;
                }
                found = false;
            } else if (Character.getDirectionality(c) == Character.DIRECTIONALITY_LEFT_TO_RIGHT) {
                found = true;
            }
        }
        return found;
    }

    private static BidiLevels analysed(String text, Directionality directionality) {
        var length = text.length();
        var levels = new byte[length];
        var starts = new ArrayList<Integer>();
        var bases = new ArrayList<Byte>();
        var rtl = directionality.direction() == Direction.RTL;
        var start = 0;
        while (true) {
            var newline = text.indexOf('\n', start);
            var end = newline < 0 ? length : newline;
            var base = switch (directionality.source()) {
                case OVERRIDE -> {
                    var level = (byte) (rtl ? 1 : 0);
                    for (var i = start; i < end; i++) {
                        levels[i] = level;
                    }
                    yield level;
                }
                case DIRECTION, FIRST_STRONG -> resolve(text, start, end, flags(directionality), levels);
            };
            starts.add(start);
            bases.add(base);
            if (newline < 0) {
                break;
            }
            levels[newline] = base;
            start = newline + 1;
        }

        var resets = new boolean[length];
        var separators = new boolean[length];
        for (var i = 0; i < length; i++) {
            var kind = Character.getDirectionality(text.charAt(i));
            separators[i] = kind == Character.DIRECTIONALITY_SEGMENT_SEPARATOR
                    || kind == Character.DIRECTIONALITY_PARAGRAPH_SEPARATOR;
            resets[i] = separators[i]
                    || kind == Character.DIRECTIONALITY_WHITESPACE
                    || kind == Character.DIRECTIONALITY_BOUNDARY_NEUTRAL
                    || isIsolateControl(kind);
        }
        var startArray = starts.stream().mapToInt(Integer::intValue).toArray();
        var baseArray = new byte[bases.size()];
        for (var k = 0; k < baseArray.length; k++) {
            baseArray[k] = bases.get(k);
        }
        return new BidiLevels(length, levels, startArray, baseArray, resets, separators);
    }

    private static boolean isIsolateControl(byte kind) {
        return kind == Character.DIRECTIONALITY_LEFT_TO_RIGHT_ISOLATE
                || kind == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ISOLATE
                || kind == Character.DIRECTIONALITY_FIRST_STRONG_ISOLATE
                || kind == Character.DIRECTIONALITY_POP_DIRECTIONAL_ISOLATE;
    }

    /// `java.text.Bidi`'s flags for a paragraph under `directionality`.
    private static int flags(Directionality directionality) {
        var rtl = directionality.direction() == Direction.RTL;
        return switch (directionality.source()) {
            case DIRECTION, OVERRIDE -> rtl ? Bidi.DIRECTION_RIGHT_TO_LEFT : Bidi.DIRECTION_LEFT_TO_RIGHT;
            case FIRST_STRONG -> rtl ? Bidi.DIRECTION_DEFAULT_RIGHT_TO_LEFT : Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT;
        };
    }

    /// Writes the levels of `[start, end)`, one paragraph, into `levels`, and
    /// answers its base level.
    private static byte resolve(String text, int start, int end, int flags, byte[] levels) {
        var bidi = new Bidi(text.substring(start, end), flags);
        for (var r = 0; r < bidi.getRunCount(); r++) {
            var level = (byte) bidi.getRunLevel(r);
            for (var i = bidi.getRunStart(r); i < bidi.getRunLimit(r); i++) {
                levels[start + i] = level;
            }
        }
        return (byte) bidi.getBaseLevel();
    }

    /// Whether every character is at level 0 under a left-to-right base: there
    /// is nothing to reorder and no run to shape right to left.
    public boolean isLeftToRight() {
        return levels == null;
    }

    /// The length of the text these are the levels of.
    public int length() {
        return length;
    }

    /// The level of the character at `offset`.
    ///
    /// @throws IndexOutOfBoundsException if `offset` is not a character of the text
    public int levelAt(int offset) {
        Objects.checkIndex(offset, length);
        var held = levels;
        return held == null ? 0 : held[offset];
    }

    /// The base direction of the paragraph `offset` is in. An offset on a
    /// newline is in the paragraph the newline ends, and the end of the text in
    /// the last one.
    ///
    /// @throws IndexOutOfBoundsException if `offset` is outside `[0, length]`
    public Direction directionAt(int offset) {
        return (baseLevelAt(offset) & 1) == 1 ? Direction.RTL : Direction.LTR;
    }

    /// The base level of the paragraph `offset` is in.
    private int baseLevelAt(int offset) {
        Objects.checkIndex(offset, length + 1);
        var starts = paragraphStarts;
        var bases = paragraphLevels;
        if (starts == null || bases == null) {
            return 0;
        }
        var k = 0;
        while (k + 1 < starts.length && starts[k + 1] <= offset) {
            k++;
        }
        return bases[k];
    }

    /// The runs of `[start, end)` in logical order, each at one level: what is
    /// shaped as one piece.
    ///
    /// @throws IndexOutOfBoundsException if the range is not within the text
    public List<LevelRun> runs(int start, int end) {
        Objects.checkFromToIndex(start, end, length);
        var held = levels;
        if (held == null) {
            return start == end ? List.of() : List.of(new LevelRun(start, end, 0));
        }
        var runs = new ArrayList<LevelRun>();
        var from = start;
        for (var i = start + 1; i <= end; i++) {
            if (i == end || held[i] != held[from]) {
                runs.add(new LevelRun(from, i, held[from]));
                from = i;
            }
        }
        return List.copyOf(runs);
    }

    /// The runs of one line, `[start, end)`, in the order they are placed from
    /// left to right.
    ///
    /// The line must lie within one paragraph. Rule L1 first: whitespace at the
    /// end of the line, and a tab with the whitespace before it, take the
    /// paragraph's level, so a trailing space sits at the line's end rather
    /// than inside the run before it. Then rule L2: from the highest level down
    /// to the lowest odd one, every stretch at that level or above is reversed.
    /// A run's own characters are not reversed here; a right-to-left run is
    /// drawn right to left by its shaping.
    ///
    /// @throws IndexOutOfBoundsException if the range is not within the text
    public List<LevelRun> visualRuns(int start, int end) {
        Objects.checkFromToIndex(start, end, length);
        var held = levels;
        var resets = resetsAtLineEnd;
        var tabs = separators;
        if (held == null || resets == null || tabs == null) {
            return start == end ? List.of() : List.of(new LevelRun(start, end, 0));
        }
        if (start == end) {
            return List.of();
        }

        var base = (byte) baseLevelAt(start);
        var line = new byte[end - start];
        System.arraycopy(held, start, line, 0, line.length);
        // L1, from the end: trailing whitespace, then each separator and the
        // whitespace before it.
        var trailing = true;
        for (var i = end - 1; i >= start; i--) {
            if (tabs[i]) {
                line[i - start] = base;
                trailing = true;
            } else if (trailing && resets[i]) {
                line[i - start] = base;
            } else {
                trailing = false;
            }
        }

        var runs = new ArrayList<LevelRun>();
        var from = 0;
        var highest = 0;
        var lowestOdd = Integer.MAX_VALUE;
        for (var i = 1; i <= line.length; i++) {
            if (i == line.length || line[i] != line[from]) {
                var level = line[from];
                runs.add(new LevelRun(start + from, start + i, level));
                highest = Math.max(highest, level);
                if ((level & 1) == 1) {
                    lowestOdd = Math.min(lowestOdd, level);
                }
                from = i;
            }
        }
        // L2.
        for (var level = highest; level >= lowestOdd && level > 0; level--) {
            var i = 0;
            while (i < runs.size()) {
                if (runs.get(i).level() < level) {
                    i++;
                    continue;
                }
                var j = i;
                while (j < runs.size() && runs.get(j).level() >= level) {
                    j++;
                }
                Collections.reverse(runs.subList(i, j));
                i = j;
            }
        }
        return List.copyOf(runs);
    }

    @Override
    public String toString() {
        return levels == null ? "BidiLevels[ltr, " + length + "]" : "BidiLevels[" + length + " characters]";
    }
}
