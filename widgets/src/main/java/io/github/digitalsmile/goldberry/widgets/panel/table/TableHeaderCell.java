package io.github.digitalsmile.goldberry.widgets.panel.table;

import java.util.function.Consumer;

import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;

/// A resizable column's header, and the width it last came out as — a
/// composition node with no CSS type.
///
/// A table is a stateless leaf and a column's width is the application's, so
/// the one number a drag needs — how wide the header is *now*, which for a
/// weighted column only the layout knows — has nowhere else to live. It is read
/// once, on the press, as the gesture's anchor (ADR-0361).
///
/// @param column   the column
/// @param sort     the table's sort when it is this column's, else null
/// @param onSort   asked to sort
/// @param onResize asked for a new width
record TableHeaderCell(Column<?> column, Sort sort, Consumer<String> onSort, TableHead.Resize onResize)
        implements Widget.Stateful {

    @Override
    public Object key() {
        return column.key();
    }

    @Override
    public State<?> createState() {
        return new HeaderWidth();
    }

    /// The last laid-out width.
    static final class HeaderWidth extends State<TableHeaderCell> {

        private double width = Double.NaN;

        @Override
        public Widget build(BuildContext context) {
            var cell = widget();
            return new TableHead.TableHeader(
                    cell.column(), cell.sort(), cell.onSort(), width, this::measured, cell.onResize());
        }

        private void measured(double value) {
            // Kept without a rebuild when nothing is dragging: the anchor is read
            // on the press, and a rebuild per resize of the window would be a
            // frame of work for a number nobody has asked for yet.
            if (value != width && isMounted()) {
                setState(() -> width = value);
            }
        }
    }
}
