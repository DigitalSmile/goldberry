package io.github.digitalsmile.goldberry.example;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.digitalsmile.goldberry.Application;
import io.github.digitalsmile.goldberry.Goldberry;
import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.Overlay;
import io.github.digitalsmile.goldberry.Popup;
import io.github.digitalsmile.goldberry.bind.runtime.Models;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.css.cascade.CascadeLayer;
import io.github.digitalsmile.goldberry.example.brand.ShowcaseIcon;
import io.github.digitalsmile.goldberry.example.ui.AppMenu;
import io.github.digitalsmile.goldberry.example.ui.Screen;
import io.github.digitalsmile.goldberry.html.view.HtmlStyles;
import io.github.digitalsmile.goldberry.icon.Icon;
import io.github.digitalsmile.goldberry.image.Image;
import io.github.digitalsmile.goldberry.input.key.Key;
import io.github.digitalsmile.goldberry.input.key.Mod;
import io.github.digitalsmile.goldberry.input.key.Shortcut;
import io.github.digitalsmile.goldberry.log.Startup;
import io.github.digitalsmile.goldberry.markdown.view.MarkdownStyles;
import io.github.digitalsmile.goldberry.render.model.LogicalSize;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.style.Corner;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.Icons;
import io.github.digitalsmile.goldberry.widgets.Widgets;
import io.github.digitalsmile.goldberry.widgets.menu.Item;
import io.github.digitalsmile.goldberry.widgets.menu.Menu;
import io.github.digitalsmile.goldberry.widgets.menu.Menus;
import io.github.digitalsmile.goldberry.widgets.menu.Separator;
import io.github.digitalsmile.goldberry.widgets.overlay.dialog.Dialog;
import io.github.digitalsmile.goldberry.widgets.overlay.dialog.DialogAction;
import io.github.digitalsmile.goldberry.widgets.overlay.dialog.Dialogs;
import io.github.digitalsmile.goldberry.widgets.overlay.hud.Hud;
import io.github.digitalsmile.goldberry.widgets.overlay.toast.Toast;
import io.github.digitalsmile.goldberry.widgets.overlay.toast.ToastController;
import io.github.digitalsmile.goldberry.widgets.overlay.toast.Toasts;
import io.github.digitalsmile.goldberry.widgets.overlay.tour.Stop;
import io.github.digitalsmile.goldberry.widgets.overlay.tour.Tours;
import io.github.digitalsmile.goldberry.widgets.shell.tray.TrayIcon;
import io.github.digitalsmile.goldberry.widgets.shell.tray.Trays;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// Goldberry's showcase — the [Application], and nothing else.
///
/// Five things live here and each is the application's own: the **lifecycle**
/// (what to open and what to close), the **stylesheets**, the **registries** a
/// markup document resolves its names against, the **window's own commands** —
/// the four things a menu row can ask for that a view model cannot answer — and
/// the accelerators. The window, the trees, the renderer, the router and the
/// frame loop are [Goldberry#launch]'s ([ADR-0093]); the state and the actions
/// are [ShowcaseModel]'s; the widgets are [Screen]'s and the five `.kdl`
/// documents beside it.
///
/// ## What the showcase exercises
///
/// - **The three trees.** Widgets are values, the element tree persists across
///   rebuilds, the box tree is materialized per frame (ADR-0052, ADR-0053).
/// - **Markup with all four registries.** `statusbar.kdl` and the four screen
///   documents name properties, actions, icons and objects that this class
///   registers, so `bind=`, `change=`, `press=`, `icon=`, `controller=` and
///   `validator=` all run in a window rather than only in a test (§9, ADR-0062,
///   ADR-0170).
/// - **The cascade, with a theme in it.** Throwing the switch in the bar swaps
///   one stylesheet and calls [Host#restyle]; everything restyles, including
///   rules that name no colour (§10).
/// - **Input, end to end.** Hover, press, click, focus, `Tab`, `Space`/`Enter`,
///   a menu bar's `F10` and eleven accelerators, through a router fed by the
///   frame that was painted rather than a fresh layout (ADR-0054, ADR-0058).
/// - **A window that opens maximized**, which is a *state* the desktop owns
///   rather than a large size (ADR-0221) — and the reason it does is the gallery
///   itself: a wall of cards is a layout whose whole subject is how much fits.
///
/// Logging is configured here too — `logback.xml` beside this class, because
/// binding a logging implementation is an application's decision and never a
/// library's (ADR-0023).
///
/// Run it with `./gradlew run`, or build the self-contained image with
/// `./gradlew :example:nativeImage` (ADR-0159, ADR-0340).
public final class Showcase implements Application {

