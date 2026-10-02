package dev.goldberry.widgets.core.scroll;

import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.goldberry.kdl.KdlNode;
import dev.goldberry.widget.State;
import dev.goldberry.widget.Widget;
import dev.goldberry.widget.attr.Attributed;
import dev.goldberry.widget.attr.Attributes;
import dev.goldberry.widgets.markup.Markup;
import dev.goldberry.widgets.markup.Wiring;

/// A viewport that shows part of something taller than itself, with overlay
/// scrollbars, a wheel, a keyboard and a position that survives rebuilds.
///
/// ```kdl
/// scroll id="chapters" axis="vertical" {
///     column { text "Hobbiton"; text "Bree"; text "Rivendell" }
/// }
/// ```
///
/// `new Scroll(Widget...)` is vertical; `new Scroll(List<Widget>, ScrollAxis,
/// Attributes)` chooses the axis. Several children are wrapped in one moving
/// box, so they stack as they would in a `column`.
///
/// `axis=` is `vertical` (the default), `horizontal` or `both`. `anchor=` is
/// `start` (the default) or `end`, which opens at the end and stays there while
/// the viewport is already there. `preserve-on-prepend=` says whether content
/// inserted above the viewport moves the offset by the height added so what is
/// on screen stays still; unset, it is on for `end` and off for `start`. Four
/// things are Java only: [#height(double)] caps the viewport in logical pixels,
/// [#controlledBy(ScrollController)] hands it a handle the application keeps,
/// [#tabStopOnlyWhenScrollable()] takes it out of the Tab order while
/// everything fits, and [#anchor(ScrollAnchor)] and
/// [#preserveOnPrepend(boolean)] are the two attributes as methods.
///
/// The viewport is three nodes: `scroll`, which clips and takes the wheel and
/// the keys; `scroll-content`, the box that moves; and whatever was written
/// inside. The offset lives on this widget's state, so a rebuild keeps it with
/// no key. This record styles nothing; the CSS type `scroll` is the viewport
/// node it builds, so a document's `id` and `class` land there. Scrollbars are
/// drawn over the content, or in a reserved gutter when the application's
/// `Scrollbars` setting asks for one, and a click on the track pages by a whole
/// viewport. A wheel or a key is consumed only when it moved something, so at
/// the edge a further turn bubbles to the viewport around it. A `scroll` has
/// `flex-grow: 1` from the stylesheet and fills what is left of a column; in a
/// box that sizes to its content it needs a height from the stylesheet.
///
/// Anchoring at the end and preserving on prepend are what a chat, a log or a
/// console wants: open on the newest line, stay there while the reader is
/// there, and keep the reader's line still when older lines are paged in above.
/// Preserving the offset needs keyed children, because it means recognising a
/// row that was on screen a frame ago.
///
/// @param children          what to show. Wrapped in one `scroll-content`, so
///                          several children stack the way they would in a
///                          `column`
/// @param axis              which way it moves
/// @param height            a height in logical pixels, or `NaN` for the
///                          stylesheet's
/// @param controller        the handle an application scrolls it with, or null
/// @param anchor            where it opens and where it stays
/// @param preserveOnPrepend whether content inserted above it moves the offset
///                          rather than the reader; null for "whatever the
///                          anchor says", which is the state an unset attribute
///                          is in
/// @param tabStopWhenFits   whether the viewport is a Tab stop even while
///                          everything fits in it, which it is unless
///                          [#tabStopOnlyWhenScrollable()] said otherwise
/// @param attributes        `id` and `class`, exactly as on the primitives
///
/// Read more: [Scroll](https://goldberry.dev/docs/layout/scroll.html#scroll).
@Markup("scroll")
public record Scroll(
        List<Widget> children,
        ScrollAxis axis,
        double height,
        @Nullable ScrollController controller,
        ScrollAnchor anchor,
        @Nullable Boolean preserveOnPrepend,
        boolean tabStopWhenFits,
        Attributes attributes)
        implements Widget.Stateful, Attributed<Scroll> {

    /// Written out so that the parameters that take null for a default can say so.
    public Scroll(
            @Nullable List<Widget> children,
            @Nullable ScrollAxis axis,
            double height,
            @Nullable ScrollController controller,
            @Nullable ScrollAnchor anchor,
            @Nullable Boolean preserveOnPrepend,
            boolean tabStopWhenFits,
            @Nullable Attributes attributes) {
        children = List.copyOf(children == null ? List.of() : children);
        axis = axis == null ? ScrollAxis.VERTICAL : axis;
        anchor = anchor == null ? ScrollAnchor.START : anchor;
        attributes = attributes == null ? Attributes.NONE : attributes;
        this.children = children;
        this.axis = axis;
        this.height = height;
        this.controller = controller;
        this.anchor = anchor;
        this.preserveOnPrepend = preserveOnPrepend;
        this.tabStopWhenFits = tabStopWhenFits;
        this.attributes = attributes;
    }

    /// The form every caller wrote before a viewport could leave the Tab order:
    /// a Tab stop always.
    public Scroll(
            @Nullable List<Widget> children,
            @Nullable ScrollAxis axis,
            double height,
            @Nullable ScrollController controller,
            @Nullable ScrollAnchor anchor,
            @Nullable Boolean preserveOnPrepend,
            @Nullable Attributes attributes) {
        this(children, axis, height, controller, anchor, preserveOnPrepend, true, attributes);
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
        return new Scroll(children, axis, height, controller, value, preserveOnPrepend, tabStopWhenFits, attributes);
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
        return new Scroll(children, axis, height, controller, anchor, value, tabStopWhenFits, attributes);
    }

    /// Whether this viewport preserves its offset on a prepend, with the anchor
    /// consulted when nothing said.
    public boolean preservesOnPrepend() {
        return preserveOnPrepend == null ? anchor.preservesOnPrepend() : preserveOnPrepend;
    }

    /// This viewport answering to `value`, the handle an application scrolls it
    /// with.
    ///
    /// A controller is a handle something outside the viewport holds, so it
    /// cannot be created by the viewport's own state: whoever needs to scroll it
    /// is by definition somewhere else, and a controller made here would have a
    /// new identity on every rebuild.
    public Scroll controlledBy(ScrollController value) {
        return new Scroll(children, axis, height, value, anchor, preserveOnPrepend, tabStopWhenFits, attributes);
    }

    /// This viewport with a height of `value` logical pixels.
    ///
    /// For the caller that has a number a stylesheet cannot have. A menu capped
    /// at the screen's height is the case: nothing in a stylesheet can know how
    /// tall the display is, and the CSS subset has no `max-height`.
    ///
    /// An ordinary `scroll` leaves this alone and takes its height from the
    /// stylesheet, which is `flex-grow: 1` — fill what is left of the column.
    public Scroll height(double value) {
        return new Scroll(children, axis, value, controller, anchor, preserveOnPrepend, tabStopWhenFits, attributes);
    }

    /// This viewport out of the Tab order **while everything fits in it**.
    ///
    /// A viewport is a Tab stop because its keys scroll it. With nothing to
    /// scroll the stop does nothing, and where a viewport wraps content of
    /// its own — a dialog's body — it is a stop before every field the reader
    /// came to fill in. This makes it one only when its content overflows, so
    /// a short body adds nothing to the Tab order and a long one can still be
    /// scrolled from the keyboard.
    public Scroll tabStopOnlyWhenScrollable() {
        return new Scroll(children, axis, height, controller, anchor, preserveOnPrepend, false, attributes);
    }

    @Override
    public Scroll withAttributes(Attributes attributes) {
        return new Scroll(children, axis, height, controller, anchor, preserveOnPrepend, tabStopWhenFits, attributes);
    }

    @Override
    public @Nullable Object key() {
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
