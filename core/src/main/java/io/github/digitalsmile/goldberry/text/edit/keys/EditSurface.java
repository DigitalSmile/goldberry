package io.github.digitalsmile.goldberry.text.edit.keys;

/// What kind of text an editor is editing, which is all [EditKeys] needs to know
/// about it.
///
/// The three editors differ in the keyboard in exactly two ways — whether `Up`
/// is a line or the start of the text, and whether `Enter` is a newline or
/// somebody else's — and those two questions are these three answers
/// ([ADR-0376]).
public enum EditSurface {

    /// One line that never wraps: `text-input`. `Up` is the start of the text
    /// and `Enter` belongs to the form around it.
    FIELD,

    /// Text that wraps onto more lines than were typed, and takes no newline of
    /// its own — an [io.github.digitalsmile.goldberry.text.edit.Editor] that was
    /// not made multiline. `Up` is a line; `Enter` is the caller's.
    WRAPPED,

    /// Text with lines in it because somebody typed them: `text-area`, and a
    /// multiline canvas editor.
    DOCUMENT;

    /// Whether `Up`, `Down` and the page keys move by lines here.
    public boolean isVertical() {
        return this != FIELD;
    }

    /// Whether `Enter` inserts a newline rather than being left for a default
    /// button, a submit, or whatever else is listening.
    public boolean takesNewlines() {
        return this == DOCUMENT;
    }
}
