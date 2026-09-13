package io.github.digitalsmile.goldberry.markdown.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// A `| a | b |` table — GitHub's extension, and off in plain CommonMark.
///
/// The head and the body are separate rather than one list with a flag on each row,
/// because every renderer treats them differently and a `rows().getFirst()` that
/// happened to be a body row is the bug that arrangement invites.
///
/// @param head the header rows. One, in every dialect md4c parses, but md4c reports
///        a count rather than promising one
/// @param body the data rows
public record Table(List<TableRow> head, List<TableRow> body) implements Block {

    public Table {
        head = List.copyOf(Objects.requireNonNull(head, "head"));
        body = List.copyOf(Objects.requireNonNull(body, "body"));
    }

    @Override
    public List<TableRow> children() {
        var rows = new ArrayList<TableRow>(head.size() + body.size());
        rows.addAll(head);
        rows.addAll(body);
        return List.copyOf(rows);
    }

    /// How many columns the table has, taken from its widest row.
    ///
    /// md4c reports a column count and also reports rows; they agree for every
    /// document it accepts, and this reads the rows because they are what a renderer
    /// actually draws.
    public int columns() {
        var columns = 0;
        for (var row : children()) {
            columns = Math.max(columns, row.cells().size());
        }
        return columns;
    }

    /// The alignment of column `index`, read off the head where there is one.
    public CellAlignment alignmentOf(int index) {
        for (var row : children()) {
            if (index < row.cells().size()) {
                return row.cells().get(index).alignment();
            }
        }
        return CellAlignment.DEFAULT;
    }
}
