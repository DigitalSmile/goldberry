package io.github.digitalsmile.goldberry.example;

import io.github.digitalsmile.goldberry.Application;
import io.github.digitalsmile.goldberry.Goldberry;
import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.Overlay;
import io.github.digitalsmile.goldberry.Popup;
import io.github.digitalsmile.goldberry.Placement;
import io.github.digitalsmile.goldberry.backend.LogicalSize;
import io.github.digitalsmile.goldberry.css.CascadeLayer;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.example.ui.Screen;
import io.github.digitalsmile.goldberry.icon.Icon;
import io.github.digitalsmile.goldberry.input.Key;
import io.github.digitalsmile.goldberry.input.Mod;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.Corner;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.Actions;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.Icons;
import io.github.digitalsmile.goldberry.widgets.menu.Item;
import io.github.digitalsmile.goldberry.widgets.menu.Menu;
import io.github.digitalsmile.goldberry.widgets.menu.Menus;
import io.github.digitalsmile.goldberry.widgets.menu.Separator;
import io.github.digitalsmile.goldberry.widgets.overlay.hud.Hud;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// Goldberry's showcase — the [Application], and nothing else.
///
/// Four things live here and each is the application's own: the **lifecycle**
/// (what to open and what to close), the **stylesheets**, the **registries** a
/// markup document resolves its names against, and the two window accelerators.
/// The window, the trees, the renderer, the router and the frame loop are
/// [Goldberry#launch]'s ([ADR-0093]); the state and the actions are
/// [ShowcaseModel]'s; the widgets are
/// [io.github.digitalsmile.goldberry.example.ui.Screen]'s and the two `.kdl`
/// documents beside it.
///
/// That split is the point of the file being this short. It was one 770-line
/// class holding all four ([ADR-0094]).
///
/// ## What the showcase exercises
///
/// - **The three trees.** Widgets are values, the element tree persists across
///   rebuilds, the box tree is materialized per frame (ADR-0052, ADR-0053).
/// - **Markup with all three registries.** `titlebar.kdl` and `sidebar.kdl` name
///   properties, actions and icons that this class registers, so `bind=`,
///   `change=`, `press=` and `icon=` all run in a window rather than only in a
///   test (§9, ADR-0062).
/// - **The cascade, with a theme in it.** Picking a theme swaps one stylesheet
///   and calls [Host#restyle]; everything restyles, including rules that name no
///   colour (§10).
/// - **Input, end to end.** Hover, press, click, focus, `Tab`, `Space`/`Enter`
///   and two accelerators, through a router fed by the frame that was painted
///   rather than a fresh layout (ADR-0054, ADR-0058).
/// - **A cursor and an icon in a box**, which is what makes
///   `SDL_CreateSystemCursor` and the icon-as-a-box path run outside a unit test
///   (ADR-0043, ADR-0057).
///
/// Logging is configured here too — `logback.xml` beside this class, because
/// binding a logging implementation is an application's decision and never a
/// library's (ADR-0023).
///
/// Run it with `./gradlew run`, or build the self-contained image with
/// `./gradlew :example:showcaseImage` (ADR-0048).
public final class Showcase implements Application {

    private static final Logger LOG = LoggerFactory.getLogger(Showcase.class);

    private static final float ICON_SIZE = 20;

    /// The window's own stylesheet, read from `showcase.css` beside this class —
    /// the layout of *this* window and nothing about how a button looks.
    private final Stylesheet styles =
            Stylesheet.resource(CascadeLayer.APPLICATION, Showcase.class, "showcase.css");

    private final ShowcaseModel model = new ShowcaseModel();

    private Host host;

    /// The frame-rate readout, while it is on screen. Null when it is not — see
    /// [#toggleHud].
    private Overlay hud;

    /// The menu, while it is open. Null when it is not — see [#toggleMenu].
    ///
    /// A popup is light-dismissed by default, so it can also close itself: a
    /// press anywhere in the window below it, or `Escape`. `isOpen()` is what
    /// this field is checked with rather than nullness alone.
    private Popup menu;
    private Icon paletteIcon;
    private Icon plusIcon;
    private Screen screen;

