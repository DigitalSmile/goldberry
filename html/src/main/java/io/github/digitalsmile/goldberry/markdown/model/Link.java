package io.github.digitalsmile.goldberry.markdown.model;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/// `[text](href "title")`, or a bare URL where the dialect allows one.
///
/// @param href where it points. Resolved as the author wrote it and **not**
///        validated: `mailto:`, a relative path and a fragment are all legitimate,
///        and deciding what may be followed is the application's — the same
///        division ADR-0291 drew for URL schemes
/// @param title the tooltip, or null when there is none
/// @param autolink whether the author wrote a bare URL rather than brackets, in
///        which case [#content()] is the URL again
/// @param content the link's words
public record Link(String href, @Nullable String title, boolean autolink, List<Inline> content) implements Inline {

    public Link {
        Objects.requireNonNull(href, "href");
        content = List.copyOf(Objects.requireNonNull(content, "content"));
    }

    /// An ordinary link.
    public Link(String href, List<Inline> content) {
        this(href, null, false, content);
    }

    @Override
    public List<Inline> children() {
        return content;
    }

    /// The link's words with every mark dropped.
    public String text() {
        return Inlines.text(content);
    }
}
