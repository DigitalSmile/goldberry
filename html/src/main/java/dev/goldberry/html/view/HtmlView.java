package dev.goldberry.html.view;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import dev.goldberry.bind.Observable;
import dev.goldberry.content.ImageSource;
import dev.goldberry.content.select.SelectableDocument;
import dev.goldberry.html.Html;
import dev.goldberry.html.model.HtmlDocument;
import dev.goldberry.html.style.Colours;
import dev.goldberry.html.style.DocumentStyles;
import dev.goldberry.kdl.KdlNode;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributed;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.attr.Bindable;
import dev.goldberry.widget.style.StyleScope;
import dev.goldberry.widgets.markup.Markup;
import dev.goldberry.widgets.markup.Wiring;

/// A rendered HTML document — `html-view`.
///
/// ```java
/// var page = HtmlView.of(document);                      // already parsed
/// var page = HtmlView.of(help.body());                   // parse and show
/// var live = HtmlView.following(model.source());         // and follow it
/// var page = HtmlView.of(document).onLink(app::navigate);// and follow its links
/// ```
///
/// ```kdl
/// scroll {
///     html-view id="page" bind="doc.source" link="app.open-link"
/// }
/// ```
///
/// ## It is the Markdown view's twin, deliberately
///
/// Same shape, same rules, same stylesheet layer: a document is a **value**, the view
/// is a fold over it, `bind=` makes a preview live and the appearance is a stylesheet
/// an application adds beside `Controls.stylesheets(theme)`. That is not a
/// coincidence — the two halves of `goldberry-html` render through one
/// [dev.goldberry.content.inline.Words] and differ only in what
/// parsed the document.
///
/// **There is no litehtml behind this.** What that costs and what it saves is in
/// [Html]'s documentation; the short form is that an engine buys real inline layout
/// and costs a native library, and everything else a page needs — the tags, tables,
/// followable links, a themed stylesheet — is a parser and a fold.
///
/// ## One thing `markdown-view` cannot do: a link you can press
///
/// An anchor becomes a `button.link` that hands its `href` to [#onLink(Consumer)].
/// It is a Tab stop, it hovers, and it takes `Space` and `Enter` — so a help page is
/// navigable from the keyboard. **Whether a link may be followed is still the
/// application's**: nothing here opens a browser, resolves a relative path or fetches
/// anything, because what a URL scheme means is packaging, and packaging is the
/// application's. A view with no handler draws its links and does nothing when they
/// are pressed.
///
/// ## What it is made of
///
/// `column`, `row`, `text` and `button` from the catalog, with classes `html.css`
/// styles — one per tag, so the stylesheet reads like a browser's default sheet. An
/// application must add [HtmlStyles#stylesheet()] beside `Controls.stylesheets(theme)`
/// or a page renders as unstyled words.
///
/// ## A page's own styles reach that page and nothing else
///
/// A mail's `<style>` and `style=`, and the `bgcolor` of mail written before CSS,
/// are applied: a safe subset (colours, type, spacing, boxes, borders, corners,
/// `display: none`) and nothing that fetches, moves a box out of the page or reads
/// the theme. The rules are matched against the document's own tree, handed to the
/// widgets as one generated class per element, and held in a `StyleScope` round the
/// page, so one mail's rules cannot reach the shell or the mail beside it. They rank
/// above the application's own sheets for the page's nodes, and an application
/// overrides them with `!important`.
///
/// Colour is the open question a mail asks of a themed application, and
/// [#authorStyles(AuthorStyles)] answers it: [AuthorStyles#ADAPTED] by default, which
/// keeps a colour where it can be seen to read on a background the mail painted.
///
/// ## A reader can take a copy of it
///
/// Drag, double-click a word, triple-click a block, `Ctrl+A`, `Ctrl+C`, with the
/// separators the page implies — the same in both views, through the same code, and
/// nothing to switch on.
///
/// **Not scrollable.** A document is as tall as it is, and `scroll` is a widget that
/// already exists — wrapping one in the other here would take away the choice of
/// whether the scrollbar belongs round the page or round the pane it sits in.
///
/// Read more: [HTML view](https://goldberry.dev/docs/components/content.html#html-view).
///
/// @param document what to render when nothing is bound — and what a bound view falls
///        back to before its property has a value, which is what a lenient inflater
///        produces for a path nothing answers
/// @param source the property the source text comes from, or null
/// @param onLink what an anchor hands its `href` to, or null for a page whose links
///        are drawn and inert
/// @param images where an `<img src=…>` comes from, or null for a page that draws
///        its alt text
/// @param styles what is done with the document's own `<style>` and `style=`; see
///        [AuthorStyles]
/// @param attributes the view's own `id`, classes and key. They land on the column
///        this builds, beside the `html` class every rule hangs off
@Markup("html-view")
public record HtmlView(
        HtmlDocument document,
        @Nullable Observable<?> source,
        @Nullable Consumer<String> onLink,
        @Nullable ImageSource images,
        AuthorStyles styles,
        Attributes attributes)
        implements Widget.Stateless, Attributed<HtmlView>, Bindable<HtmlView> {

    /// Written out so that the parameters taking null for a default can say so.
    public HtmlView(
            HtmlDocument document,
            @Nullable Observable<?> source,
            @Nullable Consumer<String> onLink,
            @Nullable ImageSource images,
            @Nullable AuthorStyles styles,
            @Nullable Attributes attributes) {
        Objects.requireNonNull(document, "document");
        attributes = attributes == null ? Attributes.NONE : attributes;
        this.document = document;
        this.source = source;
        this.onLink = onLink;
        this.images = images;
        this.styles = styles == null ? AuthorStyles.ADAPTED : styles;
        this.attributes = attributes;
    }

    /// A view applying the document's styles [AuthorStyles#ADAPTED], as every
    /// view does unless it says otherwise.
    public HtmlView(
            HtmlDocument document,
            @Nullable Observable<?> source,
            @Nullable Consumer<String> onLink,
            @Nullable ImageSource images,
            @Nullable Attributes attributes) {
        this(document, source, onLink, images, AuthorStyles.ADAPTED, attributes);
    }

    /// A view of `document`.
    public HtmlView(HtmlDocument document) {
        this(document, null, null, null, AuthorStyles.ADAPTED, Attributes.NONE);
    }

    /// A view of `document`.
    public static HtmlView of(HtmlDocument document) {
        return new HtmlView(document);
    }

    /// Parses `html` and shows it.
    ///
    /// For the caller who has a page and wants it on screen. An application that also
    /// wants its links checked or its headings listed parses once itself and uses
    /// [#of(HtmlDocument)] — the model is exported for exactly that.
    public static HtmlView of(String html) {
        return of(Html.parse(html));
    }

    /// A view that **follows** `source`, re-parsing whatever text it holds.
    ///
    /// The Java spelling of `bind=`. What an editor's preview is: the property is the
    /// page, and this node rebuilds when it changes.
    public static HtmlView following(Observable<?> source) {
        return new HtmlView(
                HtmlDocument.EMPTY,
                Objects.requireNonNull(source, "source"),
                null,
                null,
                AuthorStyles.ADAPTED,
                Attributes.NONE);
    }

    /// This view, handing every anchor's `href` to `handler`.
    public HtmlView onLink(Consumer<String> handler) {
        return new HtmlView(document, source, Objects.requireNonNull(handler, "handler"), images, styles, attributes);
    }

    /// This view, drawing the images `source` can find.
    ///
    /// Without one an `<img>` is its alt text, which is what every view did before
    /// this existed and what a page with a broken `src` still shows.
    public HtmlView images(ImageSource source) {
        return new HtmlView(
                document, this.source, onLink, Objects.requireNonNull(source, "source"), styles, attributes);
    }

    /// This view, doing with the document's own styles what `styles` says.
    ///
    /// [AuthorStyles#ADAPTED] unless this is called: a mail's layout and boxes as
    /// written, and its colours kept where they read.
    public HtmlView authorStyles(AuthorStyles styles) {
        return new HtmlView(document, source, onLink, images, Objects.requireNonNull(styles, "styles"), attributes);
    }

    /// What this view is showing **right now**: the bound property, parsed, or the
    /// document it was built with.
    ///
    /// Read at build rather than captured at construction, so a property that changes
    /// between one frame and the next is shown by the next one — the same rule `text`
    /// follows. `null` in the property reads as an empty document: a page that has not
    /// loaded yet is nothing to show, not the word "null".
    public HtmlDocument resolved() {
        if (source == null) {
            return document;
        }
        var value = source.get();
        if (value == null) {
            return document;
        }
        var text = String.valueOf(value);
        return text.isEmpty() ? HtmlDocument.EMPTY : Html.parse(text);
    }

    @Override
    public HtmlView bound(Observable<?> value) {
        return new HtmlView(document, value, onLink, images, styles, attributes);
    }

    @Override
    public @Nullable Observable<?> binding() {
        return source;
    }

    @Override
    public HtmlView withAttributes(Attributes attributes) {
        return new HtmlView(document, source, onLink, images, styles, attributes);
    }

    @Override
    public @Nullable Object key() {
        return attributes.key();
    }

    @Override
    public Widget build(BuildContext context) {
        var document = resolved();
        // Wrapped, for the reason `markdown-view` wraps its own: a rendered page is
        // something a reader selects text in, and a selection is state.
        // The memo is ignored here. `markdown-view` got block reuse because that is
        // where the measurement was taken; the same lever is available to this fold
        // and is a separate piece of work, not a line of it.
        var authored = authored(document, styles);
        return new SelectableDocument((minter, _, overlay) -> {
            var page = new HtmlWidgets(onLink, images, minter, authored).document(document, attributes, overlay);
            // Always a scope, empty or not, so a page whose `<style>` comes and goes
            // keeps its widgets and the selection in them.
            return new StyleScope(authored.rules(), page);
        });
    }

    /// What `document` says about its own appearance, as far as `styles` lets it.
    private static DocumentStyles authored(HtmlDocument document, AuthorStyles styles) {
        return switch (styles) {
            case IGNORED -> DocumentStyles.NONE;
            case THEME_COLOURS -> DocumentStyles.of(document, Colours.THEME);
            case ADAPTED -> DocumentStyles.of(document, Colours.ADAPT);
            case AS_WRITTEN -> DocumentStyles.of(document, Colours.AUTHOR);
        };
    }

    /// Builds an `html-view` from markup.
    ///
    /// The node's argument is the **HTML source** and `bind=` is a property to follow; a
    /// node with both keeps the argument as what is shown until the property answers,
    /// which is what a lenient registry does everywhere else. `link=` names
    /// a valued action — one that is handed the `href` — so a help pane navigates with
    /// no Java between the document and the model.
    ///
    /// ```kdl
    /// html-view "<p>A <em>little</em> page.</p>"
    /// html-view bind="doc.source" link="doc.open"
    /// html-view bind="mail.body" author-styles="theme-colours"
    /// ```
    ///
    /// `author-styles=` is [AuthorStyles#markupName()]: `ignored`, `theme-colours`,
    /// `adapted` (when it says nothing) or `as-written`.
    ///
    /// **No `src=` yet**, which would read as `html-view
    /// src="help/getting-started.html"`. Reading a file means deciding what a relative
    /// path is relative to, what happens when it is missing, and whether a widget may
    /// touch the filesystem while a document is being inflated — three answers `Icons`
    /// and the stylesheets each needed a resolver for. An application reads the file and
    /// passes the text, which is one line, until there is a resource resolver worth the
    /// name (`docs/TODO.md`).
    ///
    /// Children are refused rather than ignored: a page has content of its own, and an
    /// `html-view` with widgets inside it is a document saying something its author did
    /// not write.
    @SuppressWarnings("unused")
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        if (!children.isEmpty()) {
            throw new IllegalArgumentException("html-view takes its document as its argument, not as children;"
                    + " it has " + children.size() + " child node(s)");
        }
        var literal = Wiring.label(node);
        return new HtmlView(
                literal.isEmpty() ? HtmlDocument.EMPTY : Html.parse(literal),
                wiring.bound(node),
                wiring.valued(node, "link"),
                // `images=` names an object rather than an action, because what it
                // names is neither a value nor a method -- which is the third
                // registry's whole job (`Wiring.handle`).
                wiring.handle(node, "images", ImageSource.class),
                AuthorStyles.parse(node.stringProperty("author-styles")),
                Attributes.of(node));
    }
}