    private static final Logger LOG = LoggerFactory.getLogger(Showcase.class);

    private static final float ICON_SIZE = 20;

    /// The window's own stylesheet, read from `showcase.css` beside this class —
    /// the layout of *this* window and nothing about how a button looks.
    private final Stylesheet styles = Stylesheet.resource(CascadeLayer.APPLICATION, Showcase.class, "showcase.css");

    private final ShowcaseModel model = new ShowcaseModel();
    private final ShowcaseModel.Actions actions = new ShowcaseModel.Actions(model);

    /// The four commands `overlays.kdl` presses by name. Not on the model,
    /// because every one of them needs a [Host] — see [WindowActions].
    private final WindowActions window =
            new WindowActions(this::toggleMenu, this::toggleHud, this::openDialog, this::raiseToast);

    private Host host;

    /// The dialog that is showing, or null. One at a time: a second `Ctrl+O`
    /// while one is up would put a modal over a modal, which the router handles
    /// and which this application has no reason to demonstrate.
    private @Nullable Overlay open;

    /// The about box, while it is showing. Null when it is not, and kept apart
    /// from [#open] so that `Ctrl+O` and Help ▸ About do not close each other.
    private @Nullable Overlay about;

    /// §7's toast stack, attached once and raised through for ever after.
    ///
    /// A field rather than something reached through the tree, which is the whole
    /// point of a controller: what raises a notification is by definition
    /// somewhere else (ADR-0177).
    private final ToastController toasts = new ToastController();

    private int toastsRaised;

    /// The frame-rate readout, while it is on screen. Null when it is not — see
    /// [#toggleHud]. Whether it is up is *also* on the model, because the Help
    /// menu draws a tick beside it and a menu row's `checked` is a constant.
    private @Nullable Overlay hud;

    /// The context menu, while it is open. Null when it is not.
    ///
    /// A popup is light-dismissed by default, so it can also close itself: a
    /// press anywhere in the window below it, or `Escape`. `isOpen()` is what
    /// this field is checked with rather than nullness alone.
    private @Nullable Popup menu;
    private Icon paletteIcon;
    private Icon plusIcon;
    private Screen screen;

    /// §9's `tray-icon`, while this desktop has one. Empty on a session with no
    /// notification area, which is an ordinary answer and not a failure — every
    /// platform's own guidance says an application must run without one.
    private Optional<io.github.digitalsmile.goldberry.render.tray.BackendTray> tray = Optional.empty();

    // --- Application ---------------------------------------------------------

    @Override
    public String title() {
        return "Goldberry — showcase";
    }

    /// The size the window **restores** to, since it opens maximized.
    ///
    /// Still worth choosing: un-maximizing a window that had never been given a
    /// size would drop it to whatever the desktop felt like, and 1280×800 is the
    /// smallest shape on which the widest wall on this screen — the Forms
    /// screen's three columns — still reads as three columns.
    @Override
    public LogicalSize size() {
        return new LogicalSize(1280, 800);
    }

    /// The smallest the window may be dragged to.
    ///
    /// 640×480, which is not a taste: the shell is a sidebar and a content pane,
    /// and below roughly this the sidebar and the pane stop being two things.
    /// Narrower still and the `split` in the Editor screen has two panes that are
    /// each a few characters wide, which is a layout nobody can read and the
    /// toolkit will happily draw.
    ///
    /// The window manager stops the drag at the edge rather than the application
    /// noticing afterwards, which is the whole point of declaring it here
    /// (ADR-0304).
    @Override
    public LogicalSize minimumSize() {
        return LogicalSize.of(640, 480);
    }

