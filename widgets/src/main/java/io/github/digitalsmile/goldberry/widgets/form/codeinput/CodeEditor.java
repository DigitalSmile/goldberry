package io.github.digitalsmile.goldberry.widgets.form.codeinput;

/// What [CodeField] tells about a key or a focus change — the seam between the
/// node that has a frame and the state that holds the code.
///
/// [io.github.digitalsmile.goldberry.widgets.form.textinput.TextEditor]'s shape,
/// and much shorter, because the editing model is
/// ([CodeEdit] says why): there is no caret to move, no
/// selection to extend, no undo stack and no word to step over.
///
/// Every method answers **whether it did anything**, which is what decides
/// whether the key event is consumed. A `Backspace` on an empty code has not
/// been handled, so it reaches whatever is behind the field.
interface CodeEditor {

    /// Committed text arrived — one character from a keystroke, or the whole code
    /// from a paste the platform delivered as text.
    boolean type(String text);

    /// `Backspace`.
    boolean backspace();

    /// `Escape` — every box emptied.
    ///
    /// Not a text field's `Escape`, which belongs to the dialog around it: a code
    /// field is filled in one go, and starting it again is the only correction
    /// anybody makes to one. Only handled when there is something to clear, so an
    /// empty field's `Escape` still closes the dialog.
    boolean clear();

    /// `Ctrl+V`. §4's "the thing users actually do".
    boolean paste();

    /// Focus arrived or left.
    void focusChanged(boolean focused, boolean fromKeyboard);
}
