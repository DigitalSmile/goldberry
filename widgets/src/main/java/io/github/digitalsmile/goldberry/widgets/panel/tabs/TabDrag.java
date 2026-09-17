package io.github.digitalsmile.goldberry.widgets.panel.tabs;

import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.Widget;

/// A tab header that can be dragged to a new place — a composition node around
/// the [Tab] with no CSS type, so a stylesheet sees the tab exactly as before
/// (ADR-0372).
///
/// It hears the pointer after the tab, which consumes only the click, so a press
/// and a release that did not travel still select. Past [#SLOP] points of
/// horizontal travel the gesture is a drag: every move reports where the pointer
/// is, and the release reports the drop.
///
/// @param tab   the header, already wired and, while dragged, offset
/// @param value the tab's value
/// @param drag  what a drag reports to
record TabDrag(Tab tab, String value, Listener drag) implements Widget.Stateless, Handles {

    /// How far a press must travel along the row before it is a drag rather than
    /// a click — `toggle`'s and `knob`'s click slop.
    static final double SLOP = 4;

    /// What the strip hears.
    interface Listener {

        /// The pointer, `dx` along the row from where this tab was pressed, is at
        /// `pointerX` in the window.
        void moved(String value, double dx, double pointerX);

        /// The press that started the drag was released.
        void dropped(String value);

        /// Whether a drag of this tab is under way.
        boolean isDragging(String value);
    }

    @Override
    public Object key() {
        return tab.key();
    }

    @Override
    public Widget build(BuildContext context) {
        return tab;
    }

    @Override
    public void onPointer(PointerEvent event) {
        switch (event.kind()) {
            case MOVED -> {
                var dx = event.dragX();
                if (Double.isNaN(dx) || (!drag.isDragging(value) && Math.abs(dx) < SLOP)) {
                    return;
                }
                drag.moved(value, dx, event.x());
                event.consume();
            }
            case RELEASED -> {
                if (drag.isDragging(value)) {
                    drag.dropped(value);
                }
            }
            default -> {}
        }
    }
}
