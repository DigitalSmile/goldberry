package dev.goldberry.example.ui.collections;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.core.scroll.Scroll;
import dev.goldberry.widgets.core.scroll.ScrollAxis;
import dev.goldberry.widgets.panel.list.ListView;
import dev.goldberry.widgets.panel.list.Selection;
import dev.goldberry.widgets.text.Text;

/// A `list` of ten thousand rows in a viewport that builds the dozen it can see,
/// with multiple selection.
///
/// Read more: [`list`](https://goldberry.dev/docs/components/collections.html#list).
record LeaguesCard() implements Widget.Stateful {

    /// Ten thousand of them, written down rather than paged in from anywhere: what
    /// is shown is that the *widget* does not instantiate them, and a slow source
    /// would confuse the two.
    static final List<String> ROAD = road();

    private static final double ROW_HEIGHT = 32;

    private static final ShowcaseCard CARD = new ShowcaseCard(
            "leagues-card",
            "Ten thousand rows",
            "A list builds only the rows its viewport can see, so ten thousand cost a dozen. Click to select,"
                    + " Ctrl+click to add a row, Shift+click for a range; Home and End reach the ends.",
            DocLink.to("components/collections", "list"));

    private static List<String> road() {
        var out = new ArrayList<String>(10_000);
        for (var i = 1; i <= 10_000; i++) {
            out.add("League " + i + " of the road");
        }
        return List.copyOf(out);
    }

    @Override
    public State<?> createState() {
        return new LeaguesState();
    }

    /// The selection, which the application holds and the list reports whole.
    static final class LeaguesState extends State<LeaguesCard> {

        private Set<String> chosen = Set.of("League 3 of the road");

        private void choose(Set<String> values) {
            setState(() -> chosen = values);
        }

        @Override
        public Widget build(BuildContext context) {
            return CARD.of(
                    new Scroll(
                            List.of(ListView.of(ROAD)
                                    .virtualized(ROW_HEIGHT)
                                    .selection(Selection.MULTIPLE)
                                    .selected(chosen, this::choose)
                                    .withAttributes(Attributes.NONE.id("many"))),
                            ScrollAxis.VERTICAL,
                            Attributes.NONE.classes("tall-list")),
                    new Text(
                            chosen.isEmpty()
                                    ? "Nothing chosen"
                                    : "Holding " + chosen.size() + ": " + String.join(", ", chosen),
                            Attributes.NONE.id("many-chosen").classes("caption")));
        }
    }
}
