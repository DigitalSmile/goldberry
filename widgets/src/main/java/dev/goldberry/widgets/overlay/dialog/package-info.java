/// Dialogs: a modal over a dimmed window, with a focus trap and an action bar
/// in the platform's button order.
///
/// [dev.goldberry.widgets.overlay.dialog.Dialog] is the widget and
/// [dev.goldberry.widgets.overlay.dialog.DialogAction] one of its
/// buttons, carrying the role that decides its place and its key. Showing a modal
/// needs a window to cover and a widget has none, so
/// [dev.goldberry.widgets.overlay.dialog.Dialogs] is the half that
/// puts one on a window, the same split `menu` makes. The scrim,
/// panel, title, body and action bar are parts.
///
/// Null-marked: every reference is non-null unless it says `@Nullable`.
///
/// Read more: [Overlays](https://goldberry.dev/docs/components/overlays.html#dialog).
@NullMarked
package dev.goldberry.widgets.overlay.dialog;

import org.jspecify.annotations.NullMarked;
