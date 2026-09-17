package io.github.digitalsmile.goldberry.text.edit.keys;

/// Where a movement key sends the caret, along a line or to an end of the text.
///
/// Vertical movement is not here: it is [EditCommand.MoveLine], because moving
/// by a line needs a layout and moving along one does not.
public enum Motion {

    /// One position back, or one word back.
    LEFT,

    /// One forward.
    RIGHT,

    /// The start of the **soft** line — what `Home` means to a reader, and not
    /// the start of the hard line it wrapped from. On a single-line surface the
    /// two are the same place.
    LINE_START,

    /// The end of the soft line.
    LINE_END,

    /// The start of the whole text — `Ctrl+Home`.
    DOCUMENT_START,

    /// The end of it — `Ctrl+End`.
    DOCUMENT_END
}
