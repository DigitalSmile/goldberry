package dev.goldberry.text.mark;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/// A text and its marks: words that are bold, a link, a mention.
///
/// ```java
/// var text = MarkedText.of("Ship it on Friday")
///         .mark(0, 4, MarkKind.Style.BOLD)
///         .mark(11, 17, new MarkKind.Link(URI.create("https://example.org")));
///
/// text.kindsAt(2);                                  // {BOLD}
/// text.replace(4, 4, MarkedText.of("ped"), text.kindsInheritedAt(4));
///                                                   // "Shipped it on Friday", "Shipped" bold
/// ```
///
/// A value, like the `TextEdit` it is edited beside: every operation returns a
/// new one. The marks are kept in **normal form**: sorted, inside the text,
/// never empty, one mark per stretch of one kind (two bold marks that touch are
/// one), and no two conflicting kinds ([MarkKind#conflict]) over one char. So
/// two values with the same formatting are equal, and an undo stack of these
/// knows when nothing changed.
///
/// The constructor puts any list into that form, adding the marks in order: a
/// later mark wins a conflict, except that formatting never cuts an atom.
///
/// ## How marks move when the text changes
///
/// [#replace] is the one edit; typing, deleting and pasting are all of it.
///
/// 1. Text removed from under a mark shrinks it, and a mark left empty goes.
/// 2. A mark that spans the replaced range is split around the new text.
/// 3. The new text gets the kinds it is told to inherit, and then its own
///    marks, which win a conflict. A typed letter inherits what
///    [#kindsInheritedAt] says; a paste inherits nothing.
/// 4. A replaced range that touches an atom is widened to all of it, so an atom
///    goes whole or not at all. Text inserted strictly inside an atom takes the
///    atom's mark away and leaves its text, since it is no longer what was
///    inserted. An editor's caret never stands there.
///
/// Read more: [Selection and editing](https://goldberry.dev/docs/guide/text.html#selection-and-editing).
///
/// @param text  the text
/// @param marks its marks, put into normal form
public record MarkedText(String text, List<Mark> marks) {

    /// No text and no marks.
    public static final MarkedText EMPTY = new MarkedText("", List.of());

    public MarkedText {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(marks, "marks");
        marks = MarkList.normal(text.length(), marks);
    }

    /// `text` with no marks.
    public static MarkedText of(String text) {
        return new MarkedText(text, List.of());
    }

    /// How many chars the text has.
    public int length() {
        return text.length();
    }

    /// Whether there is no text.
    public boolean isEmpty() {
        return text.isEmpty();
    }

    /// Whether nothing is marked.
    public boolean isPlain() {
        return marks.isEmpty();
    }

    // --- marking -------------------------------------------------------------

    /// `kind` over `[start, end)`. An empty range changes nothing.
    public MarkedText mark(int start, int end, MarkKind kind) {
        checkRange(start, end);
        Objects.requireNonNull(kind, "kind");
        if (start == end) {
            return this;
        }
        var next = new ArrayList<>(marks);
        next.add(new Mark(start, end, kind));
        return new MarkedText(text, next);
    }

    /// `kind` taken off `[start, end)`. An atom of that kind in the range goes
    /// whole, its text left plain.
    public MarkedText unmark(int start, int end, MarkKind kind) {
        checkRange(start, end);
        Objects.requireNonNull(kind, "kind");
        return new MarkedText(text, MarkList.without(text.length(), marks, start, end, kind));
    }

    /// Every kind but the atoms taken off `[start, end)`: "clear formatting",
    /// which leaves a mention a mention.
    public MarkedText clearFormatting(int start, int end) {
        checkRange(start, end);
        return new MarkedText(text, MarkList.withoutFormatting(text.length(), marks, start, end));
    }

    // --- asking --------------------------------------------------------------

    /// The kinds over the char at `offset`. Empty at the end of the text.
    public Set<MarkKind> kindsAt(int offset) {
        var kinds = new LinkedHashSet<MarkKind>();
        for (var mark : marks) {
            if (mark.covers(offset)) {
                kinds.add(mark.kind());
            }
        }
        return Set.copyOf(kinds);
    }

    /// The kinds a letter typed at `offset` takes: those of every mark the
    /// offset is strictly inside, and of every mark ending there that grows at
    /// its end. Never an atom: nothing typed becomes part of one.
    public Set<MarkKind> kindsInheritedAt(int offset) {
        var kinds = new LinkedHashSet<MarkKind>();
        for (var mark : marks) {
            if (mark.kind().isAtomic()) {
                continue;
            }
            if (mark.splitsAt(offset)
                    || (mark.end() == offset && mark.kind().behaviour().growsAtEnd())) {
                kinds.add(mark.kind());
            }
        }
        return Set.copyOf(kinds);
    }

    /// Whether every char of `[start, end)` outside an atom is under `kind`.
    ///
    /// Atoms are left out because formatting never covers one: a selection
    /// holding a mention is bold when everything around the mention is. False
    /// for an empty range, and for one that is all atoms.
    public boolean has(int start, int end, MarkKind kind) {
        checkRange(start, end);
        Objects.requireNonNull(kind, "kind");
        if (kind.isAtomic()) {
            return start < end
                    && marks.stream()
                            .anyMatch(mark -> mark.kind().equals(kind) && mark.start() <= start && end <= mark.end());
        }
        var pieces = MarkList.outsideAtoms(marks, start, end);
        if (pieces.isEmpty()) {
            return false;
        }
        for (var piece : pieces) {
            // One mark of a kind per stretch, so a piece is covered by one mark
            // or it is not covered.
            var covered = marks.stream()
                    .anyMatch(mark ->
                            mark.kind().equals(kind) && mark.start() <= piece.start() && piece.end() <= mark.end());
            if (!covered) {
                return false;
            }
        }
        return true;
    }

