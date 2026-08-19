package io.github.digitalsmile.goldberry.widgets.core.affix;

import io.github.digitalsmile.goldberry.widget.Attributed;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;

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
/// ([ADR-0119](../../../../../../../../book/src/adr/0119-a-widget-may-be-told-where-it-is.md)).
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
/// @param children   what to pin. Several are stacked, as in a `column`
/// @param edge       which side of the viewport to pin to
/// @param offset     how far from that edge to sit, in logical pixels
/// @param attributes `id` and `class`, exactly as on the primitives
public record Affix(List<Widget> children, Edge edge, double offset, Attributes attributes)
        implements Widget.Stateful, Attributed<Affix> {

    public Affix {
        children = List.copyOf(children == null ? List.of() : children);
        edge = edge == null ? Edge.TOP : edge;
        attributes = attributes == null ? Attributes.NONE : attributes;
    }

    public Affix(Widget... kids) {
        this(List.of(kids), Edge.TOP, 0, Attributes.NONE);
    }

    @Override
    public Affix withAttributes(Attributes attributes) {
        return new Affix(children, edge, offset, attributes);
    }

    @Override
    public Object key() {
        return attributes.key();
    }

    @Override
    public State<?> createState() {
        return new AffixState();
    }
}
