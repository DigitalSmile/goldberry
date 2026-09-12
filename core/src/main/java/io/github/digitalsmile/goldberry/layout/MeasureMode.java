package io.github.digitalsmile.goldberry.layout;

/// How a leaf should read the constraint it was handed.
///
/// The toolkit's own vocabulary, for [FlexDirection]'s reason: these are
/// questions about layout, not about a C header, and the numbering that makes
/// them C stays in `:natives` where the layout probe checks it (ADR-0279).
public enum MeasureMode {

    /// There is no constraint; the accompanying number means nothing.
    UNDEFINED,

    /// The size has already been decided. A leaf that reports something else is
    /// overruled, so this is not a question.
    EXACTLY,

    /// At most this much. A leaf may report less, and a paragraph that wraps
    /// usually does.
    AT_MOST
}
