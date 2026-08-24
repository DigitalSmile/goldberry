package io.github.digitalsmile.goldberry.widgets.data.linechart;

import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.data.Series;
import java.util.List;

/// The plot of a chart — one canvas, whatever the shape.
///
/// **Stateful, because a hovered point is state.** A widget is a value rebuilt
/// every frame and the drawing is a pure function of it, so the one thing that
/// has to persist across those rebuilds — which point the pointer is over — lives
/// here, on the element, and everything else is derived from it. What this builds
/// is [ChartSurface], which is the `chart-plot` part itself: the canvas, the
/// painter, and the node the pointer lands on.
///
/// The split is the shape `SplitPane` already has — a stateful widget above, and
/// a leaf below that hears the pointer and reports upward
/// ([ADR-0063](../../../../../../../../book/src/adr/0063-data-flows-down-events-flow-up.md)).
/// It costs one element and it is what makes the crosshair possible at all.
///
/// One part rather than three, because the axes, the gridlines, the gutter
/// measurement and the label collision rule are the same for a line, a band and
/// a bar — and three copies of that would be three chances to have a chart whose
/// gridlines are a pixel off its labels.
///
/// @param isolated the series shown alone, or -1 for all of them — decided
///                 above this widget, because a legend entry's click has to
///                 reach the plot and they are siblings ([io.github.digitalsmile.goldberry.widgets.data.ChartSpec])
public record ChartPlot(
        List<Series> series, List<String> categories, Mode mode, int isolated)
        implements Widget.Stateful {

    /// Which shape the plot draws.
    public enum Mode {

        /// A polyline per series.
        LINE,

        /// Filled bands, **stacked**. Overlapping translucent areas are the
        /// classic unreadable chart: with three series there are seven possible
        /// colours on screen and none of them is in the legend. Stacked, the
        /// bands add up to the total, which is what a reader assumes an area
        /// chart means anyway.
        AREA,

        /// A bar per point, **grouped** side by side when there is more than one
        /// series.
        BAR
    }

    public ChartPlot {
        series = List.copyOf(series == null ? List.of() : series);
        categories = List.copyOf(categories == null ? List.of() : categories);
        mode = mode == null ? Mode.LINE : mode;
    }

    public ChartPlot(List<Series> series, List<String> categories, Mode mode) {
        this(series, categories, mode, -1);
    }

    ChartPlot(List<Series> series, List<String> categories) {
        this(series, categories, Mode.LINE, -1);
    }

    @Override
    public State<?> createState() {
        return new ChartPlotState();
    }

    /// The hovered point, and the geometry the pointer resolves it against.
    static final class ChartPlotState extends State<ChartPlot> {

        /// Which point the pointer is over, or -1 for none — which is also what
        /// a chart nobody has pointed at reads, so a golden image is the chart
        /// and not the chart plus a crosshair.
        private int hovered = -1;

        /// Where the painter leaves the geometry the pointer needs. Owned by the
        /// state rather than by the widget, because the widget is a new value
        /// every frame and this has to outlive them (see [PaintedGeometry]).
        private final PaintedGeometry painted = new PaintedGeometry();

        @Override
        public Widget build(io.github.digitalsmile.goldberry.widget.BuildContext context) {
            return new ChartSurface(widget().series(), widget().categories(), widget().mode(),
                    widget().isolated(), hovered, painted, this::hover);
        }

        /// Moves the crosshair, and asks for a frame only when it actually moved.
        ///
        /// The guard is not an optimization, it is the idle frame loop: a pointer
        /// moving across one point sends an event per pixel, and a `setState` per
        /// event would rebuild and repaint the chart sixty times a second to draw
        /// the same crosshair (§1.7, ADR-0122).
        private void hover(int index) {
            if (index == hovered) {
                return;
            }
            setState(() -> hovered = index);
        }
    }
}
