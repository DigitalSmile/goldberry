package dev.goldberry.html.style;

/// What [DocumentStyles] does with the colours a document writes. The view's
/// public choice, `AuthorStyles`, maps onto these three.
public enum Colours {

    /// Every colour dropped: the page is in the theme's.
    THEME,

    /// Each colour kept where it can be seen to read: see [DocumentStyles].
    ADAPT,

    /// Every colour as written.
    AUTHOR
}
