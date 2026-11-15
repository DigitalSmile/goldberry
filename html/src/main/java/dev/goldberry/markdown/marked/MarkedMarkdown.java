package dev.goldberry.markdown.marked;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.content.marked.MarkLinks;
import dev.goldberry.content.marked.MarkedBuilder;
import dev.goldberry.markdown.Markdown;
import dev.goldberry.markdown.MarkdownExtension;
import dev.goldberry.markdown.MarkdownSyntax;
import dev.goldberry.markdown.model.Block;
import dev.goldberry.markdown.model.BulletList;
import dev.goldberry.markdown.model.Code;
import dev.goldberry.markdown.model.CodeBlock;
import dev.goldberry.markdown.model.Document;
import dev.goldberry.markdown.model.Emphasis;
import dev.goldberry.markdown.model.Heading;
import dev.goldberry.markdown.model.HtmlBlock;
import dev.goldberry.markdown.model.Image;
import dev.goldberry.markdown.model.Inline;
import dev.goldberry.markdown.model.Item;
import dev.goldberry.markdown.model.LineBreak;
import dev.goldberry.markdown.model.Link;
import dev.goldberry.markdown.model.NumberedList;
import dev.goldberry.markdown.model.Paragraph;
import dev.goldberry.markdown.model.Quote;
import dev.goldberry.markdown.model.RawHtml;
import dev.goldberry.markdown.model.Strong;
import dev.goldberry.markdown.model.Struck;
import dev.goldberry.markdown.model.Table;
import dev.goldberry.markdown.model.TableCell;
import dev.goldberry.markdown.model.TableRow;
import dev.goldberry.markdown.model.Text;
import dev.goldberry.markdown.model.ThematicBreak;
import dev.goldberry.markdown.model.Underlined;
import dev.goldberry.markdown.model.WikiLink;
import dev.goldberry.text.mark.Mark;
import dev.goldberry.text.mark.MarkKind;
import dev.goldberry.text.mark.MarkKind.Style;
import dev.goldberry.text.mark.MarkRun;
import dev.goldberry.text.mark.MarkedText;

/// Marked text as Markdown, and Markdown as marked text: what a composer sends
/// to a service that speaks Markdown, and how `**bold**` typed by hand becomes
/// bold on send.
///
/// ```java
/// var markdown = MarkedMarkdown.write(draft);        // "Ship it **today**"
/// var draft = MarkedMarkdown.read("Ship it **today**");
/// ```
///
/// ## The dialect
///
/// [#SYNTAX]: CommonMark with `~~strike~~`, `_underline_`, every newline a line
/// break, no indented code and no HTML, which is how a message is written.
///
/// | Kind | Written |
/// |------|---------|
/// | bold | `**bold**` |
/// | italic | `*italic*` |
/// | underline | `_underline_` |
/// | strike | `~~strike~~` |
/// | code | `` `code` ``, with as many backticks as the code needs |
/// | spoiler | `\|\|spoiler\|\|`, a messenger's own syntax |
/// | link | `[words](address)` |
/// | mention | `[@Ann Lee](mention:user%3A42)` |
/// | custom | `[#rel](mark:hashtag/rel)` |
///
/// Text is escaped so that it reads back as itself: `*` and `_` and the rest get
/// a backslash, and so does a `#` or a `-` that starts a line.
///
/// ## What does not survive
///
/// Markdown is a smaller language than marks, so a few things come back
/// different. A mark across a line break comes back as one mark per line.
/// Whitespace at the edge of bold, italic, underline or strike-through falls
/// outside it, since `** x**` is not bold; a link and a spoiler keep theirs.
/// Underline inside a word (`a_b_c`) is not underline to CommonMark. Leading
/// spaces on a line, and more than one blank line in a row, are not kept.
/// Reading takes the words of what marks cannot hold: a heading is its words, a
/// list item its words after a `- `, a code block code.
///
/// Read more: [Markdown, HTML and the web](https://goldberry.dev/docs/components/content.html).
public final class MarkedMarkdown {

