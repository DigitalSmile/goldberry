package io.github.digitalsmile.goldberry.markdown.model;

import java.util.List;
import java.util.Objects;

/// `*emphasis*` — italic, by convention.
public record Emphasis(List<Inline> content) implements Inline {

    public Emphasis {
        content = List.copyOf(Objects.requireNonNull(content, "content"));
    }

    @Override
    public List<Inline> children() {
        return content;
    }
}
