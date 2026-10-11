package dev.goldberry.layout;

/// What happens to content larger than the box holding it — CSS's `overflow`.
///
/// Declared from the least cutting to the most, which is the order two axes
/// are joined in when a box can hold only one.
public enum Overflow {

    /// It spills, and is drawn. The default.
    VISIBLE,

    /// It is clipped to the box, and nothing can scroll it: not even a program.
    CLIP,

    /// It is clipped to the box.
    HIDDEN,

    /// It is clipped, and the box scrolls.
    SCROLL
}
