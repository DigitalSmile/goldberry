package dev.goldberry.text.mark;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/// The marks of one text being put into normal form, one mark at a time.
///
/// Normal form is what makes two [MarkedText]s with the same formatting equal:
///
/// - every mark lies inside the text and covers at least one char;
/// - two marks of one non-atomic kind neither overlap nor touch: they are one;
/// - no two conflicting kinds ([MarkKind#conflict]) share a char;
/// - the list is sorted by [Mark#ORDER].
///
/// A mark added later wins a conflict, with one exception: an atom is never cut
/// by formatting. Formatting added over an atom covers the text around it and
/// not the atom; an atom added over formatting cuts the formatting out of its
/// range, and an atom it overlaps goes whole.
///
/// Not thread-safe, and never shared: [MarkedText] makes one, fills it and keeps
/// only the list.
final class MarkList {

    private final int length;
    private final List<Mark> marks = new ArrayList<>();

    private MarkList(int length) {
        this.length = length;
    }

    /// `input` in normal form over a text of `length` chars, each mark added in
    /// order.
    static List<Mark> normal(int length, List<Mark> input) {
        var list = new MarkList(length);
        for (var mark : input) {
            list.add(mark);
        }
        return list.done();
    }

    /// `kind` taken off `[from, to)` of `input`. An atom of that kind it touches
    /// goes whole.
    static List<Mark> without(int length, List<Mark> input, int from, int to, MarkKind kind) {
        var list = new MarkList(length);
        list.marks.addAll(input);
        list.marks.removeIf(mark -> mark.kind().isAtomic() && mark.kind().equals(kind) && mark.overlaps(from, to));
        list.cut(from, to, kind::equals);
        return list.done();
    }

    /// Every non-atomic kind taken off `[from, to)` of `input`.
    static List<Mark> withoutFormatting(int length, List<Mark> input, int from, int to) {
        var list = new MarkList(length);
        list.marks.addAll(input);
        list.cut(from, to, kind -> !kind.isAtomic());
        return list.done();
    }

    private void add(Mark mark) {
        if (mark.end() > length) {
            throw new IllegalArgumentException(
                    "a mark lies inside its text: " + mark + " reaches past the end of " + length + " chars");
        }
        if (mark.kind().isAtomic()) {
            marks.removeIf(other -> other.kind().isAtomic() && other.overlaps(mark.start(), mark.end()));
            cut(mark.start(), mark.end(), _ -> true);
            marks.add(mark);
            return;
        }
        for (var piece : outsideAtoms(marks, mark.start(), mark.end())) {
            cut(piece.start(), piece.end(), other -> MarkKind.conflict(other, mark.kind()));
            merge(mark.at(piece.start(), piece.end()));
        }
    }

    /// The parts of `[from, to)` no atom covers, in order.
    static List<Mark.Span> outsideAtoms(List<Mark> marks, int from, int to) {
        var atoms = marks.stream()
                .filter(mark -> mark.kind().isAtomic() && mark.overlaps(from, to))
                .sorted(Mark.ORDER)
                .toList();
        var pieces = new ArrayList<Mark.Span>();
        var at = from;
        for (var atom : atoms) {
            if (atom.start() > at) {
                pieces.add(new Mark.Span(at, atom.start()));
            }
            at = Math.max(at, atom.end());
        }
        if (at < to) {
            pieces.add(new Mark.Span(at, to));
        }
        return pieces;
    }

    /// Takes `[from, to)` out of every non-atomic mark whose kind `matches`,
    /// keeping what lies on either side of it.
    private void cut(int from, int to, Predicate<MarkKind> matches) {
        var kept = new ArrayList<Mark>(marks.size() + 1);
        for (var mark : marks) {
            if (mark.kind().isAtomic() || !matches.test(mark.kind()) || !mark.overlaps(from, to)) {
                kept.add(mark);
                continue;
            }
            if (mark.start() < from) {
                kept.add(mark.at(mark.start(), from));
            }
            if (mark.end() > to) {
                kept.add(mark.at(to, mark.end()));
            }
        }
        marks.clear();
        marks.addAll(kept);
    }

    /// Adds `mark`, folding in every mark of its kind it overlaps or touches.
    ///
    /// One pass is enough: the list is already in normal form, so the marks of
    /// one kind are apart from each other, and only the ones touching `mark`
    /// itself can touch the union.
    private void merge(Mark mark) {
        var from = mark.start();
        var to = mark.end();
        var iterator = marks.iterator();
        while (iterator.hasNext()) {
            var other = iterator.next();
            if (other.kind().equals(mark.kind()) && other.start() <= to && from <= other.end()) {
                from = Math.min(from, other.start());
                to = Math.max(to, other.end());
                iterator.remove();
            }
        }
        marks.add(mark.at(from, to));
    }

    private List<Mark> done() {
        marks.sort(Mark.ORDER);
        return List.copyOf(marks);
    }
}
