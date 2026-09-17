package io.github.digitalsmile.goldberry.markdown.model;

import java.util.List;

/// The one thing every consumer of inline content wants and nobody should write
/// twice: the words, with the marks dropped.
///
/// A heading's anchor, a table of contents, an image's alt text, a window title, a
/// search index, brd's note list — all of them want the text of a run of inlines and
/// none of them wants the emphasis. [Heading#text()], [Paragraph#text()],
/// [Link#text()] and [TableCell#text()] are all this.
public final class Inlines {

    private Inlines() {}

    /// The text of `content`, with every mark dropped.
    ///
    /// A hard break becomes a newline and a soft one a space, because that is what
    /// they mean. Raw HTML contributes nothing: `<br>` is markup, not a word, and a
    /// heading whose anchor contained a tag would be a worse answer than one that
    /// skipped it.
    public static String text(List<? extends Inline> content) {
        var out = new StringBuilder();
        append(out, content);
        return out.toString();
    }

    private static void append(StringBuilder out, List<? extends Inline> content) {
        for (var inline : content) {
            switch (inline) {
                case Text(var text) -> out.append(text);
                case Code(var code) -> out.append(code);
                case Image(var _, var _, var alt) -> out.append(alt);
                case LineBreak(var hard) -> out.append(hard ? '\n' : ' ');
                case RawHtml _ -> {}
                case Emphasis(var children) -> append(out, children);
                case Strong(var children) -> append(out, children);
                case Struck(var children) -> append(out, children);
                case Underlined(var children) -> append(out, children);
                case Link(var _, var _, var _, var children) -> append(out, children);
                case WikiLink(var _, var children) -> append(out, children);
            }
        }
    }
}
