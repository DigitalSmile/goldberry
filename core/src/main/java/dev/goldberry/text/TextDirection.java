package dev.goldberry.text;

/// Which way a run of text is shaped.
///
/// The toolkit's own vocabulary rather than the shaper's constants, so the
/// numbering of a C header stays inside the native layer where it is checked
/// against the compiled library, and nothing native crosses into this package.
///
/// Only the horizontal directions are here. Nothing in the toolkit lays out a
/// vertical line, and an enumerator that could be named and would then be
/// dropped somewhere downstream is worse than one that cannot be named at all.
///
/// Read more: [Text, fonts and icons](https://goldberry.dev/docs/guide/text.html#paragraphs).
public enum TextDirection {

    /// Left to right.
    LTR,

    /// Right to left.
    ///
    /// The direction one run is shaped in. Bidirectional text is a paragraph
    /// split into such runs and placed in visual order, which [Paragraph] does
    /// with the levels in `dev.goldberry.text.bidi`.
    RTL
}
