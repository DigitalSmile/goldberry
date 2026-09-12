package io.github.digitalsmile.goldberry.text;

/// Which way a run of text is shaped.
///
/// The toolkit's own vocabulary, for the reason every other mirrored enum has
/// one: the shaper numbers these — left-to-right is 4, not 0 — and that numbering
/// is a fact about a C header, checked against the compiled library where it
/// belongs (ADR-0282).
///
/// ## Horizontal only, for now
///
/// The vertical directions the shaper knows are deliberately absent. Nothing in
/// the toolkit lays out a vertical line, and an enumerator that could be named
/// and would then be dropped somewhere downstream is worse than one that cannot
/// be named at all. `docs/gaps.md` is where the case for them goes.
public enum TextDirection {

    /// Left to right.
    LTR,

    /// Right to left.
    ///
    /// Shaping a run this way is not bidirectional text: real bidi is splitting a
    /// paragraph into runs and ordering them, which the toolkit does not do
    /// (ADR-0218). This is the direction *one* run is shaped in.
    RTL
}
