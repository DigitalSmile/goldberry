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
/// of thirteen screens under them.
///
/// ```text
    /// ┌──────────────────────────────────┐
    /// │ File  Edit  Help                 │  menubar  — the window's
    /// ├──────────────────────────────────┤
    /// │ Goldberry 9  42 ms  ◐  Switch    │  #bar     — startup, and the light
    /// ├──────────────────────────────────┤
    /// │ Basic │ Panels │ … │ Motion      │  #gallery — thirteen screens
    /// │ ┌────────┐ ┌────────┐            │
    /// │ │  card  │ │  card  │  a masonry │
    /// │ └────────┘ └────────┘            │
    /// └──────────────────────────────────┘
    /// ```
///
/// ## Why the screens are questions and not widget families
///
/// Because twelve was one screen per *widget family* and nobody reads a gallery
/// that way. `Controls`, `Values` and `Text` were three tabs you had to visit in
/// order to see what one screen's worth of chrome looks like; `Overlays` and
/// `Notifications` were the two halves of a comparison a reader could not make
/// with a tab between them; `Tabs` and `Scrolling` were both "how do I get around
/// a window". The screens are *questions* rather than widget families, and all but
/// two of them are a wall of cards
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
/// @param inflater what turns the seven documents into widgets
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
    /// **Thirteen, and ten of them have a digit** — `Ctrl+0` is the tenth and a
    /// keyboard has no eleventh digit. That was once written here as a *limit* on
    /// how many screens the gallery could hold, and it is not one:
    /// `GalleryOrderTest` has always asserted "ten digits, however many screens
    /// there are", and `Showcase.screenShortcuts` has always bound what it can and
    /// stopped. So `icons`, `emoji` and `motion` are reached by the strip, by the
    /// arrow keys inside it, and by Edit ▸ Go to — three ways, none of them a
    /// digit ([ADR-0307]).
    ///
    /// The rule that *is* load-bearing is the one below it: the strip and this
    /// list must name the same screens, because a screen in one and not the other
    /// is either a key bound to nothing or a tab no key can reach. That has been
    /// checked since the gallery was twelve screens long and two of them were
    /// unreachable.
    public static final List<String> GALLERY = List.of(
            "basic", "panels", "overlays", "forms", "navigation", "collections", "charts", "markdown", "html",
            "canvas", "icons", "emoji", "motion", "web");

    /// What each screen is called, for the strip, the Edit ▸ Go to submenu and the
    /// tray.
    ///
    /// A map beside [#GALLERY] rather than a `Tab` label written where the tab is
    /// built, because three separate places name these screens and two of them are
    /// not tabs. It is checked against the list below, so a screen added to one
    /// and not the other is a failure at build rather than a menu row reading
    /// `null`.
    ///
    /// `Map.ofEntries` and not `Map.of`, which takes at most ten pairs — the
    /// eleventh screen is what found that, at compile time, which is where a
    /// limit like this should be found.
    private static final Map<String, String> TITLES = Map.ofEntries(
            Map.entry("basic", "Basic"),
            Map.entry("panels", "Panels"),
            Map.entry("overlays", "Overlays"),
            Map.entry("forms", "Forms"),
            Map.entry("navigation", "Navigation"),
            Map.entry("collections", "Collections"),
            Map.entry("charts", "Charts"),
            Map.entry("markdown", "Markdown"),
            Map.entry("html", "HTML"),
            Map.entry("canvas", "Canvas"),
            Map.entry("icons", "Icons"),
            Map.entry("emoji", "Emoji"),
            Map.entry("motion", "Motion"),
            Map.entry("web", "Web view"));

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

        /// The Markdown screen's two panes. **Not a `Masonry`**: that screen is one
        /// thing divided rather than a wall of cards, so its document's root is a
        /// `split-pane` and nothing is appended to it.
        private Widget markdown;

        /// And the HTML screen's, which is the same shape for the same reason — see
        /// [HtmlScreen].
        private Widget html;

        @Override
        protected void initState() {
            bar = Panes.bar(widget().inflater());
            basic = Panes.basic(widget().inflater());
            panels = Panes.panels(widget().inflater());
            overlays = Panes.overlays(widget().inflater());
            forms = Panes.forms(widget().inflater());
            markdown = Panes.markdown(widget().inflater());
            html = Panes.html(widget().inflater());

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

            // The gallery: one strip, thirteen screens, none of them closable. It is
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
                                    panels.columns(), panels.minColumnWidth(), panels.children()))),
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
                                    2, Masonry.UNSET, Collections.cards()))),
                    new Tab("charts", title("charts"), scrolled(new Charts())),
                    // The screen about an **optional module**: `goldberry-html`'s
                    // `markdown-view`, which this application opts into and never
                    // registers (ADR-0294, ADR-0295). Not `scrolled`, for
                    // [Navigation]'s reason and one more: the preview pane owns a
                    // viewport, and a `split-pane` needs a height to divide.
                    new Tab("markdown", title("markdown"), new MarkdownScreen(markdown)),
                    // The same optional module's other half: `html-view`, which is
                    // the entry `docs/gaps.md` G17 asked for and which landed
                    // without the engine that entry assumed (ADR-0298). Not
                    // `scrolled`, for the Markdown screen's two reasons.
                    new Tab("html", title("html"), new HtmlScreen(html)),
                    // §1's `canvas`, which is the one screen about a *primitive*
                    // rather than about a family of widgets -- and the only one
                    // whose cards respond to the pointer by redrawing themselves
                    // (ADR-0281).
                    new Tab("canvas", title("canvas"), scrolled(new CanvasScreen())),
                    // The sheet of every bundled icon, and the one screen that is
                    // about an *asset* rather than about a widget. Not `scrolled`:
                    // it is a virtualized `list` and owns a viewport of its own,
                    // which is [Navigation]'s reason and §2.4's ban on nested
                    // same-axis scrollers (ADR-0307).
                    new Tab("icons", title("icons"), new IconsScreen(model, actions)),
                    // The same sheet with a different asset in it, and the screen
                    // where this application opts into `goldberry-emoji` and
                    // carries the credit CC BY-SA asks for (ADR-0384, ADR-0386).
                    // Not `scrolled`, for the Icons screen's reason.
                    new Tab("emoji", title("emoji"), new EmojiScreen(model, actions)),
                    // What moves by itself: a canvas choreography, `@keyframes`
                    // and `@starting-style`, one card each. Last, so no digit
                    // moves (ADR-0354).
                    new Tab("motion", title("motion"), scrolled(new MotionScreen())),
                    // §9's `web-view`, filling the tab: a real child window over
                    // the widget's box, moved with it (ADR-0442). NOT `scrolled` --
                    // a page is clipped by the window rather than by an ancestor's
                    // box, so a viewport would scroll the frame out from under a
                    // page that stayed put. Last, after `motion`, so no digit
                    // moves (ADR-0354); the gallery is longer than ten digits now,
                    // so this screen has no accelerator.
                    new Tab("web", title("web"), new WebScreen())))),
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
