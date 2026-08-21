package io.github.digitalsmile.goldberry.widgets.panel.split;

import io.github.digitalsmile.goldberry.input.Extent;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;

/// Where a [SplitPane]'s divider is, and how long the pane turned out to be.
///
/// Two numbers, and they are different kinds of thing. The **position** is a
/// fraction the widget may or may not own — a controlled split reads it from its
/// widget every build. The **length** is a measurement, which no widget can own
/// at all: it arrives from the frame that was laid out
/// ([ADR-0117](../../../../../../../../book/src/adr/0117-a-widget-may-be-told-what-it-measured.md)).
final class SplitPaneState extends State<SplitPane> {

    /// What a key press moves the divider by, in logical pixels.
    ///
    /// Pixels rather than a fraction of the pane, so a key press feels the same
    /// on a narrow split and a wide one — which is the same argument
    /// `docs/design-system.md` §2.4 makes for a scroll line.
    private static final double KEY_STEP = 16;

    /// The larger step, for `PageUp` and `PageDown`.
    private static final double PAGE_STEP = 64;

    /// Only meaningful while the widget is uncontrolled.
    private double position;

    /// The pane's length along its axis, from the last frame that laid it out,
    /// or 0 before the first one.
    ///
    /// **Not `setState`.** Nothing drawn depends on it directly — the fraction is
    /// what positions the divider — and marking the element dirty from a
    /// measurement is how a widget told its own size ends up rebuilding forever
    /// (ADR-0119).
    private double length;

    /// What the divider was before it was collapsed, so `Enter` can put it back.
    ///
    /// `NaN` when nothing is collapsed. Held here rather than derived, because
    /// once a pane is at zero there is nothing left to derive it from.
    private double restore = Double.NaN;

    @Override
    protected void initState() {
        position = widget().position();
    }

    /// A controlled split's answer comes from its widget; an uncontrolled one's
    /// from here. Clamped on the way out, so a model outside the minimums shows a
    /// legal divider rather than a pane with no room in it.
    private double resolved() {
        return clamp(widget().isControlled() ? widget().position() : position);
    }

    @Override
    public Widget build(BuildContext context) {
        var split = widget();
        return new SplitPaneView(
                split.axis(), resolved(), firstLength(), split.children(), split.attributes(),
                this::measured, this::offsetOf, this::dragTo, this::nudge, this::toggleCollapse);
    }

    /// The pane's own size, once a frame and only when it changes.
    private void measured(Extent bounds, Extent part) {
        length = widget().axis().isVertical() ? bounds.height() : bounds.width();
    }

    /// Where the divider currently is, in logical pixels from the near edge —
    /// what the drag anchors on.
    private double offsetOf() {
        return resolved() * length;
    }

    /// How long the first pane should be, in logical pixels, or `-1` before the
    /// pane has been measured.
    ///
    /// The arithmetic lives here rather than in the view because the measurement
    /// does: a widget is a value rebuilt every frame and cannot hold what the
    /// last layout said.
    private double firstLength() {
        if (length <= 0) {
            return -1;
        }
        return resolved() * Math.max(0, length - SplitPaneView.DIVIDER);
    }

    /// A drag: an offset in logical pixels, from `anchor + drag`.
    private void dragTo(double offset) {
        if (length <= 0) {
            return;
        }
        set(offset / length);
    }

    /// A key press: a step in logical pixels along the axis.
    ///
    /// In pixels rather than fractions so that the arrow key moves the divider by
    /// the same visible amount whatever the pane's width, and `page` for the
    /// larger jump.
    private void nudge(double steps, boolean page) {
        if (length <= 0) {
            return;
        }
        var by = steps * (page ? PAGE_STEP : KEY_STEP);
        set((resolved() * length + by) / length);
    }

    /// `Enter` on a collapsible split: put the first pane away, or bring it back.
    private void toggleCollapse() {
        var split = widget();
        if (!split.collapsible()) {
            return;
        }
        if (Double.isNaN(restore)) {
            restore = resolved();
            set(0, true);
        } else {
            var back = restore;
            restore = Double.NaN;
            set(back, true);
        }
    }

    private void set(double fraction) {
        set(fraction, false);
    }

    /// The one place the position changes.
    ///
    /// `past` is how a collapse gets to 0 or 1 at all: the ordinary clamp keeps
    /// the divider off the minimums, and collapsing is precisely the request to
    /// go past one of them.
    private void set(double fraction, boolean past) {
        var next = past ? Math.clamp(fraction, 0.0, 1.0) : clamp(fraction);
        // A drag that has hit a minimum reports the same number every frame, and
        // an application that rebuilds on it would rebuild sixty times a second
        // for a divider that is not moving.
        if (next == resolved()) {
            return;
        }
        // A drag that has left the collapsed state by hand should not have `Enter`
        // spring back to a position from before it.
        if (!past) {
            restore = Double.NaN;
        }
        if (widget().isControlled()) {
            widget().onResize().accept(next);
            return;
        }
        setState(() -> position = next);
    }

    /// The fraction, held off both minimums — or snapped **to** an edge when the
    /// split is collapsible and the drag went most of the way into a minimum.
    ///
    /// Half the minimum is the threshold, which is the ordinary rule for a
    /// collapse-to-edge: far enough in that a divider parked at its minimum does
    /// not collapse when a window shrinks a little, near enough that a deliberate
    /// shove to the edge lands.
    private double clamp(double fraction) {
        var split = widget();
        if (length <= 0) {
            // Nothing has been laid out yet, so there is no minimum to enforce and
            // no length to enforce it in. The fraction is used as given, which is
            // the only honest answer on the first frame.
            return Math.clamp(fraction, 0.0, 1.0);
        }
        var lowest = Math.min(split.firstMin() / length, 1.0);
        var highest = Math.max(1 - split.secondMin() / length, 0.0);
        if (lowest > highest) {
            // Both minimums cannot be honoured at this size. Splitting the
            // difference is the least surprising thing to do: pinning to one
            // minimum would make the *other* pane disappear entirely as the
            // window narrows, and which of the two got that treatment would be
            // an accident of which check ran first.
            return 0.5;
        }
        if (split.collapsible()) {
            if (fraction < lowest / 2) {
                return 0;
            }
            if (fraction > 1 - (1 - highest) / 2) {
                return 1;
            }
        }
        return Math.clamp(fraction, lowest, highest);
    }
}
