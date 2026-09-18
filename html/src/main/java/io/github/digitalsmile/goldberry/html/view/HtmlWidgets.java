package io.github.digitalsmile.goldberry.html.view;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.content.ImageSource;
import io.github.digitalsmile.goldberry.content.image.Picture;
import io.github.digitalsmile.goldberry.content.inline.Words;
import io.github.digitalsmile.goldberry.content.select.WordMinter;
import io.github.digitalsmile.goldberry.html.model.Comment;
import io.github.digitalsmile.goldberry.html.model.Element;
import io.github.digitalsmile.goldberry.html.model.HtmlDocument;
import io.github.digitalsmile.goldberry.html.model.HtmlNode;
import io.github.digitalsmile.goldberry.html.model.HtmlText;
import io.github.digitalsmile.goldberry.html.model.Tags;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.controls.button.Button;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.core.Row;

/// The page, as widgets.
///
/// Every node of the model becomes `column`, `row`, `text` and — for a link —
/// `button` from the catalog, under the ordinary cascade. There is no engine here, no
/// second text stack and nothing a theme cannot restyle, which is the same argument
/// `markdown-view` makes and the reason HTML could ship without litehtml at all
/// (ADR-0295, ADR-0298).
///
/// ## The class on a widget is the tag that produced it
///
/// There is no table in this file mapping `em` to "emphasis". An element contributes
/// **`html-<tag>`** and the stylesheet decides what that looks like, so `html.css`
/// reads like a browser's default sheet and a tag nobody anticipated is already
/// styleable. Only the handful of tags whose *structure* differs — a list's gutter, a
/// quotation's bar, a fence's lines, an anchor's button — are named in Java.
///
/// A document's own `class="callout"` comes through as **`html-callout`**: the same
/// namespace as the tag classes, so a page cannot be restyled by an application's
/// rule for its own `.callout`, and an application that wants to style its documents
/// has one prefix to learn. An `id` is **not** forwarded, because an id is unique in a
/// tree and a document's ids are the author's rather than the application's.
///
/// ## What this cannot do, said out loud
///
/// - **An image is its alt text.** There is no `img` widget in the catalog and
///   fetching anything is the application's (ADR-0190) — the same limit
///   `markdown-view` has, and the one thing on this list that an `img` widget would
///   close rather than an engine.
/// - **A line is a row of words, not a shaped run.** Mixed faces on one line are one
///   `text` widget per word, so justification and hyphenation are not available. This
///   is the one that *is* litehtml's, and it is why ADR-0298 leaves the engine open
///   rather than closed. **Selecting** across those words does work, because each one
///   reports where it was painted (ADR-0301).
/// - **`<style>` and `style=` are read into the model and applied by nothing.** The
///   cascade here is the application's stylesheets, which is what makes a page follow
///   the theme; an author's colours would fight it.
/// - **`sub` and `sup` are a size and not a baseline shift**, because §10's subset has
///   no `vertical-align`.
/// - **A form control draws nothing.** `docs/content-widgets.md` §1.5 puts interactive
///   widgets out of scope for a content renderer; an `<input>` folds to an empty box
///   rather than to a `text-input` that would write somewhere nobody asked for.
///
/// All of it is in `book/src/TODO.md` as well as here.
final class HtmlWidgets {

    /// The `html-word` / `html-token` namespace — see [Words], which the Markdown
    /// fold shares with this one. Per fold since ADR-0301, because the words it makes
    /// report where they land.
    private final Words words;

    /// Where the blocks are, which only the fold knows — see [WordMinter].
    private final WordMinter minter;

    /// The elements whose content is a program, a stylesheet or metadata: nothing to
    /// draw. `head` is here rather than in [Tags#isRawText] because it is ordinary
    /// markup that simply is not content.
    private static final Set<String> SKIPPED = Set.of("head", "title", "script", "style", "meta", "link", "base");

    /// The inline elements whose own whitespace is content, so a fragment of one is
    /// atomic: `a  b` inside them is two spaces somebody typed.
    private static final Set<String> CODE = Set.of("code", "kbd", "samp", "var");

    /// What an anchor does when it is pressed, or null for a document nobody has
    /// wired — in which case a link is drawn, focusable and inert.
    private final @Nullable Consumer<String> onLink;

