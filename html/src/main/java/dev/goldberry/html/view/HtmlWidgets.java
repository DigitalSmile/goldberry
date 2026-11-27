package dev.goldberry.html.view;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import dev.goldberry.content.ImageSource;
import dev.goldberry.content.image.Picture;
import dev.goldberry.content.inline.Words;
import dev.goldberry.content.select.ParagraphLine;
import dev.goldberry.content.select.Word;
import dev.goldberry.content.select.WordMinter;
import dev.goldberry.html.model.Comment;
import dev.goldberry.html.model.Element;
import dev.goldberry.html.model.HtmlDocument;
import dev.goldberry.html.model.HtmlNode;
import dev.goldberry.html.model.HtmlText;
import dev.goldberry.html.model.Tags;
import dev.goldberry.html.style.DocumentStyles;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.core.scroll.Scroll;
import dev.goldberry.widgets.core.scroll.ScrollAxis;

/// The page, as widgets.
///
/// Every node of the model becomes `column`, `row`, `text` and — for a link —
/// `button` from the catalog, under the ordinary cascade. There is no engine here, no
/// second text stack and nothing a theme cannot restyle, which is the same argument
/// `markdown-view` makes and the reason HTML could ship without litehtml at all.
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
/// - **Nothing here fetches an image.** An `<img>` is drawn as a [Picture] when the
///   application's [ImageSource] can find its `src`, and is its alt text when it
///   cannot — which is a line drawn on purpose and not a missing widget: no file is
///   opened and no socket, so a page full of remote images shows what its author
///   wrote for a reader who cannot see them.
/// - Inline typography and background colours apply to ranges in a shaped
///   paragraph. Block elements retain their own CSS boxes.
/// - **`sub` and `sup` are a size and not a baseline shift**, because the toolkit's
///   CSS subset has no `vertical-align`.
/// - **A form control draws nothing.** Interactive widgets are out of scope for a
///   content renderer; an `<input>` folds to an empty box rather than to a
///   `text-input` that would write somewhere nobody asked for.
///
/// All of it is in `docs/TODO.md` as well as here.
final class HtmlWidgets {

    /// The `html-word` namespace — see [Words], which the Markdown
    /// fold shares with this one. Per fold rather than static, because the words it
    /// makes report where they land.
    private final Words words;

    /// Where the blocks are, which only the fold knows — see [WordMinter].
    private final WordMinter minter;

    /// The inline elements whose own whitespace is content, so a fragment of one is
    /// atomic: `a  b` inside them is two spaces somebody typed.
    private static final Set<String> CODE = Set.of("code", "kbd", "samp", "var");

    /// What an anchor does when it is pressed, or null for a document nobody has
    /// wired — in which case a link is drawn, focusable and inert.
    private final @Nullable Consumer<String> onLink;

    /// Where `src` comes from, or null for a view that draws alt text.
    private final @Nullable ImageSource images;

    /// The class each element's own styles were minted as.
    private final DocumentStyles authored;

    HtmlWidgets(
            @Nullable Consumer<String> onLink,
            @Nullable ImageSource images,
            WordMinter minter,
            DocumentStyles authored) {
        this.onLink = onLink;
        this.images = images;
        this.minter = minter;
        this.authored = authored;
        this.words = Words.prefixed("html", minter);
    }

    /// With no styles of the document's own.
    HtmlWidgets(@Nullable Consumer<String> onLink, @Nullable ImageSource images, WordMinter minter) {
        this(onLink, images, minter, DocumentStyles.NONE);
    }

