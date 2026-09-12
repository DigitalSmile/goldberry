package io.github.digitalsmile.goldberry.layout;

/// What happens to content larger than the box holding it — CSS's `overflow`.
public enum Overflow {

    /// It spills, and is drawn. The default.
    VISIBLE,

    /// It is clipped to the box.
    HIDDEN,

    /// It is clipped, and the box scrolls.
    SCROLL
}
