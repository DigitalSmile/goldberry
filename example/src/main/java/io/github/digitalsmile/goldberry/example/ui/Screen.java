package io.github.digitalsmile.goldberry.example.ui;

import io.github.digitalsmile.goldberry.example.ShowcaseModel;
import io.github.digitalsmile.goldberry.icon.Icon;
import io.github.digitalsmile.goldberry.kdl.KdlInflater;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.core.Row;

/// The whole window: a title bar over a sidebar and a content pane.
///
/// Three children and a layout, which is all a screen should be. Two of them are
/// documents ([Panes]) and one is Java ([Content], which says why).
///
/// ## What it listens to, and what it does not
///
/// Almost nothing needs this widget to rebuild. A bound `badge`, `slider` or
/// `text` hears its own property and redraws itself, which is the point of §9's
/// `bind=` — the count in the title bar changes without this class being
/// involved at all.
///
/// The exception is **structure**: whether the prose is in the tree, and whether
/// Undo is disabled, are decisions [Content] makes at build time rather than
/// values a widget follows. So the state subscribes to exactly those two
/// properties and rebuilds, and to nothing else
/// ([ADR-0094](../../../../../../../book/src/adr/0094-name-the-overload-not-the-allocation.md)).
///
/// @param model    what the panes read
/// @param inflater the registry the documents resolve their names against
/// @param plus     the icon on the primary button
public record Screen(ShowcaseModel model, KdlInflater<Widget> inflater, Icon plus)
        implements Widget.Stateful {

    @Override
    public State<?> createState() {
        return new ScreenState();
    }

    /// The subscriptions, and the two things they exist for.
    static final class ScreenState extends State<Screen> {

        private final java.util.List<io.github.digitalsmile.goldberry.bind.Subscription> watching =
                new java.util.ArrayList<>(2);

        /// The documents, inflated once — see [Panes].
        private Widget titleBar;
        private Widget sidebar;

        @Override
        protected void initState() {
            titleBar = Panes.titleBar(widget().inflater());
            sidebar = Panes.sidebar(widget().inflater());

            // Structure only. Every *value* in this window reaches its widget
            // through a binding and needs no rebuild here.
            watching.add(widget().model().showProse().subscribe(value -> setState(() -> { })));
            watching.add(widget().model().clicks().subscribe(value -> setState(() -> { })));
            // Which tabs exist is structure too: a tab added or closed is a
            // different tree, not a different value, and nothing else would
            // rebuild for it (ADR-0109).
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
            return new Column(
                    titleBar,
                    new Row(sidebar, new Content(widget().model(), widget().plus()))
                            .id("body"))
                    .id("root");
        }
    }
}
