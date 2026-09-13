package io.github.digitalsmile.goldberry.html.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/// The walks [HtmlDocument] and [Element] hand out — collecting text, and finding a
/// tag.
///
/// Package-private and static, because they are the *same* walk from two roots and
/// the alternative was writing each one twice: a document and an element differ in
/// what they are, not in how their descendants are reached.
final class Walk {

    private Walk() {}

    /// Every word under `node`, with one space where a tag was.
    ///
    /// The space is what makes this the honest answer for a label, a summary or an
    /// index: `<b>bold</b>text` is two words in a heading. It is also why there is a
    /// [Sink] below rather than four lines — the space belongs at a boundary *only*
    /// when there is not one there already, and whether there is depends on the text
    /// that has not been read yet.
    static String text(HtmlNode node) {
        var sink = new Sink(true);
        append(node, sink);
        return sink.out.toString().strip();
    }

    /// The same with nothing inserted — what a `pre` holds.
    static String raw(HtmlNode node) {
        var sink = new Sink(false);
        append(node, sink);
        return sink.out.toString();
    }

    private static void append(HtmlNode node, Sink sink) {
        switch (node) {
            case HtmlText(var text) -> sink.text(text);
            // A comment is not read out, and neither is a `script` or a `style`: their
            // content is a program and a stylesheet, and a summary that quoted either
            // would be quoting something nobody wrote to be read.
            case Comment ignored -> {}
            case Element element -> {
                if (Tags.isRawText(element.tag())) {
                    return;
                }
                // A boundary on both sides of the tag: `<b>bold</b>text` is two words,
                // and a boundary only opened at the start would have read "boldtext".
                sink.boundary();
                element.children().forEach(child -> append(child, sink));
                sink.boundary();
            }
            case HtmlDocument document -> document.children().forEach(child -> append(child, sink));
        }
    }

    /// The text being collected, and whether a tag boundary is owed a space.
    ///
    /// **The space is owed rather than written**, which is the whole reason this is a
    /// class. `a <em>b</em> c` has an author's space on each side of the emphasis, so
    /// writing one at the boundary as well produced `a b  c` — two spaces in a
    /// summary, from a rule that was right about `<b>bold</b>text`. Owing it and paying
    /// only when the next character is not itself whitespace is right about both.
    private static final class Sink {

        private final StringBuilder out = new StringBuilder();

        private final boolean spaced;

        private boolean boundary;

        Sink(boolean spaced) {
            this.spaced = spaced;
        }

        /// A run of the author's own text.
        void text(String text) {
            if (text.isEmpty()) {
                return;
            }
            if (owes() && !Character.isWhitespace(text.charAt(0))) {
                out.append(' ');
            }
            boundary = false;
            out.append(text);
        }

        /// A tag opened or closed here.
        void boundary() {
            boundary = true;
        }

        private boolean owes() {
            return spaced && boundary && !out.isEmpty() && !Character.isWhitespace(out.charAt(out.length() - 1));
        }
    }

    /// Every **descendant** of `node` whose tag is `tag`, in document order.
    ///
    /// Descendants and not `node` itself, so that `div.find("div")` answers "the divs
    /// inside this one" — the question somebody asking it has.
    static List<Element> find(HtmlNode node, String tag) {
        var wanted = tag.toLowerCase(Locale.ROOT);
        var found = new ArrayList<Element>();
        node.children().forEach(child -> collect(child, wanted, found));
        return List.copyOf(found);
    }

    private static void collect(HtmlNode node, String tag, List<Element> found) {
        if (node instanceof Element element && element.tag().equals(tag)) {
            found.add(element);
        }
        node.children().forEach(child -> collect(child, tag, found));
    }
}
