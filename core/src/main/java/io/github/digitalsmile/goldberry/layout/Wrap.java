package io.github.digitalsmile.goldberry.layout;

/// Whether children that do not fit start a new line — CSS's `flex-wrap`.
public enum Wrap {

    /// Everything on one line, overflowing or shrinking to fit. The default.
    NO_WRAP,

    /// New lines after the first in the cross axis' own direction.
    WRAP,

    /// New lines in the other direction, so the first line ends up last.
    WRAP_REVERSE
}
