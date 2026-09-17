package io.github.digitalsmile.goldberry.text.edit.keys;

/// What a key press means to an editor, before anything knows which editor.
///
/// The three editors in the toolkit — [io.github.digitalsmile.goldberry.text.edit.Editor]
/// on a canvas, and the states behind `text-input` and `text-area` — hold their
/// text in three different shapes and agreed about the keyboard only because
/// each was written from the last. This is the agreement made into a value:
/// [EditKeys] turns a key into one of these, and each editor turns one of these
/// into its own call ([ADR-0376]).
///
/// Sealed, so an editor's `switch` over it is exhaustive and a command added
/// tomorrow fails to compile in the three places that have to answer it — which
/// is the whole point of writing the map down.
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

        /// `Ctrl+Shift+Z` and `Ctrl+Y`, which are the two spellings desktops use
        /// and both of which every editor here has always taken.
        REDO;

        /// Whether obeying this would change the text, and therefore whether a
        /// read-only editor must refuse it.
        ///
        /// Here rather than at each of the three call sites, because "which of
        /// these are edits" is exactly the kind of list that was being kept in
        /// three places (ADR-0376).
        public boolean isEdit() {
            return this == CUT || this == PASTE || this == UNDO || this == REDO;
        }
    }
}
