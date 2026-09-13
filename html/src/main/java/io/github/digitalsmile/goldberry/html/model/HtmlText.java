package io.github.digitalsmile.goldberry.html.model;

import java.util.List;
import java.util.Objects;

/// The words between two tags.
///
/// Entities are already resolved: `AT&amp;T` is one of these holding `AT&T`, for the
/// reason the Markdown model gives — the model holds what a person reads, and the
/// one place that decision can be made once is on the way in
/// ([io.github.digitalsmile.goldberry.content.entity.Entities]).
///
/// **Whitespace is not.** What the author typed is here exactly as typed, newlines
/// and runs of spaces included, because whether it matters depends on where it is:
/// inside a `pre` every space is content, and everywhere else a renderer collapses
/// it. A model that collapsed early would have thrown away the one thing `pre`
/// needs, and `html-view` is where the collapsing happens — in
/// [io.github.digitalsmile.goldberry.content.inline.Words], which every paragraph of
/// either content half goes through.
///
/// @param text the characters, entities resolved
public record HtmlText(String text) implements HtmlNode {

    public HtmlText {
        Objects.requireNonNull(text, "text");
    }

    /// Nothing. Text is a leaf.
    @Override
    public List<HtmlNode> children() {
        return List.of();
    }

    /// Whether this is nothing but whitespace — the text between two block tags,
    /// which a renderer outside a `pre` has nothing to draw for.
    public boolean isBlank() {
        return text.isBlank();
    }
}
