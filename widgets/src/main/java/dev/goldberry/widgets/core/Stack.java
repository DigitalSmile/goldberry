package dev.goldberry.widgets.core;

import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.ComputedStyle;
import dev.goldberry.kdl.KdlNode;
import dev.goldberry.layout.Position;
import dev.goldberry.paint.Box;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributed;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widget.style.Paints;
import dev.goldberry.widget.style.Styled;
import dev.goldberry.widgets.markup.Markup;
import dev.goldberry.widgets.markup.Wiring;

/// Children drawn on top of one another: a badge on an avatar, an overlay on a
/// picture.
///
/// ```kdl
/// stack id="avatar" {
///     panel class="portrait" { text "GB" }
///     badge class="info" "3"
/// }
/// ```
///
/// `new Stack(Widget...)` and `new Stack(List<Widget>, Attributes)` are the two
/// constructors.
///
/// The first child stays in flow and gives the stack its size; every child
/// after it is `position: absolute`, so adding an overlay cannot move or resize
/// the thing it sits on. A stack of one child is that child in a box.
///
/// Where an overlay lands is the stylesheet's. An absolute child with no inset
/// is placed by the stack's `align-items` and `justify-content` and by its own
/// `align-self`, so a badge goes to a corner with two declarations:
///
/// ```css
/// #avatar       { align-items: flex-start; justify-content: flex-start }
/// #avatar badge { align-self: flex-end }
/// ```
///
/// A child that wants a specific offset says so with `top`, `right` and the
/// other insets; zero pins it to the stack's edge. Later children draw over
/// earlier ones, which is the painter's rule for siblings and all a stack
/// promises about order.
///
/// Read more: [Stack](https://goldberry.dev/docs/layout/stack.html#stack).
@Markup("stack")
public record Stack(List<Widget> children, Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Attributed<Stack> {

    public Stack(Widget... kids) {
        this(List.of(kids), Attributes.NONE);
    }

    /// Written out so that the parameters that take null for a default can say so.
    public Stack(@Nullable List<Widget> children, Attributes attributes) {
        children = List.copyOf(children == null ? List.of() : children);
        this.children = children;
        this.attributes = attributes;
    }

    @Override
    public Stack withAttributes(Attributes attributes) {
        return new Stack(children, attributes);
    }

    @Override
    public List<Widget> children() {
        return children;
    }

    @Override
    public @Nullable String id() {
        return attributes.id();
    }

    @Override
    public Set<String> classes() {
        return attributes.classes();
    }

    @Override
    public @Nullable Object key() {
        return attributes.key();
    }

    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        var laid = new Box[boxes.size()];
        for (var i = 0; i < boxes.size(); i++) {
            // The first stays in flow because something has to give the stack a
            // size; everything after it is taken out so that adding an overlay
            // cannot resize what it sits on.
            laid[i] = i == 0 ? boxes.get(i) : boxes.get(i).position(Position.ABSOLUTE);
        }
        return Box.of().children(laid).style(style);
    }

    /// Builds a `stack` from markup.
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        return new Stack(children, Attributes.of(node));
    }
}
