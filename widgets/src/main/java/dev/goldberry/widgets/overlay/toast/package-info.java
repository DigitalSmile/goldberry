/// Toasts: transient notifications, queued and stacked in a corner of the
/// window, timed out with a pause on hover.
///
/// A [dev.goldberry.widgets.overlay.toast.Toast] is a value, not a
/// widget: an application holds a
/// [dev.goldberry.widgets.overlay.toast.ToastController] and
/// raises toasts through it, because whatever raises one is by definition somewhere
/// else. [dev.goldberry.widgets.overlay.toast.Toasts]
/// puts the stack on a window once, and
/// [dev.goldberry.widgets.overlay.toast.Toaster] is that stack,
/// the widget in the window's overlay layer that holds the queue.
///
/// Null-marked: every reference is non-null unless it says `@Nullable`.
///
/// Read more: [Overlays](https://goldberry.dev/docs/components/overlays.html#toasts).
@NullMarked
package dev.goldberry.widgets.overlay.toast;

import org.jspecify.annotations.NullMarked;
