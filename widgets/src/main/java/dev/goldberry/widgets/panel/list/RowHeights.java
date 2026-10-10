package dev.goldberry.widgets.panel.list;

/// How a virtualized [ListView] learns the height of rows that are not all one
/// height: it **measures** the rows it builds, and **estimates** the ones it has
/// not built yet.
///
/// ```java
/// ListView.of(messages).virtualized(RowHeights.estimating(64))
/// ```
///
/// A message is one line or twelve, a picture or an album, and no row's height
/// can be known without laying it out. So the list keeps two things: the height
/// each row came out as the last time it was built, by the row's identity, and
/// this one number for every row it has never built. The spacers standing in for
/// the unbuilt rows are the sum of the two, and the scroll extent converges on
/// the true one as the reader moves through the model and more rows are measured.
///
/// **The estimate is a guess and is allowed to be wrong.** When a row turns out
/// taller or shorter than it was counted, everything below it moves by the
/// difference; when that row is above the line the reader is looking at, the
/// list moves the enclosing `scroll` by the same amount, so the line stays where
/// it was drawn. A good estimate — the commonest row's height — makes the
/// scrollbar honest sooner, and nothing more.
///
/// The measured heights are keyed by the item's identity rather than by its
/// index, so a page of history prepended above keeps every height already
/// measured. A change in the list's width makes them stale — a paragraph wraps
/// differently — and they are forgotten, apart from the rows on screen, which
/// are measured again on the frame the width changed.
///
/// A row's height here is its **border box**. A stylesheet that spaces rows
/// with a `margin` or a `gap` on `list` puts space between them that no row
/// reports, and the spacers come out short by it; pad the row instead.
///
/// @param estimate the height counted for a row that has not been built yet, in
///                 logical pixels; positive
///
/// Read more: [Collections](https://goldberry.dev/docs/components/collections.html#list).
public record RowHeights(double estimate) {

    public RowHeights {
        if (!(estimate > 0) || Double.isInfinite(estimate)) {
            throw new IllegalArgumentException(
                    "a row-height estimate must be a positive number of logical pixels; got " + estimate);
        }
    }

    /// Rows measured as they are built, and counted at `estimate` until then.
    ///
    /// @param estimate the height of a typical row, in logical pixels
    public static RowHeights estimating(double estimate) {
        return new RowHeights(estimate);
    }
}
