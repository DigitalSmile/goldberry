/// Goldberry's content module for authored text: Markdown and HTML, each parsed
/// into a document and rendered as ordinary widgets.
///
/// The first of the optional modules `docs/content-widgets.md` specifies and
/// [ADR-0190](../book/src/adr/0190-a-content-module-brings-its-own-natives.md)
/// adopts as a plan: an application opts in, nothing in `:core` or `:widgets`
/// knows this module exists, and what it brings with it is quarantined here.
///
/// **Two halves, not one** (ADR-0295). Markdown and HTML are two upstreams with two
/// lifetimes and the dependency runs one way — Markdown *produces* HTML and never
/// reads it — so `…markdown` and `…html` are separate package trees with separate
/// entry points, separate models and separate stylesheets.
///
/// **And neither of them is an engine.** This module's Markdown half landed first
/// without litehtml under it, and
/// [ADR-0298](../book/src/adr/0298-html-is-a-document-and-not-an-engine.md) settles
/// the other half the same way: `html-view` is a parser in Java over a model of
/// records, folded into `column`, `row`, `text` and `button` by the same code that
/// draws a note. litehtml is still the only way to get real inline layout and is
/// still waiting on a wider native paint surface (`book/src/TODO.md`); what it is
/// no longer holding up is HTML on screen.
module io.github.digitalsmile.goldberry.html {

    /// The widget catalog. `markdown-view` is built from `column`, `row` and
    /// `text`, so a rendered document is ordinary widgets under the ordinary
    /// cascade — `transitive` because a [io.github.digitalsmile.goldberry.markdown.view.MarkdownView]
    /// is a [io.github.digitalsmile.goldberry.widget.Widget] and whoever holds one
    /// has to be able to say so.
    requires transitive io.github.digitalsmile.goldberry.widgets;

    /// md4c's wrapper, which `:natives` exports to this module by name and to
    /// nobody else (ADR-0294). **Not** `transitive`: no type of it appears in
    /// anything exported below, because the model is the toolkit's own vocabulary
    /// and the translation happens in one package-private class.
    requires io.github.digitalsmile.goldberry.natives;

    /// JSpecify's nullness annotations, for the packages under NullAway.
    requires transitive static org.jspecify;

    /// The furniture: [io.github.digitalsmile.goldberry.markdown.Markdown] parses,
    /// and [io.github.digitalsmile.goldberry.markdown.MarkdownSyntax] says which
    /// dialect it parses. An application names one of these and then works in the
    /// model.
    exports io.github.digitalsmile.goldberry.markdown;

    /// The document, as a tree of records — a sealed hierarchy an application
    /// pattern-matches over. This is the module's real surface: brd's note
    /// preview, an outline, a word count and a table of contents are all walks of
    /// it.
    exports io.github.digitalsmile.goldberry.markdown.model;

    /// Markdown out as HTML, which is what a document served over HTTP needs — and
    /// what `html-view` reads back in, which is the round trip a preview of served
    /// output is tested with.
    exports io.github.digitalsmile.goldberry.markdown.html;

    /// `markdown-view` — the widget. Its parts stay in here, which is ADR-0065's
    /// rule: a part is styleable and not constructible.
    exports io.github.digitalsmile.goldberry.markdown.view;

    /// What the two content views share as **API**: today
    /// [io.github.digitalsmile.goldberry.content.ImageSource], which an application
    /// implements to say where a document's images come from — because a `src` is a
    /// string whose meaning is the application's and nothing here fetches anything
    /// (ADR-0190, ADR-0300).
    ///
    /// Its neighbours — `content.inline`, `content.image`, `content.entity` — are how
    /// the views are built and are exported to nobody: a part is styleable and not
    /// constructible (ADR-0065).
    exports io.github.digitalsmile.goldberry.content;

    /// HTML in: [io.github.digitalsmile.goldberry.html.Html] parses, and its own
    /// documentation carries the list of what this is **not** — no browser, no
    /// scripting, no CSS engine, no HTML5 parsing algorithm (ADR-0298).
    exports io.github.digitalsmile.goldberry.html;

    /// The page, as a tree of records: sealed over four node kinds, with an open
    /// tag name on an element so that `<my-callout>` is representable. An
    /// application's outline, link check and word count are walks of this.
    exports io.github.digitalsmile.goldberry.html.model;

    /// `html-view` — the widget, and [io.github.digitalsmile.goldberry.html.view.HtmlStyles]
    /// beside it. Its parts stay in here for ADR-0065's reason, the same way the
    /// Markdown view's do.
    exports io.github.digitalsmile.goldberry.html.view;

    /// So the toolkit can read `markdown.css`.
    ///
    /// JPMS encapsulates **resources** as well as classes: a file inside a package
    /// of a named module is invisible to other modules unless the package is open,
    /// and `exports` is not enough — it governs types, not bytes (ADR-0093). The
    /// reader is [io.github.digitalsmile.goldberry.css.Stylesheet#resource], which
    /// lives in `:core`, so that is the one module this is opened to.
    ///
    /// It is also the one thing about this module that **no test on the class path
    /// can catch**, because module encapsulation does not apply there: the first
    /// thing to find it missing was the showcase, on the module path, at its first
    /// frame. `MarkdownStylesTest` reads this descriptor from disk rather than
    /// trusting a call that happens to work under the test runner.
    opens io.github.digitalsmile.goldberry.markdown.view to io.github.digitalsmile.goldberry.core;

    /// And the same for `html.css`, which is the other half's master stylesheet and
    /// has the same problem: a resource beside a class is invisible to `:core` on the
    /// module path unless its package is open, and no class-path test can notice.
    /// `HtmlStylesTest` reads this descriptor from disk for exactly that reason.
    opens io.github.digitalsmile.goldberry.html.view to io.github.digitalsmile.goldberry.core;

    /// Every widget module announces its node names through a `WidgetCatalog`, and
    /// the build patches the `provides` into this descriptor from the `@Markup`
    /// annotations rather than asking anybody to keep a list (ADR-0131).
    uses io.github.digitalsmile.goldberry.widgets.markup.WidgetCatalog;
}
