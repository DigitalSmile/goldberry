package io.github.digitalsmile.goldberry.widgets.panel.table;

import java.util.List;
import java.util.Set;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.render.Cursor;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// The strip at a resizable header's trailing edge — a **part**, `table-grip`.
///
/// A press here is anchored at the width its header last came out as — the
/// header answers the anchor, since the router asks the pressed chain
/// deepest-first — and every move asks for that width plus the pointer's travel.
/// That is `split-pane`'s arithmetic, and the reason the drag is 1:1 rather than
/// jumping to wherever the press landed. The press, the release and the click are
/// consumed, so a drag never sorts the column (ADR-0361).
///
/// It also owns the cursor, the only affordance a strip this thin has.
///
/// @param column   the column's key
/// @param onResize asked for a new width
record TableGrip(String column, TableHead.Resize onResize) implements Widget.Leaf, Styled, Paints, Handles {

    @Override
    public String cssType() {
        return "table-grip";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public void onPointer(PointerEvent event) {
        switch (event.kind()) {
            case MOVED -> {
                var travel = event.dragX();
                if (Double.isNaN(travel) || Double.isNaN(event.anchor())) {
                    return;
                }
                onResize.to(column, Math.max(TableHead.MINIMUM_WIDTH, event.anchor() + travel));
                event.consume();
            }
            case PRESSED, RELEASED, CLICKED -> event.consume();
            default -> {}
        }
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        return Box.of().style(style).cursor(Cursor.EW_RESIZE);
    }
}
