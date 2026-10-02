package dev.goldberry.widgets.panel.table;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import dev.goldberry.widget.BuildContext;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;

/// A resizable column's header, and the width it last came out as — a
/// composition node with no CSS type.
///
/// A table is a stateless leaf and a column's width is the application's, so
/// the one number a drag needs — how wide the header is *now*, which for a
/// weighted column only the layout knows — has nowhere else to live. It is read
/// once, on the press, as the gesture's anchor.
///
/// @param column   the column
/// @param sort     the table's sort when it is this column's, else null
/// @param onSort   asked to sort
/// @param onResize asked for a new width
record TableHeaderCell(Column<?> column, @Nullable Sort sort, Consumer<String> onSort, TableHead.Resize onResize)
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
            // A rebuild, but only when the number actually moved: `setState` is
            // what puts a measured width where the next drag's anchor can read
            // it, and the equality check above is what keeps a window resize from
            // costing a frame per header per pixel. (This comment used to say the
            // width was kept "without a rebuild", which is not what the line
            // below does.)
            if (value != width && isMounted()) {
                setState(() -> width = value);
            }
        }
    }
}
