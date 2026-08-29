package io.github.digitalsmile.goldberry.example.ui;

import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.core.Column;
import io.github.digitalsmile.goldberry.widgets.core.scroll.Scroll;
import io.github.digitalsmile.goldberry.widgets.core.scroll.ScrollAxis;
import io.github.digitalsmile.goldberry.widgets.panel.list.ListView;
import io.github.digitalsmile.goldberry.widgets.panel.list.Selection;
import io.github.digitalsmile.goldberry.widgets.panel.table.Sort;
import io.github.digitalsmile.goldberry.widgets.panel.table.Table;
import io.github.digitalsmile.goldberry.widgets.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/// The **Collections** screen: `docs/core-widgets.md` §10 — `list`, the
/// virtualization it commits to, and the `table` that was waiting on it.
///
/// ## Why this screen is Java, and why it is its own screen
///
/// Java for `list`'s reason: an item-factory is a function, and §8's documents
/// have no way to write one ([ADR-0212]). Its own screen because the two things
/// worth seeing here are **scale** and **sort**, and neither fits beside the
/// `select`s on Choosers: a ten-thousand-row list needs a viewport of its own to
/// be scrolled through, and a sortable table needs somewhere to put the state the
/// sorting is done in.
///
/// ## The sorting is here, which is the point of it being here
///
/// §10 says sorting is the application's, and this screen is the application: the
/// table reports what a header click *means* and the rows below arrive in
/// whatever order this class puts them in ([ADR-0214]). A table over a database
/// would sort in the query and the widget would not know the difference — which
/// is exactly why the widget does not do it.
public record Collections() implements Widget.Stateful {

    @Override
    public State<?> createState() {
        return new CollectionsState();
    }

    static final class CollectionsState extends State<Collections> {

        /// A row of the table. A record, because §10's item is the
        /// *application's* type and this is what one looks like.
        record Peak(String id, String name, String range, int metres) { }

        private static final List<Peak> PEAKS = List.of(
                new Peak("everest", "Everest", "Mahalangur", 8849),
                new Peak("k2", "K2", "Baltoro Karakoram", 8611),
                new Peak("kangchenjunga", "Kangchenjunga", "Kangchenjunga", 8586),
                new Peak("lhotse", "Lhotse", "Mahalangur", 8516),
                new Peak("makalu", "Makalu", "Mahalangur", 8485),
                new Peak("cho-oyu", "Cho Oyu", "Mahalangur", 8188));

        /// Ten thousand rows, which is the number §10 says an unvirtualized list
        /// cannot do.
        ///
        /// Built once and held, because the point on screen is that a list of
        /// this size scrolls at all — rebuilding the model every frame would be
        /// measuring this screen rather than the widget.
        private static final List<String> MANY = many();

        private static List<String> many() {
            var out = new ArrayList<String>(10_000);
            for (var i = 1; i <= 10_000; i++) {
                out.add("Row " + i);
            }
            return List.copyOf(out);
        }

        /// §3's `--gb-list-row-height` default. Stated because the widget cannot
        /// read a resolved custom property and therefore has to be told
        /// ([ADR-0213]) — an application that restyled its rows would change this
        /// number in both places, which is the cost of that decision made visible.
        private static final double ROW_HEIGHT = 32;

        private Set<String> chosen = Set.of("Row 3");
        private Set<String> picked = Set.of("k2");

        /// What the table is sorted by. The **application's**, which is the whole
        /// point: the widget draws the caret for this and the rows arrive in the
        /// order [#sorted] puts them in.
        private Sort sort = new Sort("metres", true);

        private void choose(Set<String> values) {
            setState(() -> chosen = values);
        }

        private void pick(Set<String> values) {
            setState(() -> picked = values);
        }

        private void sortBy(Sort next) {
            setState(() -> sort = next);
        }

        /// The rows in the order the sort asks for.
        ///
        /// An eight-line method that is the entire "sorting is the application's"
        /// argument: a table over a database would put this in the query.
        private List<Peak> sorted() {
            if (sort == null) {
                return PEAKS;
            }
            Comparator<Peak> by = switch (sort.column()) {
                case "name" -> Comparator.comparing(Peak::name);
                case "range" -> Comparator.comparing(Peak::range);
                case "metres" -> Comparator.comparingInt(Peak::metres);
                default -> null;
            };
            if (by == null) {
                return PEAKS;
            }
            return PEAKS.stream()
                    .sorted(sort.descending() ? by.reversed() : by)
                    .toList();
        }

        @Override
        public Widget build(BuildContext context) {
            return new Column(List.of(
                    new SectionHeader("Ten thousand rows"),
                    new Text("§10's virtualization: the list builds only the rows this viewport"
                            + " can see and stands the rest off with two spacers, so the"
                            + " scrollbar is honest about a model nothing has instantiated."
                            + " Home and End still reach the ends of it.")
                            .withAttributes(Attributes.NONE.classes("caption")),
                    // Its own viewport, because that is what makes the window
                    // small: a virtual list clipped to the screen's own scroll
                    // would have a window the height of the screen.
                    new Scroll(List.of(
                            ListView.of(MANY)
                                    .virtualized(ROW_HEIGHT)
                                    .selection(Selection.MULTIPLE)
                                    .selected(chosen, this::choose)
                                    .withAttributes(Attributes.NONE.id("many"))),
                            ScrollAxis.VERTICAL,
                            Attributes.NONE.classes("tall-list")),
                    new Text(chosen.isEmpty()
                            ? "Nothing chosen"
                            : "Holding " + chosen.size() + ": " + String.join(", ", chosen))
                            .withAttributes(Attributes.NONE.classes("caption")),

                    new SectionHeader("A list with columns"),
                    new Text("§10's table: the same rows, with a cell per column. Clicking a"
                            + " header asks for a sort and this screen does it — a table over a"
                            + " database would sort in the query, which is why the widget does"
                            + " not. The caret keeps its place on every sortable header, so"
                            + " nothing shuffles when the sort moves.")
                            .withAttributes(Attributes.NONE.classes("caption")),
                    new Table<>(sorted(), Peak::id, List.of(
                            io.github.digitalsmile.goldberry.widgets.panel.table.Column
                                    .<Peak>of("name", "Name", Peak::name)
                                    .sortable(true).weight(2),
                            io.github.digitalsmile.goldberry.widgets.panel.table.Column
                                    .<Peak>of("range", "Range", Peak::range)
                                    .sortable(true).weight(2),
                            io.github.digitalsmile.goldberry.widgets.panel.table.Column
                                    .<Peak>of("metres", "Metres", p -> String.valueOf(p.metres()))
                                    .sortable(true).fixed(96)))
                            .sorted(sort, this::sortBy)
                            .selection(Selection.MULTIPLE)
                            .selected(picked, this::pick)
                            .withAttributes(Attributes.NONE.id("peaks")),
                    new Text(picked.isEmpty()
                            ? "No peak chosen"
                            : "Chose: " + String.join(", ", picked))
                            .withAttributes(Attributes.NONE.classes("caption"))),
                    Attributes.NONE.id("collections"));
        }
    }
}
