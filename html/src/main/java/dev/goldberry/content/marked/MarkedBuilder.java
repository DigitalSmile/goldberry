package dev.goldberry.content.marked;

import java.util.ArrayList;
import java.util.List;

import dev.goldberry.text.mark.Mark;
import dev.goldberry.text.mark.MarkKind;
import dev.goldberry.text.mark.MarkedText;

/// Text and marks collected while a document is walked, made a [MarkedText] at
/// the end.
///
/// ```java
/// var out = new MarkedBuilder();
/// var start = out.length();
/// out.append("bold");
/// out.mark(start, MarkKind.Style.BOLD);
/// out.build();                             // "bold", bold
/// ```
///
/// A walk closes an inner element before its outer one, so marks arrive inner
/// first. They are handed to [MarkedText] outermost first instead, so that the
/// inner kind wins a conflict: code inside bold is code.
public final class MarkedBuilder {

    private final StringBuilder text = new StringBuilder();

    private final List<Pending> marks = new ArrayList<>();

    private int depth;

    private record Pending(int start, int end, MarkKind kind, int depth, int order) {}

    /// Where the next char goes.
    public int length() {
        return text.length();
    }

    /// The text so far.
    public CharSequence text() {
        return text;
    }

    /// `words` added.
    public MarkedBuilder append(CharSequence words) {
        text.append(words);
        return this;
    }

    /// One char added.
    public MarkedBuilder append(char c) {
        text.append(c);
        return this;
    }

    /// One level deeper: what is marked until [#leave()] is inside what is
    /// marked around it.
    public void enter() {
        depth++;
    }

    /// One level back out.
    public void leave() {
        depth--;
    }

    /// `kind` from `start` to here, at the current depth. Nothing for an empty
    /// range.
    public void mark(int start, MarkKind kind) {
        mark(start, text.length(), kind);
    }

    /// `kind` over `[start, end)`, at the current depth.
    public void mark(int start, int end, MarkKind kind) {
        if (start < end) {
            marks.add(new Pending(start, end, kind, depth, marks.size()));
        }
    }

    /// `kind` over `[start, end)`, outside every other mark: a span whose two
    /// ends were found at different depths, such as a spoiler's.
    public void outermost(int start, int end, MarkKind kind) {
        if (start < end) {
            marks.add(new Pending(start, end, kind, Integer.MIN_VALUE, marks.size()));
        }
    }

    /// Whether the text so far ends with a line break, or is empty: where a
    /// block may start without one.
    public boolean atLineStart() {
        return text.isEmpty() || text.charAt(text.length() - 1) == '\n';
    }

    /// The text and its marks, outer marks first.
    public MarkedText build() {
        var ordered = marks.stream()
                .sorted((a, b) -> a.depth() != b.depth()
                        ? Integer.compare(a.depth(), b.depth())
                        : Integer.compare(a.order(), b.order()))
                .map(pending -> new Mark(pending.start(), pending.end(), pending.kind()))
                .toList();
        return new MarkedText(text.toString(), ordered);
    }
}
