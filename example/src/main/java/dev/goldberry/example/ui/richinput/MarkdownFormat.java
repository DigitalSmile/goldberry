package dev.goldberry.example.ui.richinput;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import dev.goldberry.markdown.marked.MarkedMarkdown;
import dev.goldberry.text.mark.MarkedFormat;
import dev.goldberry.text.mark.MarkedText;

/// A clipboard format of the showcase's own: marked text as `text/markdown`,
/// written and read by [MarkedMarkdown]. What an application installs when the
/// other side of its clipboard is a Markdown editor.
///
/// ```java
/// new RichInput(draft, onChange).clipboardFormats(List.of(new MarkdownFormat(), MarkedHtml.format()));
/// ```
public record MarkdownFormat() implements MarkedFormat {

    /// The type it is offered under.
    public static final String MIME = "text/markdown";

    @Override
    public String mime() {
        return MIME;
    }

    @Override
    public byte[] write(MarkedText text) {
        return MarkedMarkdown.write(text).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public Optional<MarkedText> read(byte[] bytes) {
        try {
            return Optional.of(MarkedMarkdown.read(new String(bytes, StandardCharsets.UTF_8)));
        } catch (RuntimeException e) {
            // Bytes from another process: an unreadable paste falls through to
            // the next format, not into the field as an exception.
            return Optional.empty();
        }
    }
}
