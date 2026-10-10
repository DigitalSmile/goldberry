package dev.goldberry.widgets.panel.list;

import java.util.List;
import java.util.Set;
import java.util.function.DoubleConsumer;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.input.event.KeyEvent;
import dev.goldberry.input.event.PointerEvent;
import dev.goldberry.input.event.TextEvent;
import dev.goldberry.input.handler.Handles;
import dev.goldberry.input.handler.Measured;
import dev.goldberry.input.handler.Selects;
import dev.goldberry.input.hit.Extent;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributed;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.semantics.Role;
import dev.goldberry.widget.semantics.Semantics;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;

/// A [ListRow] that says what height it came out as — the row of a list
/// virtualized over [RowHeights].
///
/// The same row in every other respect, and the same `list-row` to a
/// stylesheet, with the class `measured` beside `selected`. A separate type
/// rather than a flag on [ListRow], because being [Measured] is what puts a node
/// in the router's per-frame walk: a list of rows that are all one height, or
/// that are all built, has no use for a height per row and must not pay for one.
///
/// @param row      the row, exactly as a list of one-height rows would build it
/// @param onHeight told the row's border-box height whenever it changes
record MeasuredRow(ListRow row, DoubleConsumer onHeight)
        implements Widget.Leaf, Styled, Paints, Handles, Selects, Attributed<MeasuredRow>, Semantics, Measured {

    /// The class a measured row carries, which the stylesheet gives an
    /// automatic height.
    static final String CLASS = "measured";

    private static final Set<String> PLAIN = Set.of(CLASS);
    private static final Set<String> SELECTED = Set.of("selected", CLASS);

    @Override
    public void measured(Extent bounds, Extent part) {
        onHeight.accept(bounds.height());
    }

    @Override
    public String cssType() {
        return row.cssType();
    }

    @Override
    public String id() {
        return row.id();
    }

    @Override
    public Object key() {
        return row.key();
    }

    @Override
    public Set<String> classes() {
        return row.selected() ? SELECTED : PLAIN;
    }

    @Override
    public boolean isFocusable() {
        return row.isFocusable();
    }

    @Override
    public List<Widget> children() {
        return row.children();
    }

    @Override
    public Attributes attributes() {
        return row.attributes();
    }

    @Override
    public MeasuredRow withAttributes(Attributes value) {
        return new MeasuredRow(row.withAttributes(value), onHeight);
    }

    @Override
    public void onPointer(PointerEvent event) {
        row.onPointer(event);
    }

    @Override
    public void selectForContextMenu() {
        row.selectForContextMenu();
    }

    @Override
    public void onKey(KeyEvent event) {
        row.onKey(event);
    }

    @Override
    public void onText(TextEvent event) {
        row.onText(event);
    }

    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        return row.render(style, boxes, context);
    }

    @Override
    public Role role() {
        return row.role();
    }

    @Override
    public @Nullable String accessibleName() {
        return row.accessibleName();
    }
}
