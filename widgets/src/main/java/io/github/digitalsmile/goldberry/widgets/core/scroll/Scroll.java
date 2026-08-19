package io.github.digitalsmile.goldberry.widgets.core.scroll;

import io.github.digitalsmile.goldberry.widget.Attributed;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;
import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.widgets.Wiring;
import io.github.digitalsmile.goldberry.widgets.Markup;

/// A viewport that shows part of something taller than itself —
/// `docs/core-widgets.md` §1's `scroll`.
///
/// ```kdl
/// scroll axis="vertical" {
///     column gap=8 { text "…"; text "…" }
/// }
/// ```
///
/// ## The one widget three separate pieces of work were waiting on
///
/// A menu taller than the work area is clamped and loses its bottom; a tab strip
/// wider than its window overflows it; `select` over a realistic option list
/// cannot be written at all. All three are this, and it is the reason `scroll`
/// came before the rest of §5
/// ([ADR-0116](../../../../../../../../book/src/adr/0116-a-scroll-view-is-a-clip-an-offset-and-two-extents.md)).
///
/// ## What it is made of
///
/// Three nodes, and each of them is one idea:
///
/// ```
/// scroll           the viewport. Clips, takes the wheel and the keys, holds nothing
/// └── scroll-content   the moving box. Translated by the offset, sized by its content
///     └── …            whatever was written inside
/// ```
///
/// The offset lives **here**, on this node's state, because §1 says "scroll
/// position is retained state surviving rebuilds" and the element tree is what
/// makes that true without anybody writing a key: a rebuild re-describes the
/// widget and the element keeps the state
/// ([ADR-0052](../../../../../../../../book/src/adr/0052-state-is-a-plain-object-and-setstate-defers.md)).
///
/// **This node styles nothing.** `scroll` as a CSS type is [ScrollViewport], the
/// node this builds — for [io.github.digitalsmile.goldberry.widgets.panel.tabs.Tabs]'
/// reason exactly: a stateful widget that was also styled would put two `scroll`
/// nodes in the cascade, one inside the other, and every rule would apply twice.
///
/// ## What it does not do yet
///
/// It has no scrollbars, `scrollIntoView` or track-click paging, and it does not
/// chain to an ancestor at its edge. Those are named in `book/src/TODO.md` with
/// what each is waiting on; what is here is the viewport, which is what the three
/// pieces of blocked work actually needed.
///
/// @param children   what to show. Wrapped in one `scroll-content`, so several
///                   children stack the way they would in a `column`
/// @param axis       which way it moves
/// @param attributes `id` and `class`, exactly as on the primitives
@Markup("scroll")
public record Scroll(
        List<Widget> children, ScrollAxis axis, double height,
        ScrollController controller, Attributes attributes)
        implements Widget.Stateful, Attributed<Scroll> {

    public Scroll {
        children = List.copyOf(children == null ? List.of() : children);
        axis = axis == null ? ScrollAxis.VERTICAL : axis;
        attributes = attributes == null ? Attributes.NONE : attributes;
    }

    public Scroll(List<Widget> children, ScrollAxis axis, Attributes attributes) {
        this(children, axis, Double.NaN, null, attributes);
    }

    public Scroll(Widget... kids) {
        this(List.of(kids), ScrollAxis.VERTICAL, Attributes.NONE);
    }

    /// This viewport answering to `value` — §1's `scrollIntoView` API.
    ///
    /// A controller is a handle something *outside* the viewport holds, so it
    /// cannot be created by the viewport's own state: whoever needs to scroll it
    /// is by definition somewhere else, and a controller made here would have a
    /// new identity on every rebuild ([ADR-0120]).
    public Scroll controlledBy(ScrollController value) {
        return new Scroll(children, axis, height, value, attributes);
    }

    /// This viewport with a height of `value` logical pixels.
    ///
    /// **For the caller that has a number a stylesheet cannot have.** A menu
    /// capped at the screen's height is the case it was added for: nothing in
    /// `controls.css` can know how tall the display is, and §8's subset has no
    /// `max-height` to express "no taller than" with
    /// ([ADR-0118](../../../../../../../../book/src/adr/0118-a-popup-that-does-not-fit-scrolls.md)).
    ///
    /// An ordinary `scroll` leaves this alone and takes its height from the
    /// stylesheet, which is `flex-grow: 1` — fill what is left of the column.
    public Scroll height(double value) {
        return new Scroll(children, axis, value, controller, attributes);
    }

    @Override
    public Scroll withAttributes(Attributes attributes) {
        return new Scroll(children, axis, height, controller, attributes);
    }

    @Override
    public Object key() {
        return attributes.key();
    }

    @Override
    public State<?> createState() {
        return new ScrollState();
    }

    /// Builds a `scroll` from markup.
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        return new Scroll(children, ScrollAxis.parse(node.stringProperty("axis")),
                Attributes.of(node));
    }
}