    /// The dialect written and read: CommonMark with strike-through, `_`
    /// underline, hard line breaks, no indented code blocks and no HTML.
    public static final MarkdownSyntax SYNTAX = MarkdownSyntax.of(
            MarkdownExtension.STRIKETHROUGH,
            MarkdownExtension.UNDERLINE,
            MarkdownExtension.HARD_LINE_BREAKS,
            MarkdownExtension.NO_INDENTED_CODE,
            MarkdownExtension.NO_HTML);

    /// The punctuation an unescaped `||` may become before the parser sees it:
    /// rare marks from the Supplemental Punctuation block. Punctuation, as `|`
    /// is, so that a delimiter beside a spoiler's edge flanks it the same way;
    /// whichever the document does not hold already is used, so the reader can
    /// tell a spoiler's edge from a pipe the author escaped.
    private static final String EDGES = "\u2E38\u2E39\u2E3B\u2E3E\u2E3F\u2E41\u2E4C\u2E4D\u2E4E";

    private static final String SPOILER_TEXT = "||";

    private MarkedMarkdown() {}

    // --- writing ------------------------------------------------------------------

    /// `text` as Markdown in [#SYNTAX].
    public static String write(MarkedText text) {
        Objects.requireNonNull(text, "text");
        var out = new StringBuilder(text.length() + 16);
        var source = text.text();
        var lineStart = 0;
        while (true) {
            var newline = source.indexOf('\n', lineStart);
            var lineEnd = newline < 0 ? source.length() : newline;
            new LineWriter(out).write(text.slice(lineStart, lineEnd));
            if (newline < 0) {
                return out.toString();
            }
            out.append('\n');
            lineStart = newline + 1;
        }
    }

    /// One line written: the marks opened and closed around its runs, with
    /// whitespace kept outside the delimiters so that CommonMark sees them.
    private static final class LineWriter {

        private final StringBuilder out;

        /// Where this line began in [#out].
        private final int begin;

        /// The kinds open, outermost first.
        private final List<MarkKind> open = new ArrayList<>();

        /// Kinds opened with no word written since: their delimiters wait for
        /// one, so that whitespace goes before them.
        private final List<MarkKind> waiting = new ArrayList<>();

        /// Whitespace written last, held back so that a closing delimiter can go
        /// before it.
        private String space = "";

        /// The code being collected while a code mark is open, or null.
        private @Nullable StringBuilder code;

        LineWriter(StringBuilder out) {
            this.out = out;
            this.begin = out.length();
        }

        void write(MarkedText line) {
            for (var run : MarkRun.of(line.length(), line.marks())) {
                var kinds = run.kinds();
                var keep = 0;
                while (keep < open.size() && kinds.contains(open.get(keep))) {
                    keep++;
                }
                // Code is the innermost: nothing opens inside a code span, so
                // a kind that opens while code is open closes the code first.
                var codeAt = open.indexOf(Style.CODE);
                if (codeAt >= 0 && codeAt < keep && !open.containsAll(kinds)) {
                    keep = codeAt;
                }
                while (open.size() > keep) {
                    close(open.removeLast());
                }
                var opening = new ArrayList<MarkKind>();
                for (var kind : kinds) {
                    if (!open.contains(kind)) {
                        opening.add(kind);
                    }
                }
                opening.sort(outermostFirst(line, run.start()));
                for (var kind : opening) {
                    open(kind);
                }
                text(line.text().substring(run.start(), run.end()));
            }
            while (!open.isEmpty()) {
                close(open.removeLast());
            }
            out.append(space);
        }

        /// The order kinds opening at `at` are opened in: the one that runs
        /// furthest outermost, code innermost.
        private static Comparator<MarkKind> outermostFirst(MarkedText line, int at) {
            return Comparator.<MarkKind>comparingInt(kind -> kind == Style.CODE ? 1 : 0)
                    .thenComparing(Comparator.<MarkKind>comparingInt(kind -> endOf(line, at, kind))
                            .reversed())
                    .thenComparing(MarkKind.ORDER);
        }

        private static int endOf(MarkedText line, int at, MarkKind kind) {
            for (Mark mark : line.marks()) {
                if (mark.kind().equals(kind) && mark.covers(at)) {
                    return mark.end();
                }
            }
            return at;
        }

