package dev.goldberry.example.ui.collections;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.goldberry.example.docs.DocLink;
import dev.goldberry.example.ui.gallery.ShowcaseCard;
import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.panel.list.Selection;
import dev.goldberry.widgets.panel.table.Column;
import dev.goldberry.widgets.panel.table.Sort;
import dev.goldberry.widgets.panel.table.Table;
import dev.goldberry.widgets.text.Text;

/// A `table`: the Company in columns, sorted by the application when a header
/// asks, with a Name column that can be dragged wider.
///
/// Read more: [`table`](https://goldberry.dev/docs/components/collections.html#table).
record CompanyCard() implements Widget.Stateful {

    private static final ShowcaseCard CARD = new ShowcaseCard(
            "company-card",
            "The Company, in columns",
            "A table is a list with columns. Click a header to sort: the table asks and the application sorts."
                    + " Drag the edge of the Name header to resize it, and Ctrl+click rows to choose several.",
            DocLink.to("components/collections", "table"));

    @Override
    public State<?> createState() {
        return new CompanyState();
    }

    /// The selection, the sort and the Name column's width: the application's to
    /// keep and hand back.
    static final class CompanyState extends State<CompanyCard> {

        private Set<String> picked = Set.of("aragorn");

        private Sort sort = new Sort("leagues", true);

        /// The Name column's width once somebody has dragged it, or NaN while it
        /// still takes its share.
        private double nameWidth = Double.NaN;

        private void pick(Set<String> values) {
            setState(() -> picked = values);
        }

        private void sortBy(Sort next) {
            setState(() -> sort = next);
        }

        private void resize(String column, double width) {
            if (column.equals("name")) {
                setState(() -> nameWidth = width);
            }
        }

        private Column<Walker> nameColumn() {
            var column = Column.<Walker>of("name", "Name", Walker::name)
                    .sortable(true)
                    .resizable(true);
            return Double.isNaN(nameWidth) ? column.weight(2) : column.fixed(nameWidth);
        }

        /// The sort, done here: a table over a database would sort in the query,
        /// which is why the widget does not.
        private List<Walker> sorted() {
            @Nullable
            Comparator<Walker> by =
                    switch (sort.column()) {
                        case "name" -> Comparator.comparing(Walker::name);
                        case "kindred" -> Comparator.comparing(Walker::kindred);
                        case "realm" -> Comparator.comparing(Walker::realm);
                        case "leagues" -> Comparator.comparingInt(Walker::leagues);
                        default -> null;
                    };
            if (by == null) {
                return Walker.COMPANY;
            }
            return Walker.COMPANY.stream()
                    .sorted(sort.descending() ? by.reversed() : by)
                    .toList();
        }

        @Override
        public Widget build(BuildContext context) {
            var table = new Table<>(
                            sorted(),
                            Walker::id,
                            List.of(
                                    nameColumn(),
                                    Column.<Walker>of("kindred", "Kindred", Walker::kindred)
                                            .sortable(true)
                                            .weight(2),
                                    Column.<Walker>of("realm", "Realm", Walker::realm)
                                            .sortable(true)
                                            .weight(2),
                                    Column.<Walker>of("leagues", "Leagues", w -> String.valueOf(w.leagues()))
                                            .sortable(true)
                                            .fixed(96)))
                    .sorted(sort, this::sortBy)
                    .resized(this::resize)
                    .selection(Selection.MULTIPLE)
                    .selected(picked, this::pick)
                    .withAttributes(Attributes.NONE.id("company"));
            return CARD.of(
                    table,
                    new Text(
                            picked.isEmpty() ? "Nobody chosen" : "Chose: " + String.join(", ", picked),
                            Attributes.NONE.id("company-chosen").classes("caption")));
        }
    }
}