    // --- Application ---------------------------------------------------------

    @Override
    public String title() {
        return "Goldberry — showcase";
    }

    @Override
    public LogicalSize size() {
        return new LogicalSize(960, 640);
    }

    /// The toolkit's sheets for the current theme and density, then this window's.
    ///
    /// Re-read only when [Host#restyle] asks, which is what makes a theme switch
    /// two lines and costs nothing the rest of the time. The theme and the
    /// density are one call because they are one list (ADR-0074).
    @Override
    public List<Stylesheet> stylesheets() {
        var sheets = new ArrayList<>(Controls.stylesheets(model.theme(), model.density()));
        sheets.add(styles);
        return sheets;
    }

    /// Opens what has to be closed, binds what markup will name, and wires the
    /// model to the window.
    @Override
    public void start(Host host) {
        this.host = host;
        paletteIcon = Icon.bundled("palette", ICON_SIZE);
        plusIcon = Icon.bundled("plus", ICON_SIZE);

        // The three registries §9 asks for, and they are three because they
        // answer three different questions: what a name *does*, what a name
        // *draws*, and where a value *lives*. Markup names an icon and cannot
        // build one -- an `Icon` owns native memory, and a document reloaded on
        // every keystroke would leak one per reload (ADR-0043).
        screen = new Screen(
                model,
                Controls.inflater(
                        actions(model, this::toggleMenu, this::toggleHud),
                        Icons.strict().bind("palette", paletteIcon).bind("plus", plusIcon),
                        ShowcaseModelRegistry.bindings(model)),
                plusIcon);

        model.onChanged(host::repaint);
        model.onRestyle(() -> {
            host.restyle();
            LOG.info("theme {} / density {}", model.theme(), model.density());
        });

        // A per-window accelerator map is what §7.2 asks for (ADR-0058). Ctrl+D
        // is the interesting one: not a widget in this application mentions a
        // height, and every control still resizes.
        host.shortcut(Mod.CTRL.and(Key.T), model::toggleTheme);
        host.shortcut(Mod.CTRL.and(Key.D), model::toggleDensity);
        // Off by default, and deliberately: a HUD reports the frames the loop
        // was already painting, so the interesting time to switch it on is
        // *during* a resize or a drag, when there is something to watch.
        host.shortcut(Mod.CTRL.and(Key.F), this::toggleHud);

        // §8's other half: `context-menu="…"` on any widget, and one line to say
        // what the names mean. The toolkit notices the right-click and finds the
        // name; only the catalog can turn a name into a menu (ADR-0108).
        Menus.contextMenus(host, java.util.Map.of("content", menuContent()));

        host.window().onResize(size -> LOG.info("resized to {}", size));
        host.window().onScaleChange(scale -> LOG.info("scale is now {}", scale));
        host.window().onCloseRequest(() -> {
            LOG.info("close requested");
            return true;
        });

        // Work that is not instant belongs off the UI thread. It comes back on it
        // automatically, so touching the window here is safe (ADR-0020).
        Goldberry.async(Showcase::describeEnvironment).thenAccept(text -> {
            host.title("Goldberry — " + text);
            // Nothing here reaches into the tree: the property is set, and the
            // sidebar line bound to it redraws itself.
            model.setStatus(text);
        });
    }

    /// The action registry the two documents resolve against: the model's
    /// generated one, plus the two handlers that are the **window's**.
    ///
    /// Opening a popup and floating a HUD are facts about this window rather than
    /// about the application's data, so they are not `@Action`s on the model —
    /// and they are bound here, in one place, because `sidebar.kdl` names them
    /// and a strict registry refuses a name nobody bound. That refusal is the
    /// point (a `press=` typo is otherwise a button that silently does nothing),
    /// which is why the test resolves through this method rather than keeping its
    /// own list of what the document happens to mention.
    ///
    /// @param model     the model whose `@Action`s the processor generated
    /// @param openMenu  what `app.open-menu` does
    /// @param toggleHud what `app.toggle-hud` does
    static Actions actions(ShowcaseModel model, Runnable openMenu, Runnable toggleHud) {
        return ShowcaseModelRegistry.actions(model)
                .bind("app.open-menu", openMenu)
                .bind("app.toggle-hud", toggleHud);
    }