    /// Where `src` comes from, or null for a view that draws alt text.
    private final @Nullable ImageSource images;

    HtmlWidgets(@Nullable Consumer<String> onLink, @Nullable ImageSource images, WordMinter minter) {
        this.onLink = onLink;
        this.images = images;
        this.minter = minter;
        this.words = Words.prefixed("html", minter);
    }

    /// The whole document, as one column.
    ///
    /// `attributes` is the view's own — an `id` and classes from a KDL document or from
    /// Java — merged with `html`, which is the class every rule in the stylesheet hangs
    /// off.
    Widget document(HtmlDocument document, Attributes attributes, Widget overlay) {
        // The overlay first: paint order is document order, so the wash goes behind
        // the words (ADR-0301).
        var children = new ArrayList<Widget>();
        children.add(overlay);
        children.addAll(blocks(document.children()));
        return new Column(children, with(attributes, "html"));
    }

    // --- blocks ----------------------------------------------------------------

    /// A run of nodes as the boxes that stack down the page.
    ///
    /// **Inline content between two blocks becomes a paragraph nobody wrote**, which is
    /// what a browser does with `<div>Hello <b>there</b><p>…`: the words before the `p`
    /// are a line of their own, and a renderer that dropped them would lose text that
    /// is on every second page written by hand. The implicit paragraph carries
    /// `html-prose` and no tag class, because no tag produced it.
    private List<Widget> blocks(List<HtmlNode> nodes) {
        var widgets = new ArrayList<Widget>(nodes.size());
        var run = new Prose();
        for (var node : nodes) {
            if (isInline(node)) {
                run.add(node, Set.of());
                continue;
            }
            flush(run, widgets);
            var block = block(node);
            if (block != null) {
                widgets.add(block);
            }
        }
        flush(run, widgets);
        return widgets;
    }

    /// Whether `node` flows along a line rather than stacking.
    ///
    /// Whitespace between two blocks is **not** inline content: it is the newline an
    /// author put between two tags, and treating it as a word would open a paragraph
    /// holding one space in front of every heading in the document. Whitespace *inside*
    /// a run is ordinary text, which is [Prose]'s business rather than this test's.
    private static boolean isInline(HtmlNode node) {
        return switch (node) {
            case HtmlText text -> !text.isBlank();
            case Element element -> Tags.isInline(element.tag()) && !SKIPPED.contains(element.tag());
            case Comment _ -> false;
            case HtmlDocument _ -> false;
        };
    }

    /// The run so far as the paragraph nobody wrote, if it holds anything.
    ///
    /// **The block boundary is announced here and not once at the top of [#blocks]**,
    /// because where it is announced decides which block the words land in. An implicit
    /// paragraph can be the *last* thing in a parent — `<div><p>one</p>two</div>` — and
    /// a boundary declared before the `p` was folded is one the `p` has already taken:
    /// the trailing words were minted into the paragraph above them, so a copy joined
    /// the two with a space where the document means a newline and a triple-click on
    /// either took both (ADR-0301).
    private void flush(Prose run, List<Widget> widgets) {
        if (run.isEmpty()) {
            return;
        }
        minter.block();
        var line = run.take();
        if (!line.isEmpty()) {
            widgets.add(new Row(line, classes("html-prose")));
        }
    }

    /// One block, or null for a node with nothing to draw.
    private @Nullable Widget block(HtmlNode node) {
        return switch (node) {
            case Element element -> element(element);
            // A comment is content the model keeps and a renderer has nothing to do
            // with; blank text between two tags is the author's own line break.
            case Comment _ -> null;
            case HtmlText _ -> null;
            case HtmlDocument document -> new Column(blocks(document.children()), classes("html"));
        };
    }

