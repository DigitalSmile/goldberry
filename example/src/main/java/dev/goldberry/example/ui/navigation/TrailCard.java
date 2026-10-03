package dev.goldberry.example.ui.navigation;

import java.util.ArrayList;
import java.util.List;

import dev.goldberry.bind.Subscription;
import dev.goldberry.bind.runtime.Models;
import dev.goldberry.example.ShowcaseModel;
import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.controls.button.Button;
import dev.goldberry.widgets.core.Row;
import dev.goldberry.widgets.nav.breadcrumbs.Breadcrumbs;
import dev.goldberry.widgets.nav.breadcrumbs.Crumb;

/// The `breadcrumbs`, one crumb per step of the model's path.
///
/// Every crumb gets the same kind of handler, because a trail is built from a
/// loop: the trail itself makes the last crumb inert. Past four crumbs the middle
/// folds into a `…` that opens a menu of what it hid.
///
/// Stateful only to follow `app.path`: the gallery builds a screen once, and the
/// path changes while it is open.
///
/// Read more: [`breadcrumbs`](https://goldberry.dev/docs/components/navigation.html#breadcrumbs).
///
/// @param model   where the path is
/// @param actions what a crumb and Go deeper ask for
record TrailCard(ShowcaseModel model, ShowcaseModel.Actions actions) implements Widget.Stateful {

    private static final ShowcaseCard CARD = new ShowcaseCard(
            "trail-card",
            "The path to here",
            "A trail of crumbs, built from the model's path. Click a crumb to go back up to it; the last is"
                    + " where you are and does not press. Go deeper until the middle folds into a … menu.",
            DocLink.to("components/navigation", "breadcrumbs"));

    @Override
    public State<?> createState() {
        return new TrailState();
    }

    /// The subscription to the path.
    static final class TrailState extends State<TrailCard> {

        @SuppressWarnings("NullAway.Init") // subscribed in initState()
        private Subscription watching;

        @Override
        protected void initState() {
            watching = Models.observable(widget().model(), "app.path").subscribe(_ -> setState(() -> {}));
        }

        @Override
        protected void dispose() {
            watching.close();
        }

        /// Runs `change` on the model, then says so. A plain Java call is not an
        /// action a document dispatched, so a model bound at run time is swept
        /// here; a woven one noticed the assignment already.
        private void changed(Runnable change) {
            change.run();
            Models.refresh(widget().model());
        }

        @Override
        public Widget build(BuildContext context) {
            var path = widget().model().path();
            var actions = widget().actions();
            var crumbs = new ArrayList<Widget>(path.size());
            for (var depth = 0; depth < path.size(); depth++) {
                var steps = depth + 1;
                crumbs.add(new Crumb(path.get(depth), () -> changed(() -> actions.goUp(steps)))
                        .keyed(path.get(depth))
                        .id("crumb-" + depth));
            }
            return CARD.of(
                    new Breadcrumbs(crumbs.toArray(Widget[]::new)).id("path"),
                    new Row(
                            List.of(new Button("Go deeper", () -> changed(actions::goDeeper)).id("go-deeper")),
                            Attributes.NONE.id("trail-actions")));
        }
    }
}
