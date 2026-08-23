package io.github.digitalsmile.goldberry.widgets.overlay.toast;

import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.Overlay;
import io.github.digitalsmile.goldberry.widget.style.Corner;

import java.util.Objects;

/// Putting a toast stack on a window — the half of §7's `toast` that is not a
/// widget.
///
/// `Menus.open` and `Dialogs.show` are the other two of these, and the split is
/// the same one every time: what floats over a window needs the **window**, and
/// a widget has none.
///
/// ```java
/// private final ToastController toasts = new ToastController();
///
/// @Override
/// public void start(Host host) {
///     Toasts.at(host, toasts, Corner.BOTTOM_END);
/// }
/// ```
///
/// One call, once, and the application never mentions the stack again: from then
/// on it holds a [ToastController] and raises toasts through it from wherever
/// they happen.
///
/// Confined to the UI thread, like everything that touches a [Host].
public final class Toasts {

    private Toasts() {
    }

    /// Attaches a toast stack to `host`'s window at `corner`.
    ///
    /// A **corner** overlay rather than a filling one, which is the difference
    /// between this and a dialog in one word: a toast is non-modal, so it must
    /// cover as little as possible and take no press that is not its own. The
    /// overlay layer has done exactly this since `hud` was its first occupant
    /// ([ADR-0100](../../../../../../../../book/src/adr/0100-a-window-has-an-overlay-layer.md)).
    ///
    /// @return the handle that takes the whole stack away again — for a window
    ///         that changes where its toasts appear, and for a test
    public static Overlay at(Host host, ToastController controller, Corner corner) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(controller, "controller");
        return host.overlay(new Toaster(controller, corner), corner);
    }

    /// [#at(Host, ToastController, Corner)] in the corner most applications
    /// want, which is the one furthest from where the eye reads and closest to
    /// where a status bar would be.
    public static Overlay at(Host host, ToastController controller) {
        return at(host, controller, Corner.BOTTOM_END);
    }
}
