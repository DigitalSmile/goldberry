package io.github.digitalsmile.goldberry.html.model;

import java.util.List;

/// A node of a parsed HTML document.
///
/// Four kinds, and that is the whole hierarchy: a [HtmlDocument] at the root, an
/// [Element] for every tag, an [HtmlText] for the words between them and a
/// [Comment] for what the author wrote to themselves. There is no node for a
/// doctype, a processing instruction or a CDATA section — a content renderer reads
/// them and has nothing to do with them, so [io.github.digitalsmile.goldberry.html.Html]
/// drops them rather than modelling something no fold would match on.
///
/// **Sealed, because a fold over a document should stop compiling when the model
/// grows.** That is the same argument the Markdown model makes
/// (ADR-0295): an exhaustive `switch` with no
/// `default` is how `html-view`, a table of contents and a link checker each say
/// they have considered every node, and a `default` branch drawing "something else"
/// is how one of them silently stops being true.
public sealed interface HtmlNode permits HtmlDocument, Element, HtmlText, Comment {

    /// This node's children, in document order. Empty for the two leaves.
    List<HtmlNode> children();
}