    /// **Yes**, and this is the one application in the repository that says so.
    ///
    /// A gallery is a layout whose whole subject is how much fits on a screen: a
    /// masonry with three cards per column at 1280 has two at 900, and a reader
    /// who opens the showcase in a small window is looking at a different
    /// argument from the one it is making. Every other example stays false, which
    /// is the default and the right one for a tool (ADR-0221).
    @Override
    public boolean maximized() {
        return true;
    }

    /// The toolkit's sheets for the current theme and density, then this window's.
    ///
    /// Re-read only when [Host#restyle] asks, which is what makes a theme switch
    /// two lines and costs nothing the rest of the time. The theme and the
    /// density are one call because they are one list (ADR-0074).
    /// Four tiles on a plate, drawn at each size a desktop asks for rather than
    /// scaled from one (ADR-0351).
    @Override
    public List<Image> icon() {
        return ShowcaseIcon.sizes();
    }

    @Override
    public List<Stylesheet> stylesheets() {
        var sheets = new ArrayList<>(Controls.stylesheets(model.theme(), model.density(), model.scrollbars()));
        // An optional module brings its own rules, and adding them is the
        // application's -- `:widgets` does not know Markdown exists, so
        // `Controls.stylesheets` cannot include these (ADR-0190, ADR-0295). Before
        // this window's own sheet, so the showcase can still override a document's
        // appearance the way it overrides a control's.
        sheets.add(MarkdownStyles.stylesheet());
        // The module's other half brings its own rules as well, and they are two
        // sheets rather than one on purpose: an application that renders notes and
        // never a page adds one of them (ADR-0298).
        sheets.add(HtmlStyles.stylesheet());
        sheets.add(styles);
        return sheets;
    }

