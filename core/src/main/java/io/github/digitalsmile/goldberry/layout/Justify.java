package io.github.digitalsmile.goldberry.layout;

/// How spare space is distributed **along** a container's main axis — CSS's
/// `justify-content`.
///
/// [FlexDirection] decides which axis that is: in a `ROW` this is horizontal and
/// in a `COLUMN` it is vertical. The pairing is the single most common source of
/// "why is it not centred", and it is why this enum is not called `Horizontal`.
///
/// See [Align] for the other axis.
public enum Justify {

    /// Packed at the start. The default.
    FLEX_START,

    /// Packed in the middle.
    CENTER,

    /// Packed at the end.
    FLEX_END,

    /// First and last against the edges, the rest spread evenly between.
    SPACE_BETWEEN,

    /// Equal space around each child, so the outer gaps are half the inner ones.
    SPACE_AROUND,

    /// Equal space everywhere, outer gaps included.
    SPACE_EVENLY
}
