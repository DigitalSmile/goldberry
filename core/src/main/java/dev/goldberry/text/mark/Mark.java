package dev.goldberry.text.mark;

import java.util.Comparator;
import java.util.Objects;

/// One [MarkKind] over a range of a text: `[start, end)` in `char` offsets.
///
/// ```java
/// var bold = new Mark(0, 4, MarkKind.Style.BOLD);   // the first four chars
/// ```
///
/// Offsets are UTF-16 code units, Java's `char`s: the units a `String` is
/// indexed by, and the ones a messenger's entities are counted in, so a mark
/// and an entity convert one to one. A mark is never empty.
///
/// Read more: [Selection and editing](https://goldberry.dev/docs/guide/text.html#selection-and-editing).
///
/// @param start the first char it covers
/// @param end   one past the last
/// @param kind  what it says about them
public record Mark(int start, int end, MarkKind kind) {

    /// The order of a [MarkedText]'s marks: by start, then end, then kind.
    public static final Comparator<Mark> ORDER =
            Comparator.comparingInt(Mark::start).thenComparingInt(Mark::end).thenComparing(Mark::kind, MarkKind.ORDER);

    /// A range of chars, `[start, end)`, with no kind: where a selection grows
    /// to so that it splits no atom.
    ///
    /// @param start the first char
    /// @param end   one past the last
    public record Span(int start, int end) {

        /// How many chars it holds.
        public int length() {
            return end - start;
        }
    }

    public Mark {
        Objects.requireNonNull(kind, "kind");
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException(
                    "a mark covers at least one char from offset 0 or later, and " + start + ".." + end + " does not");
        }
    }

    /// How many chars it covers.
    public int length() {
        return end - start;
    }

    /// Whether the char at `offset` is under it.
    public boolean covers(int offset) {
        return offset >= start && offset < end;
    }

    /// Whether it shares at least one char with `[from, to)`.
    public boolean overlaps(int from, int to) {
        return start < to && from < end;
    }

    /// Whether `offset` is strictly inside it: a caret there would split it.
    public boolean splitsAt(int offset) {
        return offset > start && offset < end;
    }

    /// This mark over `[from, to)`, its kind kept.
    public Mark at(int from, int to) {
        return new Mark(from, to, kind);
    }

    /// This mark moved by `delta` chars.
    public Mark shifted(int delta) {
        return new Mark(start + delta, end + delta, kind);
    }

    @Override
    public String toString() {
        return kind + "[" + start + ".." + end + ")";
    }
}
