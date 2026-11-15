package dev.goldberry.html.marked;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import dev.goldberry.content.marked.MarkLinks;
import dev.goldberry.content.marked.MarkedBuilder;
import dev.goldberry.html.Html;
import dev.goldberry.html.model.Comment;
import dev.goldberry.html.model.Element;
import dev.goldberry.html.model.HtmlDocument;
import dev.goldberry.html.model.HtmlNode;
import dev.goldberry.html.model.HtmlText;
import dev.goldberry.html.model.Tags;
import dev.goldberry.text.mark.Mark;
import dev.goldberry.text.mark.MarkKind;
import dev.goldberry.text.mark.MarkKind.Style;
import dev.goldberry.text.mark.MarkRun;
import dev.goldberry.text.mark.MarkedFormat;
import dev.goldberry.text.mark.MarkedText;

/// Marked text as HTML, and HTML as marked text: what a copy offers a word
/// processor, and what a paste from a browser keeps.
///
/// ```java
/// var html = MarkedHtml.write(draft);               // "Ship it <strong>today</strong>"
/// var draft = MarkedHtml.read(pastedHtml);
/// new RichInput(draft, onChange).clipboardFormats(List.of(MarkedHtml.format()));
/// ```
///
/// ## Reading
///
/// `<b>`, `<strong>`, `<i>`, `<em>`, `<u>`, `<ins>`, `<s>`, `<del>`, `<strike>`,
/// `<code>`, `<kbd>`, `<samp>`, `<tt>`, `<pre>` and `<a href>` become marks, and
/// so does a `span` with the class `spoiler` or `tg-spoiler`. An inline `style`
/// is read for the same four things (`font-weight`, `font-style`,
/// `text-decoration` and a monospace `font-family`), because that is how a word
/// processor and an online editor write them, and a `font-weight: normal` takes
/// bold off: a document editor wraps a whole paste in a `<b>` that says so.
///
/// Every other element is read as its words. Whitespace collapses as a browser
/// collapses it, except in a `pre`; a block starts a new line, a `br` breaks
/// one, and a list item starts with `- `. A `head`, a `script` and a `style` are
/// not words.
///
/// ## Writing
///
/// `<strong>`, `<em>`, `<u>`, `<s>`, `<code>`, `<span class="spoiler">`, and
/// `<a href>` for a link, a mention (`mention:` and the id) and a custom kind
/// (`mark:`), nested properly however the marks overlap; a line break is a
/// `<br>`.
///
/// Read more: [Markdown, HTML and the web](https://goldberry.dev/docs/components/content.html).
public final class MarkedHtml {

    /// The class a spoiler is written with and one of those it is read from.
    public static final String SPOILER_CLASS = "spoiler";

    private MarkedHtml() {}

    /// The clipboard format for `text/html`: install it on a `rich-input`, and
    /// a copy with marks offers HTML and a paste from a browser keeps its
    /// formatting.
    public static MarkedFormat format() {
        return HtmlFormat.INSTANCE;
    }

    // --- writing ------------------------------------------------------------------

    /// `text` as an HTML fragment.
    public static String write(MarkedText text) {
        Objects.requireNonNull(text, "text");
        var out = new StringBuilder(text.length() + 32);
        var open = new ArrayList<MarkKind>();
        for (var run : MarkRun.of(text.length(), text.marks())) {
            var kinds = run.kinds();
            var keep = 0;
            while (keep < open.size() && kinds.contains(open.get(keep))) {
                keep++;
            }
            while (open.size() > keep) {
                out.append(closeTag(open.removeLast()));
            }
            var opening = new ArrayList<MarkKind>();
            for (var kind : kinds) {
                if (!open.contains(kind)) {
                    opening.add(kind);
                }
            }
            opening.sort(Comparator.<MarkKind>comparingInt(kind -> endOf(text, run.start(), kind))
                    .reversed()
                    .thenComparing(MarkKind.ORDER));
            for (var kind : opening) {
                out.append(openTag(kind));
                open.add(kind);
            }
            escape(out, text.text().substring(run.start(), run.end()));
        }
        while (!open.isEmpty()) {
            out.append(closeTag(open.removeLast()));
        }
        return out.toString();
    }

