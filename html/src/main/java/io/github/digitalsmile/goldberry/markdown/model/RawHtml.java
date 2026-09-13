package io.github.digitalsmile.goldberry.markdown.model;

import java.util.List;
import java.util.Objects;

/// Inline markup the author wrote — `<kbd>K</kbd>`.
///
/// Kept for [HtmlBlock]'s reason, and rendered by a widget the same honest way: as
/// the text it is.
///
/// @param html the markup, exactly as written
public record RawHtml(String html) implements Inline {

    public RawHtml {
        Objects.requireNonNull(html, "html");
    }

    @Override
    public List<Inline> children() {
        return List.of();
    }
}