        private void open(MarkKind kind) {
            open.add(kind);
            if (kind == Style.CODE) {
                code = new StringBuilder();
            } else {
                waiting.add(kind);
            }
        }

        private void close(MarkKind kind) {
            if (kind == Style.CODE) {
                var content = code;
                code = null;
                if (content != null && !content.isEmpty()) {
                    word("", codeSpan(content.toString()));
                }
                return;
            }
            // Opened over nothing but whitespace: there is no word for it to
            // hold, and an empty `****` would read as a rule or as stars.
            if (waiting.remove(kind)) {
                return;
            }
            if (holdsWhitespace(kind)) {
                // A link or a spoiler may end in a space, which it covers.
                out.append(space);
                space = "";
            }
            out.append(closer(kind));
        }

        private void text(String words) {
            if (code != null) {
                code.append(words);
                return;
            }
            var lead = 0;
            while (lead < words.length() && Character.isWhitespace(words.charAt(lead))) {
                lead++;
            }
            if (lead == words.length()) {
                space += words;
                return;
            }
            var trail = words.length();
            while (Character.isWhitespace(words.charAt(trail - 1))) {
                trail--;
            }
            var lineStart = startsLine() && waiting.isEmpty();
            word(words.substring(0, lead), escape(words.substring(lead, trail), lineStart));
            space = words.substring(trail);
        }

        /// `written` after the held whitespace and the waiting delimiters, with
        /// `leading` whitespace inside the outer ones that can hold it (a link,
        /// a spoiler) and outside the first that cannot: `** x**` is not bold.
        private void word(String leading, String written) {
            out.append(space);
            space = "";
            var i = 0;
            while (i < waiting.size() && holdsWhitespace(waiting.get(i))) {
                out.append(opener(waiting.get(i++)));
            }
            out.append(leading);
            while (i < waiting.size()) {
                out.append(opener(waiting.get(i++)));
            }
            waiting.clear();
            out.append(written);
        }

        private boolean startsLine() {
            for (var i = begin; i < out.length(); i++) {
                if (!Character.isWhitespace(out.charAt(i))) {
                    return false;
                }
            }
            return space.isBlank();
        }
    }

    /// Whether `kind`'s delimiters may have whitespace just inside them: a
    /// link's brackets and a spoiler's pipes may, and emphasis may not.
    private static boolean holdsWhitespace(MarkKind kind) {
        return switch (kind) {
            case Style style -> style == Style.SPOILER;
            case MarkKind.Link _, MarkKind.Mention _, MarkKind.Custom _ -> true;
        };
    }

    private static String opener(MarkKind kind) {
        return switch (kind) {
            case Style style -> delimiter(style);
            case MarkKind.Link _, MarkKind.Mention _, MarkKind.Custom _ -> "[";
        };
    }

    private static String closer(MarkKind kind) {
        return switch (kind) {
            case Style style -> delimiter(style);
            case MarkKind.Link _, MarkKind.Mention _, MarkKind.Custom _ ->
                "](" + destination(MarkLinks.href(kind).orElseThrow()) + ")";
        };
    }

    private static String delimiter(Style style) {
        return switch (style) {
            case BOLD -> "**";
            case ITALIC -> "*";
            case UNDERLINE -> "_";
            case STRIKE -> "~~";
            case SPOILER -> SPOILER_TEXT;
            case CODE -> "`";
        };
    }

