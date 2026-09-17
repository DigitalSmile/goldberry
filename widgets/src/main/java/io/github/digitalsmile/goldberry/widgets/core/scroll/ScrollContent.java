package io.github.digitalsmile.goldberry.widgets.core.scroll;

import java.util.List;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.value.Transform;
import io.github.digitalsmile.goldberry.layout.FlexDirection;
import io.github.digitalsmile.goldberry.layout.Insets;
import io.github.digitalsmile.goldberry.layout.Length;
import io.github.digitalsmile.goldberry.log.Logs;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

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
/// (ADR-0116).
///
/// It is also what makes hit testing come out right for free. The painter carries
/// the accumulated matrix and the router inverts it
/// (ADR-0068),
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
/// ## Why a child asking to grow hears about it
///
/// A scroll view's content box is as tall as its content **by construction** —
/// that is what the paragraph above is about — so a child that writes
/// `flex-grow: 1` inside one is asking for a share of remaining space that does
/// not exist, and gets none. Correct, and completely silent: the showcase carried
/// the declaration on five screens where it did nothing and on one where it was
/// load-bearing, which is exactly how long it takes for a dead declaration to
/// look like a live one.
///
/// [#warnIfAChildAsksToGrow] is the diagnostic, and it is only a diagnostic —
/// the arrangement still works, it merely does nothing. The growth belongs on the
/// `scroll` box itself ([ADR-0116], [ADR-0257]).
///
/// @param children what was written inside the `scroll`
/// @param axis     which way the parent viewport moves
/// @param offsetX  how far it has been scrolled right, in logical pixels
/// @param offsetY  how far down
/// @param gutter   the width reserved for a bar, added to the content's padding on
///                 the side the bar is on; 0 for overlay bars (ADR-0364)
record ScrollContent(List<Widget> children, ScrollAxis axis, double offsetX, double offsetY, double gutter)
        implements Widget.Leaf, Styled, Paints {

    private static final org.slf4j.Logger LOG = Logs.of(ScrollContent.class);

    /// Which axes have already been told that a child inside them asks to grow.
    ///
    /// `render` runs per element per paint, so an unguarded warning here would be
    /// the log [ADR-0243] has just finished quietening — sixty lines a second for
    /// as long as the screen is up. Static and by axis for
    /// [ScrollState#REPORTED_NESTING]'s reason: what is worth saying is *"this
    /// application puts `flex-grow` inside a scroller"*, and a document that does
    /// it on five screens has one mistake rather than five.
    private static final java.util.Set<ScrollAxis> REPORTED_GROW = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /// Forgets what has been reported, for a test that drives the same
    /// arrangement twice. `ComputedStyle.forgetReportedDrops`'s reason exactly.
    static void forgetReportedGrow() {
        REPORTED_GROW.clear();
    }

    /// How many axes have been reported, so a test can say *once* rather than
    /// merely *at all*. There is no appender on the classpath here to read the
    /// log back from, so the set is what an assertion can see.
    static int reportedGrowCount() {
        return REPORTED_GROW.size();
    }

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
        if (gutter > 0) {
            // Layout, not overlay: the bar's side of the content is padded by the
            // gutter, so nothing is drawn under a bar that is always there.
            var padding = style.padding();
            style = style.padding(new Insets(
                    padding.top(),
                    axis.isVertical() ? add(padding.right(), gutter) : padding.right(),
                    axis.isHorizontal() ? add(padding.bottom(), gutter) : padding.bottom(),
                    padding.left()));
        }
        if (offsetX == 0 && offsetY == 0) {
            // The overwhelmingly common case, and worth the branch: an unscrolled
            // viewport should put no transform on the painter's context at all.
            return style;
        }
        // Negative, because scrolling *down* moves the content *up*.
        return style.transform(Transform.of(
                new Transform.Function.Translate(Transform.Length.px(-offsetX), Transform.Length.px(-offsetY))));
    }

    /// `length` plus `extra` points, when `length` is a number of points; the
    /// extra alone otherwise, which is what a padding a stylesheet left unset is.
    private static Length add(Length length, double extra) {
        return length instanceof Length.Points(var value)
                ? Length.points((float) (value + extra))
                : Length.points((float) extra);
    }

    /// Says once that a child asking to grow inside a scroller will not.
    ///
    /// Read off the **boxes** rather than off the cascade, which is what makes it
    /// exact and cheap: `flex-grow` has already been resolved by the time `render`
    /// is handed its children, so this is a field comparison and not a second
    /// question for the style engine. It also catches a widget that set the
    /// growth itself, which no rule in any stylesheet would have shown.
    ///
    /// **Whatever the axis**, because the content box's main axis *is* the
    /// scrolling axis by construction: [#render] sets `row` for a horizontal
    /// viewport and `column` for the other two, so a child's `flex-grow` is
    /// always about the direction that is unbounded. `BOTH` is a column here and
    /// behaves as `VERTICAL` does, which is what makes one check cover all three.
    ///
    /// It stops at the first one. What is worth saying is that this arrangement
    /// does nothing, and a row of six growing children is one mistake.
    private void warnIfAChildAsksToGrow(List<Box> boxes) {
        for (var box : boxes) {
            if (box.flexGrow() > 0) {
                if (REPORTED_GROW.add(axis)) {
                    LOG.warn(
                            "a child of a {} `scroll` declares flex-grow; it will get nothing,"
                                    + " because a scroll view's content box is as tall as its content"
                                    + " and there is no remaining space to share. Put the growth on"
                                    + " the `scroll` box instead.",
                            axis.toString().toLowerCase(java.util.Locale.ROOT));
                }
                return;
            }
        }
    }

    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        warnIfAChildAsksToGrow(boxes);
        return Box.of()
                .children(boxes.toArray(Box[]::new))
                .style(style)
                // Along the axis being scrolled. A column for a vertical
                // viewport, because several children written inside one are a
                // document and stack -- and a **row** for a horizontal one,
                // where a column would be a single stack of items with nothing
                // to scroll sideways past.
                .direction(axis == ScrollAxis.HORIZONTAL ? FlexDirection.ROW : FlexDirection.COLUMN);
    }
}
