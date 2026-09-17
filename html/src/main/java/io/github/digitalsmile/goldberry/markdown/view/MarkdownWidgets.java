package io.github.digitalsmile.goldberry.markdown.view;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.content.ImageSource;
import io.github.digitalsmile.goldberry.content.image.Picture;
import io.github.digitalsmile.goldberry.content.inline.Words;
import io.github.digitalsmile.goldberry.content.select.WordGeometry;
import io.github.digitalsmile.goldberry.content.select.WordMinter;
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
import io.github.digitalsmile.goldberry.markdown.model.Inlines;
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
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.controls.button.Button;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.core.Row;

/// The document, as widgets.
///
/// Every node of the model becomes `column`, `row` and `text` from the catalog, with
/// classes on them that `markdown.css` styles — so a rendered document is laid out by
/// Yoga, styled by the cascade and painted by the same painter as every button on the
/// screen. There is no engine here, no second text stack and nothing that a theme
/// cannot restyle, which is what made Markdown the toolkit's problem rather than an
/// application's (ADR-0295, `docs/gaps.md` G8).
///
/// ## What it does when the application has wired it
///
/// Three things that used to be on the list below and are not any more, each because
/// an application answered a question only it can (ADR-0300):
///
/// - **A link is a `button.link`** when the view has an `onLink` — one widget for the
///   whole run, so it is a Tab stop and a hover rather than four coloured words.
///   Following it is still the application's, and a view with no handler draws the
///   link inert.
/// - **An image is drawn** when an [ImageSource] can find it, and is its alt text
///   otherwise. Nothing here fetches anything.
/// - **A task box is pressable** when the view has an `onTask`, which is handed the
///   task's **ordinal** — the nth task in the document. `Markdown.toggleTask` is the
///   other half: it flips that one marker in the source, and the binding brings the
///   new document back.
///
/// ## What this cannot do, said out loud
///
/// - **A line of mixed faces is a row of words, not a shaped run**, so there is no
///   justification and no hyphenation. This is the one an engine would close, and it
///   is what `book/src/TODO.md` still tracks. Selecting text across those words does
///   work — the words say where they landed (ADR-0301).
/// - **A table has no rules between its cells**, because §10's `border` is uniform
///   — there is no `border-left` to draw one with. The head's fill and the space in
///   the cells are what separate them.
/// - **A hard break inside a paragraph does nothing.** A wrapping row has no widget
///   meaning "start a new line here"; the HTML writer emits the `<br>` it deserves.
///
/// All four are in `book/src/TODO.md` rather than only here.
final class MarkdownWidgets {

    /// The `md-word` / `md-token` namespace, which is all this fold and
    /// `html-view`'s disagree about — see [Words]. Per fold rather than static since
    /// ADR-0301: the words it makes report where they land, and where they land is a
    /// fact about *this* document.
    private final Words words;

    /// Where the blocks are, which only the fold knows — see [WordMinter].
    private final WordMinter minter;

    /// What a link hands its href to, or null for a document whose links are drawn
    /// and inert.
    private final @Nullable Consumer<String> onLink;

    /// The same for a `[[wiki link]]`, which hands over its **target** rather than an
    /// href: what a target names is a thing in the application's own collection,
    /// which is the reason the extension exists at all (ADR-0295).
    private final @Nullable Consumer<String> onWikiLink;

    /// Where an image's source comes from, or null for a document that draws alt text.
    private final @Nullable ImageSource images;

    /// What a task's check box reports when it is pressed — the ordinal below.
    private final @Nullable IntConsumer onTask;

    /// How many task items this fold has built, which is the **index** the next one
    /// reports.
    ///
    /// A field rather than a parameter threaded through nine methods, and the reason
    /// it is safe is that a fold is one walk of one document by one object: the
    /// counter is created with it and thrown away with it. It is also why this class
    /// stopped being static — see [MarkdownView#build].
    private int tasksSeen;

