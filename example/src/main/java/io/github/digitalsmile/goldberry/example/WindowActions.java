package io.github.digitalsmile.goldberry.example;

import io.github.digitalsmile.goldberry.bind.Action;

/// The four commands that are the **window's** rather than the model's, named so
/// that a document can press them.
///
/// Opening a popup, floating a HUD, showing a modal and raising a toast are facts
/// about a window: every one of them needs a
/// [io.github.digitalsmile.goldberry.Host], and a view model has none. So they
/// are not `@Action`s on [ShowcaseModel] — they are here, in a record that holds
/// four handlers and nothing else, and [Showcase] puts it in
/// [Showcase#models()] beside the model.
///
/// ## Why this exists when the menu bar does not need it
///
/// Because `overlays.kdl` does. The window's own menu bar is built in Java and
/// holds [AppMenu.Handlers] directly — a `Runnable` passed to a constructor needs
/// no name at all. A **document** cannot do that: `press="app.open-menu"` is a
/// string, and the only thing that can turn a string into a call is a registry.
///
/// That the registry is *strict* is the point rather than a detail: a `press=`
/// typo is otherwise a button that silently does nothing, and this record is what
/// makes it a failure at inflation that names the action it could not find
/// (ADR-0132).
///
/// @param openMenu    what `app.open-menu` does — a platform popup under the
///                    button that opened it
/// @param toggleHud   what `app.toggle-hud` does
/// @param openDialog  what `app.open-dialog` does
/// @param raiseToast  what `app.raise-toast` does
@io.github.digitalsmile.goldberry.bind.runtime.Actions
public record WindowActions(Runnable openMenu, Runnable toggleHud, Runnable openDialog, Runnable raiseToast) {

    @Action("app.open-menu")
    public void openTheMenu() {
        openMenu.run();
    }

    @Action("app.toggle-hud")
    public void toggleTheHud() {
        toggleHud.run();
    }

    @Action("app.open-dialog")
    public void openTheDialog() {
        openDialog.run();
    }

    @Action("app.raise-toast")
    public void raiseAToast() {
        raiseToast.run();
    }
}
