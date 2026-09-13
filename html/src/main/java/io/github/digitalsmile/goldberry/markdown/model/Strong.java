package io.github.digitalsmile.goldberry.markdown.model;

import java.util.List;
import java.util.Objects;

/// `**strong**` — bold, by convention.
public record Strong(List<Inline> content) implements Inline {

    public Strong {
        content = List.copyOf(Objects.requireNonNull(content, "content"));
    }

    @Override
    public List<Inline> children() {
        return content;
    }
}
