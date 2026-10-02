package dev.goldberry.widgets.overlay.dialog;

import java.util.Objects;

import dev.goldberry.Host;
import dev.goldberry.Overlay;

/// The half of a dialog that is not a widget: putting one on a window.
///
/// The same split `Menus` makes for a menu, for the same reason. A modal
/// needs the **window** — something has to cover it and dim it — and a widget has
/// no window.
///
/// ```java
/// var open = Dialogs.show(host, new Dialog("Unsaved changes",
///         new Text("Your draft has not been saved."),
///         new DialogAction("Keep editing", Role.DISMISSIVE, () -> stay()),
///         new DialogAction("Discard", Role.AFFIRMATIVE, () -> discard())));
/// // …and when the application decides it is over without the user:
/// open.dismiss();
/// ```
///
/// ## The dialog fades, then takes itself off the window
///
/// What comes back is the [Overlay] handle. Every route out of a dialog — a
/// button, `Esc`, a press on the scrim, the title bar's × — runs the closing
/// animation first, calls the handler when it is over, and then removes the
/// overlay. A handler does not have to remove it, and one that still does is
/// harmless, because removing is idempotent.
///
/// When the **application** is the one that decides — a sign-in that finished
/// on its own, a dialog that a newer one replaces — [Overlay#dismiss()] runs the
/// same fade without pressing anything, and removes it at the end.
/// [Overlay#remove()] takes it away at once, with no fade.
///
/// Confined to the UI thread, like everything that touches a [Host].
///
/// Read more: [Overlays](https://goldberry.dev/docs/components/overlays.html#dialog).
public final class Dialogs {

    /// The id a dialog gets when it arrives without one.
    ///
    /// A dialog is focused by id — that is what [Host#focus] takes — so one with
    /// no name could not be opened with the keyboard in it. Rather than refuse a
    /// perfectly reasonable `new Dialog("Delete this?", …)`, it is given a name.
    ///
    /// Constant rather than counted: two modals at once is already the rare case
    /// and two *anonymous* ones is rarer still, and a counter would make the id
    /// in a golden image depend on how many dialogs the test had opened before.
    public static final String DEFAULT_ID = "dialog";

    private Dialogs() {}

    /// Shows `dialog` over the whole of `host`'s window.
    ///
    /// A **filling** overlay ([Overlay#filling]), which is what makes it modal to
    /// the pointer: a filling overlay takes every press wherever it draws, and
    /// the scrim draws everywhere.
    /// The keyboard half is the panel declaring itself modal, which the router
    /// reads off the tree.
    ///
    /// A dialog that arrives without an id is given [#DEFAULT_ID], and **a key
    /// it was given is kept**. An id doubles as a key, so naming the dialog
    /// would otherwise replace a `keyed(…)` the caller wrote.
    ///
    /// @return the handle that takes it away again
    public static Overlay show(Host host, Dialog dialog) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(dialog, "dialog");
        return host.fill(named(dialog));
    }

    /// `dialog`, with [#DEFAULT_ID] if it has no id, and its own key either way.
    static Dialog named(Dialog dialog) {
        var attributes = dialog.attributes();
        if (attributes.id() != null) {
            return dialog;
        }
        var key = attributes.key();
        var named = attributes.id(DEFAULT_ID);
        return dialog.withAttributes(key == null ? named : named.key(key));
    }
}
