package io.github.digitalsmile.goldberry.widgets.panel.tree;

import java.util.List;
import java.util.Set;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.FocusScope;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;

/// The node a stylesheet calls `tree`: the column the rows are stacked in.
///
/// [Tree] is stateful and styles nothing, so this carries the CSS type and the
/// `id` and classes the document wrote — the arrangement every stateful widget in
/// this catalog uses.
///
/// [FocusScope#VERTICAL], which is most of §3's keyboard: `Up` and `Down` rove
/// between the **visible** rows, and the tree is one Tab stop from outside
/// (§7.2). `Right` and `Left` are the rows' own, because only a row knows whether
/// it is open.
///
/// Horizontal roving is absent for `menu`'s reason, and here it is load-bearing
/// rather than incidental: `Left` and `Right` are what a tree navigates *with*,
/// so a scope that took them would take the widget's whole keyboard.
///
/// @param children the visible rows, already flattened depth-first
record TreeBox(List<Widget> children, Attributes attributes) implements Widget.Leaf, Styled, Paints, Handles {

    @Override
    public String cssType() {
        return "tree";
    }

    @Override
    public String id() {
        return attributes.id();
    }

    @Override
    public Set<String> classes() {
        return attributes.classes();
    }

    @Override
    public FocusScope focusScope() {
        return FocusScope.VERTICAL;
    }

    @Override
    public List<Widget> children() {
        return children;
    }

    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        return Box.of().style(style).children(boxes.toArray(Box[]::new));
    }
}
