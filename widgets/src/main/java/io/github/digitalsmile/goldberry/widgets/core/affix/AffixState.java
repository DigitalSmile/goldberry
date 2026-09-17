package io.github.digitalsmile.goldberry.widgets.core.affix;

import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;

/// How far an [Affix] has lifted, and whether it has lifted at all.
///
/// One number and one flag, both derived from the two rectangles the router hands
/// over once a frame ([ADR-0119]).
final class AffixState extends State<Affix> {

    /// How far the content is translated from where it was laid out, along the
    /// edge's axis and along the cross edge's. Zero when the affix is where the
    /// layout put it, which is most of the time.
    private double shift;

    private double crossShift;

    /// Whether it has lifted, for `:affixed`.
    private boolean affixed;

    @Override
    public Widget build(BuildContext context) {
        var affix = widget();
        var vertical = affix.edge().isVertical();
        return new AffixSlot(
                affix.children(),
                vertical ? crossShift : shift,
                vertical ? shift : crossShift,
                affixed,
                this::located,
                affix.attributes());
    }

    /// Told where the last frame put this affix, what clips it, and the box it
    /// must stay inside.
    ///
    /// The whole of the widget's logic, and it is one subtraction per edge.
    ///
    /// `self` is where the affix **would be** if it did not move, because the
    /// translation is on a child and this is the outer node — which is exactly
    /// what makes the arithmetic stable rather than cumulative.
    private void located(LogicalRect self, LogicalRect clip, LogicalRect container) {
        var affix = widget();
        if (affix.onReveal() != null) {
            // The hole's rectangle, which is what `self` is — this is the outer
            // node and it never moves itself. A caller measuring the *content*
            // would be measuring something pinned to the viewport's edge, which
            // reads as already visible however far away its section is
            // ([ADR-0124]).
            affix.onReveal().accept(self, clip);
        }
        var along = shiftFor(affix.edge(), affix.offset(), self, clip, container);
        var across = affix.cross() == null ? 0 : shiftFor(affix.cross(), affix.offset(), self, clip, container);
        var lifted = along != 0 || across != 0;
        if (along == shift && across == crossShift && lifted == affixed) {
            return;
        }
        setState(() -> {
            shift = along;
            crossShift = across;
            affixed = lifted;
        });
    }

    /// The signed translation that pins an affix to `edge`: zero while the
    /// layout already has it in view, and otherwise how far past the edge it has
    /// gone, limited to the room left in its container.
    ///
    /// One subtraction per edge, and independent per axis, which is what lets an
    /// affix pin to a vertical and a horizontal edge at once (ADR-0371).
    static double shiftFor(Edge edge, double offset, LogicalRect self, LogicalRect clip, LogicalRect container) {
        // How far past the edge the affix has gone. Positive means it has
        // scrolled out of view and must be pulled back; zero or less means the
        // layout has it in the right place already and it should not move.
        var past =
                switch (edge) {
                    case TOP -> clip.top() + offset - self.top();
                    case LEFT -> clip.left() + offset - self.left();
                    // The far edges are the mirror image: the affix's *bottom* against
                    // the viewport's, and the shift is negative because pinning up means
                    // moving the content towards the origin.
                    case BOTTOM ->
                        (self.top() + self.size().height())
                                - (clip.top() + clip.size().height() - offset);
                    case RIGHT ->
                        (self.left() + self.size().width())
                                - (clip.left() + clip.size().width() - offset);
                };
        if (past <= 0.5) {
            return 0;
        }
        // CSS's sticky rule: the content never leaves its container. How far it
        // may travel is the room between the hole's far side and the
        // container's, so a section header is carried out by its own section
        // when the next one arrives rather than sitting over it (ADR-0360).
        var room =
                switch (edge) {
                    case TOP ->
                        container.top()
                                + container.size().height()
                                - (self.top() + self.size().height());
                    case LEFT ->
                        container.left()
                                + container.size().width()
                                - (self.left() + self.size().width());
                    case BOTTOM -> self.top() - container.top();
                    case RIGHT -> self.left() - container.left();
                };
        var travel = Math.min(past, Math.max(0, room));
        return edge.isNear() ? travel : -travel;
    }
}
