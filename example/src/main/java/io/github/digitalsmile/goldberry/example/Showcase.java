package io.github.digitalsmile.goldberry.example;

import io.github.digitalsmile.goldberry.Application;
import io.github.digitalsmile.goldberry.Goldberry;
import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.Overlay;
import io.github.digitalsmile.goldberry.backend.LogicalSize;
import io.github.digitalsmile.goldberry.css.CascadeLayer;
import io.github.digitalsmile.goldberry.css.Stylesheet;
import io.github.digitalsmile.goldberry.example.ui.Screen;
import io.github.digitalsmile.goldberry.icon.Icon;
import io.github.digitalsmile.goldberry.input.Key;
import io.github.digitalsmile.goldberry.input.Mod;
import io.github.digitalsmile.goldberry.widget.Corner;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.Controls;
import io.github.digitalsmile.goldberry.widgets.Icons;
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
                        ShowcaseModelRegistry.actions(model),
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

    @Override
    public Widget root() {
        return screen;
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
