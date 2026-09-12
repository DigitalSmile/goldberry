package io.github.digitalsmile.goldberry.layout;

/// Which way a container lays its children out — CSS's `flex-direction`.
///
/// The toolkit's own vocabulary rather than the layout engine's. The engine
/// numbers these, not alphabetically and not obviously — column is 0 and row is 2
/// — and that numbering is a fact about a C header, checked against the compiled
/// library where it belongs. Here a direction is a name (ADR-0279).
///
/// **`COLUMN` is the default**, which is flexbox's own and not CSS's: a bare
/// `<div>` stacks, and so does a bare flex container.
public enum FlexDirection {

    /// Top to bottom. The default.
    COLUMN,

    /// Bottom to top.
    COLUMN_REVERSE,

    /// Left to right.
    ROW,

    /// Right to left. Not the same as a right-to-left *writing* direction, which
    /// is a property of text rather than of a box.
    ROW_REVERSE
}
