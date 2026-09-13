package io.github.digitalsmile.goldberry.html;

import io.github.digitalsmile.goldberry.html.model.HtmlAttributes;

/// What [HtmlTokenizer] hands [HtmlParser] — one thing it found in the source.
///
/// Four kinds, and deliberately fewer than the model has nodes: a doctype and a
/// processing instruction are read and dropped here, because the model has no node
/// for either and a token nothing ever matches on is a branch nobody tests.
///
/// **A tokenizer and a tree builder rather than one pass**, which is the one piece of
/// structure in this parser worth explaining. The two jobs fail differently: a
/// tokenizer meets `a < b` and has to decide it is text, while a tree builder meets
/// `<li>` inside an open `<li>` and has to decide it is a sibling. Written as one
/// loop those two kinds of recovery share state, and the bug that produces — a stray
/// `>` swallowing the next tag — is invisible in the output. Written apart, each is a
/// table of cases with a test each (ADR-0298).
sealed interface Token {

    /// The words between two tags, with entities already resolved — except inside a
    /// raw-text element, where `&amp;` is five characters of a program.
    record Characters(String text) implements Token {}

    /// `<p class="lead">`, or `<br/>`.
    ///
    /// @param tag the name, lower-cased
    /// @param attributes what was in the tag
    /// @param selfClosing whether the author wrote `/>`. Kept rather than resolved
    ///        here: whether it means anything depends on the element, and that is the
    ///        tree builder's table to read
    record Start(String tag, HtmlAttributes attributes, boolean selfClosing) implements Token {}

    /// `</p>`. Attributes on a close tag are parsed and dropped, which is what HTML
    /// says to do with them.
    record End(String tag) implements Token {}

    /// `<!-- … -->`, delimiters removed.
    record Note(String text) implements Token {}
}
