package dev.goldberry.example.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import dev.goldberry.Goldberry;
import dev.goldberry.bind.Subscription;
import dev.goldberry.bind.runtime.Models;
import dev.goldberry.css.Theme;
import dev.goldberry.example.ShowcaseModel;
import dev.goldberry.example.ui.gallery.Documents;
import dev.goldberry.example.ui.gallery.Gallery;
import dev.goldberry.example.ui.gallery.GalleryContext;
import dev.goldberry.example.ui.home.HomeScreen;
import dev.goldberry.example.ui.search.IndexedCard;
import dev.goldberry.example.ui.search.SearchIndex;
import dev.goldberry.example.ui.shell.CardSpotlight;
import dev.goldberry.example.ui.shell.JumpBar;
import dev.goldberry.example.ui.shell.PageHead;
import dev.goldberry.example.ui.shell.Sidebar;
import dev.goldberry.example.ui.shell.TopBar;
import dev.goldberry.icon.Icon;
import dev.goldberry.kdl.KdlInflater;
import dev.goldberry.platform.Capability;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.core.scroll.Scroll;
import dev.goldberry.widgets.core.scroll.ScrollAxis;

/// The whole window: a **top bar**, a **sidebar** of screens beside the screen
/// that is showing, and a **footer** of the run's readings.
///
/// ```text
/// ┌──────────────────────────────────────────────────────────┐
/// │ ◧ Goldberry  [ ⌕ Search…  Ctrl K ]               ☀  Menu │  TopBar
/// ├────────────┬─────────────────────────────────────────────┤
/// │ ⌂ Home     │ ← →  Home › Controls › Buttons  Previous Next│  PageHead
/// │ ▾ BASICS   │ On this page (Buttons) (Badges) (Chips) …    │  JumpBar
/// │   Layout   │ Buttons                                      │
/// │   …        │ ┌────────┐ ┌────────┐                        │  the screen
/// │            │ │  card  │ │  card  │  a masonry             │
/// ├────────────┴─────────────────────────────────────────────┤
/// │ 3 leagues · 42 ms to start · showcase on Linux · GPU     │  #bar
/// └──────────────────────────────────────────────────────────┘
/// ```
///
/// ## Why the screens are the book's chapters
///
/// The showcase is where a reader of the guide comes to see what a chapter
/// describes. So the gallery has a screen per chapter and a card per section of
/// it, each with a link back to that section. The sidebar groups the screens
/// into the [dev.goldberry.example.ui.gallery.Section]s of [Gallery#TABS], and
/// Home sits above them.
///
/// ## What this widget rebuilds for
///
/// The screen that is showing, whether the sidebar is a rail, the light, and the
/// Help menu's frame-rate tick: four properties it subscribes to itself. A
/// rebuild builds the bar, the sidebar and the page head again, which are a few
/// dozen small widgets, and **only the showing screen**: the others are not
/// built at all. Everything inside a screen reaches its value through a binding
/// and does not wait for this.
///
/// Read more: [Views](https://goldberry.dev/docs/applications.html#views).
///
/// @param model      the state every screen reads
/// @param inflater   what turns the documents into widgets
/// @param plus       the icon on the primary buttons
/// @param startTour  starts the guided tour, which needs the window
/// @param openSearch opens the search palette, which needs the window
/// @param menu       File, Edit, View and Help, folded into the overflow menu.
///                   Built here rather than handed over finished, because the
///                   frame-rate row's tick follows `app.hud` and this is what
///                   rebuilds when it moves
/// @param capabilities what the screens say this build can do; see
///                 [GalleryContext#capabilities()]
public record Screen(
        ShowcaseModel model,
        ShowcaseModel.Actions actions,
        KdlInflater<Widget> inflater,
        Icon plus,
        Runnable startTour,
        Runnable openSearch,
        AppMenu menu,
        Set<Capability> capabilities)
        implements Widget.Stateful {

    public Screen {
        capabilities = Set.copyOf(capabilities);
    }

    /// The window's screen, saying what the loaded library answers.
    public Screen(
            ShowcaseModel model,
            ShowcaseModel.Actions actions,
            KdlInflater<Widget> inflater,
            Icon plus,
            Runnable startTour,
            Runnable openSearch,
            AppMenu menu) {
        this(model, actions, inflater, plus, startTour, openSearch, menu, Goldberry.capabilities());
    }

    /// Every screen in the gallery, in the sidebar's order: the names in
    /// [Gallery#TABS].
    ///
    /// Read by the sidebar, by `Showcase`, which hangs `Ctrl+1`… off it, and by
    /// the Edit ▸ Go to submenu and the tray. Ten of the screens have a digit; the
    /// rest are reached by the sidebar, search, and Edit ▸ Go to.
    public static final List<String> GALLERY = Gallery.names();

    /// What a screen is called, for the sidebar, the Edit ▸ Go to submenu and the
    /// tray. Refuses rather than defaults, because a defaulted title is a menu row
    /// named `collections` that nobody notices for a month.
    public static String title(String name) {
        return Gallery.tab(name).title();
    }

    /// The id of the box the showing screen is in.
    public static final String PAGE = "page";

    @Override
    public State<?> createState() {
        return new ScreenState();
    }

    /// The subscriptions, the documents, and the window.
    static final class ScreenState extends State<Screen> {

        /// The properties a rebuild follows.
        private static final List<String> FOLLOWED = List.of("app.screen", "app.sidebar", "app.light", "app.hud");

        private final List<Subscription> watching = new ArrayList<>();

        /// What the jump bar's chips scroll to and ring.
        private final CardSpotlight spotlight = new CardSpotlight();

        /// The footer, inflated once.
        @SuppressWarnings("NullAway.Init") // inflated in initState()
        private Widget bar;

        /// The gallery's documents, inflated as each screen first asks.
        @SuppressWarnings("NullAway.Init") // made in initState()
        private GalleryContext context;

        @Override
        protected void initState() {
            var documents = new Documents(widget().inflater());
            bar = documents.document("statusbar.kdl");
            context = new GalleryContext(
                    widget().model(),
                    widget().actions(),
                    documents,
                    widget().plus(),
                    widget().startTour(),
                    widget().capabilities());
            for (var path : FOLLOWED) {
                watching.add(Models.observable(widget().model(), path).subscribe(value -> setState(() -> {})));
            }
        }

        /// A property outlives the tree — it is the application's — so a listener
        /// left behind keeps this subtree alive and rebuilds something nobody can
        /// see.
        @Override
        protected void dispose() {
            watching.forEach(Subscription::close);
            watching.clear();
            spotlight.clear();
        }

        /// The showing screen, in a viewport that can show more of it than the
        /// window is tall — unless the screen owns a viewport itself, which the
        /// design system will not nest inside another on the same axis.
        ///
        /// Keyed by the screen's name, so a screen that comes back starts from
        /// its top with fresh state, as a tab that was not kept alive did.
        private Widget page(String name) {
            var model = widget().model();
            var actions = widget().actions();
            if (!Gallery.has(name)) {
                var home = new HomeScreen(
                        model.recentScreens(),
                        SearchIndex.shipped().counts(),
                        actions::pickScreen,
                        widget().openSearch(),
                        widget().startTour());
                return scrolled(home, HomeScreen.NAME);
            }
            var tab = Gallery.tab(name);
            var screen = tab.screen().apply(context);
            return switch (tab.fit()) {
                case SCROLLED -> scrolled(screen, name);
                case FILLS ->
                    new Column(
                            List.of(screen),
                            Attributes.NONE.id(PAGE).classes("page", "fills").key(name));
            };
        }

        private static Widget scrolled(Widget screen, String name) {
            return new Scroll(
                    List.of(screen),
                    ScrollAxis.VERTICAL,
                    Attributes.NONE.id(PAGE).classes("page").key(name));
        }

        @Override
        public Widget build(BuildContext buildContext) {
            var model = widget().model();
            var actions = widget().actions();
            var showing = model.screen();
            var wide = model.isSidebarWide();

            var top = new TopBar(
                    wide,
                    model.theme() == Theme.NORD_LIGHT,
                    widget().menu().bar(model.isHudShown()),
                    actions::toggleSidebar,
                    widget().openSearch(),
                    actions::toggleTheme);
            var sidebar = new Sidebar(showing, wide, SearchIndex.shipped().counts(), actions::pickScreen);
            var head = new PageHead(
                    showing,
                    model.canGoBack(),
                    model.canGoForward(),
                    new PageHead.Moves(
                            actions::back,
                            actions::forward,
                            actions::previousScreen,
                            actions::nextScreen,
                            () -> actions.pickScreen(HomeScreen.NAME)));
            var parts = new ArrayList<Widget>(3);
            parts.add(head);
            var cards = Gallery.has(showing) ? SearchIndex.shipped().cardsOn(showing) : List.<IndexedCard>of();
            if (cards.size() >= JumpBar.FEWEST) {
                var host = buildContext.host().orElse(null);
                parts.add(new JumpBar(cards, id -> {
                    if (host != null) {
                        spotlight.show(host, id);
                    }
                }));
            }
            parts.add(page(showing));
            var content = new Column(parts, Attributes.NONE.id("content"));
            var body = new Row(List.of(sidebar, content), Attributes.NONE.id("body"));

            return new Column(List.of(top, body, bar), Attributes.NONE)
                    .id("root")
                    // `context-menu=` on any widget: right-clicking anywhere
                    // in the window opens the menu the application registered
                    // under this name.
                    .contextMenu("content");
        }
    }
}
