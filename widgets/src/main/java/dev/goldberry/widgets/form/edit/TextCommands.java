package dev.goldberry.widgets.form.edit;

/// The four clipboard and selection commands a text field answers, and whether
/// each can run now: what the stock [TextMenu] is built from.
///
/// The same commands the field runs on `Ctrl+X`, `Ctrl+C`, `Ctrl+V` and
/// `Ctrl+A`, so the menu and the keys cannot disagree about what Cut does in a
/// password field. Each `can…` is asked when the menu is built, and again when
/// the clipboard changes while it is open; a command whose `can…` said false is
/// shown disabled and never run from the menu.
public interface TextCommands {

    /// Whether Cut would remove something: a selection, in a field that takes
    /// edits and lets text leave.
    boolean canCut();

    /// Whether Copy would put something on the clipboard: a selection, in a field
    /// that lets text leave. A password field never does.
    boolean canCopy();

    /// Whether Paste would insert something: a field that takes edits, and a
    /// clipboard holding text.
    boolean canPaste();

    /// Whether Select all would change the selection: some text, not all of it
    /// selected already.
    boolean canSelectAll();

    /// Cuts the selection to the clipboard. @return whether anything went
    boolean cut();

    /// Copies the selection to the clipboard. @return whether the platform took it
    boolean copy();

    /// Inserts the clipboard's text at the caret. @return whether anything went in
    boolean paste();

    /// Selects all the text. @return whether the selection changed
    boolean selectAll();
}
