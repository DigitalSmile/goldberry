package io.github.digitalsmile.goldberry.layout;

/// How a box is placed — CSS's `position`.
///
/// Named for the CSS property rather than for the engine's `PositionType`, which
/// is the sort of name a binding carries and a vocabulary should not (ADR-0279).
///
/// **[#RELATIVE] is the default**, not CSS's `static`. It is the layout engine's,
/// and the two lay out identically until something sets an inset — so the box a
/// stylesheet never mentions behaves the way a reader expects, and `static` stays
/// available for the one thing it is actually needed for: declining to be an
/// absolute child's containing block.
/// [io.github.digitalsmile.goldberry.css.ComputedStyle#INITIAL] and
/// [io.github.digitalsmile.goldberry.paint.Box#of()] are where that is written
/// down.
public enum Position {

    /// Laid out in flow, and [Insets] on it do nothing.
    STATIC,

    /// Laid out in flow, then shifted by its [Insets] without disturbing
    /// anything around it. The default.
    RELATIVE,

    /// Taken out of flow entirely and placed against its containing block's
    /// **padding** box — which is CSS's rule, and not the border box a reader
    /// might expect (ADR-0272).
    ABSOLUTE
}
