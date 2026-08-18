package io.github.digitalsmile.goldberry.widgets.core.scroll;

import io.github.digitalsmile.goldberry.widget.Attributed;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.List;

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
public record Scroll(List<Widget> children, ScrollAxis axis, Attributes attributes)
        implements Widget.Stateful, Attributed<Scroll> {

    public Scroll {
        children = List.copyOf(children == null ? List.of() : children);
        axis = axis == null ? ScrollAxis.VERTICAL : axis;
        attributes = attributes == null ? Attributes.NONE : attributes;
    }

    public Scroll(Widget... kids) {
        this(List.of(kids), ScrollAxis.VERTICAL, Attributes.NONE);
    }

    @Override
    public Scroll withAttributes(Attributes attributes) {
        return new Scroll(children, axis, attributes);
    }

    @Override
    public Object key() {
        return attributes.key();
    }

    @Override
    public State<?> createState() {
        return new ScrollState();
    }
}
