package io.github.digitalsmile.goldberry.html;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;

import io.github.digitalsmile.goldberry.html.model.Comment;
import io.github.digitalsmile.goldberry.html.model.Element;
import io.github.digitalsmile.goldberry.html.model.HtmlAttributes;
import io.github.digitalsmile.goldberry.html.model.HtmlDocument;
import io.github.digitalsmile.goldberry.html.model.HtmlNode;
import io.github.digitalsmile.goldberry.html.model.HtmlText;
import io.github.digitalsmile.goldberry.html.model.Tags;

/// The tokens, as a tree.
///
/// One stack of open elements, and three tables of what closes what. Everything
/// interesting about this file is in [#closedBy], because that table is the whole
/// difference between reading HTML and reading XML: an author writes
///
/// ```html
/// <ul>
///   <li>First
///   <li>Second
/// ```
///
/// and means two items, not one item containing the other. A parser without the
/// table produces the nesting the source literally says and a renderer draws the
/// second bullet indented under the first — a picture that looks like a styling bug
/// and is a parsing one (ADR-0298).
///
/// Nothing here throws. A stray `</div>` is dropped, an unclosed `<div>` is closed at
/// the end of the file, and a `<p>` interrupted by a `<div>` is ended rather than
/// nested — every case written down beside the code that does it, because the
/// alternative is a renderer that fails on a page a browser draws.
final class HtmlParser {

    /// What is open, innermost first.
    private final Deque<Frame> open = new ArrayDeque<>();

    /// The document's own children — the root of the tree, which is not an element.
    private final List<HtmlNode> roots = new ArrayList<>();

    /// An element that has been started and not yet finished.
    ///
    /// A mutable list inside it, which every other value in this module would refuse:
    /// a parse is the one place a tree is being *built*, and the record the tree is
    /// made of copies the list when it is closed. Nothing outside this class sees one.
    private record Frame(String tag, HtmlAttributes attributes, List<HtmlNode> children) {

        Frame(String tag, HtmlAttributes attributes) {
            this(tag, attributes, new ArrayList<>());
        }
    }

    private HtmlParser() {}

    /// `source`, as a document.
    static HtmlDocument parse(String source) {
        var parser = new HtmlParser();
        for (var token : HtmlTokenizer.tokenize(source)) {
            parser.accept(token);
        }
        return parser.finish();
    }

    private void accept(Token token) {
        switch (token) {
            case Token.Characters(var text) -> add(new HtmlText(text));
            case Token.Note(var text) -> add(new Comment(text));
            case Token.Start(var tag, var attributes, var selfClosing) -> start(tag, attributes, selfClosing);
            case Token.End(var tag) -> end(tag);
        }
    }

    private void start(String tag, HtmlAttributes attributes, boolean selfClosing) {
        // Implied closes first: an open `p` or `li` that this tag cannot live inside
        // ends here, however many of them there are.
        while (!open.isEmpty() && closedBy(open.peek().tag(), tag)) {
            pop();
        }
        if (Tags.isVoid(tag) || selfClosing) {
            // **`/>` is honoured on any tag**, which HTML5 does not do — it ignores the
            // slash and opens the element. The corpus decides this one: a `<div/>` in an
            // authored document is a templating language's output and meant to be empty,
            // while the case HTML5's rule protects — a page that would otherwise lose
            // everything after `<div/>` — is the same page either way, because the
            // unclosed element is closed at the end of the file below.
            add(new Element(tag, attributes, List.of()));
            return;
        }
        open.push(new Frame(tag, attributes));
    }

    private void end(String tag) {
        // A close tag for something that is not open is dropped. `</br>` is the common
        // one and `</div>` after a templating mistake is the other; neither is worth
        // failing a page over, and neither has anything to close.
        if (open.stream().noneMatch(frame -> frame.tag().equals(tag))) {
            return;
        }
        while (!open.isEmpty()) {
            var closing = open.peek().tag().equals(tag);
            pop();
            if (closing) {
                return;
            }
        }
    }

    /// Closes the innermost open element and hands it to whatever holds it.
    private void pop() {
        var frame = open.pop();
        add(new Element(frame.tag(), frame.attributes(), frame.children()));
    }

    private void add(HtmlNode node) {
        var parent = open.peek();
        if (parent == null) {
            roots.add(node);
        } else {
            parent.children().add(node);
        }
    }

    private HtmlDocument finish() {
        // Everything still open at the end of the file is closed here, innermost
        // first. A truncated page renders what it has.
        while (!open.isEmpty()) {
            pop();
        }
        return roots.isEmpty() ? HtmlDocument.EMPTY : new HtmlDocument(roots);
    }

    // --- the table -------------------------------------------------------------

    /// A cell in a row, which the next cell or the next row ends.
    private static final Set<String> CELLS = Set.of("td", "th");

    /// The sections of a table, which each other end.
    private static final Set<String> SECTIONS = Set.of("thead", "tbody", "tfoot");

    /// Whether an open `open` element is ended by a `starting` tag beginning.
    ///
    /// Six rules, and every one of them is something authors write on purpose:
    ///
    /// - **A `p` is ended by any block.** This is the one that matters most, because a
    ///   `<p>` is the element people most often do not close, and the next thing in the
    ///   file is usually a heading or another paragraph. An *inline* tag does not end
    ///   it, which is the other half: `<p>a <em>b</em>` is one paragraph.
    /// - **An `li` is ended by the next `li`** — the list above.
    /// - **A `dt` or `dd` is ended by either**, for the same reason in a definition
    ///   list.
    /// - **A cell is ended by the next cell, the next row or a section**, and a row by
    ///   the next row or a section, which is how every hand-written table in a
    ///   changelog is laid out. The section is the half that was missing: HTML's own
    ///   "in cell" mode closes an open cell for any of `td`, `th`, `tr`, `thead`,
    ///   `tbody`, `tfoot`, `caption`, `col` and `colgroup`, and without it
    ///   `<thead><tr><th>a<th>b<tbody>` put the whole body **inside** the last header
    ///   cell — and the row's own rule below could never run, because the cell above it
    ///   never closed. A `table` is deliberately not in the list: a table inside a cell
    ///   is a nested table, which is legal and meant.
    /// - **A section is ended by the next section**, so a `tfoot` after a `tbody` is a
    ///   sibling.
    /// - **An `option` is ended by the next one.** A `select` is not something this
    ///   renderer draws, but the model is exported and a document holding a form should
    ///   parse into the shape it means.
    private static boolean closedBy(String open, String starting) {
        return switch (open) {
            case "p" -> Tags.isBlock(starting);
            case "li" -> "li".equals(starting);
            case "dt", "dd" -> "dt".equals(starting) || "dd".equals(starting);
            case "td", "th" -> CELLS.contains(starting) || "tr".equals(starting) || SECTIONS.contains(starting);
            case "tr" -> "tr".equals(starting) || SECTIONS.contains(starting);
            case "thead", "tbody", "tfoot" -> SECTIONS.contains(starting);
            case "option" -> "option".equals(starting) || "optgroup".equals(starting);
            default -> false;
        };
    }
}
