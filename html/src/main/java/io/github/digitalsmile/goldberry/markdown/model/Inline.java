package io.github.digitalsmile.goldberry.markdown.model;

import java.util.List;

/// Something that flows along a line — a word, an emphasis, a link, a break.
///
/// [#children()] is the nested content: emphasis and links hold more inlines, while
/// text, code, images and breaks hold none. An image is a leaf here even though
/// Markdown lets its alt text be marked up, because what an alt text *is* is a
/// string a screen reader says — see [Image#alt()].
public sealed interface Inline extends MarkdownNode
        permits Text, Emphasis, Strong, Struck, Underlined, Code, Link, Image, WikiLink, LineBreak, RawHtml {

    /// The inlines inside this one, in order. Empty for a leaf.
    List<Inline> children();
}
