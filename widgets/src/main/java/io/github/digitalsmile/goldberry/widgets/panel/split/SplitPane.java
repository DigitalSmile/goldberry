package io.github.digitalsmile.goldberry.widgets.panel.split;

import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.widget.Attributed;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.Markup;
import io.github.digitalsmile.goldberry.widgets.Wiring;
import java.util.List;
import java.util.function.DoubleConsumer;

/// Two children and a divider you can drag — `docs/core-widgets.md` §5's
/// `split-pane`.
///
/// ```kdl
/// split-pane axis="horizontal" position=0.3 first-min=160 second-min=240 {
///     panel { text "The list" }
///     panel { text "The detail" }
/// }
/// ```
///
/// ```java
/// new SplitPane(list, detail)                          // keeps its own position
/// new SplitPane(SplitAxis.HORIZONTAL, split, this::setSplit, list, detail)
/// ```
///
/// ## The position is a fraction, and the minimums are pixels
///
/// Two different things, deliberately. The **position** has to survive a resize —
/// a divider a third of the way across stays a third of the way across when the
/// window widens, which is what everybody expects and what a stored pixel offset
/// gets wrong. The **minimums** cannot be fractions: "this list needs 160 points
/// or its labels wrap" is a fact about the content, not about the window, and a
/// fraction would let a narrow window squeeze it to nothing.
///
/// So the fraction is clamped against the pixels on every layout, which needs the
/// pane's measured length — and that arrives through
/// [io.github.digitalsmile.goldberry.input.Measured], once a frame and only on a
/// change ([ADR-0117](../../../../../../../../book/src/adr/0117-a-widget-may-be-told-what-it-measured.md)).
/// Before the first measurement the fraction is used as given, which is right:
/// the first frame has no minimum it could be violating yet.
///
/// ## The drag is a translation, not a position
///
/// A slider reads its value straight off the pointer, because the value **is** a
/// position along a track ([ADR-0079]). A divider cannot: the pointer is
/// somewhere in a 6-point bar, and mapping that to a fraction of the pane would
/// snap the divider so its centre jumped under the finger on every press.
///
/// So it is the knob's arrangement instead
/// ([ADR-0089](../../../../../../../../book/src/adr/0089-a-knobs-gesture-is-a-rate.md)):
/// the divider reports its current offset as a
/// [io.github.digitalsmile.goldberry.input.Handles#gestureAnchor()], the router
/// hands that back on every event of the gesture, and the new offset is
/// `anchor + dragX`. Nothing jumps, and the divider does not need to remember
/// anything between frames — which it could not, being a value rebuilt from the
/// state.
///
/// ## The keyboard
///
/// §5: "keyboard-resizable when focused". The **divider** is the tab stop —
/// `split-pane` itself is not focusable and neither pane is a composite — and the
/// arrows along the axis move it by a step, `Home` and `End` go to the
/// minimums, and `Enter` collapses and restores when the pane is collapsible.
/// The arrows across the axis are left alone, because a horizontal split has
/// nothing to say about `Up`.
///
/// @param axis        which way the two children are laid out — see [SplitAxis]
/// @param position    where the divider sits, `0..1` of the pane's length; with
///                    [#onResize] this is where it *is*, and without it where it
///                    starts
/// @param onResize    what a drag asks for, or null to keep the position here
/// @param firstMin    the least the first child may be, in logical pixels
/// @param secondMin   likewise for the second
/// @param collapsible whether dragging past a minimum collapses that child to
///                    nothing rather than stopping at it
/// @param children    exactly two
/// @param attributes  the `id` and classes, which land on the `split-pane` node
@Markup("split-pane")
public record SplitPane(
        SplitAxis axis, double position, DoubleConsumer onResize,
        float firstMin, float secondMin, boolean collapsible,
        List<Widget> children, Attributes attributes)
        implements Widget.Stateful, Attributed<SplitPane> {

    /// What a child is given when nothing says otherwise.
    ///
    /// 48 logical points: §1.3's hit-target floor is 32 and a pane squeezed to
    /// exactly that is a pane with no room for anything *in* it. Small enough
    /// that it is never in the way, large enough that a divider dragged to the
    /// edge leaves something to grab.
    public static final float DEFAULT_MINIMUM = 48;

    public SplitPane(Widget first, Widget second) {
        this(SplitAxis.HORIZONTAL, 0.5, null, DEFAULT_MINIMUM, DEFAULT_MINIMUM, false,
                List.of(first, second), Attributes.NONE);
    }

    public SplitPane(SplitAxis axis, double position, DoubleConsumer onResize,
            Widget first, Widget second) {
        this(axis, position, onResize, DEFAULT_MINIMUM, DEFAULT_MINIMUM, false,
                List.of(first, second), Attributes.NONE);
    }

    public SplitPane {
        axis = axis == null ? SplitAxis.HORIZONTAL : axis;
        children = List.copyOf(children == null ? List.of() : children);
        attributes = attributes == null ? Attributes.NONE : attributes;
        if (children.size() != 2) {
            throw new IllegalArgumentException(
                    "a split pane divides exactly two children, and this has " + children.size()
                            + "; a three-way split is two split panes, one inside the other");
        }
        if (firstMin < 0 || secondMin < 0) {
            throw new IllegalArgumentException(
                    "a minimum size may not be negative: " + firstMin + ", " + secondMin);
        }
        if (Double.isNaN(position)) {
            throw new IllegalArgumentException(
                    "a divider position must be a number; use 0.5 for the middle");
        }
        position = Math.clamp(position, 0.0, 1.0);
    }

    /// Whether the application is deciding, rather than this widget.
    public boolean isControlled() {
        return onResize != null;
    }

    /// The first child — the left one, or the top one.
    public Widget first() {
        return children.getFirst();
    }

    /// The second child.
    public Widget second() {
        return children.get(1);
    }

    @Override
    public SplitPane withAttributes(Attributes value) {
        return new SplitPane(axis, position, onResize, firstMin, secondMin, collapsible,
                children, value);
    }

    @Override
    public Object key() {
        return attributes.key();
    }

    @Override
    public State<?> createState() {
        return new SplitPaneState();
    }

    /// Builds a `split-pane` from markup.
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        return new SplitPane(
                SplitAxis.of(node.stringProperty("axis")),
                node.numberProperty("position", 0.5),
                wiring.numeric(node, "resize"),
                (float) node.numberProperty("first-min", DEFAULT_MINIMUM),
                (float) node.numberProperty("second-min", DEFAULT_MINIMUM),
                node.booleanProperty("collapsible"),
                children,
                Attributes.of(node));
    }
}
