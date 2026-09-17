package io.github.digitalsmile.goldberry.markdown.html;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.markdown.Markdown;
import io.github.digitalsmile.goldberry.markdown.model.Block;
import io.github.digitalsmile.goldberry.markdown.model.BulletList;
import io.github.digitalsmile.goldberry.markdown.model.CellAlignment;
import io.github.digitalsmile.goldberry.markdown.model.Code;
import io.github.digitalsmile.goldberry.markdown.model.CodeBlock;
import io.github.digitalsmile.goldberry.markdown.model.Document;
import io.github.digitalsmile.goldberry.markdown.model.Emphasis;
import io.github.digitalsmile.goldberry.markdown.model.Heading;
import io.github.digitalsmile.goldberry.markdown.model.HtmlBlock;
import io.github.digitalsmile.goldberry.markdown.model.Image;
import io.github.digitalsmile.goldberry.markdown.model.Inline;
import io.github.digitalsmile.goldberry.markdown.model.Item;
import io.github.digitalsmile.goldberry.markdown.model.LineBreak;
import io.github.digitalsmile.goldberry.markdown.model.Link;
import io.github.digitalsmile.goldberry.markdown.model.NumberedList;
import io.github.digitalsmile.goldberry.markdown.model.Paragraph;
import io.github.digitalsmile.goldberry.markdown.model.Quote;
import io.github.digitalsmile.goldberry.markdown.model.RawHtml;
import io.github.digitalsmile.goldberry.markdown.model.Strong;
import io.github.digitalsmile.goldberry.markdown.model.Struck;
import io.github.digitalsmile.goldberry.markdown.model.Table;
import io.github.digitalsmile.goldberry.markdown.model.TableCell;
import io.github.digitalsmile.goldberry.markdown.model.TableRow;
import io.github.digitalsmile.goldberry.markdown.model.Text;
import io.github.digitalsmile.goldberry.markdown.model.ThematicBreak;
import io.github.digitalsmile.goldberry.markdown.model.Underlined;
import io.github.digitalsmile.goldberry.markdown.model.WikiLink;

/// A [Document], written out as HTML.
///
/// ```java
/// var body = MarkdownHtml.of(Markdown.parse(note.source()));   // GET /docs/{id}/body.html
/// ```
///
/// A **fragment**, not a page: no `<html>`, no `<head>`, no stylesheet link. What a
/// server puts in a response body or a template drops into a slot, which is the shape
/// every caller has wanted so far — an application that needs a whole page has
/// opinions about its head that this class could only get wrong.
///
/// ## Why this is Java and not md4c's own renderer
///
/// md4c ships `md4c-html.c`, which would have produced HTML with no Java in the way
/// at all. It is not used, and the reason is the model: an application that shows a
/// note *and* serves it needs both halves to agree — the same dialect, the same
/// entity resolution, the same decision about what a soft break means — and two
/// renderers, one in C and one over the model, is how they stop agreeing. So HTML is
/// a fold over the same tree `markdown-view` renders, and the compiler is what keeps
/// the pair honest: a node added to the model is a missing case in both (ADR-0295).
///
/// ## What it writes
///
/// The tags CommonMark's reference output uses, so that a document's HTML is
/// recognisable next to any other Markdown tool's: `h1`…`h6`, `p`, `blockquote`,
/// `ul`/`ol`/`li`, `pre`/`code`, `hr`, `table`, `em`, `strong`, `del`, `u`, `a`,
/// `img`, `br`. Three departures, each because the model has something the reference
/// dialect does not:
///
/// - A fenced block's language becomes `class="language-java"`, which is what every
///   highlighter on the web looks for and what `goldberry-code` will read.
/// - A task item becomes a `<li class="task">` holding a disabled check box, which is
///   how GitHub renders one and means the output needs no stylesheet to read.
/// - A wiki link becomes `<a href="target" class="wikilink">`, because a target is
///   not a URL and an application that serves these is expected to rewrite them. The
///   class is the seam it rewrites against.
public final class MarkdownHtml {

    private MarkdownHtml() {}

    /// `document` as an HTML fragment.
    public static String of(Document document) {
        Objects.requireNonNull(document, "document");
        var out = new StringBuilder();
        blocks(out, document.blocks());
        return out.toString();
    }

    /// `markdown` parsed in [io.github.digitalsmile.goldberry.markdown.MarkdownSyntax#gitHub()]
    /// and written out, for the caller who wants the one and never the other.
    public static String of(String markdown) {
        return of(Markdown.parse(markdown));
    }

    private static void blocks(StringBuilder out, List<? extends Block> blocks) {
        for (var block : blocks) {
            block(out, block);
        }
    }

    private static void block(StringBuilder out, Block block) {
        switch (block) {
            // A nested document is not a thing md4c produces, but the model permits
            // one and a caller can build one -- so it writes its children rather than
            // a tag, which is what a document is.
            case Document(var blocks) -> blocks(out, blocks);
            case Heading(var level, var content) -> {
                out.append("<h").append(level).append('>');
                inlines(out, content);
                out.append("</h").append(level).append(">\n");
            }
            case Paragraph(var content) -> {
                out.append("<p>");
                inlines(out, content);
                out.append("</p>\n");
            }
            case Quote(var blocks) -> {
                out.append("<blockquote>\n");
                blocks(out, blocks);
                out.append("</blockquote>\n");
            }
            case BulletList(var tight, var items) -> {
                out.append("<ul>\n");
                items(out, items, tight);
                out.append("</ul>\n");
            }
            case NumberedList(var start, var tight, var items) -> {
                out.append("<ol");
                if (start != 1) {
                    out.append(" start=\"").append(start).append('"');
                }
                out.append(">\n");
                items(out, items, tight);
                out.append("</ol>\n");
            }
            // An item with no list round it is not something the parser produces, and a
            // caller who built one has said nothing about tightness -- so it is written
            // the way the common case is.
            case Item item -> item(out, item, true);
            case CodeBlock(var language, var _, var code) -> {
                out.append("<pre><code");
                if (language != null && !language.isBlank()) {
                    out.append(" class=\"language-");
                    HtmlEscape.attribute(out, language);
                    out.append('"');
                }
                out.append('>');
                HtmlEscape.text(out, code);
                out.append("</code></pre>\n");
            }
            case ThematicBreak _ -> out.append("<hr>\n");
            // Verbatim, which is the whole point of it: the author wrote markup and
            // asked for markup. An application that does not want that parses with
            // `MarkdownExtension.NO_HTML` and gets escaped text instead.
            case HtmlBlock(var html) -> out.append(html);
            case Table table -> table(out, table);
            case TableRow row -> row(out, row);
            case TableCell cell -> cell(out, cell);
        }
    }