    private @Nullable Widget element(Element element) {
        var tag = element.tag();
        if (SKIPPED.contains(tag)) {
            return null;
        }
        if (Tags.headingLevel(tag) > 0) {
            return prose(element, "html-prose");
        }
        return switch (tag) {
            case "p", "dt", "figcaption", "caption", "summary", "label", "legend" -> prose(element, "html-prose");
            case "ul", "dl" -> list(element, false);
            case "ol" -> list(element, true);
            case "li", "dd" -> new Column(blocks(element.children()), classesOf(element, "html-item-body"));
            case "blockquote" -> quote(element);
            case "pre" -> preformatted(element);
            case "hr" -> new Row(List.of(), classesOf(element, "html-rule"));
            case "table" -> table(element);
            case "thead", "tbody", "tfoot" -> new Column(rows(element), classesOf(element, "html-table"));
            // **A row with no table above it is still a row.** It reaches here only
            // when nothing folded it as part of one, and [#rows] matches `tr` and
            // sections and a `caption` — so a `tr` handed to it answered with no rows
            // at all and the cells went nowhere. HTML's own "in body" mode drops the
            // `<tr>` tag and keeps what is inside it; the model here keeps both
            // ([Element]), and the fold's rule for anything in the wrong place —
            // a stray paragraph in a list, a tag nobody has heard of — is to draw it
            // where it is.
            case "tr" -> row(element);
            case "td", "th" -> cell(element);
            default -> new Column(blocks(element.children()), classesOf(element, "html-block"));
        };
    }

    /// An element's inline content as a wrapping row of words.
    private Widget prose(Element element, String... extra) {
        minter.block();
        var run = new Prose();
        element.children().forEach(child -> run.add(child, Set.of()));
        return new Row(run.take(), classesOf(element, extra));
    }

    /// A quotation: the bar, and the blocks beside it.
    ///
    /// The bar is a widget rather than a `border-left`, because §10's CSS subset has one
    /// border for a whole box and no way to ask for an edge — the same move
    /// `markdown-view` makes, and `html.css` says so again beside the rule.
    private Widget quote(Element element) {
        var bar = new Row(List.of(), classes("html-quote-bar"));
        var body = new Column(blocks(element.children()), classes("html-quote-body"));
        return new Row(List.of(bar, body), classesOf(element, "html-quote"));
    }

    // --- lists -----------------------------------------------------------------

    /// A list: one row per item, its mark in the gutter.
    ///
    /// `start=` is honoured because a changelog's second page begins at 11, and a list
    /// that restarted at 1 would be wrong in a way only its author notices.
    private Widget list(Element element, boolean numbered) {
        var rows = new ArrayList<Widget>();
        var number = numbered ? start(element) : 0;
        for (var child : element.children()) {
            if (child instanceof Element item && ("li".equals(item.tag()) || "dd".equals(item.tag()))) {
                var mark = numbered ? number++ + "." : "•";
                rows.add(new Row(
                        List.of(marker("dd".equals(item.tag()) ? "" : mark), body(item)), classes("html-item")));
            } else if (child instanceof Element term && "dt".equals(term.tag())) {
                rows.add(prose(term, "html-prose", "html-term"));
            } else {
                // Anything else inside a list — a stray paragraph, a nested list an
                // author forgot to put in an `li` — is drawn where it is rather than
                // dropped for being in the wrong place.
                var block = block(child);
                if (block != null) {
                    rows.add(block);
                }
            }
        }
        return new Column(rows, classesOf(element, "html-list", numbered ? "numbered" : "bulleted"));
    }

    private Widget body(Element item) {
        return new Column(blocks(item.children()), classes("html-item-body"));
    }

    private Widget marker(String text) {
        minter.block();
        return new Row(text.isEmpty() ? List.of() : List.of(words.whole(text, Set.of())), classes("html-marker"));
    }

