package dev.goldberry.example.ui.panels;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.goldberry.bind.Subscription;
import dev.goldberry.bind.runtime.Models;
import dev.goldberry.example.ShowcaseModel;
import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.form.textinput.TextInput;
import dev.goldberry.widgets.panel.tabs.Tab;
import dev.goldberry.widgets.panel.tabs.Tabs;
import dev.goldberry.widgets.text.Text;

/// A tab strip that gains and loses tabs, inside the gallery's own strip.
///
/// The gallery's strip is fixed. This one is everything else a strip can be:
/// tabs that close, a `+` that opens the next stage of the road, a tab coloured
/// after what it holds, tabs dragged into a new order, and content kept alive
/// while another tab is shown.
///
/// The list of tabs is the model's `app.tabs`, so the card watches that path and
/// rebuilds when it changes: a different list is a different strip. Each of the
/// strip's events only asks: `change` to show a tab, `close` to remove one, `new`
/// to add one, and a drag to move one. The model answers, and a strip whose
/// handlers did nothing would not move.
///
/// Read more: [`tabs`](https://goldberry.dev/docs/components/panels.html#tabs).
///
/// @param model   what the strip reads
/// @param actions what the strip asks of
public record TabsCard(ShowcaseModel model, ShowcaseModel.Actions actions) implements Widget.Stateful {

    /// The card's id.
    static final String ID = "chapters-card";

    private static final ShowcaseCard CARD = new ShowcaseCard(
            ID,
            "Tabs that come and go",
            "A strip of headers over one panel. Close a tab with its ×, open one with +, or drag one along the "
                    + "row. The strip keeps every tab it has shown, so a note typed under one is there when you "
                    + "come back.",
            DocLink.to("components/panels", "tabs"));

    /// What each tab's panel says. A sentence per stage rather than one with the
    /// name substituted, so a change of tab visibly rebuilds the panel.
    static String body(String chapter) {
        return switch (chapter) {
            case "Rivendell" -> "The Council is called, and nine are chosen to answer it.";
            case "Moria" -> "The doors stand open on a hall that has been dark a long while.";
            default -> "Nothing has been written under " + chapter + " yet.";
        };
    }

    @Override
    public State<?> createState() {
        return new TabsCardState();
    }

    /// The subscription to the list of tabs.
    static final class TabsCardState extends State<TabsCard> {

        private @Nullable Subscription watching;

        @Override
        protected void initState() {
            // Watched rather than bound: another list of tabs is another strip,
            // a change of structure rather than of a value inside one.
            watching = Models.observable(widget().model(), "app.tabs").subscribe(value -> setState(() -> {}));
        }

        @Override
        protected void dispose() {
            if (watching != null) {
                watching.close();
                watching = null;
            }
        }

        @Override
        public Widget build(BuildContext context) {
            var model = widget().model();
            var actions = widget().actions();
            var strip = new ArrayList<Widget>();
            // A bound field may hold null, and a model with no list has no tabs.
            var names = Models.<List<String>>observable(model, "app.tabs").get();
            for (var name : names == null ? List.<String>of() : names) {
                strip.add(new Tab(
                                name,
                                name,
                                new Text(body(name)).id("tab-body"),
                                // Unbound on purpose: what is typed lives in the
                                // field's own state, which only keep-alive keeps.
                                new TextInput().placeholder("A note on " + name))
                        .closable(true)
                        // A colour a stylesheet cannot know: it is a fact about
                        // the tab's name rather than about its state.
                        .colour("Moria".equals(name) ? 0xFFBF616A : 0));
            }
            return CARD.of(new Tabs(
                            null,
                            strip,
                            Models.observable(model, "app.tab"),
                            actions::pickTab,
                            actions::closeTab,
                            actions::newTab,
                            Attributes.NONE)
                    .keepAlive(true)
                    .onReorder(actions::moveTab)
                    .id("demo-tabs"));
        }
    }
}
