package io.github.digitalsmile.goldberry.example.ui;

import io.github.digitalsmile.goldberry.example.ShowcaseModel;
import io.github.digitalsmile.goldberry.icon.Icon;
import io.github.digitalsmile.goldberry.kdl.KdlInflater;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.core.Column;
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
public record Screen(ShowcaseModel model, KdlInflater<Widget> inflater, Icon plus)
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

        @Override
        protected void initState() {
            titleBar = Panes.titleBar(widget().inflater());
            controls = Panes.controls(widget().inflater());
            values = Panes.values(widget().inflater());
            overlays = Panes.overlays(widget().inflater());

            // Structure only. Every *value* in this window reaches its widget
            // through a binding and needs no rebuild here.
            watching.add(widget().model().showProse().subscribe(value -> setState(() -> { })));
            watching.add(widget().model().clicks().subscribe(value -> setState(() -> { })));
            // Which tabs exist is structure too: a tab added or closed is a
            // different tree, not a different value (ADR-0109).
            watching.add(widget().model().tabs().subscribe(value -> setState(() -> { })));
        }

        /// A property outlives the tree — it is the application's — so a listener
        /// left behind keeps this subtree alive and rebuilds something nobody can
        /// see.
        @Override
        protected void dispose() {
            watching.forEach(io.github.digitalsmile.goldberry.bind.Subscription::close);
            watching.clear();
        }

        @Override
        public Widget build(BuildContext context) {
            var model = widget().model();
            // The gallery: one strip, five screens, none of them closable. It is
            // bound like every other control — `Ctrl+1`… and the strip itself are
            // two ways to set one property rather than two copies of a selection.
            var gallery = new Tabs(null, List.of(
                    new Tab("controls", "Controls", controls),
                    new Tab("values", "Values", values),
                    new Tab("text", "Text", new Content(model, widget().plus())),
                    new Tab("overlays", "Overlays", overlays),
                    new Tab("tabs", "Tabs", new TabsDemo(model))),
                    model.screen(), model::pickScreen, null, null, Attributes.NONE)
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
