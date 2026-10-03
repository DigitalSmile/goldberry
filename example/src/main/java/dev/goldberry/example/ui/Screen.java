package dev.goldberry.example.ui;

import java.util.List;
import java.util.Set;

import dev.goldberry.Goldberry;
import dev.goldberry.bind.Subscription;
import dev.goldberry.bind.runtime.Models;
import dev.goldberry.example.ShowcaseModel;
import dev.goldberry.example.ui.gallery.Documents;
import dev.goldberry.example.ui.gallery.Gallery;
import dev.goldberry.example.ui.gallery.GalleryContext;
import dev.goldberry.example.ui.gallery.GalleryTab;
import dev.goldberry.icon.Icon;
import dev.goldberry.kdl.KdlInflater;
import dev.goldberry.platform.Capability;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.core.Column;
import dev.goldberry.widgets.core.scroll.Scroll;
import dev.goldberry.widgets.core.scroll.ScrollAxis;
import dev.goldberry.widgets.panel.tabs.Tab;
import dev.goldberry.widgets.panel.tabs.Tabs;

/// The whole window, in three bands: a **menu bar**, a **bar**, and a **gallery**
/// of screens under them.
///
/// ```text
/// ┌──────────────────────────────────┐
/// │ File  Edit  Help                 │  menubar  — the window's
/// ├──────────────────────────────────┤
/// │ Goldberry 9  42 ms  ◐  Switch    │  #bar     — startup, and the light
/// ├──────────────────────────────────┤
/// │ Layout │ Scrolling │ … │ Guide   │  #gallery — one screen per chapter
/// │ ┌────────┐ ┌────────┐            │
/// │ │  card  │ │  card  │  a masonry │
/// │ └────────┘ └────────┘            │
/// └──────────────────────────────────┘
/// ```
///
/// ## Why the screens are the book's chapters
///
/// The showcase is where a reader of the guide comes to see what a chapter
/// describes. So the gallery has a screen per chapter, in the guide's order, and
/// a card per section of it, each with a link back to that section. The list is
/// [Gallery#TABS].
///
/// ## What this widget rebuilds for
///
/// Almost nothing. Every value in this window reaches its widget through a
/// binding, and a screen whose structure follows the model subscribes to what it
/// needs itself. The one subscription here is the Help menu's frame-rate tick.
///
/// The gallery's own selection is not among them either: the strip reads it
/// through `bind` like any other control, and `tabs` builds only the selected
/// screen's widgets into elements. That is why switching screens costs a rebuild
/// of one screen rather than of the window.
///
/// Read more: [Views](https://goldberry.dev/docs/applications.html#views).
///
/// @param model    the state every screen reads
/// @param inflater what turns the documents into widgets
/// @param plus     the icon on the primary buttons
/// @param menu     File, Edit and Help. Built here rather than handed over
///                 finished, because the frame-rate row's tick follows
///                 `app.hud` and this is what rebuilds when it moves
/// @param capabilities what the screens say this build can do; see
///                 [GalleryContext#capabilities()]
public record Screen(ShowcaseModel model, ShowcaseModel.Actions actions,
        KdlInflater<Widget> inflater, Icon plus,
        Runnable startTour, AppMenu menu, Set<Capability> capabilities)
        implements Widget.Stateful {

    public Screen {
        capabilities = Set.copyOf(capabilities);
    }

    /// The window's screen, saying what the loaded library answers.
    public Screen(ShowcaseModel model, ShowcaseModel.Actions actions,
            KdlInflater<Widget> inflater, Icon plus,
            Runnable startTour, AppMenu menu) {
        this(model, actions, inflater, plus, startTour, menu, Goldberry.capabilities());
    }

    /// Every screen in the gallery, in the order the strip shows them: the names in
    /// [Gallery#TABS].
    ///
    /// Read by the strip below, by `Showcase`, which hangs `Ctrl+1`… off it, and by
    /// the Edit ▸ Go to submenu and the tray. Ten of the screens have a digit; the
    /// rest are reached by the strip, its arrow keys, and Edit ▸ Go to.
    public static final List<String> GALLERY = Gallery.names();

    /// What a screen is called, for the strip, the Edit ▸ Go to submenu and the
    /// tray. Refuses rather than defaults, because a defaulted title is a menu row
    /// named `collections` that nobody notices for a month.
    public static String title(String name) {
        return Gallery.tab(name).title();
    }

    @Override
    public State<?> createState() {
        return new ScreenState();
    }

    /// The subscription, the documents, and the gallery.
    static final class ScreenState extends State<Screen> {

        @SuppressWarnings("NullAway.Init") // subscribed in initState()
        private Subscription watching;

        /// The bar, inflated once.
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
            // The Help menu's frame-rate row shows a tick, and a menu row's
            // `checked` is a constant: the bar is built again when it moves.
            watching = Models.observable(widget().model(), "app.hud").subscribe(value -> setState(() -> {}));
        }

        /// A property outlives the tree — it is the application's — so a listener
        /// left behind keeps this subtree alive and rebuilds something nobody can
        /// see.
        @Override
        protected void dispose() {
            watching.close();
        }

        /// One screen, in a viewport that can show more of it than the window is
        /// tall — unless the screen owns a viewport itself, which the design system
        /// will not nest inside another on the same axis.
        private Widget screen(GalleryTab tab) {
            var screen = tab.screen().apply(context);
            return switch (tab.fit()) {
                case SCROLLED -> new Scroll(List.of(screen), ScrollAxis.VERTICAL, Attributes.NONE);
                case FILLS -> screen;
            };
        }

        @Override
        public Widget build(BuildContext buildContext) {
            var model = widget().model();
            var actions = widget().actions();

            // The gallery: one strip, a screen per chapter, none of them closable.
            // It is bound like every other control -- `Ctrl+1`..., the Edit menu
            // and the strip itself are three ways to set one property rather than
            // three copies of a selection.
            var gallery = new Tabs(
                    null,
                    Gallery.TABS.stream()
                            .map(tab -> (Widget) new Tab(tab.name(), tab.title(), screen(tab)))
                            .toList(),
                    Models.observable(model, "app.screen"), actions::pickScreen, null, null,
                    Attributes.NONE)
                    .id("gallery");

            return new Column(
                    List.of(widget().menu().bar(model.isHudShown()), bar, gallery),
                    Attributes.NONE)
                    .id("root")
                    // `context-menu=` on any widget: right-clicking anywhere
                    // in the window opens the menu the application registered
                    // under this name.
                    .contextMenu("content");
        }
    }
}
