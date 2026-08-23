package io.github.digitalsmile.goldberry.widgets.overlay.toast;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Corner;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

import java.util.List;
import java.util.Set;
import java.util.function.DoubleConsumer;

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
/// @param onFrame  told the frame clock on every frame — [ToasterState] needs a
///                 clock to pause a timeout with, and `render` is the only place
///                 a widget is given one
record ToasterBox(List<Widget> children, Corner corner, DoubleConsumer onFrame)
        implements Widget.Leaf, Styled, Paints {

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
        onFrame.accept(context.nowMillis());
        return Box.of().style(style).children(boxes.toArray(Box[]::new));
    }
}
