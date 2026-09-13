package io.github.digitalsmile.goldberry.markdown.model;

import java.util.List;
import java.util.Objects;

/// `[[target]]` or `[[target|label]]`. Needs
/// [io.github.digitalsmile.goldberry.markdown.MarkdownExtension#WIKI_LINKS].
///
/// A node of its own rather than a [Link] with an odd href, because a wiki link's
/// target is **not a URL**: it names something in a collection the application owns,
/// and only the application can say whether `Meeting notes` exists, where it is, or
/// what to do when it does not. A note-taking application is the reason this
/// extension exists, and flattening it into a link would take away exactly the
/// distinction it came for.
///
/// @param target what it points at, as written
/// @param content the label — the target's own text when the author wrote no `|`
public record WikiLink(String target, List<Inline> content) implements Inline {

    public WikiLink {
        Objects.requireNonNull(target, "target");
        content = List.copyOf(Objects.requireNonNull(content, "content"));
    }

    @Override
    public List<Inline> children() {
        return content;
    }

    /// The label's words with every mark dropped.
    public String text() {
        return Inlines.text(content);
    }
}
