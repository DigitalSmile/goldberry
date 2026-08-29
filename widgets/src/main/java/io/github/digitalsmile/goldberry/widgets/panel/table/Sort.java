package io.github.digitalsmile.goldberry.widgets.panel.table;

import java.util.Objects;

/// Which column a [Table] is sorted by, and which way — §10's "sorting is the
/// application's".
///
/// A value the application holds and hands down, exactly as it holds the
/// selection: the table draws the caret for whatever it is given and reports what
/// a click on a header would mean. It sorts nothing itself ([ADR-0063]).
///
/// **Null is a real answer** and means "in the order the model is in", which is
/// not the same as sorted ascending by anything — a table showing rows in the
/// order a query returned them has no sorted column, and drawing a caret on one
/// would be a claim nobody made.
///
/// @param column     the [Column#key] the rows are ordered by
/// @param descending which way
public record Sort(String column, boolean descending) {

    public Sort {
        Objects.requireNonNull(column, "column");
    }

    /// Ascending by `column` — where a first click on a header lands.
    public static Sort by(String column) {
        return new Sort(column, false);
    }

    /// What a click on `column`'s header asks for next.
    ///
    /// The three-state cycle every desktop table has, with the third state left
    /// out: a click on **another** column sorts by that one ascending, and a
    /// click on the one already sorted turns it round. Returning to "unsorted" on
    /// a third click is the state most tables do not offer and the one whose
    /// meaning nobody agrees on — an application that wants it can return null
    /// from its own handler, because this is a suggestion rather than a command.
    ///
    /// @param current what the table is sorted by now, or null for nothing
    public static Sort next(Sort current, String column) {
        Objects.requireNonNull(column, "column");
        return current != null && column.equals(current.column())
                ? new Sort(column, !current.descending())
                : Sort.by(column);
    }

    /// Whether this sort is the one on `column`.
    public boolean on(String column) {
        return this.column.equals(column);
    }
}
