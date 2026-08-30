package io.github.digitalsmile.goldberry.example.ui;

import java.util.List;

import io.github.digitalsmile.goldberry.example.ShowcaseModel;
import io.github.digitalsmile.goldberry.icon.Icon;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.menu.Item;
import io.github.digitalsmile.goldberry.widgets.menu.MenuBar;
import io.github.digitalsmile.goldberry.widgets.menu.Separator;

/// The window's own menu bar: **File**, **Edit** and **Help**, across the top of
/// the window and above everything else.
///
/// ## Why it is a widget and not a platform menu
///
/// Because on three of the four targets it is not one. A `menubar` is drawn by
/// this toolkit, styled by the same stylesheet as everything under it, and reaches
/// the keyboard through the same router — so `F10`, `Alt`, the arrow keys and the
/// accelerators are one implementation rather than four
/// ([ADR-0163](../../../../../../../book/src/adr/0163-a-menu-bar-owns-its-menus.md)).
/// The one menu this application does hand to the desktop is the tray's, and it
/// is an ordinary [io.github.digitalsmile.goldberry.widgets.menu.Menu] value for
/// exactly that reason ([ADR-0191]).
///
/// ## Why in Java and not in `statusbar.kdl` beside it
///
/// Because half of these rows are the *window's* rather than the model's. Opening
/// a dialog, floating a HUD and closing the window are things only a `Host` can
/// do, and a widget has none — so they arrive here as [Runnable]s the application
/// hands over, where a document would have had to name them through the action
/// registry and could not have said `checked(hud != null)` at all.
///
/// The rows that *are* the model's — the light, the density, the counter — are
/// direct method references on [ShowcaseModel.Actions], which is the shorter half
/// of §9's story and worth having one of in the showcase
/// ([ADR-0222](../../../../../../../book/src/adr/0222-a-showcase-is-a-window-a-bar-and-seven-screens.md)).
///
/// @param actions   the model's half — the light, the density and the road
/// @param window    the window's half — see [Handlers]
/// @param palette   the icon on Switch the light
public record AppMenu(ShowcaseModel.Actions actions, Handlers window, Icon palette) {

    /// The four things a menu row can ask for that a view model cannot answer.
    ///
    /// A record of [Runnable]s rather than four parameters, because they arrive
    /// together, they are all the window's, and a five-argument constructor of
    /// identical types is one nobody can call correctly twice.
    ///
    /// @param openDialog what File ▸ Unsaved changes… opens
    /// @param toggleHud  what View's frame-rate row toggles
    /// @param raiseToast what Edit ▸ Send word raises
    /// @param quit       what File ▸ Quit does
    public record Handlers(Runnable openDialog, Runnable toggleHud, Runnable raiseToast, Runnable quit) {}

    /// The bar itself.
    ///
    /// @param hudShown whether the frame-rate row draws its tick. Passed in rather
    ///                 than read, because whether a HUD is up is a fact about the
    ///                 window's overlay layer and this record cannot see one
    public MenuBar bar(boolean hudShown) {
        return new MenuBar(List.of(file(), edit(), help(hudShown)), Attributes.NONE.id("app-menu"));
    }

    /// **File** — the road, and the way out.
    ///
    /// `Begin again` and `Quit` are the two rows in this whole bar that destroy
    /// something, so they are the two at the bottom with a rule above them: a
    /// destructive row beside an ordinary one is a row somebody presses by
    /// accident.
    private Item file() {
        return new Item("File")
                .submenu(
                        new Item("March a league", actions::click).accelerator("Ctrl+K"),
                        new Item("Unsaved changes…", window.openDialog()).accelerator("Ctrl+O"),
                        new Separator(),
                        new Item("Switch the light", actions::toggleTheme)
                                .icon(palette)
                                .accelerator("Ctrl+T"),
                        new Item("Switch the density", actions::toggleDensity).accelerator("Ctrl+D"),
                        new Separator(),
                        new Item("Begin again at Bag End", actions::reset),
                        new Item("Quit", window.quit()).accelerator("Ctrl+Q"));
    }

    /// **Edit** — everything that changes what the model holds, and one row that
    /// cannot.
    ///
    /// `Redo` is disabled and stays disabled: the counter has an undo and no redo,
    /// and a menu that offered one anyway would be a row that silently did
    /// nothing. A disabled row is the honest version of that, and it is also the
    /// only one on this bar, which is why it is here rather than in a `Nothing`
    /// menu invented to hold it.
    private Item edit() {
        return new Item("Edit")
                .submenu(
                        new Item("Turn back", actions::undo).accelerator("Ctrl+Z"),
                        new Item("Press on", actions::click).disabled(true).accelerator("Ctrl+Y"),
                        new Separator(),
                        // Checked from the model rather than remembered here: the same
                        // box is on the Basic screen and the same switch beside it, and a
                        // menu that kept its own copy would disagree with both the moment
                        // either was used (ADR-0063).
                        new Item("Read the verse", actions::toggleProse)
                                .checked(actions.values().isProseShown()),
                        new Item("Send word", window.raiseToast()),
                        new Separator(),
                        new Item("Go to")
                                .submenu(Screen.GALLERY.stream()
                                        .map(name ->
                                                (Widget) new Item(Screen.title(name), () -> actions.pickScreen(name)))
                                        .toArray(Widget[]::new)));
    }

    /// **Help** — what this window is, and the two diagnostics that say how it is
    /// doing.
    ///
    /// The frame-rate row is the only **checkable** one on the bar, and it is
    /// checkable rather than a plain command because a HUD is a state you are in:
    /// a row that said `Frame rate` with nothing beside it would give a reader no
    /// way to tell whether pressing it turns one on or off.
    private Item help(boolean hudShown) {
        return new Item("Help")
                .submenu(
                        new Item("Frame rate", window.toggleHud())
                                .accelerator("Ctrl+F")
                                .checked(hudShown),
                        new Separator(),
                        new Item("Take the tour", () -> actions.pickScreen("navigation")),
                        new Item("About Goldberry", window.openDialog()));
    }
}
