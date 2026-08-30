package io.github.digitalsmile.goldberry.example.ui;

import io.github.digitalsmile.goldberry.bind.Subscription;
import io.github.digitalsmile.goldberry.bind.runtime.Models;
import io.github.digitalsmile.goldberry.example.ShowcaseModel;
import io.github.digitalsmile.goldberry.icon.Icon;
import io.github.digitalsmile.goldberry.kdl.KdlInflater;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.core.scroll.Scroll;
import io.github.digitalsmile.goldberry.widgets.core.scroll.ScrollAxis;
import io.github.digitalsmile.goldberry.widgets.panel.masonry.Masonry;
import io.github.digitalsmile.goldberry.widgets.panel.tabs.Tab;
import io.github.digitalsmile.goldberry.widgets.panel.tabs.Tabs;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// The whole window, in three bands: a **menu bar**, a **bar**, and a **gallery**
/// of seven screens under them.
///
/// ```text
    /// ┌──────────────────────────────────┐
    /// │ File  Edit  Help                 │  menubar  — the window's
    /// ├──────────────────────────────────┤
    /// │ Goldberry 9  42 ms  ◐  Switch    │  #bar     — startup, and the light
    /// ├──────────────────────────────────┤
    /// │ Basic │ Panels │ … │ Charts      │  #gallery — seven screens
    /// │ ┌────────┐ ┌────────┐            │
    /// │ │  card  │ │  card  │  a masonry │
    /// │ └────────┘ └────────┘            │
    /// └──────────────────────────────────┘
    /// ```