    /// The non-atomic kinds [#has] says cover all of `[start, end)`: what a
    /// formatting bar shows as on.
    public Set<MarkKind> kindsOver(int start, int end) {
        checkRange(start, end);
        var kinds = new LinkedHashSet<MarkKind>();
        for (var mark : marks) {
            if (!mark.kind().isAtomic() && mark.overlaps(start, end) && has(start, end, mark.kind())) {
                kinds.add(mark.kind());
            }
        }
        return Set.copyOf(kinds);
    }

    /// The atom `offset` is strictly inside, if any: a caret there would split
    /// it.
    public Optional<Mark> atomAt(int offset) {
        for (var mark : marks) {
            if (mark.kind().isAtomic() && mark.splitsAt(offset)) {
                return Optional.of(mark);
            }
        }
        return Optional.empty();
    }

    /// The smallest range holding `[start, end)` that splits no atom: rule 4 of
    /// the class comment. An empty range is its own answer.
    public Mark.Span atomicRange(int start, int end) {
        checkRange(start, end);
        var from = start;
        var to = end;
        if (start < end) {
            for (var mark : marks) {
                if (mark.kind().isAtomic() && mark.overlaps(start, end)) {
                    from = Math.min(from, mark.start());
                    to = Math.max(to, mark.end());
                }
            }
        }
        return new Mark.Span(from, to);
    }

    /// The marks reaching into `[start, end)`, cut to it and counted from
    /// `start`: what one line of the text is drawn with. Atoms are cut too,
    /// since a line can end inside one.
    public List<Mark> marksIn(int start, int end) {
        checkRange(start, end);
        var inside = new ArrayList<Mark>();
        for (var mark : marks) {
            if (mark.overlaps(start, end)) {
                inside.add(new Mark(
                        Math.max(mark.start(), start) - start, Math.min(mark.end(), end) - start, mark.kind()));
            }
        }
        return List.copyOf(inside);
    }

    // --- editing -------------------------------------------------------------

    /// `[start, end)` with its marks, as a text of its own: what is copied.
    ///
    /// An atom only partly inside the range is left out, its chars kept as plain
    /// text: half a mention is not a mention.
    public MarkedText slice(int start, int end) {
        checkRange(start, end);
        var inside = new ArrayList<Mark>();
        for (var mark : marks) {
            if (!mark.overlaps(start, end)) {
                continue;
            }
            if (mark.kind().isAtomic() && (mark.start() < start || mark.end() > end)) {
                continue;
            }
            inside.add(mark.at(Math.max(mark.start(), start) - start, Math.min(mark.end(), end) - start));
        }
        return new MarkedText(text.substring(start, end), inside);
    }

    /// `[start, end)` replaced by `inserted`, which also gets the kinds in
    /// `inherit`. See the class comment for how the marks move.
    ///
    /// @param inherit the kinds the new text takes from where it lands:
    ///                [#kindsInheritedAt] for a typed letter, nothing for a
    ///                paste. Atomic kinds in it are ignored
    public MarkedText replace(int start, int end, MarkedText inserted, Set<MarkKind> inherit) {
        checkRange(start, end);
        Objects.requireNonNull(inserted, "inserted");
        Objects.requireNonNull(inherit, "inherit");
        var range = atomicRange(start, end);
        var from = range.start();
        var to = range.end();
        var length = inserted.length();
        var delta = length - (to - from);
        var next = text.substring(0, from) + inserted.text + text.substring(to);

        var moved = new ArrayList<Mark>(marks.size() + inherit.size() + inserted.marks.size() + 1);
        for (var mark : marks) {
            if (mark.kind().isAtomic()) {
                if (mark.overlaps(from, to) || mark.splitsAt(from)) {
                    continue;
                }
                moved.add(mark.start() >= to ? mark.shifted(delta) : mark);
                continue;
            }
            if (mark.start() < from) {
                moved.add(mark.at(mark.start(), Math.min(mark.end(), from)));
            }
            if (mark.end() > to) {
                moved.add(mark.at(Math.max(mark.start(), to) + delta, mark.end() + delta));
            }
        }
        if (length > 0) {
            inherit.stream()
                    .filter(kind -> !kind.isAtomic())
                    .sorted(MarkKind.ORDER)
                    .forEach(kind -> moved.add(new Mark(from, from + length, kind)));
        }
        for (var mark : inserted.marks) {
            moved.add(mark.shifted(from));
        }
        return new MarkedText(next, moved);
    }

    private void checkRange(int start, int end) {
        if (start < 0 || end < start || end > text.length()) {
            throw new IndexOutOfBoundsException("a range of a " + text.length()
                    + "-char text runs forward inside it, and " + start + ".." + end + " does not");
        }
    }

    @Override
    public String toString() {
        return "MarkedText[" + text.length() + " chars, " + marks + "]";
    }
}
