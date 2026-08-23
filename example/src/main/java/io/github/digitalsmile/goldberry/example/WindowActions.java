package io.github.digitalsmile.goldberry.example;

import io.github.digitalsmile.goldberry.bind.Action;
import io.github.digitalsmile.goldberry.bind.runtime.Actions;
import java.util.Objects;

/// The two actions that belong to the **window** rather than to what it shows.
///
/// `app.open-menu` needs a `Host` to open a platform popup, and `app.toggle-hud`
/// changes what the window overlays — neither is anything a view model should
/// know about, and neither has a value behind it.
///
/// ## Why this exists at all
///
/// The alternative was annotating [Showcase] itself, and that leaked:
/// an `Application` is the class that owns the window and the lifecycle, and
/// making it *also* a thing markup resolves names against put two unrelated roles
/// on one type — visible as a `@Model` sitting on top of an
/// `implements Application`
/// ([ADR-0138](../../../../../../book/src/adr/0138-a-window-s-actions-are-a-model-of-their-own.md)).
///
/// `Runnable`s rather than a reference to the window, so this knows what the
/// actions *do* and nothing about what does it. The window supplies the
/// behaviour; this supplies the names.
@Actions
public record WindowActions(Runnable openMenu, Runnable toggleHud, Runnable openDialog,
        Runnable raiseToast) {

    public WindowActions {
        Objects.requireNonNull(openMenu, "openMenu");
        Objects.requireNonNull(toggleHud, "toggleHud");
        Objects.requireNonNull(openDialog, "openDialog");
        Objects.requireNonNull(raiseToast, "raiseToast");
    }

    /// Opens the menu under the button that opened it, or closes it again.
    @Action("app.open-menu")
    public void open() {
        openMenu.run();
    }

    /// Shows or hides the frame-rate overlay.
    @Action("app.toggle-hud")
    public void hud() {
        toggleHud.run();
    }

    /// Opens the modal.
    ///
    /// Here rather than on the view model for `app.open-menu`'s reason: showing
    /// a dialog covers the *window*, and a view model has none (ADR-0176).
    @Action("app.open-dialog")
    public void dialog() {
        openDialog.run();
    }

    /// Raises a toast.
    ///
    /// The **only** one of these four that a view model could have owned, and it
    /// is here for symmetry rather than necessity: a `ToastController` is a field
    /// an application holds and needs no `Host` at the point of use, which is the
    /// whole reason it is a controller (ADR-0177). It sits beside the other three
    /// because it is a fact about this window's furniture.
    @Action("app.raise-toast")
    public void toast() {
        raiseToast.run();
    }
}
