package io.github.digitalsmile.goldberry.widgets.core;

import java.util.List;
import java.util.Set;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.natives.yoga.style.PositionType;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributed;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;
import io.github.digitalsmile.goldberry.widgets.markup.Markup;
import io.github.digitalsmile.goldberry.widgets.markup.Wiring;

/// Children drawn on top of one another — `docs/core-widgets.md` §1's `stack`,
/// and §1's "basis for badges-over-things and custom overlays" ([ADR-0250]).
///
/// ```kdl
/// stack {
///     image src="avatar.png"
///     badge "3"
/// }
/// ```
///
/// ## The first child sizes it and the rest float over it
///
/// A stack has to get its size from *something*, and a box whose children are all
/// out of flow is a box of nothing — so the first child stays **in flow** and
/// every one after it is `position: absolute`. That is the arrangement the name
/// implies: the thing, and then what goes on top of it. A stack of one child is
/// exactly that child in a box, which is what makes adding an overlay to an
/// existing widget a change that cannot move it.
///
/// ## Where an overlay lands is the stylesheet's, and it already worked
///
/// Nothing here positions anything. An absolute child with **no inset** is
/// placed by its container's `align-items` and `justify-content`, and by its own
/// `align-self` ([ADR-0244]) — so a badge goes to a corner with two declarations
/// and no arithmetic:
///
/// ```css
/// stack       { align-items: flex-start; justify-content: flex-start }
/// stack badge { align-self: flex-start }
/// ```
///
/// This is the case [ComputedStyle#INITIAL] has been describing since before
/// anything could reach it: an inset of zero pins a node to its container's edge,
/// "no inset at all" is `undefined`, and *"the difference only shows on an
/// absolute node — where zero would stretch it and undefined leaves it where the
/// alignment put it"*. `stack` is the widget that finally shows it.
///
/// An overlay that wants a **specific** offset says so with `inset` instead, which
/// is the other half of §1's sentence and needs nothing from this class either.
///
/// ## Z-order is document order
///
/// Later children draw over earlier ones, which is the painter's rule for
/// siblings and not a thing this widget arranges. `elevated` is still the way to
/// lift one box over its siblings out of order (ADR-0069); a stack does not use
/// it, because "the order they are written in" is the whole of what a stack
/// promises.
@Markup("stack")
public record Stack(List<Widget> children, Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Attributed<Stack> {

    public Stack(Widget... kids) {
        this(List.of(kids), Attributes.NONE);
    }

    public Stack {
        children = List.copyOf(children == null ? List.of() : children);
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
    public String id() {
        return attributes.id();
    }

    @Override
    public Set<String> classes() {
        return attributes.classes();
    }

    @Override
    public Object key() {
        return attributes.key();
    }

    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        var laid = new Box[boxes.size()];
        for (var i = 0; i < boxes.size(); i++) {
            // The first stays in flow because something has to give the stack a
            // size; everything after it is taken out so that adding an overlay
            // cannot resize what it sits on.
            laid[i] = i == 0 ? boxes.get(i) : boxes.get(i).position(PositionType.ABSOLUTE);
        }
        return Box.of().children(laid).style(style);
    }

    /// Builds a `stack` from markup.
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        return new Stack(children, Attributes.of(node));
    }
}
