package io.github.digitalsmile.goldberry.widgets.panel.table;

import java.util.Objects;
import java.util.function.Function;

import io.github.digitalsmile.goldberry.widget.Widget;

/// One column of a [Table] — `docs/core-widgets.md` §10's "a key, a header, a
/// width and a cell-factory each".
///
/// ```java
/// Column.of("name", "Name", Person::name).sortable(true)
/// Column.of("age", "Age", p -> "" + p.age()).fixed(64)
/// ```
///
/// ## The width is a number or a share, and flexbox already had both
///
/// A [#fixed] column is that many logical pixels. A column that is not fixed has
/// a **weight**, and the columns with weights share out what the fixed ones left
/// — which is `flex-grow`, so the layout engine does the arithmetic and there is
/// no second sizing model to keep in step with it ([ADR-0214]).
///
/// ## The cell-factory is `list`'s item-factory, per column
///
/// §10 gives a list "any widget as row"; a table gives a column any widget as
/// *cell*, which is the same function with the same rules — called during build,
/// handed the item, and free to return anything. A column of buttons is a column
/// of buttons.
///
/// @param <T>     the row's item type
/// @param key     what names this column — its CSS `id`, and what a sort reports
/// @param header  the words at the top; empty for a column that has none
/// @param cell    what an item looks like in this column
/// @param width   logical pixels when [#fixed] is set, otherwise a flex weight
/// @param fixed   whether [#width] is a size or a share
/// @param sortable whether clicking the header asks for a sort
/// @param resizable whether the header has a grip that asks for a new width
public record Column<T>(
        String key,
        String header,
        Function<T, Widget> cell,
        double width,
        boolean fixed,
        boolean sortable,
        boolean resizable) {

    public Column {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(cell, "cell");
        header = header == null ? "" : header;
        if (width <= 0 || Double.isNaN(width)) {
            throw new IllegalArgumentException(
                    "a column's width is a positive number of pixels or a positive" + " weight; got " + width);
        }
    }

    /// A column of text, sharing the width equally with its neighbours.
    ///
    /// The common case, and the one a caller would otherwise write three times: a
    /// column whose cells are the item's own words.
    public static <T> Column<T> of(String key, String header, Function<T, String> text) {
        return new Column<>(
                key,
                header,
                item -> new io.github.digitalsmile.goldberry.widgets.text.Text(text.apply(item)),
                1,
                false,
                false,
                false);
    }

    /// A column whose cells are whatever `cell` returns — §10's "any widget as a
    /// cell".
    public static <T> Column<T> widget(String key, String header, Function<T, Widget> cell) {
        return new Column<>(key, header, cell, 1, false, false, false);
    }

    /// This column at exactly `pixels` wide, whatever is left over.
    ///
    /// What a column of dates or of a fixed-width control wants: a share would
    /// make it grow with the window, and a date is the same width in a wide table
    /// as in a narrow one.
    public Column<T> fixed(double pixels) {
        return new Column<>(key, header, cell, pixels, true, sortable, resizable);
    }

    /// This column taking `share` of what the fixed columns left.
    ///
    /// Relative to the other weighted columns, exactly as `flex-grow` is: two
    /// columns of 1 and 3 split the space one part to three.
    public Column<T> weight(double share) {
        return new Column<>(key, header, cell, share, false, sortable, resizable);
    }

    /// This column's header asking for a sort when it is clicked.
    ///
    /// It asks and does not sort: the order of the rows is the application's, for
    /// [Table]'s reason.
    ///
    /// It takes the value rather than reading `sortable()` as "make it so",
    /// because a record's accessor already has that name — which is the same
    /// reason [Table#selection] and `tree`'s `checkable` take theirs.
    public Column<T> sortable(boolean value) {
        return new Column<>(key, header, cell, width, fixed, value, resizable);
    }

    /// This column's header carrying a grip at its trailing edge that asks for a
    /// new width when it is dragged — §3.1's "column resize: 1:1, like
    /// `split-pane`'s drag".
    ///
    /// It asks and does not resize, for [#sortable]'s reason: the widths are the
    /// application's, which is what lets one be saved and put back. The answer is
    /// in pixels, and a weighted column that is dragged is a column the
    /// application makes [#fixed] at that width (ADR-0361).
    public Column<T> resizable(boolean value) {
        return new Column<>(key, header, cell, width, fixed, sortable, value);
    }
}
