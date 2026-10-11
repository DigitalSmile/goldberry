package dev.goldberry.text.edit.keys;

import dev.goldberry.layout.Direction;

/// Where a movement key sends the caret, along a line or to an end of the text.
///
/// Vertical movement is not here: it is [EditCommand.MoveLine], because moving
/// by a line needs a layout and moving along one does not.
///
/// [#LEFT] and [#RIGHT] are the keys, and the caret moves logically: back and
/// forward through the text, one grapheme or one word, whatever the runs it
/// crosses look like on screen. In a right-to-left paragraph the text goes on
/// to the left, so [#in(Direction)] swaps the two there.
///
/// Read more: [Input and focus](https://goldberry.dev/docs/guide/input.html#keys-and-text-are-different-events).
public enum Motion {

    /// One position back, or one word back.
    LEFT,

    /// One forward.
    RIGHT,

    /// The start of the visual line — what `Home` means to a reader, and not
    /// the start of the hard line it wrapped from. On a single-line surface the
    /// two are the same place.
    LINE_START,

    /// The end of the visual line.
    LINE_END,

    /// The start of the whole text — `Ctrl+Home`.
    DOCUMENT_START,

    /// The end of it — `Ctrl+End`.
    DOCUMENT_END;

    /// The motion this key means in a paragraph whose base direction is
    /// `direction`: [#LEFT] is forward and [#RIGHT] back right to left, since
    /// the text reads towards the left there. Every other motion is itself.
    public Motion in(Direction direction) {
        if (direction == Direction.LTR) {
            return this;
        }
        return switch (this) {
            case LEFT -> RIGHT;
            case RIGHT -> LEFT;
            case LINE_START, LINE_END, DOCUMENT_START, DOCUMENT_END -> this;
        };
    }
}
