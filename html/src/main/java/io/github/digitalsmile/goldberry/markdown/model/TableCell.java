package io.github.digitalsmile.goldberry.markdown.model;

import java.util.List;
import java.util.Objects;

/// One cell of a table.
///
/// @param header whether it belongs to the head row rather than the body
/// @param alignment what the delimiter row asked for. The same for every cell in a
///        column, which is why a renderer can read it off any of them
/// @param content the cell's words
public record TableCell(boolean header, CellAlignment alignment, List<Inline> content) implements Block {

    public TableCell {
        Objects.requireNonNull(alignment, "alignment");
        content = List.copyOf(Objects.requireNonNull(content, "content"));
    }

    @Override
    public List<Inline> children() {
        return content;
    }

    /// The cell's words with every mark dropped.
    public String text() {
        return Inlines.text(content);
    }
}
