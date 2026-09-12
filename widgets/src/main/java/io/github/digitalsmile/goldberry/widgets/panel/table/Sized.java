package io.github.digitalsmile.goldberry.widgets.panel.table;

import io.github.digitalsmile.goldberry.layout.Length;
import io.github.digitalsmile.goldberry.paint.Box;

/// How a [Column]'s width reaches a box — the **one** copy of it.
///
/// A header and the cells under it have to come out the same width or the table
/// is not a table, and the cheapest way to guarantee that is for both to be sized
/// by the same three lines rather than by two that look alike
/// (ADR-0214).
///
/// It is flexbox's own arrangement and not a second one: a fixed column is a
/// width that will not shrink, and a weighted column is `flex-grow` over a zero
/// basis, which is what makes two weights of 1 and 3 split the leftovers one part
/// to three.
final class Sized {

    private Sized() {}

    static Box apply(Box box, Column<?> column) {
        if (column.fixed()) {
            return box.size(Length.points((float) column.width()), Length.UNDEFINED)
                    .shrink(0);
        }
        // A zero basis, so the share is of the *whole* leftover rather than of
        // whatever is left after each cell's own content has claimed its width --
        // otherwise a column of long strings would quietly outgrow its weight.
        return box.size(Length.points(0), Length.UNDEFINED).grow(column.width()).shrink(1);
    }
}
