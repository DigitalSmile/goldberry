package io.github.digitalsmile.goldberry.natives.md4c;

import java.util.List;
import java.util.Objects;

import io.github.digitalsmile.goldberry.natives.md4c.enums.TextType;

/// A link's href, an image's title, a fence's info string — md4c's `MD_ATTRIBUTE`.
///
/// Not a `String`, and the reason is one line of Markdown:
///
/// ```markdown
/// [a](http://example.com/?x=1&amp;y=2)
/// ```
///
/// md4c breaks that URL into three parts — two ordinary runs with an
/// [TextType#ENTITY] between them — because whoever writes the output has to treat
/// them differently. An HTML renderer escapes the ordinary parts and passes the
/// entity through unchanged; flattening first and escaping afterwards would emit
/// `&amp;amp;`, and flattening first and *not* escaping would emit a URL that ends
/// the attribute early. A widget resolves the entity to `&` instead. Both answers
/// need the parts.
///
/// [#plain()] is for the callers that genuinely do not care.
///
/// @param parts the runs, in order; never empty for an attribute that is present
///        at all, because an absent one produces no attribute rather than an empty
///        one
public record MarkdownAttribute(List<Part> parts) {

    /// An attribute that was not there.
    public static final MarkdownAttribute NONE = new MarkdownAttribute(List.of());

    /// One run of an attribute.
    ///
    /// @param type what kind of run it is — only [TextType#NORMAL], [TextType#ENTITY]
    ///        and [TextType#NULLCHAR] appear here, which is md4c's own guarantee
    /// @param text the run's text, exactly as written
    public record Part(TextType type, String text) {

        public Part {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(text, "text");
        }
    }

    public MarkdownAttribute {
        parts = List.copyOf(Objects.requireNonNull(parts, "parts"));
    }

    /// Whether the attribute was present at all. A link with no title has none.
    public boolean isPresent() {
        return !parts.isEmpty();
    }

    /// Every part's text, joined and otherwise untouched — entities still spelled
    /// `&amp;`.
    ///
    /// For a caller that wants the bytes as the author wrote them: a fence's
    /// `info` string, a wiki link's target. A URL going into HTML wants [#parts()]
    /// instead.
    public String plain() {
        if (parts.size() == 1) {
            return parts.getFirst().text();
        }
        var out = new StringBuilder();
        for (var part : parts) {
            out.append(part.text());
        }
        return out.toString();
    }
}
