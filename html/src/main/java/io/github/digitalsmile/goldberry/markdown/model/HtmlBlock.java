package io.github.digitalsmile.goldberry.markdown.model;

import java.util.List;
import java.util.Objects;

/// Raw HTML the author wrote in the document, kept verbatim.
///
/// Present in the model because dropping it silently would lose content, and
/// because the HTML output has somewhere obvious to put it. A **widget** renderer
/// does not have an obvious place: there is no engine under it in this module, so
/// `markdown-view` shows the markup as text rather than pretending to have
/// rendered it. That is the honest half of ADR-0295 and the reason `html-view`
/// exists as a gap rather than as a lie.
///
/// An application that does not want raw HTML at all parses without it — see
/// [io.github.digitalsmile.goldberry.markdown.MarkdownExtension#NO_HTML].
///
/// @param html the markup, exactly as written
public record HtmlBlock(String html) implements Block {

    public HtmlBlock {
        Objects.requireNonNull(html, "html");
    }

    @Override
    public List<MarkdownNode> children() {
        return List.of();
    }
}
