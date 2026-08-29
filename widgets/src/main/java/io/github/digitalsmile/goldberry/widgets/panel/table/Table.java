package io.github.digitalsmile.goldberry.widgets.panel.table;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.attr.Attributed;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.panel.list.ListView;
import io.github.digitalsmile.goldberry.widgets.panel.list.Selection;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/// A list with columns — `docs/core-widgets.md` §10's `table`.
///
/// ```java
/// new Table<>(people, Person::id, List.of(
///         Column.of("name", "Name", Person::name).sortable(),
///         Column.of("age", "Age", p -> "" + p.age()).fixed(64)))
///     .sorted(sort, this::sortBy)
///     .selected(chosen, this::pick)
/// ```
///
/// ## It is a `list`, composed rather than reimplemented
///
/// §10 says a table "awaits the virtualization work", and what it was waiting for
/// was `list`: a table's rows *are* a list's rows with more than one thing in
/// them. So this builds a [ListView] whose item-factory returns a row of cells,
/// and the selection models, the typeahead, `Home`/`End`, the item context menus
/// and the ten-thousand-row window are **inherited rather than written twice** —
/// a bug fixed in one is fixed in both
/// ([ADR-0214](../../../../../../../../book/src/adr/0214-a-table-is-a-list-with-columns.md)).
///
/// ## Sorting is the application's
///
/// A click on a sortable header reports the column and **what the sort would
/// become**; the rows arrive in whatever order the application hands back, and
/// the caret is drawn for whatever [Sort] it is given. The same split `select`'s
/// autocomplete draws, and for the same reason: a table over a database sorts in
/// the query, and one that sorted its own copy would be showing a different
/// answer from the one the query would give.
///
/// ## What it is made of
///
/// ```
/// table                this node
/// ├── table-head       the header row
/// │   └── table-header × columns   label, and a caret on the sorted one
/// └── list             a real `list`, with everything that means
///     └── list-row × n
///         └── table-cells
///             └── table-cell × columns
/// ```
///
/// A header and the cells under it are sized by one piece of code, which is what
/// guarantees they line up; and both lists are `columns().size()` long, so there
/// is no way to draw four headers over five cells.
///
/// @param <T>        the row's item type
/// @param items      the model, in the order it is drawn — already sorted
/// @param identity   an item's id: its key, its focus name, how a selection names it
/// @param columns    what to show of each item, in order
/// @param sort       what the rows are ordered by, or null for the model's order
/// @param onSort     asked for a sort, with what a click on that header means next
/// @param selected   the ids of the chosen rows; empty for none
/// @param onSelect   the selection the user asked for, whole
/// @param selection  how many rows may be chosen at once
/// @param rowHeight  a row's height in logical pixels, or zero to build every row
/// @param attributes `id` and `class`, exactly as on the primitives
public record Table<T>(List<T> items, Function<T, String> identity, List<Column<T>> columns,
        Sort sort, Consumer<Sort> onSort,
        Set<String> selected, Consumer<Set<String>> onSelect, Selection selection,
        double rowHeight, Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Attributed<Table<T>> {

    public Table {
        items = List.copyOf(items == null ? List.of() : items);
        columns = List.copyOf(columns == null ? List.of() : columns);
        Objects.requireNonNull(identity, "identity");
        selected = selected == null ? Set.of() : Set.copyOf(selected);
        selection = selection == null ? Selection.SINGLE : selection;
        attributes = attributes == null ? Attributes.NONE : attributes;
    }

    /// A table over `items` with `columns`, choosing one row at a time.
    public Table(List<T> items, Function<T, String> identity, List<Column<T>> columns) {
        this(items, identity, columns, null, null,
                Set.of(), null, Selection.SINGLE, 0, Attributes.NONE);
    }

    /// This table sorted by `sort`, and `onSort` told what a header click means.
    ///
    /// The callback is handed the sort the click **asks for** rather than the
    /// column it landed on: which way a second click on the same column goes is a
    /// rule about tables ([Sort#next]) and not something every application should
    /// have to restate.
    public Table<T> sorted(Sort sort, Consumer<Sort> onSort) {
        return new Table<>(items, identity, columns, sort, onSort,
                selected, onSelect, selection, rowHeight, attributes);
    }

    /// This table with `values` selected and `onSelect` told what was asked for.
    public Table<T> selected(Set<String> values, Consumer<Set<String>> onSelect) {
        return new Table<>(items, identity, columns, sort, onSort,
                values, onSelect, selection, rowHeight, attributes);
    }

    /// The same, for the caller that holds one value.
    public Table<T> selected(String value, Consumer<String> onSelect) {
        return selected(value == null ? Set.of() : Set.of(value),
                onSelect == null ? null : chosen -> onSelect.accept(
                        chosen.isEmpty() ? null : chosen.iterator().next()));
    }

    /// This table with a different selection model.
    public Table<T> selection(Selection value) {
        return new Table<>(items, identity, columns, sort, onSort,
                selected, onSelect, value, rowHeight, attributes);
    }

    /// This table building only the rows its viewport can see — `list`'s
    /// virtualization, with `list`'s precondition: every row is this tall.
    ///
    /// The header is **not** part of the window and never was: it is outside the
    /// list entirely, so it is built on every frame and costs one row.
    public Table<T> virtualized(double height) {
        return new Table<>(items, identity, columns, sort, onSort,
                selected, onSelect, selection, height, attributes);
    }

    @Override
    public String cssType() {
        return "table";
    }

    @Override
    public String id() {
        return attributes.id();
    }

    @Override
    public Set<String> classes() {
        return attributes.classes();
    }

    @Override
    public List<Widget> children() {
        var parts = new ArrayList<Widget>(2);
        parts.add(new TableHead(columns, sort, this::askForSort));
        var rows = new ListView<>(items, identity,
                item -> new TableCells<>(item, columns),
                null, null, selected, onSelect, selection, rowHeight,
                // The list keeps the table's id so a row's focus name is scoped
                // by it, exactly as a bare list's is: two tables over the same
                // items would otherwise answer to each other's `Home`.
                attributes.id() == null ? Attributes.NONE : Attributes.NONE.id(attributes.id()));
        parts.add(rows);
        return List.copyOf(parts);
    }

    /// Turns "this header was clicked" into "this is what the sort becomes".
    private void askForSort(String column) {
        if (onSort != null) {
            onSort.accept(Sort.next(sort, column));
        }
    }

    @Override
    public Table<T> withAttributes(Attributes value) {
        return new Table<>(items, identity, columns, sort, onSort,
                selected, onSelect, selection, rowHeight, value);
    }

    @Override
    public Object key() {
        return attributes.key();
    }

    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        return Box.of().style(style).children(boxes.toArray(Box[]::new));
    }
}
