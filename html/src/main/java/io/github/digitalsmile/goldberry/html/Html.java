package io.github.digitalsmile.goldberry.html;

import java.util.Objects;

import io.github.digitalsmile.goldberry.html.model.HtmlDocument;

/// HTML, as a document you can walk.
///
/// The whole entry point of the HTML half of `goldberry-html`:
///
/// ```java
/// var document = Html.parse(page.body());
///
/// var view = HtmlView.of(document);                 // to show
/// var links = document.find("a");                   // to check
/// var summary = document.text();                    // to index
/// ```
///
/// ## There is no litehtml here, and that is the decision
///
/// `docs/gaps.md` G17 and `docs/content-widgets.md` §1.1 both specified this half as
/// **litehtml behind a native `document_container`**, and it is not: this is a parser
/// in Java over the model beside it, rendered by the same fold `markdown-view` uses.
/// The reasoning is [ADR-0298](../../../../../../../book/src/adr/0298-html-is-a-document-and-not-an-engine.md)'s
/// and the short form is that the two things were never the same project. An engine
/// buys real inline layout — one line of mixed faces as a single shaped run — and
/// costs a C++ library, a second native artifact, four CI legs, and a widening of the
/// exported paint surface with rounded geometry and a nested state stack in it. A
/// *document* buys everything else — the tags, the tables, followable links, a
/// stylesheet a theme drives — and costs a parser. The engine is still worth having
/// and is still blocked on the same paint surface; what it is no longer blocking is
/// `html-view`.
///
/// So the honest list of what this is **not**, in one place rather than discovered:
///
/// - **Not a browser, and not a web view.** No scripting, no network, no navigation —
///   the README's promise was always "renders your HTML content, beautifully and
///   offline" and never "renders websites" (`docs/content-widgets.md` §1).
/// - **Not the HTML5 parsing algorithm.** No implied `html`/`head`/`body`, no foster
///   parenting of content out of a table, no adoption agency for `<b>a<i>b</b>c</i>`,
///   no namespaces. A fragment stays a fragment and misnesting is closed innermost
///   first, which is what a hand-written document means.
/// - **Not a CSS engine.** A `<style>` block is parsed and kept in the model and drawn
///   by nothing: the cascade an `html-view` is under is the application's stylesheets,
///   which is what makes a page follow the theme instead of fighting it
///   ([io.github.digitalsmile.goldberry.html.view.HtmlStyles]). An inline `style=`
///   attribute is read into the model and not applied either.
/// - **Not a resolver.** An `href` and a `src` are strings. Whether one may be
///   followed, what a relative path is relative to and what fetching costs are the
///   application's answers, in the same division ADR-0291 drew for URL schemes and
///   ADR-0190 drew for images.
///
/// ## There is no failure mode
///
/// Like Markdown, and for a stronger reason: every string is a document. A stray
/// `</div>`, an unclosed `<p>`, `a < b` in a sentence, a truncated file — each has a
/// recovery written down beside the code that performs it, in [HtmlTokenizer] and
/// [HtmlParser]. What can fail is loading the entity table, which is
/// `libgoldberry`'s, and that is an [IllegalStateException] about the library rather
/// than about the document.
public final class Html {

    private Html() {}

    /// Parses `html` into a document.
    ///
    /// @param html the source — a fragment or a whole page
    /// @return its nodes. [HtmlDocument#EMPTY] for empty text, never null
    public static HtmlDocument parse(String html) {
        Objects.requireNonNull(html, "html");
        if (html.isEmpty()) {
            return HtmlDocument.EMPTY;
        }
        return HtmlParser.parse(html);
    }
}
