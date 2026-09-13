package io.github.digitalsmile.goldberry.markdown.model;

import java.util.List;
import java.util.Objects;

/// One row of a table.
///
/// @param header whether this is the head row
/// @param cells the cells, left to right
public record TableRow(boolean header, List<TableCell> cells) implements Block {

    public TableRow {
        cells = List.copyOf(Objects.requireNonNull(cells, "cells"));
    }

    @Override
    public List<TableCell> children() {
        return cells;
    }
}
