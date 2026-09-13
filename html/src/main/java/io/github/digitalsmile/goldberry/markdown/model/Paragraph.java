package io.github.digitalsmile.goldberry.markdown.model;

import java.util.List;
import java.util.Objects;

/// A run of prose.
///
/// @param content the words and marks in it
public record Paragraph(List<Inline> content) implements Block {

    public Paragraph {
        content = List.copyOf(Objects.requireNonNull(content, "content"));
    }

    @Override
    public List<Inline> children() {
        return content;
    }

    /// The paragraph's words with every mark dropped.
    public String text() {
        return Inlines.text(content);
    }
}
