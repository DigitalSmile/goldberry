package io.github.digitalsmile.goldberry.widgets.core.scroll;

import java.util.List;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributed;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widgets.markup.Markup;
import io.github.digitalsmile.goldberry.widgets.markup.Wiring;

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
/// (ADR-0116).
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
/// (ADR-0052).
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
/// ## A timeline opens at its end
///
/// [#anchor(ScrollAnchor)] and [#preserveOnPrepend(boolean)] are the three
/// things a chat, a log or a console wants and a `scrollBy` cannot give: open on
/// the newest line, stay on it while you are already there, and keep the
/// reader's line still when older lines are paged in *above*. All three are
/// layout facts, and the offset is the only thing here that knows them
/// (ADR-0392).
///
/// @param children          what to show. Wrapped in one `scroll-content`, so
///                          several children stack the way they would in a
///                          `column`
/// @param axis              which way it moves
/// @param anchor            where it opens and where it stays
/// @param preserveOnPrepend whether content inserted above it moves the offset
///                          rather than the reader; null for "whatever the
///                          anchor says", which is the state an unset attribute
///                          is in
/// @param attributes        `id` and `class`, exactly as on the primitives
@Markup("scroll")
public record Scroll(
        List<Widget> children,
        ScrollAxis axis,
        double height,
        ScrollController controller,
        ScrollAnchor anchor,
        @Nullable Boolean preserveOnPrepend,
        Attributes attributes)
        implements Widget.Stateful, Attributed<Scroll> {

    public Scroll {
        children = List.copyOf(children == null ? List.of() : children);
        axis = axis == null ? ScrollAxis.VERTICAL : axis;
        anchor = anchor == null ? ScrollAnchor.START : anchor;
        attributes = attributes == null ? Attributes.NONE : attributes;
    }

    public Scroll(List<Widget> children, ScrollAxis axis, Attributes attributes) {
        this(children, axis, Double.NaN, null, ScrollAnchor.START, null, attributes);
    }

    public Scroll(Widget... kids) {
        this(List.of(kids), ScrollAxis.VERTICAL, Attributes.NONE);
    }

    /// This viewport anchored to `value` — [ScrollAnchor#END] for a timeline.
    ///
    /// `END` opens at the end and sticks there while the viewport is already at
    /// the end. What "already at the end" is worth to the nearest pixel is
    /// [ScrollStick]'s business, and it is half of one.
    ///
    /// On a horizontal viewport it means the **far** end along the scrolling
    /// direction rather than "the right": the offset is measured from the
    /// content's start edge, so under a right-to-left layout the same anchor
    /// puts the newest item on the left of the screen, where that language's
    /// reader ends up.
    public Scroll anchor(ScrollAnchor value) {
        return new Scroll(children, axis, height, controller, value, preserveOnPrepend, attributes);
    }

    /// Whether content inserted **above** this viewport moves the offset by the
    /// height that was added, so what is on screen stays still.
    ///
    /// On by default for [ScrollAnchor#END] and off otherwise, which is what
    /// makes the underlying component a `Boolean` and not a `boolean`: "unset"
    /// is a third state whose meaning is decided by the anchor, and collapsing
    /// it into either boolean would make `.anchor(END).preserveOnPrepend(false)`
    /// and `.preserveOnPrepend(false).anchor(END)` mean different things.
    ///
    /// **It needs keyed children.** Preserving an offset means recognising a row
    /// that was on screen a frame ago, and a list matched by position rather
    /// than by key has no such row — element 0 simply describes a different
    /// message
    /// ([Widget#key()]).
    public Scroll preserveOnPrepend(boolean value) {
        return new Scroll(children, axis, height, controller, anchor, value, attributes);
    }

    /// Whether this viewport preserves its offset on a prepend, with the anchor
    /// consulted when nothing said.
    public boolean preservesOnPrepend() {
        return preserveOnPrepend == null ? anchor.preservesOnPrepend() : preserveOnPrepend;
    }

    /// This viewport answering to `value` — §1's `scrollIntoView` API.
    ///
    /// A controller is a handle something *outside* the viewport holds, so it
    /// cannot be created by the viewport's own state: whoever needs to scroll it
    /// is by definition somewhere else, and a controller made here would have a
    /// new identity on every rebuild ([ADR-0120]).
    public Scroll controlledBy(ScrollController value) {
        return new Scroll(children, axis, height, value, anchor, preserveOnPrepend, attributes);
    }

    /// This viewport with a height of `value` logical pixels.
    ///
    /// **For the caller that has a number a stylesheet cannot have.** A menu
    /// capped at the screen's height is the case it was added for: nothing in
    /// `controls.css` can know how tall the display is, and §8's subset has no
    /// `max-height` to express "no taller than" with
    /// (ADR-0118).
    ///
    /// An ordinary `scroll` leaves this alone and takes its height from the
    /// stylesheet, which is `flex-grow: 1` — fill what is left of the column.
    public Scroll height(double value) {
        return new Scroll(children, axis, value, controller, anchor, preserveOnPrepend, attributes);
    }

    @Override
    public Scroll withAttributes(Attributes attributes) {
        return new Scroll(children, axis, height, controller, anchor, preserveOnPrepend, attributes);
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
    ///
    /// `preserve-on-prepend` is read through [KdlNode#flagProperty], not
    /// [KdlNode#booleanProperty]: the default is the anchor's, so an absent
    /// attribute has to stay absent all the way to the widget rather than being
    /// resolved into a `false` here.
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        return new Scroll(
                children,
                ScrollAxis.parse(node.stringProperty("axis")),
                Double.NaN,
                null,
                ScrollAnchor.parse(node.stringProperty("anchor")),
                node.flagProperty("preserve-on-prepend"),
                Attributes.of(node));
    }
}
