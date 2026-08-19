package io.github.digitalsmile.goldberry.widgets.overlay.tour;

import io.github.digitalsmile.goldberry.backend.LogicalRect;
import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.natives.yoga.Insets;
import io.github.digitalsmile.goldberry.natives.yoga.PositionType;
import io.github.digitalsmile.goldberry.natives.yoga.StyleLength;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Set;

/// The dim over everything except the widget being described — §5's "the window
/// dims outside the target with a `veil` cut to its rect".
///
/// ## Four rectangles, not one with a hole
///
/// §8's subset has no path, no mask and no `clip-path`, so there is no way to
/// state "this box, minus that rectangle". Four absolutely-positioned bands need
/// none of it: above the target, below it, and the two beside it between those
/// two — which tile the window exactly and leave the target uncovered
/// ([ADR-0121](../../../../../../../../book/src/adr/0121-a-tour-is-a-veil-and-a-sequence.md)).
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
record TourVeil(LogicalRect target, LogicalRect window)
        implements Widget.Leaf, Styled, Paints {

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

    private static float points(StyleLength length) {
        return length instanceof StyleLength.Points p ? p.value() : 0;
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        // The veil's own resolved size, not the `window` component: this node is
        // inset to all four sides of the tour, so the cascade has already worked
        // out how big the window is and asking twice would be asking two
        // different things to agree.
        var width = points(style.width()) > 0 ? points(style.width()) : window.size().width();
        var height = points(style.height()) > 0 ? points(style.height()) : window.size().height();
        if (target == null || target.size().width() <= 0 || target.size().height() <= 0) {
            // Nothing to cut around: one band covering everything, and the other
            // three collapsed. A tour between stops looks like a dimmed window
            // rather than flashing to clear.
            return Box.of().style(style).children(
                    band(children.get(0), 0, 0, width, height),
                    collapsed(children.get(1)), collapsed(children.get(2)),
                    collapsed(children.get(3)));
        }
        var left = target.left();
        var top = target.top();
        var right = left + target.size().width();
        var bottom = top + target.size().height();
        return Box.of().style(style).children(
                // Above and below span the full width; the two sides fill only
                // the gap between them, so the four tile the window with no
                // overlap -- which matters because they are translucent and a
                // doubled band would be visibly darker.
                band(children.get(0), 0, 0, width, Math.max(0, top)),
                band(children.get(1), 0, bottom, width, Math.max(0, height - bottom)),
                band(children.get(2), 0, top, Math.max(0, left), Math.max(0, bottom - top)),
                band(children.get(3), right, top, Math.max(0, width - right),
                        Math.max(0, bottom - top)));
    }

    private static Box band(Box box, double x, double y, double width, double height) {
        return box.position(PositionType.ABSOLUTE)
                .inset(new Insets(
                        StyleLength.points((float) x), StyleLength.UNDEFINED,
                        StyleLength.points((float) y), StyleLength.UNDEFINED))
                .size(StyleLength.points((float) width), StyleLength.points((float) height));
    }

    private static Box collapsed(Box box) {
        return band(box, 0, 0, 0, 0);
    }
}
