package io.github.digitalsmile.goldberry.html.model;

import java.util.List;
import java.util.Objects;

/// `<!-- a note to whoever edits this -->`.
///
/// **Kept rather than dropped**, although nothing draws it, and that is a deliberate
/// asymmetry with the doctype: a comment is content the author wrote and tools read
/// it — a build stamp, a section marker, a `<!-- more -->` fold — so a model that
/// swallowed comments would make an application parse the source a second time to
/// find them. `html-view` skips them, which is what a renderer does.
///
/// @param text what is between the delimiters, as written — entities are **not**
///        resolved, because a comment is not read as markup and `&amp;` inside one
///        is the five characters somebody typed
public record Comment(String text) implements HtmlNode {

    public Comment {
        Objects.requireNonNull(text, "text");
    }

    /// Nothing. A comment is a leaf.
    @Override
    public List<HtmlNode> children() {
        return List.of();
    }
}