    MarkdownWidgets(
            @Nullable Consumer<String> onLink,
            @Nullable Consumer<String> onWikiLink,
            @Nullable ImageSource images,
            @Nullable IntConsumer onTask,
            WordGeometry geometry) {
        this.onLink = onLink;
        this.onWikiLink = onWikiLink;
        this.images = images;
        this.onTask = onTask;
        this.minter = new WordMinter(geometry);
        this.words = Words.prefixed("md", minter);
    }

    /// The whole document, as one column.
    ///
    /// `attributes` is the view's own — an `id` and classes from a KDL document or
    /// from Java — merged with `markdown`, which is the class every rule in the
    /// stylesheet hangs off.
    Widget document(Document document, Attributes attributes, Widget overlay) {
        // The overlay first, because paint order is document order and the wash goes
        // behind the words (ADR-0301). It is absolutely positioned, so it is in no
        // column and takes no gap.
        var children = new ArrayList<Widget>();
        children.add(overlay);
        children.addAll(blocks(document.blocks()));
        return new Column(children, with(attributes, "markdown"));
    }

    private List<Widget> blocks(List<? extends Block> blocks) {
        var widgets = new ArrayList<Widget>(blocks.size());
        for (var block : blocks) {
            widgets.add(block(block));
        }
        return widgets;
    }

    private Widget block(Block block) {
        // Every block is where a copied selection gets a newline and where a
        // triple-click stops (ADR-0301).
        minter.block();
        return switch (block) {
            case Document(var children) -> new Column(blocks(children), classes("markdown"));
            case Heading(var level, var content) -> prose(content, "md-heading", "md-h" + level);
            case Paragraph(var content) -> prose(content);
            case Quote(var children) -> quote(children);
            case BulletList(var tight, var items) -> bulletList(tight, items);
            case NumberedList(var start, var tight, var items) -> numberedList(start, tight, items);
            case Item item -> item(marker("•"), item);
            case CodeBlock codeBlock -> codeBlock(codeBlock);
            case ThematicBreak _ -> new Row(List.of(), classes("md-rule"));
            // The markup as the text it is. A widget renderer with no HTML engine
            // under it has no honest alternative, and showing nothing would lose
            // content the author meant to keep.
            case HtmlBlock(var html) -> new Row(List.of(words.whole(html, Set.of("md-raw"))), classes("md-prose"));
            case Table table -> table(table);
            case TableRow row -> row(row);
            case TableCell cell -> cell(cell, false);
        };
    }

    // --- prose -----------------------------------------------------------------

    /// A run of inline content as a wrapping row of words.
    private Widget prose(List<Inline> content, String... extra) {
        minter.block();
        var fragments = new ArrayList<Words.Piece>();
        inlines(fragments, content, Set.of());
        var classes = new ArrayList<String>(extra.length + 1);
        classes.add("md-prose");
        classes.addAll(List.of(extra));
        return new Row(words.tokens(fragments), classes(classes.toArray(String[]::new)));
    }

    /// Walks the inline tree, carrying the marks a fragment is inside down with it.
    ///
    /// Fragments rather than widgets, because a word can span two of them: `*a*, b` is
    /// an emphasised fragment and then `, b`, and the comma belongs to the word in
    /// front of it. [Words#tokens] is what groups them.
    private void inlines(List<Words.Piece> out, List<? extends Inline> content, Set<String> marks) {
        for (var inline : content) {
            switch (inline) {
                case Text(var text) -> out.add(new Words.Fragment(text, marks, false));
                // Whitespace inside a code span is content, so the fragment is atomic:
                // `a  b` in backticks is two spaces the author wrote.
                case Code(var code) -> out.add(new Words.Fragment(code, Words.and(marks, "md-code"), true));
                case Emphasis(var children) -> inlines(out, children, Words.and(marks, "md-em"));
                case Strong(var children) -> inlines(out, children, Words.and(marks, "md-strong"));
                case Struck(var children) -> inlines(out, children, Words.and(marks, "md-struck"));
                case Underlined(var children) -> inlines(out, children, Words.and(marks, "md-underline"));
                case Link(var href, var _, var _, var children) ->
                    link(out, href, children, Words.and(marks, "md-link"), onLink);
                case WikiLink(var target, var children) ->
                    link(out, target, children, Words.and(marks, "md-wikilink"), onWikiLink);
                case Image(var src, var _, var alt) -> image(out, src, alt, Words.and(marks, "md-image"));
                case RawHtml(var html) -> out.add(new Words.Fragment(html, Words.and(marks, "md-raw"), true));
                // **A break ends the token and nothing more, hard or soft.** A
                // wrapping row breaks where the width runs out, and there is no widget
                // that means "start a new line here" -- a `spacer` with `flex-grow`
                // would fill the rest of the line, which is the trick this deliberately
                // does not play: it would make a hard break look like justified text.
                // Noted in TODO.md; `html-view` is where a hard break becomes a `<br>`
                // that means it.
                case LineBreak _ -> out.add(Words.Fragment.SEPARATOR);
            }
        }
    }