///
/// ## Why seven screens and not twelve
///
/// Because twelve was one screen per *widget family* and nobody reads a gallery
/// that way. `Controls`, `Values` and `Text` were three tabs you had to visit in
/// order to see what one screen's worth of chrome looks like; `Overlays` and
/// `Notifications` were the two halves of a comparison a reader could not make
/// with a tab between them; `Tabs` and `Scrolling` were both "how do I get around
/// a window". Seven screens are seven *questions*, and every one of them is a wall
/// of cards
/// (ADR-0222,
/// ADR-0110).
///
/// ## What this widget rebuilds for
///
/// Almost nothing. Every *value* in this window reaches its widget through a
/// binding and needs no rebuild here; the subscriptions below are the
/// **structural** changes — whether the prose card is in the tree, what the count
/// makes possible, and which chapters exist.
///
/// The gallery's own selection is not among them: the strip reads it through
/// `bind` like any other control, and the screens are all built either way. That
/// is the trade §5's lazy content makes — only the selected screen's widgets are
/// built into elements — and it is why switching screens costs a rebuild of one
/// screen rather than of the window.
///
/// @param model    the state every screen reads
/// @param inflater what turns the four documents into widgets
/// @param plus     the icon on [Basic]'s primary button
/// @param menu     File, Edit and Help. Built here rather than handed over
///                 finished, because the frame-rate row's tick follows
///                 `app.hud` and this is what rebuilds when it moves
public record Screen(ShowcaseModel model, ShowcaseModel.Actions actions,
        KdlInflater<Widget> inflater, Icon plus,
        Runnable startTour, AppMenu menu)
        implements Widget.Stateful {

    /// Every screen in the gallery, in the order the strip shows them.
    ///
    /// **The one order.** Two things read it — the strip below, which is built
    /// from it by [#inGalleryOrder], and
    /// [io.github.digitalsmile.goldberry.example.Showcase], which hangs `Ctrl+1`…
    /// off it — and they used to hold a copy each. The copies drifted the moment a
    /// screen was inserted in the middle, so `Ctrl+8` selected the screen beside
    /// the one the eighth tab named. A gallery with two orders in it is a gallery
    /// that disagrees with itself (ADR-0110).
    ///
    /// Seven of them, which is now comfortably inside the ten digits a keyboard
    /// has — where twelve was two screens past the end of them.
    public static final List<String> GALLERY = List.of(
            "basic", "panels", "overlays", "forms", "navigation", "collections", "charts");

    /// What each screen is called, for the strip, the Edit ▸ Go to submenu and the
    /// tray.
    ///
    /// A map beside [#GALLERY] rather than a `Tab` label written where the tab is
    /// built, because three separate places name these screens and two of them are
    /// not tabs. It is checked against the list below, so a screen added to one
    /// and not the other is a failure at build rather than a menu row reading
    /// `null`.
    private static final Map<String, String> TITLES = Map.of(
            "basic", "Basic",
            "panels", "Panels",
            "overlays", "Overlays",
            "forms", "Forms",
            "navigation", "Navigation",
            "collections", "Collections",
            "charts", "Charts");

    /// What a screen is called. Refuses rather than defaults, because a defaulted
    /// title is a menu row named `collections` that nobody notices for a month.
    public static String title(String name) {
        var title = TITLES.get(name);
        if (title == null) {
            throw new IllegalArgumentException(
                    "no screen is called \"" + name + "\"; the gallery is " + GALLERY);
        }
        return title;
    }

    /// `tabs` in [#GALLERY] order, and a failure rather than a silent reorder
    /// when the two do not name the same screens.
    ///
    /// The tabs are written where their content is built, because each carries a
    /// paragraph about why it is a document or Java; the *order* is the list
    /// above. Keeping them apart is only safe if disagreeing is loud, so it
    /// throws: a screen in the strip and not in the list would be one no key could
    /// reach, and a screen in the list and not in the strip would be a `Ctrl+6`
    /// that selected nothing at all.
    public static List<Tab> inGalleryOrder(List<Tab> tabs) {
        var byName = new LinkedHashMap<String, Tab>();
        for (var tab : tabs) {
            if (byName.put(tab.value(), tab) != null) {
                throw new IllegalStateException(
                        "two tabs in the gallery are called \"" + tab.value() + "\"");
            }
        }
        var ordered = new ArrayList<Tab>(GALLERY.size());
        for (var name : GALLERY) {
            var tab = byName.remove(name);
            if (tab == null) {
                throw new IllegalStateException(
                        "Screen.GALLERY names \"" + name + "\", which the strip has no tab for");
            }
            ordered.add(tab);
        }
        if (!byName.isEmpty()) {
            throw new IllegalStateException(
                    "the strip has tabs Screen.GALLERY does not name, so nothing binds a key to"
                            + " them: " + byName.keySet());
        }
        return List.copyOf(ordered);
    }

    @Override
    public State<?> createState() {
        return new ScreenState();
    }

    /// The subscriptions, the documents, and the gallery.
    static final class ScreenState extends State<Screen> {

        private final List<Subscription> watching = new ArrayList<>(3);

        /// The documents, inflated once — see [Panes].
        private Widget bar;
        private Masonry basic;
        private Masonry panels;
        private Masonry overlays;
        private Masonry forms;

        @Override
        protected void initState() {
            bar = Panes.bar(widget().inflater());
            basic = Panes.basic(widget().inflater());
            panels = Panes.panels(widget().inflater());
            overlays = Panes.overlays(widget().inflater());
            forms = Panes.forms(widget().inflater());

            // Structure only. Every *value* in this window reaches its widget
            // through a binding and needs no rebuild here.
            for (var path : List.of("app.prose", "app.clicks", "app.tabs", "app.hud")) {
                // Which chapters exist is structure too: a tab added or closed is
                // a different tree, not a different value (ADR-0109).
                watching.add(Models.observable(widget().model(), path)
                        .subscribe(value -> setState(() -> { })));
            }
        }

        /// A property outlives the tree — it is the application's — so a listener
        /// left behind keeps this subtree alive and rebuilds something nobody can
        /// see.
        @Override
        protected void dispose() {
            watching.forEach(Subscription::close);
            watching.clear();
        }

        /// One screen, in a viewport that can show more of it than the window is
        /// tall.
        ///
        /// The gallery is what `scroll` was built for
        /// (ADR-0116).
        ///
        /// Every screen but one, and the exception is the rule rather than a
        /// special case: [Navigation] holds a card that owns a viewport of its
        /// own, and §2.4 bans nested same-axis scrollers. A viewport over content
        /// that fits draws no thumb and takes no input, so wrapping a short screen
        /// costs one element — and a screen that is short at one window size is
        /// tall at another, which is the case a per-screen decision would get
        /// wrong.
        private static Widget scrolled(Widget screen) {
            return new Scroll(List.of(screen), ScrollAxis.VERTICAL, Attributes.NONE);
        }

        @Override
        public Widget build(BuildContext context) {
            var model = widget().model();
            var actions = widget().actions();

            // The gallery: one strip, seven screens, none of them closable. It is
            // bound like every other control -- `Ctrl+1`... , the Edit menu and
            // the strip itself are three ways to set one property rather than
            // three copies of a selection. Through `inGalleryOrder`, so the order
            // is `GALLERY`'s and the tabs below are free to be written wherever
            // their content is explained.
            var gallery = new Tabs(null, List.<Widget>copyOf(inGalleryOrder(List.of(
                    new Tab("basic", title("basic"),
                            scrolled(new Basic(model, actions, basic, widget().plus()))),
                    // The one screen with nothing appended to its wall: every card
                    // on it is a container, so it holds no value and needs no Java.
                    new Tab("panels", title("panels"),
                            scrolled(new Wall("panels", "Panels", PANELS_NOTE,
                                    panels.columns(), panels.children()))),
                    new Tab("overlays", title("overlays"),
                            scrolled(new Overlays(overlays))),
                    new Tab("forms", title("forms"), scrolled(new Forms(forms))),
                    // Not `scrolled`: this screen holds a card that owns a viewport
                    // of its own, and §2.4 bans nested same-axis scrollers -- so
                    // the screen that demonstrates the rule is where the gallery
                    // has to keep it.
                    new Tab("navigation", title("navigation"),
                            new Navigation(model, actions, widget().startTour())),
                    new Tab("collections", title("collections"),
                            scrolled(new Wall("collections", "Collections", COLLECTIONS_NOTE,
                                    2, Collections.cards()))),
                    new Tab("charts", title("charts"), scrolled(new Charts()))))),
                    Models.observable(model, "app.screen"), actions::pickScreen, null, null,
                    Attributes.NONE)
                    .id("gallery");

            return new Column(
                    List.of(widget().menu().bar(model.isHudShown()), bar, gallery),
                    Attributes.NONE)
                    .id("root")
                    // §8's `context-menu=` on any widget: right-clicking anywhere
                    // in the window opens the menu the application registered
                    // under this name (ADR-0108).
                    .contextMenu("content");
        }

        private static final String PANELS_NOTE =
                "§5's containers, and the only wall in this gallery with nothing appended to"
                        + " it: not one card here holds a value, so the screen is a document"
                        + " with no Java behind it at all.";

        private static final String COLLECTIONS_NOTE =
                "§10's three widgets that hold many rows. What is worth comparing is how each"
                        + " answers scale: the list virtualizes, the table sorts, the tree"
                        + " fetches — and none of the three does the work itself.";
    }
}
