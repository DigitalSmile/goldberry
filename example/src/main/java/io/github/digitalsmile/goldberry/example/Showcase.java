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
import io.github.digitalsmile.goldberry.bind.ActionRegistry;
import io.github.digitalsmile.goldberry.bind.Models;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.Icons;
import io.github.digitalsmile.goldberry.widgets.menu.Item;
import io.github.digitalsmile.goldberry.widgets.menu.Menu;
import io.github.digitalsmile.goldberry.widgets.menu.Menus;
import io.github.digitalsmile.goldberry.widgets.menu.Separator;
import io.github.digitalsmile.goldberry.widgets.overlay.hud.Hud;
import io.github.digitalsmile.goldberry.widgets.overlay.tour.Stop;
import io.github.digitalsmile.goldberry.widgets.overlay.tour.Tours;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.github.digitalsmile.goldberry.widgets.Widgets;

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
    private final ShowcaseModel.Actions actions = new ShowcaseModel.Actions(model);
    private final WindowActions window = new WindowActions(this::toggleMenu, this::toggleHud);

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

        // Two models and one icon registry, and that is the whole of the wiring.
        // The paths and the action names come off the models themselves; the node
        // names come from every widget module on the path (ADR-0131, ADR-0132).
        // Icons stay explicit because one owns native memory: markup may name an
        // icon and must not be able to build one, or a document reloaded on every
        // keystroke would leak one per reload (ADR-0043).
        screen = new Screen(
                model, actions,
                Widgets.inflater(
                        Icons.strict().bind("palette", paletteIcon).bind("plus", plusIcon),
                        models().toArray()),
                plusIcon,
                this::startTour);

        // No `repaint()` and no `restyle()` here. `models()` below hands both
        // objects to the toolkit, which subscribes: a change asks for a frame,
        // and a change to a field declared `@Bind(restyle = true)` drops the
        // resolved styles first (ADR-0128, ADR-0133).

        // A per-window accelerator map is what §7.2 asks for (ADR-0058). Ctrl+D
        // is the interesting one: not a widget in this application mentions a
        // height, and every control still resizes.
        host.shortcut(Mod.CTRL.and(Key.T), actions::toggleTheme);
        host.shortcut(Mod.CTRL.and(Key.D), actions::toggleDensity);
        // Off by default, and deliberately: a HUD reports the frames the loop
        // was already painting, so the interesting time to switch it on is
        // *during* a resize or a drag, when there is something to watch.
        host.shortcut(Mod.CTRL.and(Key.F), this::toggleHud);

        // One accelerator per screen, which is what a gallery of seven wants —
        // and three ways to set one property rather than three copies of a
        // selection: the strip, these keys, and the menu (ADR-0110).
        //
        // In gallery order, so the digit and the tab agree: a `Ctrl+5` that
        // landed on the fourth strip position would be a gallery with two
        // orders in it.
        var screens = List.of("controls", "values", "text", "overlays", "panels", "tabs",
                "scrolling");
        var digits = List.of(Key.DIGIT_1, Key.DIGIT_2, Key.DIGIT_3, Key.DIGIT_4, Key.DIGIT_5,
                Key.DIGIT_6, Key.DIGIT_7);
        for (var index = 0; index < screens.size(); index++) {
            var name = screens.get(index);
            host.shortcut(Mod.CTRL.and(digits.get(index)), () -> actions.pickScreen(name));
        }

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
            // Nothing here reaches into the tree: the field is set, and the
            // sidebar line bound to it redraws itself.
            actions.setStatus(text);
            // The one place an application says this, and the reason it is here
            // rather than in every method: `setStatus` is a plain Java call from
            // a background job's continuation, so it is neither an action a
            // document dispatched nor a write the toolkit had any reason to look
            // for. A woven model notices it from inside the assignment and this
            // returns false; a jar's model is swept here
            // ([ADR-0155](../../../../../../book/src/adr/0155-a-jar-binds-at-run-time-an-image-is-woven.md)).
            Models.refresh(model);
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
    /// Starts §5's `tour` over the scrolling screen.
    ///
    /// The application's rather than the screen's, because starting one needs a
    /// [Host] and a widget has none — the same seam `Menus.open` sits on
    /// ([ADR-0121](../../../../../../book/src/adr/0121-a-tour-is-a-veil-and-a-sequence.md)).
    ///
    /// The screen is selected first, because a tour whose targets are on a screen
    /// nobody is looking at would skip every stop and end immediately — which is
    /// correct behaviour and a useless demonstration.
    private void startTour() {
        actions.pickScreen("scrolling");
        if (host == null) {
            return;
        }
        // After the frame that switches screens, so the targets exist to be
        // found. §5 asks a tour to wait for a frame before positioning, and this
        // is that wait at its coarsest: the screen has to be *built* before any
        // of it can be anchored to.
        host.after(java.time.Duration.ofMillis(80), () -> Tours.start(host, List.of(
                new Stop("jump-bar", "Jump to a section",
                        "These ask the list to bring a section into view."
                                + " The viewport moves the least it can."),
                new Stop("scroll-demo", "A viewport of its own",
                        "Scroll it with the wheel, or focus it and use PageDown."
                                + " The headers stick as their sections pass."),
                new Stop("gallery", "The gallery strip",
                        "This scrolls too — and selecting a tab that has scrolled"
                                + " out of the strip brings it back."))));
    }


    /// The two objects this window is driven by.
    ///
    /// The view model, and the window itself — because "open the menu" and
    /// "toggle the HUD" are the *window's* actions and have no business on a view
    /// model that knows nothing about menus. Naming both here is what lets a
    /// document write `press="app.open-menu"` beside `press="app.click"` without
    /// the application merging two registries by hand (ADR-0132).
    @Override
    public List<Object> models() {
        return List.of(model, actions, window);
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
                new Item("Switch theme", actions::toggleTheme)
                        .icon(paletteIcon)
                        .accelerator("Ctrl+T"),
                new Item("Switch density", actions::toggleDensity).accelerator("Ctrl+D"),
                new Separator(),
                new Item("Frame rate", this::toggleHud)
                        .accelerator("Ctrl+F")
                        .checked(hud != null),
                new Item("More").submenu(
                        new Item("Reset the counter", actions::reset),
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
            // The breakdown rather than the two-number default: a total is what
            // tells you a frame is slow and the stages are what tell you which
            // part of it is, which is the question the showcase's own 10ms frame
            // went a month without anybody being able to ask (ADR-0142,
            // ADR-0146).
            hud = host.overlay(Hud.stages(), Corner.BOTTOM_END);
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
