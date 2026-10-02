package dev.goldberry.text.edit.keys;

/// What a key press asks an editor to do, before anything knows which editor.
///
/// ```java
/// EditCommand command = EditKeys.of(event, EditSurface.DOCUMENT);
/// if (command == null) {
///     return;                                   // not an editing key; let it go on
/// }
/// switch (command) {
///     case EditCommand.Move(var motion, var byWord, var extend) -> …
///     case EditCommand.MoveLine(var lines, var byPage, var extend) -> …
///     case EditCommand.Delete(var before, var byWord) -> …
///     case EditCommand.Type(var text) -> …
///     case EditCommand.Simple simple -> …
/// }
/// ```
///
/// The toolkit's three editors — the canvas `Editor`, and the states behind
/// `text-input` and `text-area` — hold their text in three different shapes.
/// This is the keyboard they agree on, made into a value: [EditKeys] turns a key
/// into one of these, and each editor turns one of these into its own call.
///
/// Sealed, so an editor's `switch` over it is exhaustive and a command added
/// later fails to compile in every editor that has to answer it.
///
/// Read more: [Input and focus](https://goldberry.dev/docs/guide/input.html#keys-and-text-are-different-events).
public sealed interface EditCommand {

    /// The caret moves within its line, or to an end of the text.
    ///
    /// @param motion where to
    /// @param byWord whether `Ctrl` was held — a word rather than a character
    /// @param extend whether `Shift` was — dragging the selection along
    record Move(Motion motion, boolean byWord, boolean extend) implements EditCommand {}

    /// The caret moves by whole lines, keeping the column it was in.
    ///
    /// Separate from [Move] because it is the one movement a string cannot
    /// perform: a column is an *x*, so it needs the layout, the font and the
    /// width the text wrapped at.
    ///
    /// @param lines  -1 for `Up`, 1 for `Down`
    /// @param byPage whether that is a page of lines rather than one — how many
    ///               lines a page is belongs to whoever knows how tall the
    ///               control is
    /// @param extend whether `Shift` is held
    record MoveLine(int lines, boolean byPage, boolean extend) implements EditCommand {}

    /// Text goes.
    ///
    /// @param before whether it is the text behind the caret — `Backspace` —
    ///               rather than in front of it
    /// @param byWord whether `Ctrl` was held
    record Delete(boolean before, boolean byWord) implements EditCommand {}

    /// Text arrives. Produced for `Enter` on a surface that takes newlines, and
    /// never for a character: committed text is its own event.
    record Type(String text) implements EditCommand {}

    /// The commands that take no argument at all.
    enum Simple implements EditCommand {

        /// `Ctrl+A`.
        SELECT_ALL,

        /// `Ctrl+C`.
        COPY,

        /// `Ctrl+X`.
        CUT,

        /// `Ctrl+V`.
        PASTE,

        /// `Ctrl+Z`.
        UNDO,

        /// `Ctrl+Shift+Z` and `Ctrl+Y`, the two spellings desktops use.
        REDO;

        /// Whether obeying this would change the text, and therefore whether a
        /// read-only editor must refuse it.
        ///
        /// Here rather than at each editor, so there is one list of which
        /// commands are edits.
        public boolean isEdit() {
            return this == CUT || this == PASTE || this == UNDO || this == REDO;
        }
    }
}
