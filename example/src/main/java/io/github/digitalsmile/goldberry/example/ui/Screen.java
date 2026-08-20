package io.github.digitalsmile.goldberry.example.ui;

import io.github.digitalsmile.goldberry.bind.Models;

import io.github.digitalsmile.goldberry.example.ShowcaseModel;
import io.github.digitalsmile.goldberry.icon.Icon;
import io.github.digitalsmile.goldberry.kdl.KdlInflater;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.core.scroll.Scroll;
import io.github.digitalsmile.goldberry.widgets.core.scroll.ScrollAxis;
import io.github.digitalsmile.goldberry.widgets.panel.tabs.Tab;
import io.github.digitalsmile.goldberry.widgets.panel.tabs.Tabs;
import java.util.List;

/// The whole window: a title bar, and a **gallery** of screens under it.
///
/// ## Why a gallery
///
/// `docs/core-widgets.md`'s cross-cutting notes ask for one — "a gallery app
/// exercises every widget in every state in both themes… a widget isn't done
/// until it's in the gallery" — and a single pane stopped being able to hold the
/// catalog somewhere around the eleventh control. What was one sidebar of every
/// widget in the toolkit is five screens, each about one thing
/// ([ADR-0110](../../../../../../../book/src/adr/0110-the-showcase-is-a-gallery-of-screens.md)).
///
/// **Three of the five are documents and two are Java**, and which is which is
/// the interesting part rather than an accident — see [Panes].
///
/// ## What this widget rebuilds for
///
/// Almost nothing. Every *value* in this window reaches its widget through a
/// binding and needs no rebuild here; the three subscriptions below are the three
/// **structural** changes — whether the prose is in the tree, what the click
/// count makes possible, and which tabs exist.
///
/// The gallery's own selection is not among them: the strip reads it through
/// `bind` like any other control, and the screens are all built either way. That
/// is the trade §5's lazy content makes — only the selected screen's widgets are
/// built into elements — and it is why switching screens costs a rebuild of one
/// screen rather than of the window.
///
/// @param model    the state every screen reads
/// @param inflater what turns the three documents into widgets
/// @param plus     the icon on [Content]'s primary button
public record Screen(ShowcaseModel model, ShowcaseModel.Actions actions,
        KdlInflater<Widget> inflater, Icon plus,
        Runnable startTour)
        implements Widget.Stateful {

    @Override
    public State<?> createState() {
        return new ScreenState();
    }

    /// The subscriptions, the documents, and the gallery.
    static final class ScreenState extends State<Screen> {

        private final java.util.List<io.github.digitalsmile.goldberry.bind.Subscription> watching =
                new java.util.ArrayList<>(3);

        /// The documents, inflated once — see [Panes].
        private Widget titleBar;
        private Widget controls;
        private Widget values;
        private Widget overlays;
        private Widget panels;

        @Override
        protected void initState() {
            titleBar = Panes.titleBar(widget().inflater());
            controls = Panes.controls(widget().inflater());
            values = Panes.values(widget().inflater());
            overlays = Panes.overlays(widget().inflater());
            panels = Panes.panels(widget().inflater());

            // Structure only. Every *value* in this window reaches its widget
            // through a binding and needs no rebuild here.
            for (var path : List.of("app.prose", "app.clicks", "app.tabs")) {
                // Which tabs exist is structure too: a tab added or closed is a
                // different tree, not a different value (ADR-0109).
                watching.add(Models.observable(widget().model(), path)
                        .subscribe(value -> setState(() -> { })));
            }
        }

        /// A property outlives the tree — it is the application's — so a listener
        /// left behind keeps this subtree alive and rebuilds something nobody can
        /// see.
        @Override
        protected void dispose() {
            watching.forEach(io.github.digitalsmile.goldberry.bind.Subscription::close);
            watching.clear();
        }

        /// One screen, in a viewport that can show more of it than the window is
        /// tall.
        ///
        /// The gallery is what `scroll` was built for, and it is worth saying
        /// which way round that went: the widget did not arrive and find a use
        /// here, it was written *because* three separate things — this, a menu
        /// taller than the work area, and `select` over a realistic option list —
        /// were all the same missing viewport
        /// ([ADR-0116](../../../../../../../book/src/adr/0116-a-scroll-view-is-a-clip-an-offset-and-two-extents.md)).
        ///
        /// Every screen, not just the tall ones. A viewport over content that
        /// fits draws no thumb and takes no input, so the cost of wrapping a
        /// short screen is one element — and a screen that is short at one window
        /// size is tall at another, which is the case a per-screen decision would
        /// get wrong.
        private static Widget scrolled(Widget screen) {
            return new Scroll(List.of(screen), ScrollAxis.VERTICAL, Attributes.NONE);
        }

        @Override
        public Widget build(BuildContext context) {
            var model = widget().model();
            var actions = widget().actions();
            // The gallery: one strip, seven screens, none of them closable. It is
            // bound like every other control — `Ctrl+1`… and the strip itself are
            // two ways to set one property rather than two copies of a selection.
            var gallery = new Tabs(null, List.of(
                    new Tab("controls", "Controls", scrolled(controls)),
                    new Tab("values", "Values", scrolled(values)),
                    new Tab("text", "Text", scrolled(new Content(model, actions, widget().plus()))),
                    new Tab("overlays", "Overlays", scrolled(overlays)),
                    new Tab("panels", "Panels", scrolled(panels)),
                    new Tab("tabs", "Tabs", scrolled(new TabsDemo(model, actions))),
                    // Not `scrolled`: this screen owns a viewport of its own, and
                    // §2.4 bans nested same-axis scrollers — so the screen that
                    // demonstrates the rule is where the gallery has to keep it.
                    new Tab("scrolling", "Scrolling", new Scrolling(widget().startTour()))),
                    Models.observable(model, "app.screen"), actions::pickScreen, null, null, Attributes.NONE)
                    .id("gallery");

            return new Column(List.of(titleBar, gallery), Attributes.NONE)
                    .id("root")
                    // §8's `context-menu=` on any widget: right-clicking anywhere
                    // in the window opens the menu the application registered
                    // under this name (ADR-0108).
                    .contextMenu("content");
        }
    }
}
