package io.github.digitalsmile.goldberry.widgets.core.affix;

import java.util.List;

import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributed;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.markup.Markup;
import io.github.digitalsmile.goldberry.widgets.markup.Wiring;

/// A child pinned to an edge of the nearest `scroll` once it would have scrolled
/// past it — `docs/core-widgets.md` §1's `affix`.
///
/// ```kdl
/// scroll {
///     affix edge="top" { panel class="section-header" { text "Controls" } }
///     column { /* … */ }
/// }
/// ```
///
/// ## It leaves a hole
///
/// §1: "The child keeps its place in layout — `affix` leaves a same-sized hole
/// behind, so nothing below it jumps when it detaches." That is the difference
/// between this and `position: fixed`, and it is why the widget is two nodes: an
/// outer `affix` that stays exactly where the layout put it, and an inner
/// `affix-content` that slides.
///
/// The split is not only about the hole. It is what stops the widget oscillating:
/// it is told where it is once a frame, and a node that moved *itself* in response
/// would be told a new position and move again, forever. The outer node's position
/// is a function of the layout alone, so the inner one sliding under it changes
/// nothing that is reported
/// (ADR-0119).
///
/// ## Not `position: sticky`
///
/// §1 says why, and it is worth repeating here: §8's CSS subset has no `position`
/// at all, and this is a widget "precisely so the subset does not have to grow
/// one". A sticky position would be a layout mode the cascade has to understand;
/// this is two boxes and a translate.
///
/// ## `:affixed`
///
/// The pseudo-class comes on the moment it lifts, so a header can gain a shadow
/// exactly then. No selector can express "this node is currently over another
/// one", which is why it is a pseudo-class rather than something a stylesheet
/// could have written itself.
///
/// ## Revealing one
///
/// An `affix` is the wrong thing to point `scrollIntoView` at from the outside,
/// and the reason is the whole point of the widget: once pinned, its **content**
/// sits at the viewport's edge, so anything measuring it concludes it is already
/// in view and scrolls nowhere. What travels with the document is the *hole*, and
/// only this widget can hand that out
/// (ADR-0124).
///
/// So [#onReveal] is a door rather than a policy: give it a callback and it is
/// handed the hole's rectangle and the viewport's, once a frame, which is exactly
/// the pair [io.github.digitalsmile.goldberry.widgets.core.scroll.ScrollController#reveal]
/// takes. What to do with them — whether a section wants showing at all — stays
/// with the caller.
///
/// @param children   what to pin. Several are stacked, as in a `column`
/// @param edge       which side of the viewport to pin to
/// @param offset     how far from that edge to sit, in logical pixels
/// @param cross      an edge on the other axis to pin to as well, or null —
///                   `edge="top left"` in markup (ADR-0371)
/// @param onReveal   told where the **hole** is and what clips it, or null for
///                   the ordinary case where nobody is asking
/// @param attributes `id` and `class`, exactly as on the primitives
@Markup("affix")
public record Affix(
        List<Widget> children,
        Edge edge,
        double offset,
        java.util.function.BiConsumer<LogicalRect, LogicalRect> onReveal,
        Edge cross,
        Attributes attributes)
        implements Widget.Stateful, Attributed<Affix> {

    public Affix {
        children = List.copyOf(children == null ? List.of() : children);
        edge = edge == null ? Edge.TOP : edge;
        if (cross != null && cross.isVertical() == edge.isVertical()) {
            throw new IllegalArgumentException("an affix pins to at most one edge per axis, and " + edge + " and "
                    + cross + " are on the same one");
        }
        attributes = attributes == null ? Attributes.NONE : attributes;
    }

    public Affix(
            List<Widget> children,
            Edge edge,
            double offset,
            java.util.function.BiConsumer<LogicalRect, LogicalRect> onReveal,
            Attributes attributes) {
        this(children, edge, offset, onReveal, null, attributes);
    }

    public Affix(List<Widget> children, Edge edge, double offset, Attributes attributes) {
        this(children, edge, offset, null, null, attributes);
    }

    /// This affix also pinned to an edge on the other axis — a header sticky at
    /// the top and held against the left of a table that scrolls sideways
    /// (ADR-0371). Each axis is its own subtraction, so there is nothing for one
    /// to win over the other.
    ///
    /// @throws IllegalArgumentException if `other` is on the same axis as the edge
    public Affix alsoPinnedTo(Edge other) {
        return new Affix(children, edge, offset, onReveal, other, attributes);
    }

    public Affix(Widget... kids) {
        this(List.of(kids), Edge.TOP, 0, Attributes.NONE);
    }

    /// This affix, telling `listener` where its hole is — see the class note.
    public Affix revealedBy(java.util.function.BiConsumer<LogicalRect, LogicalRect> listener) {
        return new Affix(children, edge, offset, listener, cross, attributes);
    }

    @Override
    public Affix withAttributes(Attributes attributes) {
        return new Affix(children, edge, offset, onReveal, cross, attributes);
    }

    @Override
    public Object key() {
        return attributes.key();
    }

    @Override
    public State<?> createState() {
        return new AffixState();
    }

    /// Builds an `affix` from markup.
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        var edges = node.stringProperty("edge");
        return new Affix(
                children,
                Edge.parse(edges),
                node.numberProperty("offset", 0),
                null,
                Edge.parseCross(edges),
                Attributes.of(node));
    }
}
