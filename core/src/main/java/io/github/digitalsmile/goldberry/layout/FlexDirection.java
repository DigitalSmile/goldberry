package io.github.digitalsmile.goldberry.layout;

/// Which way a container lays its children out — CSS's `flex-direction`.
///
/// The toolkit's own vocabulary rather than the layout engine's. The engine
/// numbers these, not alphabetically and not obviously — column is 0 and row is 2
/// — and that numbering is a fact about a C header, checked against the compiled
/// library where it belongs. Here a direction is a name (ADR-0279).
///
/// **`ROW` is the default**, which is CSS's. Yoga's own default is `COLUMN`,
/// and the toolkit corrects it: the layout engine is configured with web
/// defaults, so a box that says nothing about direction lays its children out
/// left to right, the way the CSS subset in `docs/ARCHITECTURE.md` §8 promises.
/// [io.github.digitalsmile.goldberry.css.ComputedStyle#INITIAL] and
/// [io.github.digitalsmile.goldberry.paint.Box#of()] are where that is written
/// down.
public enum FlexDirection {

    /// Top to bottom.
    COLUMN,

    /// Bottom to top.
    COLUMN_REVERSE,

    /// Left to right. The default.
    ROW,

    /// Right to left. Not the same as a right-to-left *writing* direction, which
    /// is a property of text rather than of a box.
    ROW_REVERSE
}