    private static int endOf(MarkedText text, int at, MarkKind kind) {
        for (Mark mark : text.marks()) {
            if (mark.kind().equals(kind) && mark.covers(at)) {
                return mark.end();
            }
        }
        return at;
    }

    private static String openTag(MarkKind kind) {
        return switch (kind) {
            case Style style ->
                switch (style) {
                    case BOLD -> "<strong>";
                    case ITALIC -> "<em>";
                    case UNDERLINE -> "<u>";
                    case STRIKE -> "<s>";
                    case CODE -> "<code>";
                    case SPOILER -> "<span class=\"" + SPOILER_CLASS + "\">";
                };
            case MarkKind.Link _, MarkKind.Mention _, MarkKind.Custom _ -> {
                var out = new StringBuilder("<a href=\"");
                escape(out, MarkLinks.href(kind).orElseThrow());
                yield out.append("\">").toString();
            }
        };
    }

    private static String closeTag(MarkKind kind) {
        return switch (kind) {
            case Style style ->
                switch (style) {
                    case BOLD -> "</strong>";
                    case ITALIC -> "</em>";
                    case UNDERLINE -> "</u>";
                    case STRIKE -> "</s>";
                    case CODE -> "</code>";
                    case SPOILER -> "</span>";
                };
            case MarkKind.Link _, MarkKind.Mention _, MarkKind.Custom _ -> "</a>";
        };
    }

