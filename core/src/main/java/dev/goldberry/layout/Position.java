package dev.goldberry.layout;

/// How a box is placed — CSS's `position`.
///
/// Named for the CSS property rather than for the engine's `PositionType`, which
/// is the sort of name a binding carries and a vocabulary should not.
///
/// **[#RELATIVE] is the default**, not CSS's `static`. It is the layout engine's,
/// and the two lay out identically until something sets an inset — so the box a
/// stylesheet never mentions behaves the way a reader expects, and `static` stays
/// available for the one thing it is actually needed for: declining to be an
/// absolute child's containing block.
/// [dev.goldberry.css.ComputedStyle#INITIAL] and
/// [dev.goldberry.paint.Box#of()] are where that is written
/// down.
///
/// Read more: [How layout works](https://goldberry.dev/docs/layout/index.html#the-box-model).
public enum Position {

    /// Laid out in flow, and [Insets] on it do nothing.
    STATIC,

    /// Laid out in flow, then shifted by its [Insets] without disturbing
    /// anything around it. The default.
    RELATIVE,

    /// Taken out of flow entirely and placed against its containing block's
    /// **padding** box, which is CSS's rule: inside the border and outside the
    /// padding, so `top: 0; right: 0` is the inner corner of the border and not
    /// the corner of the content. An edge given no inset keeps the static
    /// position, where a child in flow would start.
    ABSOLUTE
}
