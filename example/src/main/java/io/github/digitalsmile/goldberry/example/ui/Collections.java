package io.github.digitalsmile.goldberry.example.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.core.scroll.Scroll;
import io.github.digitalsmile.goldberry.widgets.core.scroll.ScrollAxis;
import io.github.digitalsmile.goldberry.widgets.panel.list.ListView;
import io.github.digitalsmile.goldberry.widgets.panel.list.Selection;
import io.github.digitalsmile.goldberry.widgets.panel.table.Column;
import io.github.digitalsmile.goldberry.widgets.panel.table.Sort;
import io.github.digitalsmile.goldberry.widgets.panel.table.Table;
import io.github.digitalsmile.goldberry.widgets.panel.tree.Checkable;
import io.github.digitalsmile.goldberry.widgets.panel.tree.Tree;
import io.github.digitalsmile.goldberry.widgets.panel.tree.TreeNode;
import io.github.digitalsmile.goldberry.widgets.text.Text;

/// The **Collections** screen: §10's three widgets that hold many rows — a
/// `list`, a `table` and a `tree`.
///
/// One screen and three cards, because what is worth comparing is how each of
/// them answers *scale*: a list virtualizes, a table sorts, and a tree fetches.
/// None of the three does the work itself — the list is handed a row height, the
/// table is handed an order, and the tree is handed a supplier — which is the
/// single sentence this screen exists to make ([ADR-0213], [ADR-0214],
/// [ADR-0212]).
///
/// Three cards and three states, for [Notifications]'s reason: a masonry places
/// by column height, and each of these owns a selection the other two never read.
public final class Collections {

    private Collections() {}

    /// The four collection cards, in the order they are offered to the wall.
    public static List<Widget> cards() {
        return List.of(new Leagues(), new Company(), new Realms(), new Chronicle());
    }

    private static Widget caption(String text) {
        return new Text(text, Attributes.NONE.classes("caption"));
    }

    /// §10's virtualization: ten thousand rows and a viewport that builds the
    /// dozen it can see.
    record Leagues() implements Widget.Stateful {

        /// Ten thousand of them, written down rather than paged in from anywhere:
        /// what is being demonstrated is that the *widget* does not instantiate
        /// them, and a source that was slow would confuse the two.
        private static final List<String> ROAD = road();

        private static List<String> road() {
            var out = new ArrayList<String>(10_000);
            for (var i = 1; i <= 10_000; i++) {
                out.add("League " + i + " of the road");
            }
            return List.copyOf(out);
        }

        private static final double ROW_HEIGHT = 32;

        @Override
        public State<?> createState() {
            return new LeaguesState();
        }

        static final class LeaguesState extends State<Leagues> {

            private Set<String> chosen = Set.of("League 3 of the road");

            private void choose(Set<String> values) {
                setState(() -> chosen = values);
            }

            @Override
            public Widget build(BuildContext context) {
                return Notifications.card(
                        "leagues-card",
                        "Ten thousand leagues",
                        List.of(
                                caption("The list builds only the rows this viewport can see and"
                                        + " stands the rest off with two spacers, so the scrollbar is"
                                        + " honest about a model nothing has instantiated. Home and"
                                        + " End still reach the ends of it."),
                                new Scroll(
                                        List.of(ListView.of(Leagues.ROAD)
                                                .virtualized(Leagues.ROW_HEIGHT)
                                                .selection(Selection.MULTIPLE)
                                                .selected(chosen, this::choose)
                                                .withAttributes(Attributes.NONE.id("many"))),
                                        ScrollAxis.VERTICAL,
                                        Attributes.NONE.classes("tall-list")),
                                caption(
                                        chosen.isEmpty()
                                                ? "Nothing chosen"
                                                : "Holding " + chosen.size() + ": " + String.join(", ", chosen))));
            }
        }
    }

    /// §10's table: the same rows with a cell per column, and a sort the
    /// application does.
    record Company() implements Widget.Stateful {

        /// A row that is genuinely a record and not three parallel strings, so
        /// that a sort on `Kindred` is a `Comparator` over a field rather than
        /// over a cell's rendered text.
        record Walker(String id, String name, String kindred, String realm, int leagues) {}

        /// Nine of them, with kindreds that repeat and leagues that do not — which
        /// is what makes both sorts worth clicking: a column of nine distinct
        /// values shows an order changing, and a column of four repeated ones
        /// shows a *stable* one.
        private static final List<Walker> WALKERS = List.of(
                new Walker("frodo", "Frodo", "Hobbit", "The Shire", 1795),
                new Walker("samwise", "Samwise", "Hobbit", "The Shire", 1795),
                new Walker("meriadoc", "Meriadoc", "Hobbit", "Buckland", 1240),
                new Walker("peregrin", "Peregrin", "Hobbit", "Tuckborough", 1240),
                new Walker("aragorn", "Aragorn", "Man", "Arnor", 2310),
                new Walker("boromir", "Boromir", "Man", "Gondor", 980),
                new Walker("legolas", "Legolas", "Elf", "Mirkwood", 2110),
                new Walker("gimli", "Gimli", "Dwarf", "Erebor", 2110),
                new Walker("gandalf", "Gandalf", "Maia", "—", 2680));

        @Override
        public State<?> createState() {
            return new CompanyState();
        }

        static final class CompanyState extends State<Company> {

            private Set<String> picked = Set.of("aragorn");

            private Sort sort = new Sort("leagues", true);

            private void pick(Set<String> values) {
                setState(() -> picked = values);
            }

            private void sortBy(Sort next) {
                setState(() -> sort = next);
            }

            /// The Name column's width once somebody has dragged it, or NaN while
            /// it still takes its share. Kept here, like the sort, because a
            /// column's width is the application's to save and put back
            /// (ADR-0361).
            private double nameWidth = Double.NaN;

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