    /// A link, as the one widget a reader can press.
    ///
    /// **This is what `markdown-view` could not do**, and the reason it can now is not
    /// an engine: it is that [Words.Piece] can carry a widget, so a link's whole run is
    /// a single `button.link` in the middle of a line rather than four words that would
    /// each have to hover on their own (ADR-0293, ADR-0300). The full stop after it
    /// stays against it, because the button is part of the same token.
    ///
    /// A link with **no text** — one wrapping only an image, or an empty destination —
    /// is folded inline instead, because a button with nothing on it has nothing to
    /// click and nothing to read out (§13).
    private void link(
            List<Words.Piece> out,
            String destination,
            List<Inline> children,
            Set<String> marks,
            @Nullable Consumer<String> handler) {

        var label = Inlines.text(children);
        if (destination.isBlank() || label.isBlank()) {
            inlines(out, children, marks);
            return;
        }
        var target = destination;
        var press = handler == null ? null : (Runnable) () -> handler.accept(target);
        var classes = new LinkedHashSet<String>();
        // ADR-0293's variant first, then the marks -- so a stylesheet restyling a
        // document's links does not have to restate the variant.
        classes.add("link");
        classes.addAll(marks);
        out.add(new Words.Node(
                new Button(label, null, press, false, Attributes.NONE.classes(classes.toArray(String[]::new))), label));
    }

    /// An image: the picture when the application can find it, the alt text when it
    /// cannot.
    private void image(List<Words.Piece> out, String src, String alt, Set<String> marks) {
        var image = images == null ? null : images.image(src);
        if (image != null) {
            // Nothing for a copy, which is what a browser puts on the clipboard for an
            // image: the alt text is what a reader who cannot see it is told, not what
            // somebody copying the sentence round it wants in the middle of it.
            out.add(new Words.Node(new Picture(image, alt), ""));
            return;
        }
        if (!alt.isBlank()) {
            out.add(new Words.Fragment(alt, marks, false));
        }
    }

    /// A quotation: the bar, and the blocks beside it.
    ///
    /// The bar is a widget rather than a `border-left`, because §10's CSS subset has
    /// one border for a whole box and no way to ask for an edge — see `markdown.css`.
    private Widget quote(List<Block> children) {
        var bar = new Row(List.of(), classes("md-quote-bar"));
        var body = new Column(blocks(children), classes("md-quote-body"));
        return new Row(List.of(bar, body), classes("md-quote"));
    }

    // --- lists -----------------------------------------------------------------

    private Widget bulletList(boolean tight, List<Item> items) {
        var rows = new ArrayList<Widget>(items.size());
        for (var item : items) {
            rows.add(item(marker("•"), item));
        }
        return new Column(rows, classes("md-list", tight ? "tight" : "loose"));
    }

    private Widget numberedList(int start, boolean tight, List<Item> items) {
        var rows = new ArrayList<Widget>(items.size());
        for (var i = 0; i < items.size(); i++) {
            rows.add(item(marker((start + i) + "."), items.get(i)));
        }
        return new Column(rows, classes("md-list", tight ? "tight" : "loose"));
    }

