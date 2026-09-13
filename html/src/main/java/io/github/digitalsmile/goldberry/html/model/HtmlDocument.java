package io.github.digitalsmile.goldberry.html.model;

import java.util.List;
import java.util.Objects;

/// A whole parsed document — what [io.github.digitalsmile.goldberry.html.Html#parse]
/// returns.
///
/// **The root is what the author wrote, not what a browser would have built.** A
/// fragment stays a fragment: `<p>Hello</p>` parses to a document holding one
/// paragraph rather than to `html > head + body > p`, because the thing an
/// application has is usually a fragment — a help page's body, a changelog, the HTML
/// half of an email — and inventing two wrapper elements round it would make every
/// fold walk past them. A full page with `<html>` and `<body>` in it keeps those
/// elements, because they are in the source; nothing is inserted and nothing is
/// hoisted (ADR-0298).
///
/// @param children the top-level nodes, in order
public record HtmlDocument(List<HtmlNode> children) implements HtmlNode {

    /// A document with nothing in it, which is what empty text parses to.
    public static final HtmlDocument EMPTY = new HtmlDocument(List.of());

    public HtmlDocument {
        children = List.copyOf(Objects.requireNonNull(children, "children"));
    }

    /// Every word in the document, in order, with one space where a tag was.
    ///
    /// The summary, the word count and the search index an application wants from a
    /// page it is about to render — see [Element#text()], which is the same walk
    /// from lower down.
    public String text() {
        return Walk.text(this);
    }

    /// Every element with this tag, in document order.
    ///
    /// What makes the model worth exporting rather than hiding behind the widget: a
    /// table of contents is `find("h2")`, a link check is `find("a")`, an image
    /// prefetch is `find("img")`, and none of them needs a visitor or a second parse
    /// (ADR-0295's argument for the Markdown tree, which this is the other half of).
    public List<Element> find(String tag) {
        return Walk.find(this, tag);
    }
}