    /// Opens what has to be closed, binds what markup will name, and wires the
    /// model to the window.
    @Override
    public void start(Host host) {
        this.host = host;
        // `Icons.SLOT`, not ICON_SIZE: this one goes in a `button icon=`, whose
        // lead slot is 16, and an icon is built at a size and cannot be rescaled
        // (ADR-0043). At 20 it was centred in a 16px column and overhung it, which
        // `ItemLead` says out loud on every run.
        paletteIcon = Icon.bundled("palette", (float) Icons.SLOT);
        plusIcon = Icon.bundled("plus", ICON_SIZE);

        // Two models and one icon registry, and that is the whole of the wiring.
        // The paths and the action names come off the models themselves; the node
        // names come from every widget module on the path (ADR-0131, ADR-0132).
        // Icons stay explicit because building one costs: an icon is parsed from
        // the bundled set and scaled to the size it is drawn at. Markup may name
        // an icon and must not be able to build one, or a document reloaded on
        // every keystroke would re-parse one per reload (ADR-0043). It was a
        // harder rule before ADR-0277, when an icon held a native path and had
        // to be closed exactly once.
        screen = new Screen(
                model,
                actions,
                Widgets.inflater(
                        // The **objects** the Forms document names: the handle
                        // that submits its form and the rule one of its fields
                        // applies. A fourth registry beside the three, because a
                        // controller is not a method, not a resource and not a
                        // value that changes -- and the binding machinery is what
                        // said so, refusing a `final` `@Bind` field in as many
                        // words (ADR-0170).
                        model.named(),
                        Icons.strict().bind("palette", paletteIcon).bind("plus", plusIcon),
                        models().toArray()),
                plusIcon,
                this::startTour,
                // The window's menu bar. Its four window commands arrive as
                // handlers because only a `Host` can open a dialog, float a HUD or
                // close a window -- and a widget has none (ADR-0222).
                new AppMenu(
                        actions,
                        new AppMenu.Handlers(
                                this::openDialog,
                                this::toggleHud,
                                this::raiseToast,
                                this::startTour,
                                this::openAbout,
                                () -> host.window().close()),
                        paletteIcon));

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
        // The four rows in the File and Edit menus that are not the model's.
        host.shortcut(Mod.CTRL.and(Key.O), this::openDialog);
        host.shortcut(Mod.CTRL.and(Key.Q), () -> host.window().close());
        host.shortcut(Mod.CTRL.and(Key.K), actions::click);
        host.shortcut(Mod.CTRL.and(Key.Z), actions::undo);

        // One accelerator per screen — three ways to set one property rather
        // than three copies of a selection: the strip, these keys, and Edit ▸ Go
        // to (ADR-0110).
        screenShortcuts(host::shortcut, actions::pickScreen);

        // §8's other half: `context-menu="…"` on any widget, and one line to say
        // what the names mean. The toolkit notices the right-click and finds the
        // name; only the catalog can turn a name into a menu (ADR-0108).
        Menus.contextMenus(host, Map.of("content", contextMenu()));

        // §7's toast stack, attached once. After this line the application never
        // mentions the stack again: it holds a controller and raises values
        // through it from wherever they happen (ADR-0177).
        Toasts.at(host, toasts, Corner.BOTTOM_END);

        // §9's tray, which is the one thing in this application Goldberry does
        // not draw: the rows below are handed to the desktop's shell, which
        // themes them, spaces them and clicks them. The description is an
        // ordinary `Menu` -- the same value a `menubar` holds -- so the light
        // toggle here and the one in the File menu are one command written once
        // (ADR-0191).
        // Lets a run start on a named screen, so a tab that only does something
        // when it is looked at -- `web`, whose page opens over the box it is
        // painted at -- can be exercised without a human clicking it.
        var startOn = System.getProperty("goldberry.example.screen");
        if (startOn != null && !startOn.isBlank()) {
            LOG.info("starting on the \"{}\" screen, as -Dgoldberry.example.screen asked", startOn);
            actions.pickScreen(startOn);
        }

        tray = Trays.show(
                host,
                TrayIcon.of(
                        "Goldberry — showcase",
                        new Menu(
                                List.of(
                                        new Item("Switch the light", actions::toggleTheme),
                                        new Item("Switch the density", actions::toggleDensity),
                                        new Separator(),
                                        // Every screen, off the one list -- so a screen added to the
                                        // gallery arrives in the tray without this file being touched.
                                        new Item("Screens")
                                                .submenu(Screen.GALLERY.stream()
                                                        .map(name -> (Widget) new Item(
                                                                Screen.title(name), () -> actions.pickScreen(name)))
                                                        .toArray(Widget[]::new)),
                                        new Separator(),
                                        new Item("Quit", () -> host.window().close())),
                                Attributes.NONE)));
        LOG.info("tray {}", tray.isPresent() ? "shown" : "unavailable on this desktop");

        host.window().onResize(size -> LOG.info("resized to {}", size));
        host.window().onScaleChange(scale -> LOG.info("scale is now {}", scale));
        host.window().onCloseRequest(() -> {
            LOG.info("close requested");
            return true;
        });

        // What the bar's first reading is: how long this process took to get
        // here. Read on the UI thread and now, because `Startup` is measuring
        // *this* thread's journey and a background job would time its own.
        actions.setStartup(describeStartup());

        // Work that is not instant belongs off the UI thread. It comes back on it
        // automatically, so touching the window here is safe (ADR-0020).
        Goldberry.async(Showcase::describeEnvironment)
                .thenAccept(text -> {
                    host.title("Goldberry — " + text);
                    // Nothing here reaches into the tree: the field is set, and the bar's
                    // line bound to it redraws itself.
                    actions.setStatus(text);
                    // The one place an application says this, and the reason it is here
                    // rather than in every method: `setStatus` is a plain Java call from
                    // a background job's continuation, so it is neither an action a
                    // document dispatched nor a write the toolkit had any reason to look
                    // for. A woven model notices it from inside the assignment and this
                    // returns false; a jar's model is swept here
                    // (ADR-0155).
                    Models.refresh(model);
                })
                .exceptionally(failure -> {
                    // Error Prone caught this: a `thenAccept` whose future nobody holds
                    // swallows whatever the job threw, so a describeEnvironment that
                    // failed would leave the bar reading "reading the map…" for ever with
                    // nothing in the log to say why. The handler is the whole fix -- an
                    // application still has nothing to await, it simply has somewhere for
                    // the failure to go.
                    LOG.warn("could not describe the environment", failure);
                    actions.setStatus("could not read the environment");
                    Models.refresh(model);
                    return null;
                });
    }

