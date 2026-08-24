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
/// @param nulls    what to do where a series has no value — decided above this
///                 widget too, because it is one convention per picture
///                 ([io.github.digitalsmile.goldberry.widgets.data.NullPolicy])
/// @param isolated the series shown alone, or -1 for all of them — decided
///                 above this widget, because a legend entry's click has to
///                 reach the plot and they are siblings ([io.github.digitalsmile.goldberry.widgets.data.ChartSpec])
public record ChartPlot(
        List<Series> series, List<String> categories, Mode mode,
        io.github.digitalsmile.goldberry.widgets.data.NullPolicy nulls, int isolated)
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
        nulls = nulls == null
                ? io.github.digitalsmile.goldberry.widgets.data.NullPolicy.GAP : nulls;
    }

    public ChartPlot(List<Series> series, List<String> categories, Mode mode) {
        this(series, categories, mode,
                io.github.digitalsmile.goldberry.widgets.data.NullPolicy.GAP, -1);
    }

    ChartPlot(List<Series> series, List<String> categories) {
        this(series, categories, Mode.LINE);
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
                    widget().nulls(), widget().isolated(), hovered, painted,
                    this::hover, this::walk);
        }

        /// Moves the crosshair, and asks for a frame only when it actually moved.
        ///
        /// The guard is not an optimization, it is the idle frame loop: a pointer
        /// moving across one point sends an event per pixel, and a `setState` per
        /// event would rebuild and repaint the chart sixty times a second to draw
        /// the same crosshair (§1.7, ADR-0122).
        private boolean hover(int index) {
            if (index == hovered) {
                return false;
            }
            setState(() -> hovered = index);
            return true;
        }

        /// Moves the crosshair `direction` points along, **from where it
        /// actually is**.
        ///
        /// The keyboard's half of the same job, and the reason it is here rather
        /// than in the widget: a widget is the description the last frame was
        /// built from, so two arrow presses between two frames would both be
        /// computed from the position before either of them and the crosshair
        /// would move once. The state's field is the only one that is current.
        ///
        /// **Clamped, not wrapping.** A line has two ends and a reader who walked
        /// to one has arrived somewhere; a crosshair that jumped back to Monday
        /// after Sunday would be a chart pretending its axis is a circle.
        private boolean walk(int direction) {
            var points = widget().series().stream()
                    .mapToInt(s -> s.values().size()).max().orElse(0);
            if (points == 0) {
                return false;
            }
            if (hovered < 0) {
                return hover(direction > 0 ? 0 : points - 1);
            }
            return hover(Math.max(0, Math.min(points - 1, hovered + direction)));
        }
    }
}
