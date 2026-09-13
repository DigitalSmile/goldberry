package io.github.digitalsmile.goldberry.html.view;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.bind.Observable;
import io.github.digitalsmile.goldberry.content.ImageSource;
import io.github.digitalsmile.goldberry.content.select.SelectableDocument;
import io.github.digitalsmile.goldberry.html.Html;
import io.github.digitalsmile.goldberry.html.model.HtmlDocument;
import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributed;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.attr.Bindable;
import io.github.digitalsmile.goldberry.widgets.markup.Markup;
import io.github.digitalsmile.goldberry.widgets.markup.Wiring;

/// A rendered HTML document — `docs/content-widgets.md` §1's `html-view`, and the
/// entry `docs/gaps.md` G17 asked for.
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
/// [io.github.digitalsmile.goldberry.content.inline.Words] and differ only in what
/// parsed the document (ADR-0295, ADR-0298).
///
/// **There is no litehtml behind this.** What that costs and what it saves is
/// [Html]'s documentation and ADR-0298; the short form is that an engine buys real
/// inline layout and costs a native library, and everything else on G17's list — the
/// tags, tables, followable links, a themed stylesheet — is a parser and a fold.
///
/// ## One thing `markdown-view` cannot do: a link you can press
///
/// An anchor becomes a `button.link` (ADR-0293) that hands its `href` to
/// [#onLink(Consumer)]. It is a Tab stop, it hovers, and it takes `Space` and
/// `Enter` — so a help page is navigable from the keyboard. **Whether a link may be
/// followed is still the application's**: nothing here opens a browser, resolves a
/// relative path or fetches anything, for the reason ADR-0291 gave about URL
/// schemes. A view with no handler draws its links and does nothing when they are
/// pressed.
///
/// ## What it is made of
///
/// `column`, `row`, `text` and `button` from the catalog, with classes `html.css`
/// styles — one per tag, so the stylesheet reads like a browser's default sheet. An
/// application must add [HtmlStyles#stylesheet()] beside `Controls.stylesheets(theme)`
/// or a page renders as unstyled words.
///
/// ## A reader can take a copy of it
///
/// Drag, double-click a word, triple-click a block, `Ctrl+A`, `Ctrl+C`, with the
/// separators the page implies (ADR-0301) — the same in both views, through the same
/// code, and nothing to switch on.
///
/// **Not scrollable.** A document is as tall as it is, and `scroll` is a widget that
/// already exists — wrapping one in the other here would take away the choice of
/// whether the scrollbar belongs round the page or round the pane it sits in.
///
/// @param document what to render when nothing is bound — and what a bound view falls
///        back to before its property has a value, which is what a lenient inflater
///        produces for a path nothing answers (ADR-0062)
/// @param source the property the source text comes from, or null
/// @param onLink what an anchor hands its `href` to, or null for a page whose links
///        are drawn and inert
/// @param images where an `<img src=…>` comes from, or null for a page that draws
///        its alt text (ADR-0300)
/// @param attributes the view's own `id`, classes and key. They land on the column
///        this builds, beside the `html` class every rule hangs off
@Markup("html-view")
public record HtmlView(
        HtmlDocument document,
        @Nullable Observable<?> source,
        @Nullable Consumer<String> onLink,
        @Nullable ImageSource images,
        Attributes attributes)
        implements Widget.Stateless, Attributed<HtmlView>, Bindable<HtmlView> {

    public HtmlView {
        Objects.requireNonNull(document, "document");
        attributes = attributes == null ? Attributes.NONE : attributes;
    }

    /// A view of `document`.
    public HtmlView(HtmlDocument document) {
        this(document, null, null, null, Attributes.NONE);
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
        return new HtmlView(HtmlDocument.EMPTY, Objects.requireNonNull(source, "source"), null, null, Attributes.NONE);
    }

    /// This view, handing every anchor's `href` to `handler`.
    public HtmlView onLink(Consumer<String> handler) {
        return new HtmlView(document, source, Objects.requireNonNull(handler, "handler"), images, attributes);
    }

    /// This view, drawing the images `source` can find.
    ///
    /// Without one an `<img>` is its alt text, which is what every view did before
    /// this existed and what a page with a broken `src` still shows (ADR-0300).
    public HtmlView images(ImageSource source) {
        return new HtmlView(document, this.source, onLink, Objects.requireNonNull(source, "source"), attributes);
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
        return new HtmlView(document, value, onLink, images, attributes);
    }

    @Override
    public @Nullable Observable<?> binding() {
        return source;
    }

    @Override
    public HtmlView withAttributes(Attributes attributes) {
        return new HtmlView(document, source, onLink, images, attributes);
    }

    @Override
    public @Nullable Object key() {
        return attributes.key();
    }

    @Override
    public Widget build(BuildContext context) {
        var document = resolved();
        // Wrapped, for the reason `markdown-view` wraps its own: a rendered page is
        // something a reader selects text in, and a selection is state (ADR-0301).
        return new SelectableDocument(
                document,
                (geometry, overlay) ->
                        new HtmlWidgets(onLink, images, geometry).document(document, attributes, overlay));
    }

    /// Builds an `html-view` from markup.
    ///
    /// The node's argument is the **HTML source** and `bind=` is a property to follow; a
    /// node with both keeps the argument as what is shown until the property answers,
    /// which is what a lenient registry does everywhere else (ADR-0062). `link=` names
    /// a valued action — one that is handed the `href` — so a help pane navigates with
    /// no Java between the document and the model.
    ///
    /// ```kdl
    /// html-view "<p>A <em>little</em> page.</p>"
    /// html-view bind="doc.source" link="doc.open"
    /// ```
    ///
    /// **No `src=` yet**, which `content-widgets.md` §1.3 sketches as `html-view
    /// src="help/getting-started.html"`. Reading a file means deciding what a relative
    /// path is relative to, what happens when it is missing, and whether a widget may
    /// touch the filesystem while a document is being inflated — three answers `Icons`
    /// and the stylesheets each needed a resolver for. An application reads the file and
    /// passes the text, which is one line, until there is a resource resolver worth the
    /// name (`book/src/TODO.md`).
    ///
    /// Children are refused rather than ignored: a page has content of its own, and an
    /// `html-view` with widgets inside it is a document saying something its author did
    /// not write.
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
                // registry's whole job (`Wiring.handle`, ADR-0130).
                wiring.handle(node, "images", ImageSource.class),
                Attributes.of(node));
    }
}