    /// Hangs `Ctrl+1`… off the gallery, in strip order.
    ///
    /// **In [Screen#GALLERY]'s order**, which is the strip's own, so the digit
    /// and the tab agree: a `Ctrl+5` that landed on the fourth strip position
    /// would be a gallery with two orders in it, and a list copied here is a list
    /// that drifts the first time a screen goes in the middle.
    ///
    /// **Ten digits, seven screens**, which is the other half of what the
    /// restructure bought: every screen has a key, where twelve screens left two
    /// of them reachable only by the strip. The loop still stops at ten rather
    /// than assuming, because the next screen added is exactly when that stops
    /// being true.
    ///
    /// Takes the binding rather than the [Host] it comes from, so that what this
    /// binds can be asserted without a window: everything else in [#start] wants
    /// a real one.
    ///
    /// @param bind what to call for each accelerator — `host::shortcut`
    /// @param pick what a key does — `actions::pickScreen`
    static void screenShortcuts(
            java.util.function.BiConsumer<Shortcut, Runnable> bind, java.util.function.Consumer<String> pick) {

        var digits = List.of(
                Key.DIGIT_1,
                Key.DIGIT_2,
                Key.DIGIT_3,
                Key.DIGIT_4,
                Key.DIGIT_5,
                Key.DIGIT_6,
                Key.DIGIT_7,
                Key.DIGIT_8,
                Key.DIGIT_9,
                Key.DIGIT_0);
        for (var index = 0; index < Math.min(Screen.GALLERY.size(), digits.size()); index++) {
            var name = Screen.GALLERY.get(index);
            bind.accept(Mod.CTRL.and(digits.get(index)), () -> pick.accept(name));
        }
    }

    /// The two objects this window is driven by.
    ///
    /// The view model, its actions, and the window's own four commands.
    ///
    /// The third is there because a **document** names them: `press=` is a string
    /// and only a registry can turn one into a call. The window's own menu bar
    /// needs no such thing — it is built in Java and holds its handlers directly
    /// — which is the difference between the two halves of §9 stated in one list
    /// ([ADR-0132], [ADR-0222]).
    @Override
    public List<Object> models() {
        return List.of(model, actions, window);
    }

    @Override
    public Widget root() {
        return screen;
    }

