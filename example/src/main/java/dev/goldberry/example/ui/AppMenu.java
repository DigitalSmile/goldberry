package dev.goldberry.example.ui;

import java.util.ArrayList;
import java.util.List;

import dev.goldberry.example.model.ShowcaseActions;
import dev.goldberry.example.ui.home.HomeScreen;
import dev.goldberry.example.ui.shell.ReadingSize;
import dev.goldberry.icon.Icon;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.menu.Item;
import dev.goldberry.widgets.menu.MenuBar;
import dev.goldberry.widgets.menu.Separator;

/// The window's own menu: **File**, **Edit**, **View** and **Help**, folded into
/// one overflow heading at the end of the top bar.
///
/// One heading rather than four, because the top bar is where the search field
/// and the light are, and four headings across it were four things to read
/// before the screen. `F10` still opens it, and every row keeps its accelerator.
///
/// ## Why it is a widget and not a platform menu
///
/// Because on three of the four targets it is not one. A `menubar` is drawn by
/// this toolkit, styled by the same stylesheet as everything under it, and reaches
/// the keyboard through the same router — so `F10`, `Alt`, the arrow keys and the
/// accelerators are one implementation rather than four.
/// The one menu this application does hand to the desktop is the tray's, and it
/// is an ordinary [dev.goldberry.widgets.menu.Menu] value for
/// exactly that reason.
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
/// direct method references on [ShowcaseActions], which is the shorter half
/// of the story and worth having one of in the showcase.
///
/// Read more: [Menus and the tray](https://goldberry.dev/docs/components/menus.html#menubar).
///
/// @param actions   the model's half — the light, the density and the road
/// @param window    the window's half — see [Handlers]
/// @param palette   the icon on Switch the light
public record AppMenu(ShowcaseActions actions, Handlers window, Icon palette) {

    /// The seven things a menu row can ask for that a view model cannot answer.
    ///
    /// A record of [Runnable]s rather than seven parameters, because they arrive
    /// together and they are all the window's. Seven positional arguments of one
    /// type is exactly the shape nobody can call correctly twice, which is why
    /// `AppMenuTest` presses every row and asserts *which* handler ran: a slot
    /// swapped with its neighbour is a test failure rather than a menu that
    /// quietly opens the wrong thing. That is not hypothetical — Help ▸ Take the
    /// tour selected a screen and Help ▸ About opened the unsaved-changes dialog
    /// until the 2026-09-18 review read the rows against their names.
    ///
    /// @param openDialog what File ▸ Unsaved changes… opens
    /// @param toggleHud  what Help's frame-rate row toggles
    /// @param raiseToast what Edit ▸ Send word raises
    /// @param startTour  what Help ▸ Take the tour starts
    /// @param about      what Help ▸ About Goldberry opens
    /// @param quit       what File ▸ Quit does
    /// @param search     what View ▸ Search… opens
    public record Handlers(
            Runnable openDialog,
            Runnable toggleHud,
            Runnable raiseToast,
            Runnable startTour,
            Runnable about,
            Runnable quit,
            Runnable search) {}

    /// What the overflow heading is called.
    public static final String HEADING = "Menu";

    /// The bar itself: one heading, holding the four menus.
    ///
    /// @param hudShown whether the frame-rate row draws its tick. Passed in rather
    ///                 than read, because whether a HUD is up is a fact about the
    ///                 window's overlay layer and this record cannot see one
    public MenuBar bar(boolean hudShown) {
        return new MenuBar(
                List.of(new Item(HEADING).submenu(file(), edit(), view(), help(hudShown))),
                Attributes.NONE.id("app-menu"));
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
                        new Item("March a league", actions.controls()::click).accelerator("Ctrl+L"),
                        new Item("Unsaved changes…", window.openDialog()).accelerator("Ctrl+O"),
                        new Separator(),
                        new Item("Switch the light", actions.appearance()::toggleTheme)
                                .icon(palette)
                                .accelerator("Ctrl+T"),
                        new Item("Switch the density", actions.appearance()::toggleDensity).accelerator("Ctrl+D"),
                        new Item("Always show scroll bars", actions.appearance()::toggleScrollbars),
                        new Separator(),
                        new Item("Begin again at Bag End", actions.controls()::reset),
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
                        new Item("Turn back", actions.controls()::undo).accelerator("Ctrl+Z"),
                        new Item("Press on", actions.controls()::click)
                                .disabled(true)
                                .accelerator("Ctrl+Y"),
                        new Separator(),
                        // Checked from the model rather than remembered here: the same
                        // box is on the Basic screen and the same switch beside it, and a
                        // menu that kept its own copy would disagree with both the moment
                        // either was used.
                        new Item("Read the verse", actions.controls()::toggleProse)
                                .checked(actions.controls().values().isProseShown()),
                        new Item("Send word", window.raiseToast()),
                        new Separator(),
                        new Item("Go to")
                                .submenu(Screen.GALLERY.stream()
                                        .map(name -> (Widget) new Item(
                                                Screen.title(name),
                                                () -> actions.shell().pickScreen(name)))
                                        .toArray(Widget[]::new)));
    }

    /// **View** — finding a screen, and how the window reads.
    ///
    /// The reading sizes are three checkable rows rather than a submenu of
    /// radio items, because a menu row's tick is the one mark a menu has, and
    /// three of them with one ticked reads as a choice.
    private Item view() {
        var model = actions.shell().values();
        var reading = model.readingSize();
        var rows = new ArrayList<Widget>();
        rows.add(new Item("Search…", window.search()).accelerator("Ctrl+K"));
        rows.add(new Item("Home", () -> actions.shell().pickScreen(HomeScreen.NAME)));
        rows.add(new Item("Back", actions.shell()::back).accelerator("Alt+Left").disabled(!model.canGoBack()));
        rows.add(new Item("Forward", actions.shell()::forward)
                .accelerator("Alt+Right")
                .disabled(!model.canGoForward()));
        rows.add(new Item("Previous screen", actions.shell()::previousScreen).accelerator("Ctrl+PgUp"));
        rows.add(new Item("Next screen", actions.shell()::nextScreen).accelerator("Ctrl+PgDn"));
        rows.add(new Separator());
        rows.add(new Item("Sidebar as icons", actions.shell()::toggleSidebar)
                .accelerator("Ctrl+B")
                .checked(!model.isSidebarWide()));
        rows.add(new Separator());
        rows.add(new Item("Larger text", actions.shell()::largerText).accelerator("Ctrl+="));
        rows.add(new Item("Smaller text", actions.shell()::smallerText).accelerator("Ctrl+-"));
        for (var size : ReadingSize.values()) {
            rows.add(new Item(size.title() + " text", () -> actions.shell().pickReading(size.name()))
                    .checked(size == reading));
        }
        return new Item("View").submenu(rows.toArray(Widget[]::new));
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
                        new Item("Take the tour", window.startTour()),
                        new Item("About Goldberry", window.about()));
    }
}
