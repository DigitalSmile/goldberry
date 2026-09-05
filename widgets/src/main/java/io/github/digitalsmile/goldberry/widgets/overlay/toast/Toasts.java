package io.github.digitalsmile.goldberry.widgets.overlay.toast;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.WeakHashMap;

import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.Overlay;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.style.Corner;

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
/// ## Finding the stack from inside the tree
///
/// That last sentence is true of an *application*, which holds the controller in
/// a field. It was not true of a **widget**: a control deep in the tree that
/// wanted to say "Saved" had to be handed a callback by whoever built it, and
/// every layer in between had to carry one.
///
/// [#of(BuildContext)] is the door — `Overlay.of(context)`'s shape, which is what
/// `TODO.md` asked for by name. `BuildContext.host()` gives the window
/// ([ADR-0140]) and [#at] is the one place that knows which stack is on it, so
/// the lookup is a map from the one to the other kept **here** rather than on
/// `Host`: `:core` must not learn what a toast is, which is the whole reason
/// `Toasts`, `Menus` and `Dialogs` are three classes in `:widgets` and not three
/// methods on the window ([ADR-0264]).
///
/// ```java
/// Toasts.of(context).ifPresent(toasts -> toasts.show("Saved"));
/// ```
///
/// Confined to the UI thread, like everything that touches a [Host].
public final class Toasts {

    private Toasts() {}

    /// Attaches a toast stack to `host`'s window at `corner`.
    ///
    /// A **corner** overlay rather than a filling one, which is the difference
    /// between this and a dialog in one word: a toast is non-modal, so it must
    /// cover as little as possible and take no press that is not its own. The
    /// overlay layer has done exactly this since `hud` was its first occupant
    /// (ADR-0100).
    ///
    /// @return the handle that takes the whole stack away again — for a window
    ///         that changes where its toasts appear, and for a test
    public static Overlay at(Host host, ToastController controller, Corner corner) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(controller, "controller");
        var overlay = host.overlay(new Toaster(controller, corner), corner);
        ATTACHED.put(host, controller);
        return overlay;
    }

    /// Which stack is on which window, for [#of(BuildContext)].
    ///
    /// **Weak on the key**, so a window that goes away takes its entry with it: a
    /// `Host` outliving the application that made it would be the one leak a
    /// convenience like this could cause, and nothing else here is in a position
    /// to notice a window closing.
    ///
    /// A plain map rather than a concurrent one, for this class's stated reason:
    /// everything that touches a [Host] is on the UI thread, and a second thread
    /// reaching a toast stack has a larger problem than this map.
    ///
    /// Last attachment wins. A window whose stack is replaced — moved to another
    /// corner, which is what [#at]'s return value is for — should hand out the new
    /// one, and the old `Overlay` has already stopped drawing by then.
    private static final Map<Host, ToastController> ATTACHED = new WeakHashMap<>();

    /// The toast stack on this widget's window, if one has been attached.
    ///
    /// `Overlay.of(context)`'s shape, and the answer to "how does a control deep
    /// in the tree raise a toast without every layer above it carrying a
    /// callback".
    ///
    /// **Empty is an answer**, twice over: a widget built into a tree with no
    /// window at all — which is what a unit test is — and a window whose
    /// application never called [#at]. Neither is a fault, and neither is a
    /// reason for a control to fail; a toast nobody arranged to show is a toast
    /// that does not appear, which is what the application decided by not
    /// attaching a stack.
    ///
    /// **For acting, not for building.** A `build` must stay a pure function of
    /// its widget, its state and its context, so what a build may do with this is
    /// *capture* it for a handler that runs later — [BuildContext#host()]'s own
    /// rule, and for the same reason.
    public static Optional<ToastController> of(BuildContext context) {
        Objects.requireNonNull(context, "context");
        return context.host().map(ATTACHED::get);
    }

    /// Forgets every attachment, for a test that builds two windows in one JVM.
    ///
    /// `ComputedStyle.forgetReportedDrops`'s reason exactly: a map that could not
    /// be cleared would make the second test in a class depend on whether the
    /// first had attached a stack to a host it no longer holds.
    static void forgetAttachments() {
        ATTACHED.clear();
    }

    /// [#at(Host, ToastController, Corner)] in the corner most applications
    /// want, which is the one furthest from where the eye reads and closest to
    /// where a status bar would be.
    public static Overlay at(Host host, ToastController controller) {
        return at(host, controller, Corner.BOTTOM_END);
    }
}
