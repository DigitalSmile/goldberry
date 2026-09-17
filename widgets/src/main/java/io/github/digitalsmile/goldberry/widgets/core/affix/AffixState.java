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

    /// How far the content is translated from where it was laid out. Zero when
    /// the affix is where the layout put it, which is most of the time.
    private double shift;

    /// Whether it has lifted, for `:affixed`.
    private boolean affixed;

    @Override
    public Widget build(BuildContext context) {
        var affix = widget();
        return new AffixSlot(affix.children(), affix.edge(), shift, affixed, this::located, affix.attributes());
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
        var offset = affix.offset();
        // How far past the edge the affix has gone. Positive means it has
        // scrolled out of view and must be pulled back; zero or less means the
        // layout has it in the right place already and it should not move.
        var past =
                switch (affix.edge()) {
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
        var lifted = past > 0.5;
        // CSS's sticky rule: the content never leaves its container. How far it
        // may travel is the room between the hole's far side and the
        // container's, so a section header is carried out by its own section
        // when the next one arrives rather than sitting over it (ADR-0360).
        var room =
                switch (affix.edge()) {
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
        var travel = lifted ? Math.min(past, Math.max(0, room)) : 0;
        var wanted = !lifted ? 0 : affix.edge().isNear() ? travel : -travel;
        if (wanted == shift && lifted == affixed) {
            return;
        }
        setState(() -> {
            shift = wanted;
            affixed = lifted;
        });
    }
}
