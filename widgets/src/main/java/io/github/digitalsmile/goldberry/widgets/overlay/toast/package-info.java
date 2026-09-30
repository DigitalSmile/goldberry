/// `docs/core-widgets.md` §7's `toast` — transient notifications, queued and stacked
/// in a corner of the window, timed out with a pause on hover.
///
/// A [io.github.digitalsmile.goldberry.widgets.overlay.toast.Toast] is a value, not a
/// widget: an application holds a
/// [io.github.digitalsmile.goldberry.widgets.overlay.toast.ToastController] and
/// raises toasts through it, because whatever raises one is by definition somewhere
/// else (ADR-0177). [io.github.digitalsmile.goldberry.widgets.overlay.toast.Toasts]
/// puts the stack on a window once, and
/// [io.github.digitalsmile.goldberry.widgets.overlay.toast.Toaster] is that stack,
/// the widget in the window's overlay layer that holds the queue.
///
/// Marked for NullAway (`docs/testing.md` §2, ADR-0497).
@NullMarked
package io.github.digitalsmile.goldberry.widgets.overlay.toast;

import org.jspecify.annotations.NullMarked;
