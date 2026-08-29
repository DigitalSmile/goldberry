package io.github.digitalsmile.goldberry.widgets.panel.table;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/// One row's cells — what a [Table] hands `list` as its item-factory.
///
/// This is the whole of how a table is a list: §10 gives a `list` "any widget as
/// row", and this is that widget. Everything a row does — being focusable, being
/// selected, carrying a context menu, being built only when the viewport can see
/// it — is `list-row`'s and arrives unchanged
/// ([ADR-0214](../../../../../../../../book/src/adr/0214-a-table-is-a-list-with-columns.md)).
///
/// **The cells come from the same column model the header does**, which is what
/// keeps them in step: there is no way to draw four headers over five cells,
/// because both lists are `columns().size()` long and are walked in the same
/// order by the same sizing.
///
/// @param <T>     the item type
/// @param item    the row's item
/// @param columns the table's columns, in order
record TableCells<T>(T item, List<Column<T>> columns)
        implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "table-cells";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public List<Widget> children() {
        var cells = new ArrayList<Widget>(columns.size());
        for (var column : columns) {
            cells.add(new TableCell(column, column.cell().apply(item)));
        }
        return List.copyOf(cells);
    }

    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        return Box.of().style(style).children(boxes.toArray(Box[]::new));
    }

    /// One cell: a column's width, around whatever the cell-factory returned.
    ///
    /// The content is a child rather than this node's own drawing, for
    /// `tree-label`'s reason — a box with text is a measured leaf and Yoga never
    /// lays a measured node's children out, so a cell that drew its own text
    /// could never hold a button.
    record TableCell(Column<?> column, Widget content)
            implements Widget.Leaf, Styled, Paints {

        @Override
        public String cssType() {
            return "table-cell";
        }

        @Override
        public Object key() {
            return column.key();
        }

        @Override
        public Set<String> classes() {
            return Set.of();
        }

        @Override
        public List<Widget> children() {
            return List.of(content);
        }

        @Override
        public Box render(ComputedStyle style, List<Box> boxes, Context context) {
            return Sized.apply(Box.of().style(style).children(boxes.toArray(Box[]::new)), column);
        }
    }
}
