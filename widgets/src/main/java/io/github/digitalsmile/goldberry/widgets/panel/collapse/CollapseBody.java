package io.github.digitalsmile.goldberry.widgets.panel.collapse;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.Transform;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import java.util.Set;

/// What a [Collapse] shows when it is open — §5's "region".
///
/// A node of its own rather than the author's children going straight into the
/// section, so that a stylesheet can indent the body under the header without
/// also indenting the header. `slider`'s anatomy argument again: two boxes were
/// doing one job.
///
/// **It only exists while the section is open.** `CollapseSection` does not
/// describe one otherwise, so there is no element, nothing mounted and nothing
/// subscribed — see [Collapse]'s note.
///
/// ## It arrives, and it does not leave
///
/// §5 forbids animating the height and always will: height is not on §1.7's
/// whitelist, precisely so a transition can never cost a reflow. So the body
/// appears at its **full height** and fades up into it with a small translation —
/// which is `tab`'s arrival exactly, and for the same reason: a newly built
/// element has no previous style for the cascade to interpolate from, so this is
/// a function of the frame clock rather than a transition
/// ([io.github.digitalsmile.goldberry.widgets.core.Phase]).
///
/// Closing is **instant**, and asymmetric on purpose: the body's absence is this
/// widget's whole claim, and holding a subtree alive for 160ms after it has been
/// asked to go away would be exactly the thing §5 says a `collapse` does not do.
record CollapseBody(List<Widget> children, java.util.function.DoubleUnaryOperator visibility)
        implements Widget.Leaf, Styled, Paints {

    /// How far the arriving body travels, in logical pixels. A `tab`'s 6: this is
    /// a settle into place, not an entrance.
    private static final double TRAVEL = 6;

    CollapseBody {
        children = List.copyOf(children == null ? List.of() : children);
    }

    @Override
    public String cssType() {
        return "collapse-body";
    }

    @Override
    public Set<String> classes() {
        return Set.of();
    }

    @Override
    public List<Widget> children() {
        return children;
    }

    /// Whether the arrival is still running, which is what keeps the frame loop
    /// awake for the length of one. A section that has been open a while asks for
    /// nothing.
    @Override
    public boolean isAnimating() {
        return visibility != null;
    }

    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        var box = Box.of().style(style).children(boxes.toArray(Box[]::new));
        if (visibility == null) {
            return box;
        }
        // Reading it is what starts the arrival -- the phase is stamped from the
        // frame clock on its first read, and this is the only place there is one.
        var visible = context.reducedMotion() ? 1 : visibility.applyAsDouble(
                context.nowMillis());
        if (visible >= 1) {
            return box;
        }
        // Down from above, which is the direction the section opened.
        return box.opacity(visible)
                .transform(Transform.of(new Transform.Function.Translate(
                        Transform.Length.ZERO, Transform.Length.px(-(1 - visible) * TRAVEL))));
    }
}