    private static void items(StringBuilder out, List<Item> items, boolean tight) {
        for (var item : items) {
            item(out, item, tight);
        }
    }

    private static void item(StringBuilder out, Item item, boolean tight) {
        if (item.task()) {
            out.append("<li class=\"task\"><input type=\"checkbox\" disabled");
            if (item.done()) {
                out.append(" checked");
            }
            out.append('>');
        } else {
            out.append("<li>");
        }
        // **A tight item's paragraph is unwrapped; a loose item's is not.** That is
        // CommonMark's own output and it is the one place the tight/loose distinction
        // is visible in HTML: `<li>one</li>` against `<li><p>one</p></li>`, which a
        // browser renders with a paragraph's spacing. The parser puts a paragraph
        // inside every item so that a consumer has one shape to handle, so the
        // question is the list's rather than the item's -- which is exactly where the
        // model keeps it.
        if (tight && item.blocks().size() == 1 && item.blocks().getFirst() instanceof Paragraph(var content)) {
            inlines(out, content);
        } else {
            out.append('\n');
            blocks(out, item.blocks());
        }
        out.append("</li>\n");
    }

    private static void table(StringBuilder out, Table table) {
        out.append("<table>\n");
        if (!table.head().isEmpty()) {
            out.append("<thead>\n");
            table.head().forEach(row -> row(out, row));
            out.append("</thead>\n");
        }
        if (!table.body().isEmpty()) {
            out.append("<tbody>\n");
            table.body().forEach(row -> row(out, row));
            out.append("</tbody>\n");
        }
        out.append("</table>\n");
    }

    private static void row(StringBuilder out, TableRow row) {
        out.append("<tr>\n");
        row.cells().forEach(cell -> cell(out, cell));
        out.append("</tr>\n");
    }

    private static void cell(StringBuilder out, TableCell cell) {
        var tag = cell.header() ? "th" : "td";
        out.append('<').append(tag);
        // `style` rather than a class, which is what every Markdown-to-HTML tool
        // emits for an alignment row: the alignment came from the document rather
        // than from a stylesheet, so a fragment dropped into a page with no CSS of
        // its own still lines up.
        var alignment =
                switch (cell.alignment()) {
                    case CellAlignment.DEFAULT -> null;
                    case CellAlignment.START -> "left";
                    case CellAlignment.CENTER -> "center";
                    case CellAlignment.END -> "right";
                };
        if (alignment != null) {
            out.append(" style=\"text-align:").append(alignment).append('"');
        }
        out.append('>');
        inlines(out, cell.content());
        out.append("</").append(tag).append(">\n");
    }

    private static void inlines(StringBuilder out, List<? extends Inline> content) {
        for (var inline : content) {
            inline(out, inline);
        }
    }

    private static void inline(StringBuilder out, Inline inline) {
        switch (inline) {
            case Text(var text) -> HtmlEscape.text(out, text);
            case Code(var code) -> {
                out.append("<code>");
                HtmlEscape.text(out, code);
                out.append("</code>");
            }
            case Emphasis(var content) -> wrap(out, "em", content);
            case Strong(var content) -> wrap(out, "strong", content);
            case Struck(var content) -> wrap(out, "del", content);
            case Underlined(var content) -> wrap(out, "u", content);
            case Link(var href, var title, var _, var content) -> {
                out.append("<a href=\"");
                HtmlEscape.attribute(out, href);
                out.append('"');
                title(out, title);
                out.append('>');
                inlines(out, content);
                out.append("</a>");
            }
            case WikiLink(var target, var content) -> {
                out.append("<a href=\"");
                HtmlEscape.attribute(out, target);
                out.append("\" class=\"wikilink\">");
                inlines(out, content);
                out.append("</a>");
            }
            case Image(var src, var title, var alt) -> {
                out.append("<img src=\"");
                HtmlEscape.attribute(out, src);
                out.append("\" alt=\"");
                HtmlEscape.attribute(out, alt);
                out.append('"');
                title(out, title);
                out.append('>');
            }
            // A soft break is a newline in the output and nothing more, because HTML
            // collapses whitespace by specification -- so the paragraph reflows in the
            // browser exactly as it does in `markdown-view`, and the source stays
            // readable.
            case LineBreak(var hard) -> out.append(hard ? "<br>\n" : "\n");
            case RawHtml(var html) -> out.append(html);
        }
    }

    private static void wrap(StringBuilder out, String tag, List<Inline> content) {
        out.append('<').append(tag).append('>');
        inlines(out, content);
        out.append("</").append(tag).append('>');
    }

    private static void title(StringBuilder out, @Nullable String title) {
        if (title == null || title.isEmpty()) {
            return;
        }
        out.append(" title=\"");
        HtmlEscape.attribute(out, title);
        out.append('"');
    }
}
