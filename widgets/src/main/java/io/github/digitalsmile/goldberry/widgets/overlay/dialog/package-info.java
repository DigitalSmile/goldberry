/// `docs/core-widgets.md` §7's `dialog` and its `action` — a modal over a dimmed
/// window, with a focus trap and an action bar in the platform's button order.
///
/// [io.github.digitalsmile.goldberry.widgets.overlay.dialog.Dialog] is the widget and
/// [io.github.digitalsmile.goldberry.widgets.overlay.dialog.DialogAction] one of its
/// buttons, carrying the role that decides its place and its key. Showing a modal
/// needs a window to cover and a widget has none, so
/// [io.github.digitalsmile.goldberry.widgets.overlay.dialog.Dialogs] is the half that
/// puts one on a window, the split `menu` makes (ADR-0106, ADR-0176). The scrim,
/// panel, title, body and action bar are parts.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package io.github.digitalsmile.goldberry.widgets.overlay.dialog;

import org.jspecify.annotations.NullMarked;