    private static void escape(StringBuilder out, String words) {
        for (var i = 0; i < words.length(); i++) {
            var c = words.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\n' -> out.append("<br>");
                case '\r' -> {}
                default -> out.append(c);
            }
        }
    }

    // --- reading ------------------------------------------------------------------

    /// `html`, a fragment or a whole page, as marked text.
    public static MarkedText read(String html) {
        Objects.requireNonNull(html, "html");
        return read(Html.parse(html));
    }

    /// `document` as marked text.
    public static MarkedText read(HtmlDocument document) {
        Objects.requireNonNull(document, "document");
        var reader = new Reader();
        reader.children(document.children(), false);
        return reader.out.build();
    }

    /// A walk of a page into a [MarkedBuilder], collapsing whitespace as it
    /// goes.
    private static final class Reader {

        private final MarkedBuilder out = new MarkedBuilder();

        /// Line breaks owed before the next word: a block boundary asks for
        /// one, and a `br` for one more. None is written before the first word
        /// or after the last.
        private int breaks;

        /// Whether a space is owed before the next word.
        private boolean space;

        private void children(List<HtmlNode> children, boolean preformatted) {
            for (var child : children) {
                node(child, preformatted);
            }
        }

        private void node(HtmlNode node, boolean preformatted) {
            switch (node) {
                case HtmlText(var text) -> text(text, preformatted);
                case Element element -> element(element, preformatted);
                case HtmlDocument(var children) -> children(children, preformatted);
                case Comment _ -> {}
            }
        }

        private void element(Element element, boolean preformatted) {
            var tag = element.tag().toLowerCase(Locale.ROOT);
            if (Tags.isMetadata(tag)) {
                return;
            }
            switch (tag) {
                case "br" -> {
                    breaks++;
                    space = false;
                    return;
                }
                case "img" -> {
                    var alt = element.attributes().value("alt");
                    if (alt != null) {
                        text(alt, false);
                    }
                    return;
                }
                default -> {}
            }
            var block = Tags.isBlock(tag);
            if (block) {
                lineBreak();
                if (tag.equals("li")) {
                    word("- ");
                }
            }
            var pre = preformatted || Tags.isPreformatted(tag);
            // Trimmed in [#mark], so a space or a break owed here falls outside.
            var start = out.length();
            out.enter();
            children(element.children(), pre);
            out.leave();
            for (var kind : kindsOf(element, tag)) {
                mark(start, kind);
            }
            if (block) {
                lineBreak();
            }
        }

        /// The kinds `element` puts on its words: by its tag, its class and its
        /// style.
        private static List<MarkKind> kindsOf(Element element, String tag) {
            var kinds = new ArrayList<MarkKind>(2);
            switch (tag) {
                case "b", "strong" -> kinds.add(Style.BOLD);
                case "i", "em" -> kinds.add(Style.ITALIC);
                case "u", "ins" -> kinds.add(Style.UNDERLINE);
                case "s", "del", "strike" -> kinds.add(Style.STRIKE);
                case "code", "kbd", "samp", "tt", "pre" -> kinds.add(Style.CODE);
                case "a" -> {
                    var href = element.attributes().value("href");
                    if (href != null) {
                        MarkLinks.kind(href).ifPresent(kinds::add);
                    }
                }
                default -> {}
            }
            var classes = element.attributes().classes();
            if (classes.contains(SPOILER_CLASS) || classes.contains("tg-spoiler")) {
                kinds.add(Style.SPOILER);
            }
            var style = element.attributes().value("style");
            if (style != null) {
                InlineStyle.apply(style, kinds);
            }
            return kinds;
        }

        /// `kind` over what was read since `start`, less the whitespace at either
        /// end, which a mark has no use for.
        private void mark(int start, MarkKind kind) {
            var text = out.text();
            var from = Math.min(start, text.length());
            var to = text.length();
            while (from < to && Character.isWhitespace(text.charAt(from))) {
                from++;
            }
            while (to > from && Character.isWhitespace(text.charAt(to - 1))) {
                to--;
            }
            out.mark(from, to, kind);
        }

        private void text(String text, boolean preformatted) {
            if (preformatted) {
                for (var i = 0; i < text.length(); i++) {
                    var c = text.charAt(i);
                    if (c == '\n') {
                        breaks++;
                    } else if (c != '\r') {
                        word(String.valueOf(c));
                    }
                }
                return;
            }
            var word = new StringBuilder();
            for (var i = 0; i < text.length(); i++) {
                var c = text.charAt(i);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f') {
                    if (!word.isEmpty()) {
                        word(word.toString());
                        word.setLength(0);
                    }
                    space = true;
                } else {
                    word.append(c);
                }
            }
            if (!word.isEmpty()) {
                word(word.toString());
            }
        }

        /// `words` written after the breaks or the space owed before them.
        private void word(String words) {
            if (out.length() > 0) {
                if (breaks > 0) {
                    out.append("\n".repeat(breaks));
                } else if (space && !out.atLineStart()) {
                    out.append(' ');
                }
            }
            breaks = 0;
            space = false;
            out.append(words);
        }

        private void lineBreak() {
            breaks = Math.max(breaks, 1);
            space = false;
        }
    }

    /// The four things an inline `style` says that marks hold.
    private static final class InlineStyle {

        private InlineStyle() {}

        static void apply(String style, List<MarkKind> kinds) {
            for (var declaration : style.split(";", -1)) {
                var colon = declaration.indexOf(':');
                if (colon < 0) {
                    continue;
                }
                var property = declaration.substring(0, colon).strip().toLowerCase(Locale.ROOT);
                var value = declaration.substring(colon + 1).strip().toLowerCase(Locale.ROOT);
                switch (property) {
                    case "font-weight" -> {
                        if (bold(value)) {
                            add(kinds, Style.BOLD);
                        } else {
                            kinds.remove(Style.BOLD);
                        }
                    }
                    case "font-style" -> {
                        if (value.startsWith("italic") || value.startsWith("oblique")) {
                            add(kinds, Style.ITALIC);
                        } else if (value.equals("normal")) {
                            kinds.remove(Style.ITALIC);
                        }
                    }
                    case "text-decoration", "text-decoration-line" -> {
                        if (value.contains("underline")) {
                            add(kinds, Style.UNDERLINE);
                        }
                        if (value.contains("line-through")) {
                            add(kinds, Style.STRIKE);
                        }
                    }
                    case "font-family" -> {
                        if (value.contains("mono") || value.contains("courier") || value.contains("consolas")) {
                            add(kinds, Style.CODE);
                        }
                    }
                    default -> {}
                }
            }
        }

        private static boolean bold(String value) {
            if (value.startsWith("bold")) {
                return true;
            }
            try {
                return Integer.parseInt(value) >= 600;
            } catch (NumberFormatException notANumber) {
                return false;
            }
        }

        private static void add(List<MarkKind> kinds, MarkKind kind) {
            if (!kinds.contains(kind)) {
                kinds.add(kind);
            }
        }
    }
}
