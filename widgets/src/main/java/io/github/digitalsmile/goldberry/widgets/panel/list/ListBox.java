package io.github.digitalsmile.goldberry.widgets.panel.list;

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

/// The node a stylesheet calls `list`: the column the rows are stacked in.
///
/// [ListView] is stateful and styles nothing, so this carries the CSS type and
/// the `id` and classes the application wrote — the arrangement every stateful
/// widget in this catalog uses.
///
/// [FocusScope#VERTICAL], which is most of §10's keyboard: `Up` and `Down` rove
/// between the rows, and the list is one Tab stop from outside (§7.2).
/// `Home`, `End` and the typeahead are the rows' own for [ListRow]'s reason —
/// each needs rows the focused one cannot see.
///
/// Horizontal roving is absent for `menu`'s reason: a list is one column, and
/// `Left` and `Right` are not its to take. Unlike a `tree`, which navigates
/// *with* them, a list simply has nothing to say to either — so an application
/// that puts something horizontal in a row keeps its own keys.
///
/// ## It is [Located] when it is virtual, and not otherwise
///
/// "Which rows can be seen" is a comparison between where this node was painted
/// and what clips it — two positions no widget can compute, which is exactly what
/// [Located] exists to hand over ([ADR-0119]). The notification is refused when
/// `onLocated` is null, so a list that builds every row costs the router nothing
/// per frame.
///
/// **This node never moves in answer to it**, which is the rule that makes it
/// terminate: the spacers and the rows always add up to the same total height,
/// because that total is a function of the *model* rather than of the window
/// (ADR-0213).
/// So the frame after a window change reports the same rectangle as the frame
/// before it, and the second report changes nothing.
///
/// @param children  the rows, in the model's order, with the spacers around them
/// @param onLocated where the frame put this node and what clips it, or null when
///                  the list is not virtual
record ListBox(
        List<Widget> children,
        java.util.function.BiConsumer<
                        io.github.digitalsmile.goldberry.render.model.LogicalRect,
                        io.github.digitalsmile.goldberry.render.model.LogicalRect>
                onLocated,
        Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Handles, io.github.digitalsmile.goldberry.input.handler.Located {

    @Override
    public String cssType() {
        return "list";
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
    public void located(
            io.github.digitalsmile.goldberry.render.model.LogicalRect self,
            io.github.digitalsmile.goldberry.render.model.LogicalRect clip) {

        if (onLocated != null) {
            onLocated.accept(self, clip);
        }
    }

    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        return Box.of().style(style).children(boxes.toArray(Box[]::new));
    }

    /// The rows outside the window, as a height and nothing else.
    ///
    /// What makes the arithmetic terminate: a spacer's height is
    /// `rowCount × rowHeight`, so however the window moves the column adds up to
    /// the same total and the node that was measured does not move
    /// (ADR-0213).
    ///
    /// Two of them rather than one padded box, because the one above and the one
    /// below answer different questions — how far down the window starts, and how
    /// much is left under it — and a single `padding` could not say both.
    ///
    /// It draws nothing and hits nothing. A stylesheet can still see it, because
    /// a part that could not be selected would be a hole in §11's parity claim.
    record ListSpacer(double height) implements Widget.Leaf, Styled, Paints {

        @Override
        public String cssType() {
            return "list-spacer";
        }

        @Override
        public Set<String> classes() {
            return Set.of();
        }

        @Override
        public Box render(ComputedStyle style, List<Box> children, Context context) {
            return Box.of()
                    .style(style)
                    .size(
                            io.github.digitalsmile.goldberry.layout.Length.UNDEFINED,
                            io.github.digitalsmile.goldberry.layout.Length.points((float) height));
        }
    }
}