    /// A link's address as a destination: parentheses, spaces and backslashes
    /// escaped.
    private static String destination(String href) {
        var out = new StringBuilder(href.length() + 4);
        for (var i = 0; i < href.length(); i++) {
            var c = href.charAt(i);
            switch (c) {
                case '(', ')', '\\', '<', '>' -> out.append('\\').append(c);
                case ' ' -> out.append("%20");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    /// `code` between enough backticks that none inside closes it, with a space
    /// inside each end when CommonMark would otherwise eat one or see a
    /// backtick as part of the fence.
    private static String codeSpan(String code) {
        var longest = 0;
        var run = 0;
        for (var i = 0; i < code.length(); i++) {
            run = code.charAt(i) == '`' ? run + 1 : 0;
            longest = Math.max(longest, run);
        }
        var fence = "`".repeat(longest + 1);
        var padded = code.startsWith("`")
                || code.endsWith("`")
                || (code.startsWith(" ") && code.endsWith(" ") && !code.isBlank());
        var pad = padded ? " " : "";
        return fence + pad + code + pad + fence;
    }

    /// `words` with what Markdown would read as markup escaped; `lineStart` when
    /// they begin a line, where `#`, `>`, `-`, `+`, `=` and `1.` begin blocks.
    static String escape(String words, boolean lineStart) {
        var out = new StringBuilder(words.length() + 8);
        var from = 0;
        if (lineStart && !words.isEmpty()) {
            var first = words.charAt(0);
            if ("#>-+=".indexOf(first) >= 0) {
                out.append('\\').append(first);
                from = 1;
            } else if (Character.isDigit(first)) {
                var digits = 0;
                while (digits < words.length() && Character.isDigit(words.charAt(digits))) {
                    digits++;
                }
                if (digits < words.length() && (words.charAt(digits) == '.' || words.charAt(digits) == ')')) {
                    out.append(words, 0, digits).append('\\').append(words.charAt(digits));
                    from = digits + 1;
                }
            }
        }
        for (var i = from; i < words.length(); i++) {
            var c = words.charAt(i);
            if ("\\`*_~[]|<&".indexOf(c) >= 0) {
                out.append('\\');
            }
            out.append(c);
        }
        return out.toString();
    }

    // --- reading ------------------------------------------------------------------

    /// `markdown` in [#SYNTAX], as marked text.
    public static MarkedText read(String markdown) {
        return read(markdown, SYNTAX);
    }

    /// `markdown` in `syntax`, as marked text: [#SYNTAX] with tables, say, for a
    /// service whose messages have them.
    public static MarkedText read(String markdown, MarkdownSyntax syntax) {
        Objects.requireNonNull(markdown, "markdown");
        Objects.requireNonNull(syntax, "syntax");
        var edge = edgeFor(markdown);
        var document = Markdown.parse(edged(markdown, edge), syntax);
        // Once to count the spoiler edges, so that an odd one out is known to
        // be a pipe before it is reached, and once to read.
        var counted = new Reader(edge, -1);
        counted.blocks(document.blocks());
        var reader = new Reader(edge, counted.edges);
        reader.blocks(document.blocks());
        return reader.out.build();
    }

    /// The first of [#EDGES] that `markdown` does not hold. A document holding
    /// all nine has its spoilers read as pipes.
    static char edgeFor(String markdown) {
        for (var i = 0; i < EDGES.length(); i++) {
            if (markdown.indexOf(EDGES.charAt(i)) < 0) {
                return EDGES.charAt(i);
            }
        }
        return '|';
    }

    /// `markdown` with each unescaped `||` made `edge`. An escaped pipe (`\|`)
    /// is left for the parser to unescape.
    static String edged(String markdown, char edge) {
        if (edge == '|') {
            return markdown;
        }
        var out = new StringBuilder(markdown.length());
        for (var i = 0; i < markdown.length(); i++) {
            var c = markdown.charAt(i);
            if (c == '\\' && i + 1 < markdown.length()) {
                out.append(c).append(markdown.charAt(i + 1));
                i++;
            } else if (c == '|' && i + 1 < markdown.length() && markdown.charAt(i + 1) == '|') {
                out.append(edge);
                i++;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /// A walk of the document into a [MarkedBuilder].
    private static final class Reader {

        private final MarkedBuilder out = new MarkedBuilder();

        /// What a `||` was made before the parse.
        private final char edge;

        /// How many spoiler edges the text holds in all, or -1 while counting.
        private final int total;

        private int edges;

        private int spoilerStart = -1;

        Reader(char edge, int total) {
            this.edge = edge;
            this.total = total;
        }

        /// Code and addresses hold the source as written, so an edge in them
        /// was a `||` all along.
        private String unedged(String text) {
            return edge == '|' || text.indexOf(edge) < 0 ? text : text.replace(String.valueOf(edge), SPOILER_TEXT);
        }

        void blocks(List<? extends Block> blocks) {
            blocks(blocks, "\n\n");
        }

        void blocks(List<? extends Block> blocks, String between) {
            var first = true;
            for (var block : blocks) {
                if (!first) {
                    out.append(between);
                }
                first = false;
                block(block);
            }
        }

        private void block(Block block) {
            switch (block) {
                case Document(var blocks) -> blocks(blocks);
                case Paragraph(var content) -> inlines(content);
                case Heading(var _, var content) -> inlines(content);
                case CodeBlock(var _, var _, var code) -> {
                    var start = out.length();
                    out.append(unedged(code.endsWith("\n") ? code.substring(0, code.length() - 1) : code));
                    out.mark(start, Style.CODE);
                }
                case Quote(var blocks) -> blocks(blocks);
                case BulletList(var _, var items) -> items(items, -1);
                case NumberedList(var start, var _, var items) -> items(items, start);
                case Item(var _, var _, var blocks) -> blocks(blocks, "\n");
                case HtmlBlock(var html) -> out.append(html);
                case ThematicBreak() -> out.append("---");
                case Table(var head, var body) -> {
                    var rows = new ArrayList<TableRow>(head);
                    rows.addAll(body);
                    blocks(rows, "\n");
                }
                case TableRow(var _, var cells) -> blocks(cells, " | ");
                case TableCell(var _, var _, var content) -> inlines(content);
            }
        }

        private void items(List<Item> items, int number) {
            var first = true;
            var at = number;
            for (var item : items) {
                if (!first) {
                    out.append('\n');
                }
                first = false;
                out.append(number < 0 ? "- " : at + ". ");
                at++;
                if (item.task()) {
                    out.append(item.done() ? "[x] " : "[ ] ");
                }
                blocks(item.blocks(), "\n");
            }
        }

        private void inlines(List<? extends Inline> content) {
            for (var inline : content) {
                inline(inline);
            }
        }

        private void inline(Inline inline) {
            switch (inline) {
                case Text(var text) -> text(text);
                case Emphasis(var content) -> styled(content, Style.ITALIC);
                case Strong(var content) -> styled(content, Style.BOLD);
                case Struck(var content) -> styled(content, Style.STRIKE);
                case Underlined(var content) -> styled(content, Style.UNDERLINE);
                case Code(var code) -> {
                    var start = out.length();
                    out.append(unedged(code));
                    out.mark(start, Style.CODE);
                }
                case Link(var href, var _, var _, var content) -> {
                    var start = out.length();
                    out.enter();
                    inlines(content);
                    out.leave();
                    MarkLinks.kind(unedged(href)).ifPresent(kind -> out.mark(start, kind));
                }
                case Image(var _, var _, var alt) -> out.append(unedged(alt));
                case WikiLink(var _, var content) -> inlines(content);
                case LineBreak(var hard) -> out.append(hard ? '\n' : ' ');
                case RawHtml(var html) -> out.append(html);
            }
        }

        private void styled(List<Inline> content, MarkKind kind) {
            var start = out.length();
            out.enter();
            inlines(content);
            out.leave();
            out.mark(start, kind);
        }

        /// Words, with each spoiler edge opening or closing a spoiler, and an
        /// odd last one read as the pipes it was.
        private void text(String text) {
            var from = 0;
            for (var i = 0; i < text.length(); i++) {
                if (edge == '|' || text.charAt(i) != edge) {
                    continue;
                }
                out.append(text.substring(from, i));
                from = i + 1;
                edges++;
                if (total < 0) {
                    continue;
                }
                if (edges == total && total % 2 == 1) {
                    out.append(SPOILER_TEXT);
                } else if (spoilerStart < 0) {
                    spoilerStart = out.length();
                } else {
                    out.outermost(spoilerStart, out.length(), Style.SPOILER);
                    spoilerStart = -1;
                }
            }
            out.append(text.substring(from));
        }
    }
}