    /// One item: its mark in the gutter, its blocks beside it.
    ///
    /// A task's mark is the drawn check box rather than the bullet, because a list of
    /// things to do is not a list of bullet points — and that is the one place a
    /// document's *state* shows in the rendering.
    private Widget item(Widget bullet, Item item) {
        // The ordinal is assigned **here**, in document order, and it is what
        // `Markdown.toggleTask` counts in the source: the nth task box on the screen
        // is the nth task marker in the text.
        var mark = item.task() ? taskMark(item.done()) : bullet;
        var body = new Column(blocks(item.blocks()), classes("md-item-body"));
        return new Row(List.of(mark, body), classes("md-item"));
    }

    /// A task's check box, pressable when the view was told what a press means.
    private Widget taskMark(boolean done) {
        var index = tasksSeen++;
        var handler = onTask;
        return new TaskMark(done, handler == null ? null : () -> handler.accept(index));
    }

    private Widget marker(String text) {
        minter.block();
        return new Row(List.of(words.whole(text, Set.of())), classes("md-marker"));
    }

    // --- code ------------------------------------------------------------------

    /// A fence, as one `text` per line.
    ///
    /// One per line and not one `text` holding the newlines, because a `text` wraps
    /// and a program does not: the lines are boxes in a column, each clipped at the
    /// fence's edge. The language, when the author named one, is a caption above them
    /// — which is also what tells a reader that `goldberry-code` would have
    /// highlighted it.
    private Widget codeBlock(CodeBlock block) {
        var lines = new ArrayList<Widget>();
        var language = block.language();
        if (language != null && !language.isBlank()) {
            minter.block();
            lines.add(words.whole(language, Set.of("md-code-language")));
        }
        for (var line : block.lines()) {
            minter.block();
            // An empty line still gets a widget: it is a line of the program, and a
            // fence that silently closed up its blank lines would be showing
            // something the author did not write. A space rather than an empty string,
            // because a `text` with nothing in it measures zero high.
            lines.add(words.whole(line.isEmpty() ? " " : line, Set.of("md-code-line")));
        }
        return new Column(lines, classes("md-code-block"));
    }

    // --- tables ----------------------------------------------------------------

    private Widget table(Table table) {
        var rows = new ArrayList<Widget>(table.head().size() + table.body().size());
        table.head().forEach(row -> rows.add(row(row)));
        table.body().forEach(row -> rows.add(row(row)));
        return new Column(rows, classes("md-table"));
    }

    private Widget row(TableRow row) {
        var cells = new ArrayList<Widget>(row.cells().size());
        for (var i = 0; i < row.cells().size(); i++) {
            cells.add(cell(row.cells().get(i), i == 0));
        }
        return new Row(cells, classes("md-row", row.header() ? "head" : "body"));
    }

    /// One cell — a wrapping row of words, like a paragraph, with the column's
    /// alignment and the border-suppressing `first` class on the leftmost one.
    private Widget cell(TableCell cell, boolean first) {
        minter.block();
        var fragments = new ArrayList<Words.Piece>();
        inlines(fragments, cell.content(), Set.of());
        var classes = new ArrayList<String>(4);
        classes.add("md-cell");
        if (cell.header()) {
            classes.add("head-cell");
        }
        if (first) {
            classes.add("first");
        }
        switch (cell.alignment()) {
            case CellAlignment.CENTER -> classes.add("center");
            case CellAlignment.END -> classes.add("end");
            // The start edge is what a cell does anyway, so `start` and `default` are
            // the same rendering -- and saying so here is cheaper than a rule that
            // repeats the default.
            case CellAlignment.START, CellAlignment.DEFAULT -> {}
        }
        return new Row(words.tokens(fragments), classes(classes.toArray(String[]::new)));
    }

    // --- attributes ------------------------------------------------------------

    private static Attributes classes(String... names) {
        return Attributes.NONE.classes(names);
    }

    /// `attributes` with `first` added to its classes, keeping its id and key.
    private static Attributes with(Attributes attributes, String first) {
        var classes = new ArrayList<String>(attributes.classes().size() + 1);
        classes.add(first);
        classes.addAll(attributes.classes());
        return attributes.classes(classes.toArray(String[]::new));
    }
}