    /// An `ol`'s `start=`, or 1 — including for a value that is not a number, because a
    /// list numbered from `start="first"` is still a list.
    private static int start(Element element) {
        var value = element.attribute("start");
        if (value == null) {
            return 1;
        }
        try {
            return Integer.parseInt(value.strip());
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    // --- code ------------------------------------------------------------------

    /// A `pre`, as one `text` per line.
    ///
    /// One per line and not one `text` holding the newlines, because a `text` wraps and
    /// a program does not: the lines are boxes in a column, each clipped at the block's
    /// edge. [Element#rawText()] is what is drawn — the author's own spacing, with no
    /// space inserted at the `<span>`s a highlighter may have left in it.
    private Widget preformatted(Element element) {
        var text = element.rawText();
        // The newline immediately after `<pre>` is not content -- HTML says so, and a
        // fence that opened with a blank line is what ignoring it looks like.
        var body = text.startsWith("\n") ? text.substring(1) : text;
        var lines = new ArrayList<Widget>();
        for (var line : body.stripTrailing().split("\n", -1)) {
            minter.block();
            // An empty line still gets a widget: it is a line of the program. A space
            // rather than an empty string, because a `text` with nothing in it measures
            // zero high.
            lines.add(words.whole(line.isEmpty() ? " " : line, Set.of("html-code-line")));
        }
        return new Column(lines, classesOf(element, "html-pre"));
    }

    // --- tables ----------------------------------------------------------------

    private Widget table(Element element) {
        return new Column(rows(element), classesOf(element, "html-table"));
    }

    /// The rows of a table, with `thead`, `tbody` and `tfoot` flattened away.
    ///
    /// Flattened because the sections carry no geometry a flex column needs — a head is
    /// distinguished by its fill and its weight, which are rules on the row — and
    /// keeping them would put three boxes between a table and its rows for nothing. A
    /// `caption` is kept, as a line above the rows.
    private List<Widget> rows(Element element) {
        var rows = new ArrayList<Widget>();
        appendRows(element.children(), rows);
        return rows;
    }

    private void appendRows(List<HtmlNode> nodes, List<Widget> rows) {
        for (var node : nodes) {
            if (!(node instanceof Element element)) {
                continue;
            }
            switch (element.tag()) {
                case "tr" -> rows.add(row(element));
                case "thead", "tbody", "tfoot" -> appendRows(element.children(), rows);
                case "caption" -> rows.add(prose(element, "html-prose", "html-caption"));
                // A `colgroup` and its `col`s describe widths this cascade cannot set
                // (§10 has no `flex-basis`), so they are read and not drawn.
                default -> {}
            }
        }
    }

    private Widget row(Element element) {
        var cells = new ArrayList<Widget>();
        var header = false;
        for (var node : element.children()) {
            if (node instanceof Element cell && ("td".equals(cell.tag()) || "th".equals(cell.tag()))) {
                header = header || "th".equals(cell.tag());
                cells.add(cell(cell));
            }
        }
        return new Row(cells, classesOf(element, "html-row", header ? "head" : "body"));
    }

    /// One cell — a column of its blocks, so that a cell holding two paragraphs is two
    /// paragraphs rather than one run of words.
    ///
    /// `align=` is honoured because it is what a hand-written table uses, and because
    /// the `text-align` an author would reach for instead is not in §10's subset.
    private Widget cell(Element element) {
        minter.block();
        var classes = new ArrayList<String>(3);
        classes.add("html-cell");
        if ("th".equals(element.tag())) {
            classes.add("head-cell");
        }
        var align = element.attribute("align");
        if (align != null) {
            switch (align.strip().toLowerCase(Locale.ROOT)) {
                case "center" -> classes.add("center");
                case "right", "end" -> classes.add("end");
                default -> {}
            }
        }
        return new Column(blocks(element.children()), classesOf(element, classes.toArray(String[]::new)));
    }

    // --- inline ----------------------------------------------------------------

    /// A line being built: the words so far, and the widgets that are not words.
    ///
    /// A tiny mutable builder, because an inline run is **not** a list of fragments any
    /// more — a link is a `button` in the middle of one, and [Words] knows how to turn
    /// fragments into words and nothing about widgets that arrive between them. So the
    /// fragments before a button are flushed into words, the button goes in, and the
    /// run carries on.
    private final class Prose {

        private final List<Words.Piece> pending = new ArrayList<>();

        /// `node`, and everything inside it, as words and widgets.
        void add(HtmlNode node, Set<String> marks) {
            switch (node) {
                case HtmlText(var text) -> pending.add(new Words.Fragment(text, marks, false));
                case Comment _ -> {}
                case HtmlDocument document -> document.children().forEach(child -> add(child, marks));
                case Element element -> element(element, marks);
            }
        }

        private void element(Element element, Set<String> marks) {
            var tag = element.tag();
            if (SKIPPED.contains(tag)) {
                return;
            }
            var own = Words.and(marks, "html-" + tag);
            for (var name : element.classes()) {
                own = Words.and(own, "html-" + name);
            }
            switch (tag) {
                // **A break ends the token and nothing more.** A wrapping row breaks
                // where the width runs out and there is no widget meaning "start a new
                // line here" -- a `spacer` with `flex-grow` would make the line before
                // it look justified. The same limit `markdown-view` has, noted in
                // TODO.md.
                case "br" -> pending.add(Words.Fragment.SEPARATOR);
                case "wbr" -> {}
                // The alt text, which is what an alt text is for.
                case "img" -> image(element, own);
                case "a" -> anchor(element, own);
                default -> {
                    if (CODE.contains(tag)) {
                        pending.add(new Words.Fragment(element.rawText(), own, true));
                    } else {
                        var inherited = own;
                        element.children().forEach(child -> add(child, inherited));
                    }
                }
            }
        }

        /// An `<img>`: the picture when the application has one, the alt text when
        /// it does not.
        ///
        /// A [Words.Node] rather than a block of its own, because that is what an
        /// `img` **is** in HTML — an inline replaced element, so it sits in the line
        /// it was written in and wraps with the words round it.
        private void image(Element element, Set<String> marks) {
            var src = element.attribute("src");
            var alt = element.attribute("alt");
            var image = src == null || images == null ? null : images.image(src);
            if (image != null) {
                // Nothing for a copy, which is what a browser puts on the clipboard
                // for an image.
                pending.add(new Words.Node(new Picture(image, alt == null ? "" : alt), ""));
                return;
            }
            if (alt != null && !alt.isBlank()) {
                pending.add(new Words.Fragment(alt, marks, false));
            }
        }

        /// An anchor: a `button.link` that calls the application back.
        ///
        /// **This is the one thing `markdown-view` cannot do**, and the reason it can be
        /// done here is that an anchor in HTML is an element with a label, so the run it
        /// spans is one widget rather than four words that would each have to hover on
        /// their own (`docs/gaps.md` G17). The variant is ADR-0293's `button.link`,
        /// which arrived for a sentence's worth of action and is exactly that; it is a
        /// Tab stop, it takes `Space` and `Enter`, and `html.css` takes its height back
        /// down to the line it sits in.
        ///
        /// An anchor with no text — one wrapping only an image, or an empty `<a
        /// name="x">` — is *not* a button, because a button with nothing on it has
        /// nothing to click on and nothing to read out (§13). Its content is folded
        /// inline instead, so an anchored heading keeps its words.
        private void anchor(Element element, Set<String> marks) {
            var href = element.attribute("href");
            var label = element.text();
            if (href == null || label.isBlank()) {
                var inherited = marks;
                element.children().forEach(child -> add(child, inherited));
                return;
            }
            var target = href;
            var press = onLink == null ? null : (Runnable) () -> onLink.accept(target);
            var classes = new LinkedHashSet<String>();
            // ADR-0293's variant first, then the tag and the author's own classes, so
            // that a document restyling its links does not have to restate the variant.
            classes.add("link");
            classes.addAll(marks);
            // A `Node` and not a widget of its own, so the button is part of the token
            // it was written in: `<a>the help</a>.` keeps its full stop against it.
            pending.add(new Words.Node(
                    new Button(label, null, press, false, Attributes.NONE.classes(classes.toArray(String[]::new))),
                    label));
        }

        /// Whether nothing has been put in it — asked before a block is opened for it,
        /// because a run holding nothing is not a paragraph and must not take a
        /// boundary the block after it needs.
        boolean isEmpty() {
            return pending.isEmpty();
        }

        /// Everything built so far, and this builder is empty again.
        List<Widget> take() {
            var taken = words.tokens(List.copyOf(pending));
            pending.clear();
            return taken;
        }
    }

    // --- attributes ------------------------------------------------------------

    private static Attributes classes(String... names) {
        return Attributes.NONE.classes(names);
    }

    /// `base`, plus the element's tag and its own classes in the `html-` namespace.
    private static Attributes classesOf(Element element, String... base) {
        var classes = new LinkedHashSet<String>(base.length + 2);
        classes.addAll(List.of(base));
        classes.add("html-" + element.tag());
        for (var name : element.classes()) {
            classes.add("html-" + name);
        }
        return Attributes.NONE.classes(classes.toArray(String[]::new));
    }

    /// `attributes` with `first` added to its classes, keeping its id and key.
    private static Attributes with(Attributes attributes, String first) {
        var classes = new ArrayList<String>(attributes.classes().size() + 1);
        classes.add(first);
        classes.addAll(attributes.classes());
        return attributes.classes(classes.toArray(String[]::new));
    }
}
