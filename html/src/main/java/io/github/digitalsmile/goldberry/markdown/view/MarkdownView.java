package io.github.digitalsmile.goldberry.markdown.view;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.bind.Observable;
import io.github.digitalsmile.goldberry.content.ImageSource;
import io.github.digitalsmile.goldberry.content.select.SelectableDocument;
import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.markdown.Markdown;
import io.github.digitalsmile.goldberry.markdown.MarkdownSyntax;
import io.github.digitalsmile.goldberry.markdown.model.Document;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributed;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.attr.Bindable;
import io.github.digitalsmile.goldberry.widgets.markup.Markup;
import io.github.digitalsmile.goldberry.widgets.markup.Wiring;

/// A rendered Markdown document — `docs/content-widgets.md` §1's `markdown-view`.
///
/// ```java
/// var preview = MarkdownView.of(document);                   // already parsed
/// var preview = MarkdownView.of(note.source());              // parse and show
/// var live    = MarkdownView.following(model.source());      // and follow it
/// ```
///
/// ```kdl
/// split-pane {
///     text-area class="mono" bind="note.source" change="note.set-source"
///     scroll { markdown-view bind="note.source" }
/// }
/// ```
///
/// ## A preview follows a property, and that is the whole of "live"
///
/// The document may be **bound** — §9's `bind=` — in which case the text is read
/// from the property on every build and parsed afresh. A `text-area` writing the
/// same property through an action is a live preview with no Java between the two
/// halves at all: the element layer subscribes to the binding, a keystroke marks
/// this node for rebuild, and the frame after it is the parsed document
/// (ADR-0062, ADR-0296).
///
/// The parse is not cached, and does not need to be: md4c reads a note in
/// microseconds ([ADR-0294](../../../../../../../book/src/adr/0294-a-parser-crosses-the-boundary-once.md)).
///
/// **And the widgets are not rebuilt either.** A keystroke changes one block, so the
/// view hands back the widget every other block already had and the element tree
/// stops at an identical description without walking under it
/// ([ADR-0315], [ADR-0389]). Nothing to switch on and no previous document to hold:
/// on a 50 kB note a keystroke costs about 2 ms of build, of which md4c is 1. What
/// it does **not** make cheap is a very large note — the style and layout passes are
/// over every element in the window whatever this does — so a 500 kB preview is
/// still slow and `docs/gaps.md` says what would fix it.
///
/// ## It takes a document, and that is the design
///
/// A [Document] rather than a `String` for the unbound case, with [#of(String)] as
/// the convenience that parses one. An editor showing a live preview parses on each
/// keystroke *anyway*, and what an application does with the result is more than
/// render it — a word count, an outline, a table of contents, the first paragraph
/// as a summary. A widget that hid the parse would make every one of those a second
/// parse.
///
/// ## What it is made of
///
/// `column`, `row` and `text` from the catalog, with classes `markdown.css` styles.
/// No engine, no second text stack, nothing a theme cannot restyle — which is what
/// ADR-0295 decided and what `MarkdownWidgets` carries out. An application must add
/// [MarkdownStyles#stylesheet()] beside `Controls.stylesheets(theme)`, or a document
/// renders as unstyled words.
///
/// ## A reader can take a copy of it
///
/// Drag across it, double-click a word, triple-click a block, `Ctrl+A`, `Ctrl+C` —
/// and what lands on the clipboard has the space between words and the newline
/// between blocks that the document implies (ADR-0301). Nothing to switch on: a
/// rendered document is selectable because the words say where they were painted.
///
/// **Not scrollable.** A document is as tall as it is, and `scroll` is a widget that
/// already exists — wrapping one in the other here would take away the choice of
/// whether the scrollbar belongs round the document or round the pane it sits in.
///
/// **Nothing is clickable.** A word does not hear a pointer, so a link is a colour
/// and not a destination. Following one needs hover state per run, which is
/// `html-view`'s work and is recorded in `book/src/TODO.md`.
///
/// @param document what to render when nothing is bound — and what a bound view
///        falls back to before its property has a value, which is what a lenient
///        inflater produces for a path nothing answers (ADR-0062)
/// @param source the property the text comes from, or null
/// @param syntax which dialect [#source] is parsed in. Unused when the document was
///        parsed by the caller, who chose a dialect at that point
/// @param attributes the view's own `id`, classes and key. They land on the column
///        this builds, beside the `markdown` class every rule hangs off
@Markup("markdown-view")
public record MarkdownView(
        Document document,
        @Nullable Observable<?> source,
        MarkdownSyntax syntax,
        @Nullable Consumer<String> onLink,
        @Nullable Consumer<String> onWikiLink,
        @Nullable ImageSource images,
        @Nullable IntConsumer onTask,
        Attributes attributes)
        implements Widget.Stateless, Attributed<MarkdownView>, Bindable<MarkdownView> {

    public MarkdownView {
        Objects.requireNonNull(document, "document");
        syntax = syntax == null ? MarkdownSyntax.gitHub() : syntax;
        attributes = attributes == null ? Attributes.NONE : attributes;
    }

    /// A view of `document`.
    public MarkdownView(Document document) {
        this(document, null, MarkdownSyntax.gitHub(), null, null, null, null, Attributes.NONE);
    }

    /// The same, for the three-component shape this had before ADR-0300.
    public MarkdownView(
            Document document, @Nullable Observable<?> source, MarkdownSyntax syntax, Attributes attributes) {
        this(document, source, syntax, null, null, null, null, attributes);
    }

    /// This view, handing every link's destination to `handler`.
    ///
    /// What makes a link a `button.link` a reader can press rather than a coloured
    /// word (ADR-0293, ADR-0300). **Following it is still the application's**: nothing
    /// here opens a browser or resolves a relative path.
    public MarkdownView onLink(Consumer<String> handler) {
        return new MarkdownView(
                document,
                source,
                syntax,
                Objects.requireNonNull(handler, "handler"),
                onWikiLink,
                images,
                onTask,
                attributes);
    }

    /// The same for `[[wiki links]]`, which hand over their **target**.
    ///
    /// A separate handler because a target is a different kind of string: an href
    /// points somewhere, and a target names something in the application's own
    /// collection — which is the reason the extension exists at all (ADR-0295). A view
    /// with an `onLink` and no `onWikiLink` draws its wiki links inert, which is
    /// honest: this application does not know what `[[Meeting]]` means.
    public MarkdownView onWikiLink(Consumer<String> handler) {
        return new MarkdownView(
                document,
                source,
                syntax,
                onLink,
                Objects.requireNonNull(handler, "handler"),
                images,
                onTask,
                attributes);
    }

    /// This view, drawing the images `source` can find.
    ///
    /// Without one an image is its alt text, which is what this view did before
    /// ADR-0300 and what a document with a broken source still shows.
    public MarkdownView images(ImageSource source) {
        return new MarkdownView(
                document,
                this.source,
                syntax,
                onLink,
                onWikiLink,
                Objects.requireNonNull(source, "source"),
                onTask,
                attributes);
    }

    /// This view, with check boxes a reader can tick.
    ///
    /// `handler` is given the task's **ordinal** — the nth task in the document, from
    /// zero — and
    /// [io.github.digitalsmile.goldberry.markdown.Markdown#toggleTask(String, int)] is
    /// what turns that into a one-character edit of the source. The application owns
    /// the text throughout; the new document arrives through the binding that was
    /// already there (ADR-0300).
    ///
    /// ```java
    /// MarkdownView.following(model.source())
    ///         .onTask(index -> notes.setSource(Markdown.toggleTask(notes.source(), index)));
    /// ```
    public MarkdownView onTask(IntConsumer handler) {
        return new MarkdownView(
                document,
                source,
                syntax,
                onLink,
                onWikiLink,
                images,
                Objects.requireNonNull(handler, "handler"),
                attributes);
    }

    /// A view of `document`.
    public static MarkdownView of(Document document) {
        return new MarkdownView(document);
    }

    /// Parses `markdown` in [MarkdownSyntax#gitHub()] and shows it.
    ///
    /// For the caller who has text and wants a preview and nothing else. An
    /// application that also wants an outline or a summary parses once itself and
    /// uses [#of(Document)].
    public static MarkdownView of(String markdown) {
        return of(Markdown.parse(markdown));
    }

    /// The same, in a dialect of the application's choosing.
    public static MarkdownView of(String markdown, MarkdownSyntax syntax) {
        return of(Markdown.parse(markdown, syntax));
    }

    /// A view that **follows** `source`, re-parsing whatever text it holds.
    ///
    /// The Java spelling of `bind=`. What an editor's preview is: the property is
    /// the document, and this node rebuilds when it changes.
    public static MarkdownView following(Observable<?> source) {
        return new MarkdownView(
                Document.EMPTY, Objects.requireNonNull(source, "source"), MarkdownSyntax.gitHub(), Attributes.NONE);
    }

    /// The same, in a dialect of the application's choosing.
    public static MarkdownView following(Observable<?> source, MarkdownSyntax syntax) {
        return new MarkdownView(
                Document.EMPTY,
                Objects.requireNonNull(source, "source"),
                Objects.requireNonNull(syntax, "syntax"),
                Attributes.NONE);
    }

    /// This view, reading a different dialect.
    public MarkdownView syntax(MarkdownSyntax value) {
        return new MarkdownView(document, source, value, onLink, onWikiLink, images, onTask, attributes);
    }

    /// What this view is showing **right now**: the bound property, parsed, or the
    /// document it was built with.
    ///
    /// Read at build rather than captured at construction, so a property that
    /// changes between one frame and the next is shown by the next one — the same
    /// rule `text` follows. `null` in the property reads as an empty document: a
    /// note that has not loaded yet is nothing to show, not the word "null".
    public Document resolved() {
        if (source == null) {
            return document;
        }
        var value = source.get();
        if (value == null) {
            return document;
        }
        var text = String.valueOf(value);
        return text.isEmpty() ? Document.EMPTY : Markdown.parse(text, syntax);
    }

    @Override
    public MarkdownView bound(Observable<?> value) {
        return new MarkdownView(document, value, syntax, onLink, onWikiLink, images, onTask, attributes);
    }

    @Override
    public @Nullable Observable<?> binding() {
        return source;
    }

    @Override
    public MarkdownView withAttributes(Attributes attributes) {
        return new MarkdownView(document, source, syntax, onLink, onWikiLink, images, onTask, attributes);
    }

    @Override
    public @Nullable Object key() {
        return attributes.key();
    }

    @Override
    public Widget build(BuildContext context) {
        var document = resolved();
        // **Wrapped rather than returned bare**, because a rendered document is
        // something a reader selects text in — and a selection is state that has to
        // survive the re-parse a keystroke causes (ADR-0301).
        //
        // A fold per build, because it counts the tasks and the words it has seen as
        // it walks (ADR-0300) — and a build is exactly one walk of one document.
        return new SelectableDocument(document, (minter, memo, overlay) -> {
            // The wiring is the memo's, not this build's: a block handed back from an
            // earlier keystroke still has to reach the handler the application is
            // holding now, and an application that writes `onLink(this::open)` in its
            // own build hands over a new object every frame ([ADR-0389]).
            var wiring = memo.<MarkdownWiring>held(MarkdownWiring::new);
            wiring.of(onLink, onWikiLink, images, onTask);
            return new MarkdownWidgets(wiring, minter).document(document, attributes, overlay, memo);
        });
    }

    /// Builds a `markdown-view` from markup.
    ///
    /// The node's argument is the **Markdown source** and `bind=` is a property to
    /// follow; a node with both keeps the argument as what is shown until the
    /// property answers, which is what a lenient registry does everywhere else
    /// (ADR-0062).
    ///
    /// ```kdl
    /// markdown-view "A *little* document."
    /// markdown-view bind="note.source" syntax="commonmark"
    /// markdown-view bind="note.source" link="app.open" images="app.assets" task="note.toggle-task"
    /// ```
    ///
    /// **No `src=` yet**, which `content-widgets.md` §1.3 sketches as
    /// `markdown-view src="CHANGELOG.md"`. Reading a file means deciding what a
    /// relative path is relative to, what happens when it is missing, and whether a
    /// widget may touch the filesystem while a document is being inflated — three
    /// answers `Icons` and the stylesheets each needed a resolver for. An
    /// application reads the file and passes the text, which is one line, until
    /// there is a resource resolver worth the name (`book/src/TODO.md`).
    ///
    /// Children are refused rather than ignored: a document has content of its own,
    /// and a `markdown-view` with widgets inside it is a document that says
    /// something its author did not write.
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        if (!children.isEmpty()) {
            throw new IllegalArgumentException("markdown-view takes its document as its argument, not as children;"
                    + " it has " + children.size() + " child node(s)");
        }
        var syntax = syntax(node.stringProperty("syntax"));
        var literal = Wiring.label(node);
        var task = wiring.valued(node, "task");
        return new MarkdownView(
                literal.isEmpty() ? Document.EMPTY : Markdown.parse(literal, syntax),
                wiring.bound(node),
                syntax,
                wiring.valued(node, "link"),
                wiring.valued(node, "wikilink"),
                // `images=` names an **object** rather than an action, because what it
                // names is neither a value nor a method -- the third registry's whole
                // job (ADR-0130).
                wiring.handle(node, "images", ImageSource.class),
                // The ordinal crosses as the string a document would have written,
                // which is the one valued shape §9 has (ADR-0073): an application that
                // wants an `int` parses it in Java, where a bad value is a bug it can
                // see.
                task == null ? null : index -> task.accept(String.valueOf(index)),
                Attributes.of(node));
    }

    /// The dialect a document named.
    ///
    /// Two words rather than a list of extensions, because a document choosing bit
    /// by bit is a document that has opinions about md4c's flags — and Java is where
    /// a `MarkdownSyntax` is composed. Absent is [MarkdownSyntax#gitHub()], which is
    /// what people mean by Markdown; a *misspelt* one is refused, because that is a
    /// document saying something it does not mean.
    private static MarkdownSyntax syntax(@Nullable String name) {
        if (name == null || name.isBlank()) {
            return MarkdownSyntax.gitHub();
        }
        return switch (name.trim().toLowerCase(Locale.ROOT)) {
            case "github" -> MarkdownSyntax.gitHub();
            case "commonmark" -> MarkdownSyntax.commonMark();
            default ->
                throw new IllegalArgumentException(
                        "a markdown-view's syntax is \"github\" or \"commonmark\", not \"" + name + "\"");
        };
    }
}
