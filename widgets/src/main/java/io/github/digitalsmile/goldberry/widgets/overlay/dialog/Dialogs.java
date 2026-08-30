package io.github.digitalsmile.goldberry.widgets.overlay.dialog;

import java.util.Objects;

import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.Overlay;

/// The half of a dialog that is not a widget: putting one on a window.
///
/// [ADR-0106](../../../../../../../../book/src/adr/0106-a-menu-is-a-widget-and-opening-one-is-not.md)
/// split `menu` this way and this is the same split for the same reason. A modal
/// needs the **window** — something has to cover it and dim it — and a widget has
/// no window. `Menus` is the other one of these.
///
/// ```java
/// var open = Dialogs.show(host, new Dialog("Unsaved changes",
///         new Text("Your draft has not been saved."),
///         new DialogAction("Keep editing", Role.DISMISSIVE, () -> stay()),
///         new DialogAction("Discard", Role.AFFIRMATIVE, () -> discard())));
/// // …and the handlers above end with:
/// open.remove();
/// ```
///
/// ## The application closes it, and the dialog fades first
///
/// What comes back is the [Overlay] handle, and `remove()` is how a dialog goes.
/// That is the application's call and always will be: only it knows whether the
/// question has been answered.
///
/// What it does **not** have to know is the animation. Every route out of a
/// dialog — a button, `Esc`, a press on the scrim — runs the closing animation
/// first and calls the handler when it is over
/// ([ADR-0176](../../../../../../../../book/src/adr/0176-a-dialog-is-a-widget-and-showing-one-is-not.md)),
/// so a handler that removes the overlay immediately still gets the fade.
///
/// Confined to the UI thread, like everything that touches a [Host].
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
    /// the scrim draws everywhere. `tour`'s veil found that out first (ADR-0121).
    /// The keyboard half is the panel declaring itself modal, which the router
    /// reads off the tree.
    ///
    /// @return the handle that takes it away again
    public static Overlay show(Host host, Dialog dialog) {
        Objects.requireNonNull(host, "host");
        Objects.requireNonNull(dialog, "dialog");
        return host.fill(dialog.attributes().id() == null ? dialog.id(DEFAULT_ID) : dialog);
    }
}
