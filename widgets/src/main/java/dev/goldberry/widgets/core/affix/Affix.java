package dev.goldberry.widgets.core.affix;

import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.goldberry.kdl.KdlNode;
import dev.goldberry.render.model.LogicalRect;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributed;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.markup.Markup;
import dev.goldberry.widgets.markup.Wiring;

/// A child pinned to an edge of the nearest `scroll` once it would have scrolled
/// past, such as a section header that stays while its rows slide under it.
///
/// ```kdl
/// scroll {
///     column {
///         affix edge="top" { panel class="section-header" { text "Hobbiton" } }
///         text "Hobbiton, line 1"
///     }
/// }
/// ```
///
/// `new Affix(Widget...)` pins to the top with no offset;
/// `new Affix(children, edge, offset, attributes)` is the usual form, and
/// [#alsoPinnedTo(Edge)] adds an edge on the other axis.
///
/// `edge=` is `top` (the default), `bottom`, `left` or `right`; two words, one
/// per axis, pin on both (`edge="top left"`), and an unknown word is `top`.
/// `offset=` is how far from that edge to sit, in logical pixels.
///
/// The widget is two nodes. The outer `affix` stays exactly where the layout put
/// it and leaves a same-sized hole, so nothing below it jumps when the child
/// detaches; the inner `affix-content` slides by a translate. The outer node's
/// position is a function of the layout alone, which is what stops the widget
/// from being told a new position and moving again, forever. The content never
/// travels past the far side of its container, so a section's header leaves with
/// its section and the next one takes over. The `:affixed` pseudo-class matches
/// the moment the content lifts, so a header can gain a shadow exactly then.
///
/// Pointing `scrollIntoView` at a pinned affix from outside scrolls nowhere,
/// because its content already sits at the viewport's edge. What travels with
/// the document is the hole, and [#revealedBy] hands a callback the hole's
/// rectangle and the viewport's once a frame, which is the pair
/// `ScrollController.reveal` takes.
///
/// @param children   what to pin. Several are stacked, as in a `column`
/// @param edge       which side of the viewport to pin to
/// @param offset     how far from that edge to sit, in logical pixels
/// @param cross      an edge on the other axis to pin to as well, or null
/// @param onReveal   told where the hole is and what clips it, or null for
///                   the ordinary case where nobody is asking
/// @param attributes `id` and `class`, exactly as on the primitives
///
/// Read more: [Affix](https://goldberry.dev/docs/layout/affix.html#affix).
@Markup("affix")
public record Affix(
        List<Widget> children,
        Edge edge,
        double offset,
        java.util.function.@Nullable BiConsumer<LogicalRect, LogicalRect> onReveal,
        @Nullable Edge cross,
        Attributes attributes)
        implements Widget.Stateful, Attributed<Affix> {

    /// Written out so that the parameters that take null for a default can say so.
    public Affix(
            @Nullable List<Widget> children,
            @Nullable Edge edge,
            double offset,
            java.util.function.@Nullable BiConsumer<LogicalRect, LogicalRect> onReveal,
            @Nullable Edge cross,
            @Nullable Attributes attributes) {
        children = List.copyOf(children == null ? List.of() : children);
        edge = edge == null ? Edge.TOP : edge;
        if (cross != null && cross.isVertical() == edge.isVertical()) {
            throw new IllegalArgumentException("an affix pins to at most one edge per axis, and " + edge + " and "
                    + cross + " are on the same one");
        }
        attributes = attributes == null ? Attributes.NONE : attributes;
        this.children = children;
        this.edge = edge;
        this.offset = offset;
        this.onReveal = onReveal;
        this.cross = cross;
        this.attributes = attributes;
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
    /// the top and held against the left of a table that scrolls sideways. Each
    /// axis is its own subtraction, so neither wins over the other.
    ///
    /// @throws IllegalArgumentException if `other` is on the same axis as the edge
    public Affix alsoPinnedTo(Edge other) {
        return new Affix(children, edge, offset, onReveal, other, attributes);
    }

    public Affix(Widget... kids) {
        this(List.of(kids), Edge.TOP, 0, Attributes.NONE);
    }

    /// This affix, telling `listener` where its hole is and what clips it, once
    /// a frame.
    public Affix revealedBy(java.util.function.BiConsumer<LogicalRect, LogicalRect> listener) {
        return new Affix(children, edge, offset, listener, cross, attributes);
    }

    @Override
    public Affix withAttributes(Attributes attributes) {
        return new Affix(children, edge, offset, onReveal, cross, attributes);
    }

    @Override
    public @Nullable Object key() {
        return attributes.key();
    }

    @Override
    public State<?> createState() {
        return new AffixState();
    }

    /// Builds an `affix` from markup.
    @SuppressWarnings("unused")
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