            /// The sort, done **here**, which is the whole reason the widget does
            /// not do it: a table over a database would sort in the query, and one
            /// that had already sorted its rows in Java would make that impossible.
            private List<Company.Walker> sorted() {
                if (sort == null) {
                    return Company.WALKERS;
                }
                Comparator<Company.Walker> by =
                        switch (sort.column()) {
                            case "name" -> Comparator.comparing(Company.Walker::name);
                            case "kindred" -> Comparator.comparing(Company.Walker::kindred);
                            case "realm" -> Comparator.comparing(Company.Walker::realm);
                            case "leagues" -> Comparator.comparingInt(Company.Walker::leagues);
                            case null, default -> null;
                        };
                if (by == null) {
                    return Company.WALKERS;
                }
                return Company.WALKERS.stream()
                        .sorted(sort.descending() ? by.reversed() : by)
                        .toList();
            }

            @Override
            public Widget build(BuildContext context) {
                return Notifications.card(
                        "company-card",
                        "The Company, in columns",
                        List.of(
                                caption("Clicking a header asks for a sort and this card does it — a"
                                        + " table over a database would sort in the query, which is why"
                                        + " the widget does not. The caret keeps its place on every"
                                        + " sortable header, so nothing shuffles when the sort moves. Drag"
                                        + " the Name header's right edge to resize it; the width is kept"
                                        + " here too."),
                                new Table<>(
                                                sorted(),
                                                Company.Walker::id,
                                                List.of(
                                                        nameColumn(),
                                                        Column.<Company.Walker>of(
                                                                        "kindred", "Kindred", Company.Walker::kindred)
                                                                .sortable(true)
                                                                .weight(2),
                                                        Column.<Company.Walker>of(
                                                                        "realm", "Realm", Company.Walker::realm)
                                                                .sortable(true)
                                                                .weight(2),
                                                        Column.<Company.Walker>of(
                                                                        "leagues",
                                                                        "Leagues",
                                                                        w -> String.valueOf(w.leagues()))
                                                                .sortable(true)
                                                                .fixed(96)))
                                        .sorted(sort, this::sortBy)
                                        .resized(this::resize)
                                        .selection(Selection.MULTIPLE)
                                        .selected(picked, this::pick)
                                        .withAttributes(Attributes.NONE.id("company")),
                                caption(picked.isEmpty() ? "Nobody chosen" : "Chose: " + String.join(", ", picked))));
            }
        }
    }

    /// §10's tree, standing on its own rather than inside a `select`'s popup —
    /// and with **boxes** on it, which is the thing a popup tree has no room for.
    record Realms() implements Widget.Stateful {

        /// The same shape [Choosers.Realms] uses and deliberately not the same
        /// list: this one is deeper, because a tri-state box only has something
        /// to say when a branch has branches under it.
        private static final List<TreeNode> LANDS = List.of(
                TreeNode.of(
                        "eriador",
                        "Eriador",
                        TreeNode.of(
                                "shire",
                                "The Shire",
                                TreeNode.leaf("hobbiton", "Hobbiton"),
                                TreeNode.leaf("buckland", "Buckland"),
                                TreeNode.leaf("tuckborough", "Tuckborough")),
                        TreeNode.of(
                                "angle",
                                "The Angle",
                                TreeNode.leaf("bree", "Bree"),
                                TreeNode.leaf("weathertop", "Weathertop"))),
                TreeNode.of(
                        "wilderland",
                        "Wilderland",
                        TreeNode.leaf("lorien", "Lothlórien"),
                        TreeNode.leaf("fangorn", "Fangorn"),
                        TreeNode.lazy(
                                "erebor",
                                "Erebor",
                                () -> List.of(TreeNode.leaf("dale", "Dale"), TreeNode.leaf("esgaroth", "Esgaroth")))),
                TreeNode.of(
                        "south",
                        "The South Kingdoms",
                        TreeNode.leaf("edoras", "Edoras"),
                        TreeNode.leaf("minas-tirith", "Minas Tirith")));

        @Override
        public State<?> createState() {
            return new RealmsState();
        }

        static final class RealmsState extends State<Realms> {

            private Set<String> selected = Set.of("hobbiton");

            /// A `LinkedHashSet` and **not** `Set.of`, which is not a style
            /// preference: `Set.of` randomizes its iteration order once per JVM,
            /// so the caption under this tree — `String.join(", ", checked)` —
            /// came out "buckland, weathertop" on one run and the other way round
            /// on the next. Everything about the tree was identical; a thousand
            /// pixels of caption were not, and the golden image failed at random.
            private Set<String> checked = new java.util.LinkedHashSet<>(List.of("buckland", "weathertop"));

            private void select(Set<String> values) {
                setState(() -> selected = values);
            }

            private void check(@Nullable Set<String> values) {
                setState(() -> checked = values);
            }

            @Override
            public Widget build(BuildContext context) {
                return Notifications.card(
                        "lands-card",
                        "Rows with rows under them",
                        List.of(
                                caption("Checking and selecting are two different things and this tree"
                                        + " does both: the highlight follows the caret and the boxes do"
                                        + " not, so a branch can be ticked without being the row you"
                                        + " are standing on. Erebor fetches its children the first time"
                                        + " it opens."),
                                new Tree(
                                        Realms.LANDS,
                                        selected,
                                        this::select,
                                        Selection.SINGLE,
                                        false,
                                        Checkable.CASCADE,
                                        checked,
                                        this::check,
                                        Attributes.NONE.id("lands")),
                                caption(
                                        checked.isEmpty()
                                                ? "Nothing ticked"
                                                : "Ticked " + checked.size() + ": " + String.join(", ", checked))));
            }
        }
    }
}
