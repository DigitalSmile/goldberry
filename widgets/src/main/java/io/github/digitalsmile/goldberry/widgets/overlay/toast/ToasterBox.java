package io.github.digitalsmile.goldberry.widgets.overlay.toast;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.natives.yoga.style.StyleLength;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Corner;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

import java.util.List;
import java.util.Set;

/// The node a stylesheet calls `toaster`: the column the toasts are stacked in.
///
/// [Toaster] is stateful and styles nothing, so this carries the CSS type — the
/// arrangement every stateful widget in this catalog uses. It has no `id`,
/// because nothing put it anywhere by name: [Toasts#at] made it.
///
/// **The corner is a class**, so which way the column grows is a rule rather than
/// a branch: a stack at the bottom of the window puts its newest toast nearest
/// the corner, which is the *end* of the column, and one at the top puts it at
/// the start. `controls.css` says that with `column` and `column-reverse`, and
/// this node describes its children oldest-first either way.
///
/// @param children the toasts, oldest first
/// @param corner   which corner the stack is in
/// @param onFrame  told the two numbers only `render` has — see [OnFrame]
record ToasterBox(List<Widget> children, Corner corner, OnFrame onFrame)
        implements Widget.Leaf, Styled, Paints {

    /// What the stack is told on every frame.
    ///
    /// Two numbers, and they are here together because they are the same kind of
    /// thing: a reading a widget can take **only in `render`**, because that is
    /// the only place it is handed the frame clock and the only place it is
    /// handed the style the cascade resolved for it.
    ///
    ///   - **the clock**, which is what pauses and resumes a timeout — `Host.after`
    ///     gives a timer and no way to ask how much of it has run
    ///     (ADR-0177);
    ///   - **the gap**, because the distance a surviving toast travels when one of
    ///     them goes is the departed one's height *plus the space it was keeping*.
    ///     Read rather than assumed: a stylesheet that changed `toaster { gap }`
    ///     and nothing else would otherwise leave the whole stack reflowing to
    ///     somewhere it is not
    ///     (ADR-0178).
    interface OnFrame {

        /// @param nowMillis the renderer's clock, not the wall one
        /// @param gap       `toaster`'s resolved `gap`, in logical pixels
        void frame(double nowMillis, double gap);
    }

    @Override
    public String cssType() {
        return "toaster";
    }

    @Override
    public Set<String> classes() {
        return Set.of(corner.cssName());
    }

    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        // A percentage gap would be a fraction of a column whose height is the
        // sum of its children, which is circular -- so `toaster` writes pixels
        // and anything else reads as none rather than as a guess.
        onFrame.frame(context.nowMillis(),
                style.gap() instanceof StyleLength.Points(var points) ? points : 0);
        return Box.of().style(style).children(boxes.toArray(Box[]::new));
    }
}
