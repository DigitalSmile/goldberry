package io.github.digitalsmile.goldberry.widgets.panel.table;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.key.Key;
import io.github.digitalsmile.goldberry.input.key.Modifiers;
import io.github.digitalsmile.goldberry.widget.ElementTree;
import io.github.digitalsmile.goldberry.widgets.TestHost;
import io.github.digitalsmile.goldberry.widgets.panel.Described;
import io.github.digitalsmile.goldberry.widgets.panel.list.ListView;
import io.github.digitalsmile.goldberry.widgets.panel.list.Selection;
import io.github.digitalsmile.goldberry.widgets.text.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// `table` — `docs/core-widgets.md` §10's list with columns ([ADR-0214]).
///
/// What is here is the part a table *adds*: the columns, the header and the
/// sort. What it inherits from `list` is tested where it lives — the point of
/// composing rather than reimplementing is that those tests already cover this
/// widget, and the two assertions below check the wiring rather than repeat them.
class TableTest {

    private record Person(String id, String name, int age) { }

    private static final List<Person> PEOPLE = List.of(
            new Person("fro", "Frodo", 50),
            new Person("sam", "Samwise", 38),
            new Person("mer", "Meriadoc", 36));

    private final TestHost host = new TestHost();
    private final List<Sort> sorts = new ArrayList<>();
    private final List<Set<String>> asked = new ArrayList<>();

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
    }

    private static List<Column<Person>> columns() {
        return List.of(
                Column.<Person>of("name", "Name", Person::name).sortable(true),
                Column.<Person>of("age", "Age", p -> String.valueOf(p.age())).fixed(64));
    }

    private Table<Person> table() {
        return new Table<>(PEOPLE, Person::id, columns());
    }

    private ElementTree tree(Table<Person> widget) {
        return new ElementTree(widget, host);
    }

    private static List<String> headers(ElementTree tree) {
        return Described.of(tree, TableHead.TableHeader.class).stream()
                .map(h -> h.column().key()).toList();
    }

    private static TableHead.TableHeader header(ElementTree tree, String key) {
        return Described.of(tree, TableHead.TableHeader.class).stream()
                .filter(h -> h.column().key().equals(key))
                .findFirst().orElseThrow(() -> new AssertionError(
                        "no header \"" + key + "\"; showing " + headers(tree)));
    }

    private static void click(io.github.digitalsmile.goldberry.input.handler.Handles target) {
        target.onPointer(new PointerEvent(PointerEvent.Kind.CLICKED, 0, 0,
                PointerEvent.Button.PRIMARY, 1, Float.NaN, Float.NaN, Modifiers.NONE, null));
    }

    @Nested
    @DisplayName("the columns")
    class Columns {

        @Test
        @DisplayName("a header per column, in the order they were given")
        void headerPerColumn() {
            assertEquals(List.of("name", "age"), headers(tree(table())));
        }

        @Test
        @DisplayName("a cell per column on every row, so the two can never disagree")
        void cellPerColumn() {
            // Both lists are columns().size() long and are walked in the same
            // order -- which is the whole reason the cells and the headers are
            // built from one model rather than two.
            var cells = Described.of(tree(table()), TableCells.TableCell.class);
            assertEquals(PEOPLE.size() * 2, cells.size());
        }

        @Test
        @DisplayName("a cell-factory may return any widget, not only text")
        void anyWidgetAsACell() {
            var widget = new Table<>(PEOPLE, Person::id, List.of(
                    Column.<Person>widget("tag", "Tag", p -> new Text("<" + p.name() + ">"))));
            var texts = Described.of(tree(widget), Text.class).stream()
                    .map(Text::content).filter(s -> s.startsWith("<")).toList();
            assertEquals(List.of("<Frodo>", "<Samwise>", "<Meriadoc>"), texts);
        }

        @Test
        @DisplayName("a column with no width at all is refused where it is written")
        void aZeroWidthIsRefused() {
            assertThrows(IllegalArgumentException.class,
                    () -> Column.of("a", "A", Person::name).fixed(0));
            assertThrows(IllegalArgumentException.class,
                    () -> Column.of("a", "A", Person::name).weight(-1));
        }

        @Test
        @DisplayName("a table with no columns is an empty table rather than a failure")
        void noColumns() {
            var tree = tree(new Table<>(PEOPLE, Person::id, List.of()));
            assertEquals(List.of(), headers(tree));
        }
    }

    @Nested
    @DisplayName("sorting")
    class Sorting {

        private Table<Person> sortable(Sort sort) {
            return table().sorted(sort, sorts::add);
        }

        @Test
        @DisplayName("clicking a sortable header asks for that column, ascending")
        void firstClickSortsAscending() {
            click(header(tree(sortable(null)), "name"));
            assertEquals(List.of(Sort.by("name")), sorts);
        }

        @Test
        @DisplayName("clicking the column already sorted turns it round")
        void secondClickReverses() {
            click(header(tree(sortable(Sort.by("name"))), "name"));
            assertEquals(List.of(new Sort("name", true)), sorts);
        }

        @Test
        @DisplayName("clicking another column starts it ascending rather than inheriting")
        void anotherColumnStartsFresh() {
            var widget = new Table<>(PEOPLE, Person::id, List.of(
                    Column.<Person>of("name", "Name", Person::name).sortable(true),
                    Column.<Person>of("age", "Age", p -> "" + p.age()).sortable(true)));
            click(header(tree(widget.sorted(new Sort("name", true), sorts::add)), "age"));
            assertEquals(List.of(Sort.by("age")), sorts);
        }

        @Test
        @DisplayName("it asks and does not sort — the order is the application's")
        void itAsksAndDoesNotAct() {
            var widget = sortable(null);
            click(header(tree(widget), "name"));
            assertEquals(List.of(Sort.by("name")), sorts);
            // The widget it was asked of is unchanged: the rows are still in the
            // model's order and the sort is still null.
            assertNull(widget.sort());
            assertEquals(PEOPLE, widget.items());
        }

        @Test
        @DisplayName("a column that does not sort ignores the click entirely")
        void anUnsortableHeaderDoesNothing() {
            var event = new PointerEvent(PointerEvent.Kind.CLICKED, 0, 0,
                    PointerEvent.Button.PRIMARY, 1, Float.NaN, Float.NaN, Modifiers.NONE, null);
            header(tree(sortable(null)), "age").onPointer(event);
            assertTrue(sorts.isEmpty());
            assertFalse(event.isConsumed());
        }

        @Test
        @DisplayName("Enter and Space sort, because a sortable header is a button")
        void theKeyboardSorts() {
            var head = header(tree(sortable(null)), "name");
            head.onKey(new KeyEvent(KeyEvent.Kind.PRESSED, Key.ENTER, Modifiers.NONE, false, null));
            head.onKey(new KeyEvent(KeyEvent.Kind.PRESSED, Key.SPACE, Modifiers.NONE, false, null));
            assertEquals(List.of(Sort.by("name"), Sort.by("name")), sorts);
        }

        @Test
        @DisplayName("only a sortable header is a Tab stop")
        void onlySortableHeadersTakeFocus() {
            // A header that answers no key would be a stop that does nothing,
            // which is worse for a keyboard user than not being there at all.
            var tree = tree(sortable(null));
            assertTrue(header(tree, "name").isFocusable());
            assertFalse(header(tree, "age").isFocusable());
        }

        @Test
        @DisplayName("the caret is drawn on the sorted column and nowhere else")
        void oneCaret() {
            var widget = new Table<>(PEOPLE, Person::id, List.of(
                    Column.<Person>of("name", "Name", Person::name).sortable(true),
                    Column.<Person>of("age", "Age", p -> "" + p.age()).sortable(true)));
            var tree = tree(widget.sorted(Sort.by("name"), sorts::add));

            var drawn = Described.of(tree, TableHead.SortCaret.class).stream()
                    .filter(caret -> caret.sort() != null).toList();
            assertEquals(1, drawn.size());
            assertTrue(header(tree, "name").classes().contains("sorted"));
            assertFalse(header(tree, "age").classes().contains("sorted"));
        }

        @Test
        @DisplayName("but the slot is kept on every sortable header, so nothing reflows")
        void theSlotIsAlwaysThere() {
            // `tree-chevron`'s rule. Without it, sorting a column takes 16px away
            // from its own label at the moment the reader clicks it, so every
            // header the sort visits shuffles its text.
            var widget = new Table<>(PEOPLE, Person::id, List.of(
                    Column.<Person>of("name", "Name", Person::name).sortable(true),
                    Column.<Person>of("age", "Age", p -> "" + p.age()).sortable(true)));
            var tree = tree(widget.sorted(Sort.by("name"), sorts::add));

            assertEquals(2, Described.of(tree, TableHead.SortCaret.class).size(),
                    "a sortable header lost its caret slot when the sort moved away");
        }

        @Test
        @DisplayName("and a column that does not sort has no slot to keep")
        void anUnsortableHeaderHasNoSlot() {
            var tree = tree(sortable(Sort.by("name")));
            assertEquals(1, Described.of(tree, TableHead.SortCaret.class).size(),
                    "the unsortable column reserved a caret slot it can never use");
        }

        @Test
        @DisplayName("and it points the way the sort goes")
        void theCaretSaysWhichWay() {
            var up = Described.of(tree(sortable(Sort.by("name"))), TableHead.SortCaret.class);
            assertFalse(up.getFirst().descending());

            var down = Described.of(tree(sortable(new Sort("name", true))),
                    TableHead.SortCaret.class);
            assertTrue(down.getFirst().descending());
        }

        @Test
        @DisplayName("an unsorted table draws no caret at all")
        void noSortNoCaret() {
            var drawn = Described.of(tree(sortable(null)), TableHead.SortCaret.class).stream()
                    .filter(caret -> caret.sort() != null).toList();
            assertEquals(List.of(), drawn);
        }

        @Test
        @DisplayName("a table with no sort listener is not a crash")
        void noListener() {
            click(header(tree(table()), "name"));
            assertTrue(sorts.isEmpty());
        }
    }

    @Nested
    @DisplayName("what it hands to `list`")
    class Inherited {

        /// The list the table built — the seam, and the only thing worth
        /// asserting here.
        ///
        /// `list`'s rows are its own parts and are package-private, which is
        /// ADR-0065 working: a table cannot reach into them and neither can this
        /// test. What a table owes is that the right model reaches the right
        /// list, and everything after that is tested where it lives — which is
        /// the whole argument for composing rather than reimplementing.
        private ListView<?> list(Table<Person> widget) {
            return Described.of(tree(widget), ListView.class).getFirst();
        }

        @Test
        @DisplayName("one row per item, through `list`'s own item-factory")
        void theListGetsTheModel() {
            assertEquals(PEOPLE, list(table()).items());
        }

        @Test
        @DisplayName("the selection is `list`'s, unchanged")
        void selectionReachesTheList() {
            var list = list(table()
                    .selection(Selection.MULTIPLE)
                    .selected(Set.of("sam"), asked::add));

            assertEquals(Selection.MULTIPLE, list.selection());
            assertEquals(Set.of("sam"), list.selected());

            // And it is the table's callback the list will report to, so a click
            // on a row asks the application rather than the table.
            list.onSelect().accept(Set.of("fro"));
            assertEquals(Set.of("fro"), asked.getLast());
        }

        @Test
        @DisplayName("virtualization is passed straight through")
        void virtualizationReachesTheList() {
            assertEquals(32.0, list(table().virtualized(32)).rowHeight());
            assertEquals(0.0, list(table()).rowHeight());
        }

        @Test
        @DisplayName("the list keeps the table's id, so two tables do not share focus names")
        void theListIsScopedByTheTableId() {
            assertEquals("people", list(table().id("people")).attributes().id());
        }

        @Test
        @DisplayName("the header is outside the list, so it is never virtualized away")
        void theHeaderIsNotARow() {
            var tree = tree(table().virtualized(32));
            assertEquals(List.of("name", "age"), headers(tree));
        }
    }

    @Nested
    @DisplayName("what a stylesheet sees")
    class Styling {

        @Test
        @DisplayName("the table node carries the id and classes")
        void attributes() {
            var widget = table().id("people").styled("striped");
            assertEquals("table", widget.cssType());
            assertEquals("people", widget.id());
            assertEquals(Set.of("striped"), widget.classes());
        }

        @Test
        @DisplayName("a sortable header says so, so a stylesheet can give it a cursor")
        void sortableIsAClass() {
            var tree = tree(table().sorted(null, sorts::add));
            assertTrue(header(tree, "name").classes().contains("sortable"));
            assertFalse(header(tree, "age").classes().contains("sortable"));
        }
    }
}
