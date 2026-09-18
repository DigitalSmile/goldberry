package io.github.digitalsmile.goldberry.widgets.overlay.tour;

import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Insets;
import io.github.digitalsmile.goldberry.layout.Length;
import io.github.digitalsmile.goldberry.layout.Position;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// The dim over everything except the widget being described — §5's "the window
/// dims outside the target with a `veil` cut to its rect".
///
/// ## Four rectangles, not one with a hole
///
/// §8's subset has no path, no mask and no `clip-path`, so there is no way to
/// state "this box, minus that rectangle". Four absolutely-positioned bands need
/// none of it: above the target, below it, and the two beside it between those
/// two — which tile the window exactly and leave the target uncovered
/// (ADR-0121).
///
/// The consequence is better than the workaround it started as. **The target
/// stays live**: nothing is drawn over it, so it takes the pointer and the
/// keyboard normally, and a tour that says "click Save to continue" can be obeyed
/// without the tour having to arrange an exception to its own veil. A single
/// masked rectangle would have had to.
///
/// @param target what to leave uncovered, or null to dim everything
/// @param window the rectangle to fill — the veil's own, since a band's size is
///               stated in pixels and Yoga has no `100%` minus anything
record TourVeil(
        @Nullable LogicalRect target,
        @Nullable LogicalRect cameFrom,
        io.github.digitalsmile.goldberry.widgets.core.@Nullable Phase travel,
        LogicalRect window)
        implements Widget.Leaf, Styled, Paints {

    /// A veil with nothing to travel from — every caller before §3.1's stop
    /// change was built ([ADR-0269]).
    TourVeil(@Nullable LogicalRect target, LogicalRect window) {
        this(target, null, null, window);
    }

    /// Frames are owed while the hole is travelling.
    ///
    /// `AnimationSweepTest` is what says so: a widget that holds a `Phase` and
    /// does not answer it is a widget painted once at whatever the loop caught
    /// and left there. It found this one the moment the veil grew a phase
    /// ([ADR-0269]).
    @Override
    public boolean isAnimating() {
        return travel != null && travel.isRunning();
    }

    @Override
    public String cssType() {
        return "tour-veil";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public List<Widget> children() {
        return List.of(new TourBand(), new TourBand(), new TourBand(), new TourBand());
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        // Handed down from [TourStop], which is the node that was measured. A
        // box sized by absolute insets carries no `width` in its style, so there
        // is nothing here to read it from (ADR-0121).
        var width = window.size().width();
        var height = window.size().height();
        // Where the hole is on this frame — [Lit], which [TourStop] asks the same
        // question of for the ring and the card.
        var target = Lit.rectAt(cameFrom, this.target, travel, context.nowMillis());
        if (target == null || target.size().width() <= 0 || target.size().height() <= 0) {
            // Nothing to cut around: one band covering everything, and the other
            // three collapsed. A tour between stops looks like a dimmed window
            // rather than flashing to clear.
            return Box.of()
                    .style(style)
                    .children(
                            band(children.get(0), 0, 0, width, height),
                            collapsed(children.get(1)),
                            collapsed(children.get(2)),
                            collapsed(children.get(3)));
        }
        var left = target.left();
        var top = target.top();
        var right = left + target.size().width();
        var bottom = top + target.size().height();
        return Box.of()
                .style(style)
                .children(
                        // Above and below span the full width; the two sides fill only
                        // the gap between them, so the four tile the window with no
                        // overlap -- which matters because they are translucent and a
                        // doubled band would be visibly darker.
                        band(children.get(0), 0, 0, width, Math.max(0, top)),
                        band(children.get(1), 0, bottom, width, Math.max(0, height - bottom)),
                        band(children.get(2), 0, top, Math.max(0, left), Math.max(0, bottom - top)),
                        band(children.get(3), right, top, Math.max(0, width - right), Math.max(0, bottom - top)));
    }

    private static Box band(Box box, double x, double y, double width, double height) {
        return box.position(Position.ABSOLUTE)
                // CSS order: top, right, bottom, left.
                .inset(new Insets(
                        Length.points((float) y), Length.UNDEFINED, Length.UNDEFINED, Length.points((float) x)))
                .size(Length.points((float) width), Length.points((float) height));
    }

    private static Box collapsed(Box box) {
        return band(box, 0, 0, 0, 0);
    }
}