    /// The whole document, as one column.
    ///
    /// `attributes` is the view's own — an `id` and classes from a KDL document or from
    /// Java — merged with `html`, which is the class every rule in the stylesheet hangs
    /// off.
    Widget document(HtmlDocument document, Attributes attributes, Widget overlay) {
        // The overlay first: paint order is document order, so the wash goes behind
        // the words.
        var children = new ArrayList<Widget>();
        children.add(overlay);
        children.addAll(blocks(document.children(), null));
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
    private List<Widget> blocks(List<HtmlNode> nodes, @Nullable Locale language) {
        var widgets = new ArrayList<Widget>(nodes.size());
        var run = new Prose();
        for (var node : nodes) {
            if (node instanceof Comment) {
                continue;
            }
            if (isInline(node)) {
                run.add(node, Set.of(), language);
                continue;
            }
            flush(run, widgets);
            var block = block(node, language);
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
            case HtmlText _ -> true;
            case Element element -> Tags.isInline(element.tag()) && !Tags.isMetadata(element.tag());
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
    /// either took both.
    private void flush(Prose run, List<Widget> widgets) {
        if (run.isEmpty()) {
            return;
        }
        minter.block();
        var lines = run.take();
        // A run that held only whitespace produced no words at all, and a paragraph of
        // nothing is not a paragraph. One line and nothing on it is exactly that case:
        // `lines` never answers with an empty line unless a `br` put one there, and a
        // `br` is a line the author asked for.
        if (lines.size() == 1 && lines.getFirst().isEmpty()) {
            return;
        }
        widgets.add(paragraph(lines, classes("html-prose")));
    }

    /// One block, or null for a node with nothing to draw.
    private @Nullable Widget block(HtmlNode node, @Nullable Locale language) {
        return switch (node) {
            case Element element -> element(element, languageOf(element, language));
            // A comment is content the model keeps and a renderer has nothing to do
            // with; blank text between two tags is the author's own line break.
            case Comment _ -> null;
            case HtmlText _ -> null;
            case HtmlDocument document -> new Column(blocks(document.children(), language), classes("html"));
        };
    }

    private static @Nullable Locale languageOf(Element element, @Nullable Locale inherited) {
        var lang = element.attribute("lang");
        return lang == null ? inherited : Locale.forLanguageTag(lang.strip());
    }

    private @Nullable Widget element(Element element, @Nullable Locale language) {
        var tag = element.tag();
        if (Tags.isMetadata(tag)) {
            return null;
        }
        if (Tags.headingLevel(tag) > 0) {
            return prose(element, language, "html-prose");
        }
        return switch (tag) {
            case "p", "dt", "figcaption", "caption", "summary", "label", "legend" ->
                prose(element, language, "html-prose");
            case "ul", "dl" -> list(element, false, language);
            case "ol" -> list(element, true, language);
            case "li", "dd" -> new Column(blocks(element.children(), language), classesOf(element, "html-item-body"));
            case "blockquote" -> quote(element, language);
            case "pre" -> preformatted(element);
            case "hr" -> new Row(List.of(), classesOf(element, "html-rule"));
            case "table" -> table(element, language);
            case "thead", "tbody", "tfoot" -> new Column(rows(element, language), classesOf(element, "html-table"));
            // **A row with no table above it is still a row.** It reaches here only
            // when nothing folded it as part of one, and [#rows] matches `tr` and
            // sections and a `caption` — so a `tr` handed to it answered with no rows
            // at all and the cells went nowhere. HTML's own "in body" mode drops the
            // `<tr>` tag and keeps what is inside it; the model here keeps both
            // ([Element]), and the fold's rule for anything in the wrong place —
            // a stray paragraph in a list, a tag nobody has heard of — is to draw it
            // where it is.
            case "tr" -> row(element, true, language);
            case "td", "th" -> cell(element, true, language);
            default -> new Column(blocks(element.children(), language), classesOf(element, "html-block"));
        };
    }

    /// An element's inline content as the lines it is.
    private Widget prose(Element element, @Nullable Locale language, String... extra) {
        minter.block();
        var run = new Prose();
        element.children().forEach(child -> run.add(child, Set.of(), language));
        return paragraph(run.take(), classesOf(element, extra));
    }

    /// Hard breaks separate shaped lines inside a single styled block.
    private Widget paragraph(List<List<Word>> lines, Attributes attributes) {
        if (lines.size() == 1) {
            return ParagraphLine.of(lines.getFirst(), with(attributes, "html-line"));
        }
        var rows = new ArrayList<Widget>(lines.size());
        for (var line : lines) {
            rows.add(ParagraphLine.of(line, classes("html-line")));
        }
        return new Column(rows, with(attributes, "html-lines"));
    }

    /// A quotation: its blocks, in a box whose `border-left` is the bar.
    ///
    /// The bar was a widget beside the blocks while the toolkit's CSS subset had one
    /// border for a whole box and no way to ask for an edge. Now that a border has four
    /// sides it is a declaration in `html.css`, and `markdown-view` made the same move.
    private Widget quote(Element element, @Nullable Locale language) {
        var body = new Column(blocks(element.children(), language), classes("html-quote-body"));
        return new Row(List.of(body), classesOf(element, "html-quote"));
    }

    // --- lists -----------------------------------------------------------------

    /// A list: one row per item, its mark in the gutter.
    ///
    /// `start=` is honoured because a changelog's second page begins at 11, and a list
    /// that restarted at 1 would be wrong in a way only its author notices.
    private Widget list(Element element, boolean numbered, @Nullable Locale language) {
        var rows = new ArrayList<Widget>();
        var number = numbered ? start(element) : 0;
        for (var child : element.children()) {
            if (child instanceof Element item && ("li".equals(item.tag()) || "dd".equals(item.tag()))) {
                var mark = numbered ? number++ + "." : "•";
                rows.add(new Row(
                        List.of(marker("dd".equals(item.tag()) ? "" : mark), body(item, languageOf(item, language))),
                        classes("html-item")));
            } else if (child instanceof Element term && "dt".equals(term.tag())) {
                rows.add(prose(term, languageOf(term, language), "html-prose", "html-term"));
            } else {
                // Anything else inside a list — a stray paragraph, a nested list an
                // author forgot to put in an `li` — is drawn where it is rather than
                // dropped for being in the wrong place.
                var block = block(child, language);
                if (block != null) {
                    rows.add(block);
                }
            }
        }
        return new Column(rows, classesOf(element, "html-list", numbered ? "numbered" : "bulleted"));
    }

    /// An item's content, carrying the item's own styles: the `li` is this box
    /// and not the row with the mark in it.
    private Widget body(Element item, @Nullable Locale language) {
        var author = authored.classOf(item);
        var attributes = author == null ? classes("html-item-body") : classes("html-item-body", author);
        return new Column(blocks(item.children(), language), attributes);
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
        // In a viewport that moves sideways, so a long line is scrolled to rather
        // than cut short; one child, because a horizontal viewport is a row.
        var scroll = new Scroll(
                        List.of(new Column(lines, classes("html-code-lines"))),
                        ScrollAxis.HORIZONTAL,
                        classes("html-code-scroll"))
                .tabStopOnlyWhenScrollable();
        return new Column(List.of(scroll), classesOf(element, "html-pre"));
    }

    // --- tables ----------------------------------------------------------------

    private Widget table(Element element, @Nullable Locale language) {
        return new Column(rows(element, language), classesOf(element, "html-table"));
    }

    /// The rows of a table, with `thead`, `tbody` and `tfoot` flattened away.
    ///
    /// Flattened because the sections carry no geometry a flex column needs — a head is
    /// distinguished by its fill and its weight, which are rules on the row — and
    /// keeping them would put three boxes between a table and its rows for nothing. A
    /// `caption` is kept, as a line above the rows.
    private List<Widget> rows(Element element, @Nullable Locale language) {
        var rows = new ArrayList<Widget>();
        appendRows(element.children(), rows, language);
        return rows;
    }

    private void appendRows(List<HtmlNode> nodes, List<Widget> rows, @Nullable Locale inherited) {
        for (var node : nodes) {
            if (!(node instanceof Element element)) {
                continue;
            }
            var language = languageOf(element, inherited);
            switch (element.tag()) {
                // The first thing in the table draws no rule above it: the table's
                // own border is already there. A caption is a thing, so
                // a head under one is ruled off from it.
                case "tr" -> rows.add(row(element, rows.isEmpty(), language));
                case "thead", "tbody", "tfoot" -> appendRows(element.children(), rows, language);
                case "caption" -> rows.add(prose(element, language, "html-prose", "html-caption"));
                // A `colgroup` and its `col`s describe widths this cascade cannot set
                // (the subset has no `flex-basis`), so they are read and not drawn.
                default -> {}
            }
        }
    }

    /// @param first whether nothing is above this row in its table, which is what
    ///              draws no rule above it
    private Widget row(Element element, boolean first, @Nullable Locale language) {
        var cells = new ArrayList<Widget>();
        var header = false;
        for (var node : element.children()) {
            if (node instanceof Element cell && ("td".equals(cell.tag()) || "th".equals(cell.tag()))) {
                header = header || "th".equals(cell.tag());
                cells.add(cell(cell, cells.isEmpty(), languageOf(cell, language)));
            }
        }
        var kind = header ? "head" : "body";
        return new Row(
                cells, first ? classesOf(element, "html-row", kind, "first") : classesOf(element, "html-row", kind));
    }

    /// One cell — a column of its blocks, so that a cell holding two paragraphs is two
    /// paragraphs rather than one run of words.
    ///
    /// `align=` is honoured because it is what a hand-written table uses, and because
    /// the `text-align` an author would reach for instead is not in the CSS subset.
    ///
    /// @param first whether this is the leftmost cell of its row, which draws no
    ///              rule before it — markdown's `first`, for the same reason
    private Widget cell(Element element, boolean first, @Nullable Locale language) {
        minter.block();
        var classes = new ArrayList<String>(4);
        classes.add("html-cell");
        if ("th".equals(element.tag())) {
            classes.add("head-cell");
        }
        if (first) {
            classes.add("first");
        }
        var align = element.attribute("align");
        if (align != null) {
            switch (align.strip().toLowerCase(Locale.ROOT)) {
                case "center" -> classes.add("center");
                case "right", "end" -> classes.add("end");
                default -> {}
            }
        }
        return new Column(blocks(element.children(), language), classesOf(element, classes.toArray(String[]::new)));
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
        void add(HtmlNode node, Set<String> marks, @Nullable Locale language) {
            switch (node) {
                case HtmlText(var text) -> pending.add(new Words.Fragment(text, marks, false, language));
                case Comment _ -> {}
                case HtmlDocument document -> document.children().forEach(child -> add(child, marks, language));
                case Element element -> element(element, marks, languageOf(element, language));
            }
        }

        private void element(Element element, Set<String> marks, @Nullable Locale language) {
            var tag = element.tag();
            if (Tags.isMetadata(tag)) {
                return;
            }
            var own = Words.and(marks, "html-" + tag);
            for (var name : element.classes()) {
                if (DocumentStyles.forwardable(name)) {
                    own = Words.and(own, "html-" + name);
                }
            }
            own = authoredOver(own, authored.classOf(element));
            switch (tag) {
                // **A `br` ends the line, which is the whole of what it is for**
                // It is a boundary rather than anything drawn, so it goes
                // into the run as one and [#take()] is where the paragraph is cut at
                // it. No `spacer` with `flex-grow`: that fills the rest of the line,
                // which makes the line before a break look justified.
                case "br" -> pending.add(Words.Break.HARD);
                case "wbr" -> {}
                // The picture, or the alt text when nobody can say where the picture is.
                case "img" -> image(element, own, language);
                case "a" -> anchor(element, own, language);
                default -> {
                    if (CODE.contains(tag)) {
                        pending.add(new Words.Fragment(element.rawText(), own, true, language));
                    } else {
                        var inherited = own;
                        element.children().forEach(child -> add(child, inherited, language));
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
        private void image(Element element, Set<String> marks, @Nullable Locale language) {
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
                pending.add(new Words.Fragment(alt, marks, false, language));
            }
        }

        /// An anchor: a `button.link` that calls the application back.
        ///
        /// **This is the one thing `markdown-view` cannot do**, and the reason it can be
        /// done here is that an anchor in HTML is an element with a label, so the run it
        /// spans is one widget rather than four words that would each have to hover on
        /// their own. The variant is `button.link`, the button that reads as a link,
        /// which exists for a sentence's worth of action and is exactly that; it is a
        /// Tab stop, it takes `Space` and `Enter`, and `html.css` takes its height back
        /// down to the line it sits in.
        ///
        /// An anchor with no text — one wrapping only an image, or an empty `<a
        /// name="x">` — is *not* a button, because a button with nothing on it has
        /// nothing to click on and nothing to read out. Its content is folded
        /// inline instead, so an anchored heading keeps its words.
        private void anchor(Element element, Set<String> marks, @Nullable Locale language) {
            var href = element.attribute("href");
            var label = element.text();
            if (href == null || label.isBlank()) {
                var inherited = marks;
                element.children().forEach(child -> add(child, inherited, language));
                return;
            }
            var target = href;
            var press = onLink == null ? null : (Runnable) () -> onLink.accept(target);
            var classes = new LinkedHashSet<String>();
            // The `link` variant first, then the tag and the author's own classes, so
            // that a document restyling its links does not have to restate the variant.
            classes.add("link");
            classes.addAll(marks);
            // A `Node` and not a widget of its own, so the button is part of the token
            // it was written in: `<a>the help</a>.` keeps its full stop against it.
            pending.add(new Words.Node(
                    new Button(label, null, press, false, Attributes.NONE.classes(classes.toArray(String[]::new))),
                    label,
                    labelLanguage(element, language)));
        }

        /// Whether nothing has been put in it — asked before a block is opened for it,
        /// because a run holding nothing is not a paragraph and must not take a
        /// boundary the block after it needs.
        boolean isEmpty() {
            return pending.isEmpty();
        }

        /// Everything built so far **as the lines it is** — one list of tokens, or one
        /// per line where a `br` ended one — and this builder is empty again.
        List<List<Word>> take() {
            var taken = words.lines(List.copyOf(pending));
            pending.clear();
            return taken;
        }
    }

    /// A link button carries one flat label. Use its text's language when all
    /// the words agree, and leave a multilingual label to ordinary wrapping.
    private static @Nullable Locale labelLanguage(Element link, @Nullable Locale inherited) {
        var languages = new LinkedHashSet<@Nullable Locale>();
        collectLanguages(link.children(), inherited, languages);
        return languages.size() == 1 ? languages.getFirst() : Locale.ROOT;
    }

    private static void collectLanguages(
            List<HtmlNode> nodes, @Nullable Locale inherited, Set<@Nullable Locale> languages) {
        for (var node : nodes) {
            switch (node) {
                case HtmlText(var text) when !text.isBlank() -> languages.add(inherited);
                case Element child
                when !Tags.isRawText(child.tag()) ->
                    collectLanguages(child.children(), languageOf(child, inherited), languages);
                default -> {}
            }
            if (languages.size() > 1) {
                return;
            }
        }
    }

    // --- attributes ------------------------------------------------------------

    private static Attributes classes(String... names) {
        return Attributes.NONE.classes(names);
    }

    /// `base`, plus the element's tag, its own classes in the `html-` namespace and
    /// the class its own styles were minted as.
    private Attributes classesOf(Element element, String... base) {
        var classes = new LinkedHashSet<String>(base.length + 3);
        classes.addAll(List.of(base));
        classes.add("html-" + element.tag());
        for (var name : element.classes()) {
            if (DocumentStyles.forwardable(name)) {
                classes.add("html-" + name);
            }
        }
        var author = authored.classOf(element);
        if (author != null) {
            classes.add(author);
        }
        return Attributes.NONE.classes(classes.toArray(String[]::new));
    }

    /// `marks` with the generated class of the inline element around them replaced
    /// by `author`, the innermost one's, which already holds everything the outer
    /// ones said: a word carries one, so two rules of equal weight never have to
    /// agree on it.
    private static Set<String> authoredOver(Set<String> marks, @Nullable String author) {
        if (author == null && marks.stream().noneMatch(mark -> mark.startsWith(DocumentStyles.PREFIX))) {
            return marks;
        }
        var replaced = new LinkedHashSet<String>(marks.size() + 1);
        for (var mark : marks) {
            if (!mark.startsWith(DocumentStyles.PREFIX)) {
                replaced.add(mark);
            }
        }
        if (author != null) {
            replaced.add(author);
        }
        return replaced;
    }

    /// `attributes` with `first` added to its classes, keeping its id and key.
    private static Attributes with(Attributes attributes, String first) {
        var classes = new ArrayList<String>(attributes.classes().size() + 1);
        classes.add(first);
        classes.addAll(attributes.classes());
        return attributes.classes(classes.toArray(String[]::new));
    }
}
