package io.github.digitalsmile.goldberry.widgets.core.scroll;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.Transform;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.natives.yoga.FlexDirection;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;

/// The box a [Scroll] moves — everything written inside the viewport, as one
/// node that can be translated.
///
/// ## Why the content moves rather than the viewport
///
/// Because a translate is the one way to move something that costs no layout.
/// §1.7 is explicit that "layout properties never transition — animating
/// width/height would run Yoga per frame", and the same arithmetic applies to a
/// scroll: an offset expressed as `top` or as a margin would re-run Yoga over the
/// whole subtree on every wheel notch, sixty times a second, to move a box that
/// did not change size. A `transform` is resolved by the painter, after layout,
/// and the tree underneath it is untouched
/// ([ADR-0116](../../../../../../../../book/src/adr/0116-a-scroll-view-is-a-clip-an-offset-and-two-extents.md)).
///
/// It is also what makes hit testing come out right for free. The painter carries
/// the accumulated matrix and the router inverts it
/// ([ADR-0068](../../../../../../../../book/src/adr/0068-the-transform-stack-is-java-side.md)),
/// so a row scrolled up by 200px is clicked where it *looks*, with nothing in the
/// scroll view arranging that.
///
/// ## Why it does not shrink
///
/// `flex-shrink: 0`, which is the whole difference between a scroll view and a
/// squashed one. Yoga's default is to shrink a child that does not fit, so
/// content in a too-short viewport would be compressed to fit it and there would
/// be nothing to scroll — the overflow this widget exists to move would have been
/// negotiated away before it was measured.
///
/// The translation is applied through [Styled#restyle], not in [#render], so it
/// is part of what the renderer observes and a programmatic scroll can be given a
/// `transition` later without moving anything (§3.1 gives `scroll` "wheel/drag:
/// direct · `scrollIntoView` / programmatic: overlay duration").
///
/// @param children what was written inside the `scroll`
/// @param axis     which way the parent viewport moves
/// @param offsetX  how far it has been scrolled right, in logical pixels
/// @param offsetY  how far down
record ScrollContent(List<Widget> children, ScrollAxis axis, double offsetX, double offsetY)
        implements Widget.Leaf, Styled, Paints {

    ScrollContent {
        children = List.copyOf(children == null ? List.of() : children);
    }

    @Override
    public List<Widget> children() {
        return children;
    }

    @Override
    public ComputedStyle restyle(ComputedStyle resolved) {
        // `flex-shrink: 0` is applied here rather than in `render` because it is
        // the cascade's property and `Box` has no wither for it -- and because a
        // stylesheet must not be able to set it back to 1, which is the one
        // declaration that would silently turn this widget into a `column`.
        var style = resolved.flexShrink(0);
        if (offsetX == 0 && offsetY == 0) {
            // The overwhelmingly common case, and worth the branch: an unscrolled
            // viewport should put no transform on the painter's context at all.
            return style;
        }
        // Negative, because scrolling *down* moves the content *up*.
        return style.transform(Transform.of(new Transform.Function.Translate(
                Transform.Length.px(-offsetX), Transform.Length.px(-offsetY))));
    }

    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        return Box.of().children(boxes.toArray(Box[]::new)).style(style)
                // Along the axis being scrolled. A column for a vertical
                // viewport, because several children written inside one are a
                // document and stack -- and a **row** for a horizontal one,
                // where a column would be a single stack of items with nothing
                // to scroll sideways past.
                .direction(axis == ScrollAxis.HORIZONTAL
                        ? FlexDirection.ROW : FlexDirection.COLUMN);
    }
}
