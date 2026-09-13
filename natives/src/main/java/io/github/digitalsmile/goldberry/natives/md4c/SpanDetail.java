package io.github.digitalsmile.goldberry.natives.md4c;

import java.util.Objects;

/// What a span carries beside its type — md4c's `MD_SPAN_*_DETAIL` structs, as
/// values.
///
/// Read in C for [BlockDetail]'s reason. A span with nothing to say — emphasis,
/// strong, code, strikethrough — carries [#NONE].
public sealed interface SpanDetail {

    /// The detail of a span that has none.
    SpanDetail NONE = new None();

    /// A span whose type is all there is to know.
    record None() implements SpanDetail {}

    /// `[text](href "title")`.
    ///
    /// @param href where it points, in parts — see [MarkdownAttribute]
    /// @param title the tooltip, or [MarkdownAttribute#NONE]
    /// @param autolink whether the author wrote a bare URL rather than brackets, in
    ///        which case the link's text is the URL
    record Link(MarkdownAttribute href, MarkdownAttribute title, boolean autolink) implements SpanDetail {

        public Link {
            Objects.requireNonNull(href, "href");
            Objects.requireNonNull(title, "title");
        }
    }

    /// `![alt](src "title")`. The alt text arrives as the text events **inside** the
    /// span, because it is Markdown in its own right.
    record Image(MarkdownAttribute src, MarkdownAttribute title) implements SpanDetail {

        public Image {
            Objects.requireNonNull(src, "src");
            Objects.requireNonNull(title, "title");
        }
    }

    /// `[[target]]`.
    record WikiLink(MarkdownAttribute target) implements SpanDetail {

        public WikiLink {
            Objects.requireNonNull(target, "target");
        }
    }
}
