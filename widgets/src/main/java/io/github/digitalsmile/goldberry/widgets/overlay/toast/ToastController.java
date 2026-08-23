package io.github.digitalsmile.goldberry.widgets.overlay.toast;

import java.util.List;
import java.util.Objects;

/// A handle on the [Toaster] — what an application holds, and the only thing it
/// ever touches.
///
/// ```java
/// private final ToastController toasts = new ToastController();
/// // once, when the window starts:
/// Toasts.at(host, toasts, Corner.BOTTOM_END);
/// // and from anywhere, for ever after:
/// toasts.show(new Toast("Draft saved"));
/// ```
///
/// The arrangement [io.github.digitalsmile.goldberry.widgets.core.scroll.ScrollController]
/// and [io.github.digitalsmile.goldberry.widgets.form.form.FormController] both
/// use, and here for the sharpest version of their reason: **whatever raises a
/// toast is by definition somewhere else**. A save handler deep in a view model
/// has no widget tree, no `Host`, and nothing it could reasonably be given —
/// what it has is a field.
///
/// ## Lifetime
///
/// A controller with no stack attached does nothing and loses nothing worth
/// keeping: [#show] on a detached controller is a no-op, which is the state every
/// controller is in for at least one frame and the state it returns to when the
/// window closes. A toast raised into a window that is gone is not an error —
/// it is a background job finishing late, which is the ordinary case rather than
/// the exceptional one.
///
/// Confined to the UI thread, like the tree it reaches into. Work that finishes
/// on a virtual thread hands back to the UI thread first
/// ([ADR-0020](../../../../../../../../book/src/adr/0020-one-ui-thread-and-virtual-threads-behind-it.md)),
/// and that is where it raises its toast.
public final class ToastController {

    /// A controller with nothing attached yet.
    public ToastController() {
    }

    /// The attached stack's state, or null. Package-private and set only by
    /// [ToasterState], which attaches on mount and detaches on unmount.
    ToasterState attached;

    /// Whether a stack is currently listening.
    public boolean isAttached() {
        return attached != null;
    }

    /// Raises a toast.
    ///
    /// Returns silently for a detached controller — see the note on lifetime.
    public void show(Toast toast) {
        Objects.requireNonNull(toast, "toast");
        if (attached != null) {
            attached.show(toast);
        }
    }

    /// Raises a toast that says one thing and goes.
    public void show(String text) {
        show(new Toast(text));
    }

    /// Takes every toast away at once, each with its own exit.
    ///
    /// What a screen change wants: a toast about the page you have left is a
    /// toast about nothing.
    public void clear() {
        if (attached != null) {
            attached.clear();
        }
    }

    /// What is on screen right now, oldest first — including any that are on
    /// their way out.
    ///
    /// For a test, and for an application that wants to know whether it has
    /// already said this. Empty for a detached controller.
    public List<Toast> showing() {
        return attached == null ? List.of() : attached.showing();
    }

    @Override
    public String toString() {
        return "ToastController[" + (attached == null ? "detached" : showing().size() + " showing")
                + "]";
    }
}
