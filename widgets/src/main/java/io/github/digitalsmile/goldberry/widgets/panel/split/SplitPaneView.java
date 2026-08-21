package io.github.digitalsmile.goldberry.widgets.panel.split;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.input.Extent;
import io.github.digitalsmile.goldberry.input.Measured;
import io.github.digitalsmile.goldberry.layout.Box;
import io.github.digitalsmile.goldberry.natives.yoga.FlexDirection;
import io.github.digitalsmile.goldberry.natives.yoga.StyleLength;
import io.github.digitalsmile.goldberry.widget.Attributes;
import io.github.digitalsmile.goldberry.widget.Paints;
import io.github.digitalsmile.goldberry.widget.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

/// **This is the `split-pane` a stylesheet selects.**
///
/// [SplitPane] is stateful and styles nothing, so this node carries the CSS type
/// and the document's `id` and classes — the split `select`, `tabs`, `menubar`
/// and `collapse` all use.
///
/// ## The first pane is sized and the second grows
///
/// Not two `flex-grow`s in proportion, which is the obvious answer and is wrong:
/// `flex-grow` shares out the space **left over** after content, so two panes
/// with content in them would land wherever their content put them and the
/// divider would ignore its own fraction. §10's subset has no `flex-basis`
/// either.
///
/// So the first pane is given an explicit main-axis size in logical pixels —
/// `fraction × (length − divider)` — with no shrink, and the second takes
/// whatever is left. That needs the measured length, which is why this node is
/// [Measured]; before the first measurement it falls back to proportional growth,
/// which is approximately right for one frame rather than degenerate.
///
/// @param axis          which way the children run
/// @param position      the divider's fraction, already clamped by the state
/// @param firstLength   the first pane's main-axis size in logical pixels, or a
///                      negative number before the pane has been measured — the
///                      state computes it, because the state is what holds the
///                      measurement
/// @param children      exactly two
/// @param attributes    the document's `id` and classes
/// @param onMeasured    where the pane's own length goes
/// @param offset        the divider's current offset in pixels, for the gesture
///                      anchor — a supplier because it is read at press time
/// @param onDrag        a new offset in pixels
/// @param onNudge       a step along the axis, and whether it is a page
/// @param onCollapse    `Enter` on a collapsible split
record SplitPaneView(
        SplitAxis axis, double position, double firstLength, List<Widget> children,
        Attributes attributes,
        BiConsumer<Extent, Extent> onMeasured, DoubleSupplier offset, DoubleConsumer onDrag,
        SplitPaneView.Nudge onNudge, Runnable onCollapse)
        implements Widget.Leaf, Styled, Paints, Measured {

    /// A step along the axis: `-1` or `+1`, and whether it is a page-sized one.
    @FunctionalInterface
    interface Nudge {
        void by(double steps, boolean page);
    }

    /// How thick the divider is, in logical pixels.
    ///
    /// Written here rather than taken from the stylesheet because the first
    /// pane's width is computed against it: a divider whose CSS width disagreed
    /// with this number would put the second pane's edge that many pixels out,
    /// silently. The stylesheet sets the same number and `SplitPaneTest` pins
    /// them together — the same bargain `Menus` makes with `--gb-menu-item-height`
    /// (ADR-0117), and the same reason it is not a token.
    static final float DIVIDER = 6;

    SplitPaneView {
        children = List.copyOf(children == null ? List.of() : children);
        attributes = attributes == null ? Attributes.NONE : attributes;
    }

    @Override
    public String cssType() {
        return "split-pane";
    }

    @Override
    public String id() {
        return attributes.id();
    }

    /// The axis is a class as well as a layout, so a stylesheet can reach a
    /// stacked split without having to know which way this widget arranged it.
    @Override
    public Set<String> classes() {
        var all = new java.util.LinkedHashSet<>(attributes.classes());
        all.add(axis.cssClass());
        return Set.copyOf(all);
    }

    @Override
    public void measured(Extent bounds, Extent part) {
        onMeasured.accept(bounds, part);
    }

    /// The first pane, the divider, the second pane.
    @Override
    public List<Widget> children() {
        var parts = new ArrayList<Widget>(3);
        parts.add(new SplitPaneSide(true, children.getFirst()));
        parts.add(new SplitDivider(axis, position, offset, onDrag, onNudge, onCollapse));
        parts.add(new SplitPaneSide(false, children.get(1)));
        return List.copyOf(parts);
    }

    @Override
    public Box render(ComputedStyle style, List<Box> boxes, Context context) {
        var content = new ArrayList<Box>(3);
        var vertical = axis.isVertical();

        if (boxes.size() == 3) {
            content.add(sized(boxes.get(0), vertical));
            content.add(boxes.get(1));
            content.add(boxes.get(2).grow(1).shrink(1));
        } else {
            content.addAll(boxes);
        }
        return Box.of().style(style)
                .direction(vertical ? FlexDirection.COLUMN : FlexDirection.ROW)
                .children(content.toArray(Box[]::new));
    }

    /// The first pane at a stated main-axis size, or in proportion when nothing
    /// has been measured yet.
    private Box sized(Box pane, boolean vertical) {
        if (firstLength < 0) {
            // The first frame: no measurement has come back, so there is no pixel
            // size to give. Proportional growth is approximately right for one
            // frame -- it lands wherever the content does rather than where the
            // fraction says -- which beats a pane of zero width that the next
            // frame corrects visibly.
            return pane.grow(position).shrink(1)
                    .size(StyleLength.UNDEFINED, StyleLength.UNDEFINED);
        }
        if (firstLength == 0) {
            // Collapsed. Still *built*: §5 asks for collapse-to-edge, not for
            // unmounting, and a pane that lost its state whenever somebody
            // dragged the divider to the edge would be a surprise the
            // specification does not ask for -- and the opposite of `collapse`,
            // where the absence is the whole point.
            return pane.grow(0).shrink(1)
                    .size(vertical ? StyleLength.UNDEFINED : StyleLength.points(0),
                            vertical ? StyleLength.points(0) : StyleLength.UNDEFINED);
        }
        var main = StyleLength.points((float) firstLength);
        return pane.grow(0).shrink(0)
                .size(vertical ? StyleLength.UNDEFINED : main,
                        vertical ? main : StyleLength.UNDEFINED);
    }

    /// One side of the split — a node so a stylesheet can reach "the pane" rather
    /// than whatever the author happened to put in it, and so that the sizing
    /// above lands on something the author does not own.
    record SplitPaneSide(boolean leading, Widget content)
            implements Widget.Leaf, Styled, Paints {

        @Override
        public String cssType() {
            return "split-pane-side";
        }

        @Override
        public Set<String> classes() {
            return Set.of(leading ? "first" : "second");
        }

        @Override
        public List<Widget> children() {
            return List.of(content);
        }

        @Override
        public Box render(ComputedStyle style, List<Box> boxes, Context context) {
            return Box.of().style(style).children(boxes.toArray(Box[]::new));
        }
    }
}
