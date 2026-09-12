package io.github.digitalsmile.goldberry.layout;

/// How children sit **across** a container's cross axis — CSS's `align-items`,
/// `align-self` and `align-content`.
///
/// The cross axis is the one [FlexDirection] did not choose: in a `ROW` this is
/// vertical, in a `COLUMN` horizontal. See [Justify] for the main axis.
///
/// One enum for all three properties, because CSS gives them one value space —
/// with the caveat that not every constant is meaningful on every property, which
/// is CSS's own untidiness rather than something to be fixed here.
///
/// The numbering deliberately does not travel with these. `Align.CENTER` and
/// `Justify.CENTER` are different numbers in the layout engine's headers, and a
/// toolkit that shared one constant between them would be relying on a
/// coincidence that is not true (ADR-0279).
public enum Align {

    /// Take the parent's `align-items`. Only meaningful for `align-self`, where
    /// it is the default.
    AUTO,

    /// Against the start of the cross axis.
    FLEX_START,

    /// Centred across it.
    CENTER,

    /// Against the end.
    FLEX_END,

    /// Filling the cross axis. The default for `align-items`.
    STRETCH,

    /// Aligned so the children's first text baselines line up.
    BASELINE,

    /// `align-content` only: lines packed with the space between them.
    SPACE_BETWEEN,

    /// `align-content` only: equal space around each line.
    SPACE_AROUND,

    /// `align-content` only: equal space everywhere.
    SPACE_EVENLY
}
