package dev.goldberry.text.mark;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/// A stretch of text under one unchanging set of kinds: where a line is cut to
/// be shaped and drawn piece by piece.
///
/// ```java
/// for (var run : MarkRun.of(line.length(), marks)) {
///     var style = run.isPlain() ? plain : styles.of(run.kinds());
///     …
/// }
/// ```
///
/// Read more: [Selection and editing](https://goldberry.dev/docs/guide/text.html#selection-and-editing).
///
/// @param start the first char
/// @param end   one past the last
/// @param kinds the kinds over every char of it, and none for plain text
public record MarkRun(int start, int end, Set<MarkKind> kinds) {

    public MarkRun {
        Objects.requireNonNull(kinds, "kinds");
        kinds = Set.copyOf(kinds);
    }

    /// Whether no mark is over it.
    public boolean isPlain() {
        return kinds.isEmpty();
    }

    /// `[0, length)` cut wherever a mark starts or ends, each run with the kinds
    /// over it, in order and covering every char. One plain run for a text with
    /// no marks; none for an empty text.
    ///
    /// @param marks marks inside `[0, length)`, in any order
    public static List<MarkRun> of(int length, List<Mark> marks) {
        Objects.requireNonNull(marks, "marks");
        if (length == 0) {
            return List.of();
        }
        if (marks.isEmpty()) {
            return List.of(new MarkRun(0, length, Set.of()));
        }
        var cuts = new TreeSet<Integer>();
        cuts.add(0);
        cuts.add(length);
        for (var mark : marks) {
            cuts.add(Math.clamp(mark.start(), 0, length));
            cuts.add(Math.clamp(mark.end(), 0, length));
        }
        var runs = new ArrayList<MarkRun>(cuts.size());
        Integer from = null;
        for (var at : cuts) {
            if (from != null) {
                var kinds = new LinkedHashSet<MarkKind>();
                for (var mark : marks) {
                    if (mark.covers(from)) {
                        kinds.add(mark.kind());
                    }
                }
                runs.add(new MarkRun(from, at, kinds));
            }
            from = at;
        }
        return List.copyOf(runs);
    }
}
