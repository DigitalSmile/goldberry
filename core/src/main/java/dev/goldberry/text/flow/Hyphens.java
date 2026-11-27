package dev.goldberry.text.flow;

/// Which discretionary breaks a document can use when wrapping a word.
public enum Hyphens {
    /// Use only soft hyphens supplied in the text (the CSS initial value).
    MANUAL,
    /// Also use the hyphenation patterns for the language of the word.
    AUTO,
    /// Ignore discretionary breaks.
    NONE
}
