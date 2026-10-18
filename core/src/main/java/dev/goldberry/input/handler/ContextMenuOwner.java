package dev.goldberry.input.handler;

import dev.goldberry.render.model.LogicalRect;
import dev.goldberry.widget.Widget;

/// A widget that has a context menu of its own, opened without the application
/// naming one.
///
/// A text field is the case. Cut, Copy, Paste and Select all mean the same in
/// every application, and an application that had to register a menu for every
/// field it shows would register the same menu every time. So the field opens
/// its own, from the state that knows whether anything is selected and whether
/// the clipboard holds text.
///
/// ## Who wins
///
/// The launcher walks up from what the gesture landed on, as it does for a
/// named menu ([dev.goldberry.Host#onContextMenu]). At each widget on the
/// walk a `context-menu=` the application wrote comes first, and then this. So
/// a field the application gave a menu of its own opens the application's, and
/// a field inside a panel with a menu opens the field's: the deepest answer
/// wins, as with every other question the walk asks. The application's handler
/// need not be registered for an owner to open its menu.
///
/// The same walk serves the menu key and `Shift+F10`, anchored to the focused
/// widget's painted rectangle instead of the pointer.
///
/// Read more: [Input and focus](https://goldberry.dev/docs/guide/input.html#context-menus).
public interface ContextMenuOwner extends Widget {

    /// Opens this widget's menu at `anchor`, in the window's coordinates.
    ///
    /// @return whether a menu opened. False lets the walk go on to the widgets
    ///         above, which is what a disabled field answers.
    boolean openContextMenu(LogicalRect anchor);
}