    /// Opens the context menu under the button that opened it, or closes it again.
    ///
    /// The showcase's demonstration of the **other** place an overlay can go: a
    /// real platform window, parented to this one and free of its bounds
    /// (ADR-0102). Anchored to the button's painted rectangle, which is a fact
    /// about the last frame rather than something this method can compute
    /// (ADR-0080) — and **sized by its own content**, so adding an item changes
    /// nothing else. Drag the window to the bottom of the screen and it opens
    /// upwards.
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
        Menus.open(host, "menu-button", contextMenu())
                .ifPresentOrElse(
                        open -> menu = open,
                        () -> LOG.info("nowhere to put a menu: either this video driver has no popup"
                                + " windows, or the button has not been painted yet"));
    }

    /// Raises a toast — §7's smallest overlay.
    ///
    /// No `Host`, no widget, no overlay: the stack was attached once when the
    /// window started, and everything after that goes through the controller
    /// (ADR-0177). Every third one carries an action, because a toast with a
    /// button and a toast without are different shapes and a demonstration of
    /// one is not a demonstration of the other.
    private void raiseToast() {
        var number = ++toastsRaised;
        toasts.show(
                number % 3 == 0
                        ? new Toast("Word " + number + " sent to Rivendell.")
                                .action("Recall it", () -> actions.setStatus("Recalled word " + number))
                        : new Toast("Word " + number + " — a rider went out and did not wait."));
    }

    /// The modal — §7's `dialog`, opened the way ADR-0176 says one is: it is a
    /// widget, and showing it is `Dialogs.show`.
    ///
    /// The three roles are all here, because the three-button dialog is the case
    /// that makes the roles worth having: `Enter` presses the affirmative, `Esc`
    /// and a press on the veil press the dismissive, and the neutral one has no
    /// key at all. The bar puts the affirmative on the right without being told
    /// to.
    ///
    /// Each handler ends by removing the overlay, and gets the closing animation
    /// for nothing: every route out fades the panel first and calls the handler
    /// when the fade is over.
    private void openDialog() {
        if (open != null && open.isAttached()) {
            return;
        }
        open = Dialogs.show(
                host,
                new Dialog(
                                "Turn back?",
                                List.of(
                                        new Text("The pass is closing and the low road is watched. Choosing"
                                                + " either cannot be undone."),
                                        new DialogAction(
                                                "Wait for word",
                                                DialogAction.Role.NEUTRAL,
                                                () -> answered("Waiting at the gate")),
                                        new DialogAction(
                                                "Keep to the pass",
                                                DialogAction.Role.DISMISSIVE,
                                                () -> answered("Still on the mountain")),
                                        new DialogAction(
                                                "Take the low road",
                                                DialogAction.Role.AFFIRMATIVE,
                                                () -> answered("Under the mountain"))),
                                Attributes.NONE)
                        .id("turn-back"));
    }

    /// What every one of the dialog's buttons does: say so, and take the dialog
    /// away. Removing the overlay is the application's — only it knows the
    /// question has been answered — and by the time this runs the panel has
    /// already faded.
    private void answered(String what) {
        actions.setStatus(what);
        if (open != null) {
            open.remove();
            open = null;
        }
    }

    /// **About Goldberry** — what this window is running.
    ///
    /// A dialog of its own rather than a row that reopened the unsaved-changes
    /// one, which is what Help ▸ About did until the 2026-09-18 review pressed it.
    /// One dismissive action, because an about box asks nothing.
    private void openAbout() {
        if (about != null && about.isAttached()) {
            return;
        }
        about = Dialogs.show(
                host,
                new Dialog(
                                "About Goldberry",
                                List.of(
                                        new Text("Goldberry " + Goldberry.version() + " — a desktop UI toolkit"
                                                + " for Java, rasterized on the CPU by Blend2D, laid out by"
                                                + " Yoga and shaped by HarfBuzz."),
                                        new DialogAction("Close", DialogAction.Role.DISMISSIVE, this::aboutClosed)),
                                Attributes.NONE)
                        .id("about-goldberry"));
    }

    /// Takes the about box away. Its own handler rather than [#answered], because
    /// closing an about box is not an answer and must not write the status line.
    private void aboutClosed() {
        if (about != null) {
            about.remove();
            about = null;
        }
    }

    /// Starts §5's `tour` over the Navigation screen.
    ///
    /// The application's rather than the screen's, because starting one needs a
    /// [Host] and a widget has none — the same seam `Menus.open` sits on
    /// (ADR-0121).
    ///
    /// The screen is selected first, because a tour whose targets are on a screen
    /// nobody is looking at would skip every stop and end immediately — which is
    /// correct behaviour and a useless demonstration.
    private void startTour() {
        actions.pickScreen("navigation");
        if (host == null) {
            return;
        }
        // After the frame that switches screens, so the targets exist to be
        // found. §5 asks a tour to wait for a frame before positioning, and this
        // is that wait at its coarsest: the screen has to be *built* before any
        // of it can be anchored to.
        host.after(
                Duration.ofMillis(80),
                () -> Tours.start(
                        host,
                        List.of(
                                new Stop(
                                        "demo-tabs",
                                        "A strip of your own",
                                        "Chapters that can be closed, and a + that opens the next stage"
                                                + " of the road. The gallery's own strip above can do"
                                                + " neither."),
                                new Stop(
                                        "jump-bar",
                                        "Jump to a chapter",
                                        "These ask the list beside them to bring a section into view."
                                                + " The viewport moves the least it can."),
                                new Stop(
                                        "scroll-demo",
                                        "A viewport of its own",
                                        "Scroll it with the wheel, or focus it and use PageDown."
                                                + " The headers stick as their sections pass."),
                                new Stop(
                                        "gallery",
                                        "The gallery strip",
                                        "Thirteen screens, and a Ctrl+digit for the first ten."
                                                + " The last three are reached by the strip, its arrow"
                                                + " keys, or Edit ▸ Go to."))));
    }

    /// Puts a `hud` in the window's overlay layer, or takes it away again.
    ///
    /// The whole of what an application does to float something: one call, and
    /// the handle that comes back is the way out. Nothing about `screen` changes
    /// — the overlay is a sibling of it, which is why switching this on mid-drag
    /// does not cost a single element its state.
    ///
    /// The model is told afterwards, and that is not bookkeeping: the Help menu
    /// draws a tick beside this row, a menu row's `checked` is a constant
    /// resolved when the bar is built, and `app.hud` is what asks for the bar to
    /// be built again.
    private void toggleHud() {
        if (hud != null) {
            hud.remove();
            hud = null;
        } else {
            // The breakdown rather than the two-number default: a total is what
            // tells you a frame is slow and the stages are what tell you which
            // part of it is (ADR-0142, ADR-0146).
            hud = host.overlay(Hud.stages(), Corner.BOTTOM_END);
        }
        actions.setHud(hud != null);
        LOG.info("hud {}", hud == null ? "off" : "on");
    }

    /// What a right-click anywhere in the window opens.
    ///
    /// A short menu with everything §8 gives a row — a label, an icon, an
    /// accelerator shown right-aligned, a checkable item, a disabled one, a rule
    /// and a submenu — because a context menu is where those are seen *together*.
    /// The window's own bar is where they are seen doing work.
    ///
    /// Nothing here closes the menu — `Menus` wraps every command in "and close
    /// the stack", which is what choosing a command does everywhere and which an
    /// application that had to remember it would eventually forget on one row.
    private Menu contextMenu() {
        return new Menu(
                new Item("Switch the light", actions::toggleTheme)
                        .icon(paletteIcon)
                        .accelerator("Ctrl+T"),
                new Item("Switch the density", actions::toggleDensity).accelerator("Ctrl+D"),
                new Separator(),
                new Item("Frame rate", this::toggleHud).accelerator("Ctrl+F").checked(model.isHudShown()),
                new Item("More")
                        .submenu(
                                new Item("Begin again at Bag End", actions::reset),
                                new Item("Nothing here", () -> {}).disabled(true)));
    }

    /// What [#start] opened, in reverse. The launcher closes what the launcher
    /// opened and nothing else — it cannot know that an icon in a field is still
    /// referenced by a widget that has not been collected.
    @Override
    public void stop() {
        // The tray first, and for a reason the icons do not have: an icon left
        // open leaks memory inside this process, while a tray left up leaves a
        // picture in somebody's notification area that the shell will not clean
        // away.
        tray.ifPresent(io.github.digitalsmile.goldberry.render.tray.BackendTray::close);
        if (menu != null && menu.isOpen()) {
            menu.close();
        }
        plusIcon.close();
        paletteIcon.close();
    }

    // --- the process ---------------------------------------------------------

    public static void main(String[] args) {
        Goldberry.launch(new Showcase(), args);
    }

    /// The bar's first reading: how long this process took to reach [#start].
    ///
    /// [Startup] is already counting — it marks the native library, the window
    /// and the first frame — so this asks it rather than starting a stopwatch of
    /// its own. Read here and not in a background job, because what is being
    /// measured is *this* thread's journey to this line.
    static String describeStartup() {
        var millis = Startup.sinceProcessStart().toMillis();
        return millis + " ms to start";
    }

    /// Stands in for real background work — reading a config file, loading a
    /// font, talking to a service. The point is where its result lands.
    private static String describeEnvironment() {
        LOG.debug("describing the environment on {}", Thread.currentThread());
        return "showcase on " + System.getProperty("os.name") + " / " + System.getProperty("os.arch");
    }
}
