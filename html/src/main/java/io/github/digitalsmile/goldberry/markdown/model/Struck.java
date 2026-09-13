package io.github.digitalsmile.goldberry.markdown.model;

import java.util.List;
import java.util.Objects;

/// `~~struck through~~`. Needs
/// [io.github.digitalsmile.goldberry.markdown.MarkdownExtension#STRIKETHROUGH].
public record Struck(List<Inline> content) implements Inline {

    public Struck {
        content = List.copyOf(Objects.requireNonNull(content, "content"));
    }

    @Override
    public List<Inline> children() {
        return content;
    }
}
