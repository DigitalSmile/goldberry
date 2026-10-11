package dev.goldberry.render;

import dev.goldberry.render.model.DisplayScale;
import dev.goldberry.render.model.PixelFormat;

/// The shape the pointer takes over a widget.
///
/// ```css
/// .link { cursor: pointer }
/// ```
///
/// The names are **CSS's**, not the platform's: `not-allowed` rather than
/// `SDL_SYSTEM_CURSOR_NOT_ALLOWED`, `ew-resize` rather than `SIZEWE`. That is what
/// lets `cursor: pointer` in a stylesheet resolve by name rather than through a
/// translation table, and it is the same trick the layout properties already use
/// to reach Yoga (`space-between` is `SPACE_BETWEEN`).
///
/// Each shape is the platform's own unless the application drew it: a shape
/// given a picture through [dev.goldberry.Application#cursors()] shows that
/// picture wherever the shape is asked for.
///
/// In the SPI rather than in `input`, alongside [PixelFormat] and [DisplayScale],
/// because it is a thing the backend has to map onto something native — and
/// because both the render tree and the input router name it, and neither should
/// have to depend on the other to do so.
///
/// Read more: [Input and focus](https://goldberry.dev/docs/guide/input.html#the-cursor).
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
    /// It is shown as the application's picture when
    /// [dev.goldberry.Application#cursors()] gives it one, and falls back to
    /// [#MOVE], which says the same thing less precisely, when it does not.
    GRAB,

    /// A closed hand: this is being dragged. Falls back to [#MOVE] without a
    /// picture, for the reason [#GRAB] does.
    GRABBING,

    /// No pointer at all while it is over the box: a game's own crosshair, a
    /// video playing full-screen.
    NONE,

    // The rest of CSS's keywords. Each falls back to the nearest platform shape
    // without a picture, since none of them is one SDL draws on every platform:
    // the four edges and four corners are the two-headed arrows, as SDL's own
    // per-edge shapes say they may be anywhere, and the others a near cousin.

    /// Help is available: the arrow without a picture.
    HELP,

    /// A context menu is available: the arrow without a picture.
    CONTEXT_MENU,

    /// A cell or a set of them can be selected: the crosshair without a picture.
    CELL,

    /// Vertical text can be selected: the I-beam without a picture.
    VERTICAL_TEXT,

    /// A drop makes a link or a shortcut: the arrow without a picture.
    ALIAS,

    /// A drop makes a copy: the arrow without a picture.
    COPY,

    /// A drop is not possible here: [#NOT_ALLOWED] without a picture.
    NO_DROP,

    /// Scrolling in any direction: [#MOVE] without a picture.
    ALL_SCROLL,

    /// A column can be resized: [#EW_RESIZE] without a picture.
    COL_RESIZE,

    /// A row can be resized: [#NS_RESIZE] without a picture.
    ROW_RESIZE,

    /// The top edge moves: [#NS_RESIZE] without a picture.
    N_RESIZE,

    /// The right edge moves: [#EW_RESIZE] without a picture.
    E_RESIZE,

    /// The bottom edge moves: [#NS_RESIZE] without a picture.
    S_RESIZE,

    /// The left edge moves: [#EW_RESIZE] without a picture.
    W_RESIZE,

    /// The top right corner moves: [#NESW_RESIZE] without a picture.
    NE_RESIZE,

    /// The top left corner moves: [#NWSE_RESIZE] without a picture.
    NW_RESIZE,

    /// The bottom right corner moves: [#NWSE_RESIZE] without a picture.
    SE_RESIZE,

    /// The bottom left corner moves: [#NESW_RESIZE] without a picture.
    SW_RESIZE,

    /// Zooming in: the crosshair without a picture.
    ZOOM_IN,

    /// Zooming out: the crosshair without a picture.
    ZOOM_OUT
}
