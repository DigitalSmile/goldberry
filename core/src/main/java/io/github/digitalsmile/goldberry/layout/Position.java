package io.github.digitalsmile.goldberry.layout;

/// How a box is placed — CSS's `position`.
///
/// Named for the CSS property rather than for the engine's `PositionType`, which
/// is the sort of name a binding carries and a vocabulary should not (ADR-0279).
public enum Position {

    /// Laid out in flow, and [Insets] on it do nothing. The default.
    STATIC,

    /// Laid out in flow, then shifted by its [Insets] without disturbing
    /// anything around it.
    RELATIVE,

    /// Taken out of flow entirely and placed against its containing block's
    /// **padding** box — which is CSS's rule, and not the border box a reader
    /// might expect (ADR-0272).
    ABSOLUTE
}
