package io.github.digitalsmile.goldberry.markdown.model;

import java.util.List;
import java.util.Objects;

/// `_underlined_`, in the dialect where
/// [io.github.digitalsmile.goldberry.markdown.MarkdownExtension#UNDERLINE] has taken
/// `_` away from emphasis.
///
/// Not in CommonMark, and deliberately a node of its own rather than an
/// [Emphasis]: an application that turns the extension on means underline, and
/// collapsing the two would make the extension do nothing.
public record Underlined(List<Inline> content) implements Inline {

    public Underlined {
        content = List.copyOf(Objects.requireNonNull(content, "content"));
    }

    @Override
    public List<Inline> children() {
        return content;
    }
}
