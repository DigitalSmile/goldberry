package io.github.digitalsmile.goldberry.backend;

/// The shape the pointer takes over a widget (§7.3).
///
/// The names are **CSS's**, not the platform's: `not-allowed` rather than
/// `SDL_SYSTEM_CURSOR_NOT_ALLOWED`, `ew-resize` rather than `SIZEWE`. That is what
/// lets `cursor: pointer` in a stylesheet resolve by name rather than through a
/// translation table, and it is the same trick the layout properties already use
/// to reach Yoga (`space-between` is `SPACE_BETWEEN`).
///
/// In the SPI rather than in `input`, alongside [PixelFormat] and [DisplayScale],
/// because it is a thing the backend has to map onto something native — and
/// because both the render tree and the input router name it, and neither should
/// have to depend on the other to do so.
public enum Cursor {

    /// The ordinary arrow. What a window shows unless something says otherwise.
    DEFAULT,

    /// The pointing hand: this is activatable. CSS calls it `pointer`, every
    /// platform's own name for it is "hand", and it is the single most-used
    /// non-default shape.
    POINTER,

    /// The I-beam, over selectable or editable text.
    TEXT,

    /// The four-headed arrow: this can be dragged anywhere.
    MOVE,

    /// Busy, and not accepting input.
    WAIT,

    /// Busy, but still interactive — the arrow with a spinner beside it.
    PROGRESS,

    /// Crosshairs, for precise picking.
    CROSSHAIR,

    /// This target will refuse the drop.
    NOT_ALLOWED,

    /// East–west double arrow: a vertical splitter, or the left and right edges
    /// of a window.
    EW_RESIZE,

    /// North–south double arrow.
    NS_RESIZE,

    /// The north-east/south-west diagonal.
    NESW_RESIZE,

    /// The north-west/south-east diagonal.
    NWSE_RESIZE,

    /// An open hand: this can be picked up.
    ///
    /// **No platform system cursor matches** — SDL has none, and neither X11's
    /// cursor font nor Win32's `IDC_*` set has an open hand as a standard shape.
    /// It falls back to [#MOVE], which says the same thing less precisely, until
    /// custom image cursors ship (§7.3).
    GRAB,

    /// A closed hand: this is being dragged. Falls back to [#MOVE] for the
    /// reason [#GRAB] does.
    GRABBING
}