    @Override
    public Widget root() {
        return screen;
    }

    /// Opens the menu under the button that opened it, or closes it again.
    ///
    /// The showcase's demonstration of the **other** place an overlay can go: a
    /// real platform window, parented to this one and free of its bounds
    /// (ADR-0102). The menu is 132px tall and the button that opens it sits near
    /// the bottom of a short window — so on a window under about 300px this menu
    /// is drawn *outside* it, which is the whole point and is impossible in the
    /// in-window layer the HUD uses.
    ///
    /// Anchored to the button's painted rectangle, which is a fact about the last
    /// frame rather than something this method can compute (ADR-0080) — and
    /// **sized by its own content**, so adding an item here changes nothing else.
    /// Drag the window to the bottom of the screen and it opens upwards.
    private void toggleMenu() {
        if (menu != null && menu.isOpen()) {
            menu.close();
            menu = null;
            return;
        }
        // One call does everything a menu needs and none of it is this
        // application's business: measure the panel, place it against the button
        // with a flip if it would open off the bottom of the screen, open a
        // platform window, close the whole stack when a command is chosen, and
        // open a submenu beside the row that owns one (ADR-0104, ADR-0106).
        Menus.open(host, "menu-button", menuContent()).ifPresentOrElse(
                open -> menu = open,
                () -> LOG.info("nowhere to put a menu: either this video driver has no popup"
                        + " windows, or the button has not been painted yet"));
    }

    /// What the menu contains: three things that do something visible, so that
    /// the popup is demonstrably live rather than a picture of a menu.
    ///
    /// A `menu` with everything §8 gives a row: a label, an icon, an accelerator
    /// shown right-aligned, a checkable item, a disabled one, a rule, and a
    /// submenu.
    ///
    /// Nothing here closes the menu — `Menus` wraps every command in "and close
    /// the stack", which is what choosing a command does everywhere and which an
    /// application that had to remember it would eventually forget on one row.
    ///
    /// Built in Java rather than in KDL because its handlers are direct calls
    /// with no names to resolve — the shorter half of §9's story, and worth having
    /// one of in the showcase.
    private Menu menuContent() {
        return new Menu(
                new Item("Switch theme", model::toggleTheme)
                        .icon(paletteIcon)
                        .accelerator("Ctrl+T"),
                new Item("Switch density", model::toggleDensity).accelerator("Ctrl+D"),
                new Separator(),
                new Item("Frame rate", this::toggleHud)
                        .accelerator("Ctrl+F")
                        .checked(hud != null),
                new Item("More").submenu(
                        new Item("Reset the counter", model::reset),
                        new Item("Nothing here", () -> { }).disabled(true)));
    }

    /// Puts a `hud` in the window's overlay layer, or takes it away again.
    ///
    /// The whole of what an application does to float something: one call, and
    /// the handle that comes back is the way out. Nothing about `screen` changes
    /// — the overlay is a sibling of it, which is why switching this on mid-drag
    /// does not cost a single element its state.
    private void toggleHud() {
        if (hud != null) {
            hud.remove();
            hud = null;
        } else {
            hud = host.overlay(new Hud(), Corner.BOTTOM_END);
        }
        LOG.info("hud {}", hud == null ? "off" : "on");
    }

    /// What [#start] opened, in reverse. The launcher closes what the launcher
    /// opened and nothing else — it cannot know that an icon in a field is still
    /// referenced by a widget that has not been collected.
    @Override
    public void stop() {
        plusIcon.close();
        paletteIcon.close();
    }

    // --- the process ---------------------------------------------------------

    public static void main(String[] args) {
        Goldberry.launch(new Showcase(), args);
    }

    /// Stands in for real background work — reading a config file, loading a
    /// font, talking to a service. The point is where its result lands.
    private static String describeEnvironment() {
        LOG.debug("describing the environment on {}", Thread.currentThread());
        return "showcase on " + System.getProperty("os.name")
                + " / " + System.getProperty("os.arch");
    }
}
