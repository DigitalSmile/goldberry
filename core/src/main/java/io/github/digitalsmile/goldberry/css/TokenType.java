package io.github.digitalsmile.goldberry.css;

/// The token kinds Goldberry's CSS subset needs.
///
/// A deliberate subset of [CSS Syntax Level 3][spec]'s token set. What is here is
/// everything the selectors, declarations and at-rules of `ARCHITECTURE.md` §8
/// can be built from; what is missing is listed on [CssTokenizer], with the
/// reason.
///
/// [spec]: https://www.w3.org/TR/css-syntax-3/#tokenization
public enum TokenType {

    /// A name: `div`, `flex`, `--gb-bg`, `-webkit-thing`.
    ///
    /// Custom properties are idents like any other — `--gb-bg` starts with two
    /// hyphens, which the spec's identifier grammar allows precisely so that
    /// theming has a namespace the language will never claim.
    IDENT,

    /// An ident immediately followed by `(`: the `var` of `var(--gb-bg)`.
    ///
    /// The paren is consumed as part of the token, because `var (x)` with a space
    /// is *not* a function call and the tokenizer is the only place that
    /// distinction still exists.
    FUNCTION,

    /// `@media`, `@import`. The `@` is not part of [Token#text()].
    AT_KEYWORD,

    /// `#id`, and also `#ff0000`.
    ///
    /// The two are told apart by [Token#isIdentifierLike()] rather than by two
    /// token types, which is what the spec does: `#ff0000` is a hash whose value
    /// is not a valid identifier, and that is exactly the test a selector parser
    /// needs to reject it as an id.
    HASH,

    /// A quoted string, with the quotes removed and escapes resolved.
    STRING,

    /// `1`, `-2.5`, `+3e2`.
    NUMBER,

    /// `50%`. [Token#numeric()] is the number *before* the sign — `50`, not `0.5`.
    PERCENTAGE,

    /// `16px`, `1.5em`. [Token#unit()] holds the unit, lowercased.
    DIMENSION,

    /// One or more whitespace characters, collapsed.
    ///
    /// Kept rather than skipped: in a selector, whitespace is the descendant
    /// combinator, so throwing it away here would make `.a .b` and `.a.b`
    /// indistinguishable — two selectors that match very different things.
    WHITESPACE,

    COLON,
    SEMICOLON,
    COMMA,
    OPEN_BRACE,
    CLOSE_BRACE,
    OPEN_PAREN,
    CLOSE_PAREN,
    OPEN_BRACKET,
    CLOSE_BRACKET,

    /// Any other single character: `>`, `*`, `.`, `+`, `~`, `=`.
    ///
    /// The spec's catch-all. The parser gives these meaning by position — a `.`
    /// before an ident is a class selector, and nowhere else is it anything.
    DELIM,

    /// The end of the input. Always the last token, so a parser can look ahead
    /// one token without a bounds check on every call.
    EOF
}
