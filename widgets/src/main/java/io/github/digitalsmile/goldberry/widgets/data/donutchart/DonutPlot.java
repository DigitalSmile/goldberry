package io.github.digitalsmile.goldberry.widgets.data.donutchart;

import java.util.List;

import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;

/// The ring of a [DonutChart].
///
/// **Stateful, because a hovered slice is state.** What it builds is
/// [DonutSurface], which is the `donut-plot` part itself — the canvas, the
/// painter, and the node that hears the pointer and the keyboard. The split is
/// the one the axis charts have and it is there for the same reason: a widget is
/// a value rebuilt every frame, and which slice is being read has to outlive
/// those rebuilds
/// (ADR-0198).
///
/// A donut has no second piece of interaction state to keep. Its legend is a key
/// rather than a control: isolating one slice of a part-to-whole chart leaves a
/// chart that no longer shows a whole, so there is nothing above this to hold
/// (`ChartSpec`).
record DonutPlot(List<Double> values, List<String> labels) implements Widget.Stateful {

    @Override
    public State<?> createState() {
        return new DonutPlotState();
    }

    /// Which slice is being read.
    static final class DonutPlotState extends State<DonutPlot> {

        /// The slice under the pointer or the keyboard, or -1 for none — which is
        /// what an untouched donut reads, so a golden image of one is the chart
        /// and not the chart plus a readout.
        private int hovered = -1;

        @Override
        public Widget build(BuildContext context) {
            return new DonutSurface(widget().values(), widget().labels(), hovered, this::hover, this::walk);
        }

        /// Moves the readout, and asks for a frame only when it actually moved.
        ///
        /// The guard is the idle frame loop rather than an optimization: a pointer
        /// crossing one slice sends an event per pixel, and a `setState` for each
        /// would rebuild and repaint the chart sixty times a second to draw the
        /// same two words (§1.7, ADR-0122).
        private boolean hover(int slice) {
            if (slice == hovered) {
                return false;
            }
            setState(() -> hovered = slice);
            return true;
        }

        /// Moves the readout `direction` slices round the ring, **from where it
        /// actually is** — skipping the slices that are not drawn.
        ///
        /// In the state rather than in the widget because a widget is the
        /// description the last frame was built from: two arrow presses between
        /// two frames would both step from the slice before either of them, and
        /// the second one would do nothing.
        ///
        /// **Wrapping**, unlike an axis chart's crosshair, which stops at the
        /// ends. A ring has no ends; stopping somewhere on it would be an edge
        /// the picture does not have.
        private boolean walk(int direction) {
            var values = widget().values();
            var slices = values.size();
            if (slices == 0) {
                return false;
            }
            var from = hovered;
            if (from < 0) {
                // Nothing is being read yet, so an arrow starts at the end it
                // came from rather than stepping off nowhere.
                for (var i = 0; i < slices; i++) {
                    var candidate = direction > 0 ? i : slices - 1 - i;
                    if (values.get(candidate) > 0) {
                        return hover(candidate);
                    }
                }
                return false;
            }
            for (var i = 1; i <= slices; i++) {
                var candidate = Math.floorMod(from + direction * i, slices);
                if (values.get(candidate) > 0) {
                    return hover(candidate);
                }
            }
            return false;
        }
    }
}
