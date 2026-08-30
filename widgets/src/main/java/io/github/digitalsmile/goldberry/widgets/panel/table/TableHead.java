package io.github.digitalsmile.goldberry.widgets.panel.table;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.input.key.Key;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// The row of column headers — a **part**, styleable and not constructible
/// ([ADR-0065]).
///
/// @param columns what the table is showing
/// @param sort    what it is sorted by, or null
/// @param onSort  asked to sort by a column key
record TableHead(List<? extends Column<?>> columns, Sort sort, Consumer<String> onSort)
        implements Widget.Leaf, Styled, Paints {

    @Override
    public String cssType() {
        return "table-head";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public List<Widget> children() {
        var cells = new ArrayList<Widget>(columns.size());
        for (var column : columns) {
            cells.add(new TableHeader(column, sortOf(column), onSort));
        }
        return List.copyOf(cells);
    }

    /// This column's place in the sort: `null` when the table is sorted by
    /// something else, which is what makes the caret appear on exactly one
    /// header.
    private Sort sortOf(Column<?> column) {
        return sort != null && sort.on(column.key()) ? sort : null;
    }

    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        return Box.of().style(style).children(boxes.toArray(Box[]::new));
    }

    /// One column's header.
    ///
    /// **Focusable only when it sorts.** §2.2 wants everything reachable, and a
    /// header that does nothing is a label — a Tab stop on it would be a stop
    /// that answers no key, which is worse for a keyboard user than not being
    /// there. A sortable one is a control and is a stop like any other.
    ///
    /// @param column this column
    /// @param sort   the table's sort when it is *this* column's, else null
    record TableHeader(Column<?> column, Sort sort, Consumer<String> onSort)
            implements Widget.Leaf, Styled, Paints, Handles {

        @Override
        public String cssType() {
            return "table-header";
        }

        @Override
        public String id() {
            return column.key();
        }

        @Override
        public Object key() {
            return column.key();
        }

        @Override
        public Set<String> classes() {
            if (sort == null) {
                return column.sortable() ? Set.of("sortable") : Set.of();
            }
            // Both, so a stylesheet can say "the sorted column" once and "which
            // way" separately -- a single `sorted-descending` class would make
            // the common rule need two selectors.
            return Set.of("sortable", "sorted", sort.descending() ? "descending" : "ascending");
        }

        @Override
        public boolean isFocusable() {
            return column.sortable();
        }

        @Override
        public void onPointer(PointerEvent event) {
            if (event.kind() == PointerEvent.Kind.CLICKED && column.sortable()) {
                ask();
                event.consume();
            }
        }

        /// `Enter` and `Space`, which is what a header is: a button that happens
        /// to be a label.
        @Override
        public void onKey(KeyEvent event) {
            if (event.kind() != KeyEvent.Kind.PRESSED
                    || !column.sortable()
                    || !event.modifiers().none()) {
                return;
            }
            if (event.key() == Key.ENTER || event.key() == Key.SPACE) {
                ask();
                event.consume();
            }
        }

        private void ask() {
            if (onSort != null) {
                onSort.accept(column.key());
            }
        }

        /// A label, and a caret slot on **every sortable header** whether or not
        /// this is the sorted one.
        ///
        /// `tree-chevron`'s rule: a leaf keeps the gutter and draws nothing in
        /// it, so the labels of a folder and a file line up. Here the reflow it
        /// prevents is worse than a misalignment — without the reserved slot,
        /// sorting a column takes 16px away from its own label at the moment the
        /// reader clicks it, so every header the sort visits shuffles its text.
        @Override
        public List<Widget> children() {
            if (!column.sortable()) {
                return List.of(new TableLabel(column.header()));
            }
            return List.of(new TableLabel(column.header()), new SortCaret(sort));
        }

        @Override
        public Box render(ComputedStyle style, List<Box> boxes, Context context) {
            return Sized.apply(Box.of().style(style).children(boxes.toArray(Box[]::new)), column);
        }
    }

    /// A header's words. A child box rather than text on the header's own node,
    /// because a box with text is a measured leaf and Yoga never lays a measured
    /// node's children out — which is what the caret beside it needs.
    record TableLabel(String text) implements Widget.Leaf, Styled, Paints {

        @Override
        public String cssType() {
            return "table-label";
        }

        @Override
        public Set<String> classes() {
            return Set.of();
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of().style(style).children(Box.text(context.paragraph(style, text), style.color()));
        }
    }

    /// Which way the sorted column is going.
    ///
    /// Two marks rather than one rotated, for the reason a `tree`'s chevron is
    /// two: §8's subset has no `transform` on a mark ([ADR-0141]). The cost is
    /// the same one — no animation between them — and it costs less here, because
    /// §3.1's row says a sort change animates nothing anyway: the rows are
    /// re-ordered by the application, and a caret that turned while the rows
    /// jumped would be the only thing moving smoothly on the screen.
    /// @param sort the sort when this is the sorted column, and null when it is
    ///             merely sortable — in which case the box is kept and nothing is
    ///             drawn in it
    record SortCaret(Sort sort) implements Widget.Leaf, Styled, Paints {

        @Override
        public String cssType() {
            return "sort-caret";
        }

        @Override
        public Set<String> classes() {
            if (sort == null) {
                return Set.of();
            }
            return sort.descending() ? Set.of("descending") : Set.of("ascending");
        }

        /// Whether this caret is drawn at all — for a test, and for nothing else.
        boolean descending() {
            return sort != null && sort.descending();
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            if (sort == null) {
                // The slot, empty. See TableHeader#children.
                return Box.of().style(style);
            }
            return Box.of()
                    .style(style)
                    .mark(new Box.Mark(
                            sort.descending() ? Box.Mark.Kind.CHEVRON_DOWN : Box.Mark.Kind.CHEVRON_UP,
                            style.color(),
                            1.5));
        }
    }
}
