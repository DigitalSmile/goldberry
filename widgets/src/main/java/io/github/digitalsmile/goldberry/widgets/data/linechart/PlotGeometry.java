package io.github.digitalsmile.goldberry.widgets.data.linechart;

import java.util.List;

import io.github.digitalsmile.goldberry.text.Paragraph;
import io.github.digitalsmile.goldberry.widgets.data.Scale;

/// Where the plot area is inside the box, and what a coordinate in it means.
///
/// The arithmetic that used to live at the top of the painter, lifted out
/// because a **second reader arrived**: a pointer. Turning a pointer's x into
/// the point it is over is the same sum as deciding where to draw that point,
/// run backwards, and two copies of it is how a crosshair ends up a few pixels
/// off the line it is supposed to be on — at one window size, on one axis, which
/// is the kind of wrong nobody notices until a screenshot.
///
/// The same argument [Scale] was written down for: one place that knows a value
/// becomes a position, and no second opinion about it.
///
/// ## What decides it
///
/// The **gutter** is measured from the shaped y labels, so an axis reading
/// `1,000,000` reserves more room than one reading `5` and nobody writes a
/// number down. The **bottom** is a line of x labels when there are any and half
/// a line when there are not — because the lowest y label is centred on the
/// baseline and would otherwise have its bottom half cut off by the edge of the
/// box.
///
/// @param left        where the plot area starts — the gutter and its gap
/// @param top         half a line of headroom, so the topmost label is not
///                    clipped
/// @param plotWidth   the drawable width
/// @param plotHeight  the drawable height
/// @param gutter      how wide the widest y label turned out to be
/// @param lineHeight  the tallest label's height
/// @param y           values to vertical positions, already swapped (see [Scale])
/// @param times       epoch milliseconds per point index when this chart has a
///                    time axis, or null when its x is the point index
// Arrays, and deliberately: these are read on every frame of a chart that may
// hold a hundred thousand points, and `equals` is never called on one. A
// `List<Double>` here would box every reading to buy a correctness property
// nothing uses.
@SuppressWarnings("ArrayRecordComponent")
record PlotGeometry(
        double left,
        double top,
        double plotWidth,
        double plotHeight,
        double gutter,
        double lineHeight,
        Scale y,
        double[] times) {

    /// A plot whose x is the point index — every chart without a time axis.
    PlotGeometry(
            double left, double top, double plotWidth, double plotHeight, double gutter, double lineHeight, Scale y) {
        this(left, top, plotWidth, plotHeight, gutter, lineHeight, y, null);
    }

    /// The gap between the axis labels and the plot, in logical pixels.
    static final double GAP = 6;

    /// The geometry of a plot this size, or **null** when there is no room for
    /// one.
    ///
    /// Null rather than an exception or a zero-sized answer: a collapsed split
    /// pane and a chart in a row that has not been given a height both produce
    /// it, and the caller's answer in every case is to draw nothing.
    ///
    /// **The domain is not the labelling.** `Ticks.extended` scores a candidate
    /// labelling on four things and coverage is only one of them, so the nicest
    /// labels for `12…36` are `10, 15, 20, 25, 30, 35` — which stops short of the
    /// data. Scaling to the *labels* would then draw the last point above the top
    /// gridline and, when it is near enough to the edge, clip it out of the
    /// picture altogether: a chart that has dropped its maximum, which is the one
    /// point a reader is most likely to have come for. So the scale spans the
    /// union of the two and the gridlines stay on the round numbers, which is
    /// what every chart a reader has seen already does.
    ///
    /// @param domainMin the smallest value that has to fit, before labelling
    /// @param domainMax the largest
    static PlotGeometry of(
            List<Paragraph> labels, boolean hasXLabels, double axisMin, double axisMax, double width, double height) {

        return of(labels, hasXLabels, axisMin, axisMax, false, width, height, null);
    }

    /// The same, for a chart whose x is **time** or whose y is **logarithmic**.
    ///
    /// `times` is one epoch-millisecond value per point index, so an unevenly
    /// sampled series is drawn unevenly — which is the whole of what a time axis
    /// buys over labelling the indices ([TimeAxis]).
    ///
    /// `axisMin`/`axisMax` are the range the axis has to reach, **already
    /// including its labels**: `Ticks.extended` scores a candidate labelling on
    /// four things and coverage is only one of them, so the nicest labels for
    /// `12…36` are `10, 15 … 35` — which stops short of the data. Scaling to the
    /// *labels* would draw the last point above the top gridline and, near enough
    /// to the edge, clip it out of the picture: a chart that has dropped its
    /// maximum, which is the one point a reader is most likely to have come for.
    /// The caller unions the two and the gridlines stay on the round numbers.
    ///
    /// @param logarithmic whether the value axis maps the logarithm — see
    ///                    [Scale#log]
    static PlotGeometry of(
            List<Paragraph> labels,
            boolean hasXLabels,
            double axisMin,
            double axisMax,
            boolean logarithmic,
            double width,
            double height,
            double[] times) {

        if (labels.isEmpty() || width <= 0 || height <= 0) {
            return null;
        }
        var gutter = 0.0;
        var lineHeight = 0.0;
        for (var label : labels) {
            var layout = label.layout(Paragraph.UNCONSTRAINED);
            gutter = Math.max(gutter, layout.width());
            lineHeight = Math.max(lineHeight, layout.height());
        }
        var bottom = hasXLabels ? lineHeight + GAP : lineHeight / 2;
        var left = gutter + GAP;
        var top = lineHeight / 2;
        var plotWidth = width - left;
        var plotHeight = height - bottom - top;
        if (plotWidth <= 0 || plotHeight <= 0) {
            return null;
        }
        var y = logarithmic && axisMin > 0 && axisMax > 0
                ? Scale.log(axisMin, axisMax, top + plotHeight, top)
                : Scale.linear(axisMin, axisMax, top + plotHeight, top);
        return new PlotGeometry(left, top, plotWidth, plotHeight, gutter, lineHeight, y, times);
    }

    /// The right-hand edge of the plot area.
    double right() {
        return left + plotWidth;
    }

    /// The bottom edge of the plot area.
    double bottom() {
        return top + plotHeight;
    }

    /// Point indices to horizontal positions, for a chart of `points` points.
    ///
    /// **Bars sit *in* a band and lines sit *on* a point**, which is why this
    /// takes the mode. The first line's first point is on the left edge; the
    /// first bar's is half a band in. Getting it the wrong way round puts every
    /// bar chart's crosshair half a band to the left, which reads as a rounding
    /// error rather than as a category error.
    double xOf(int index, int points, boolean banded) {
        if (points <= 0) {
            return left;
        }
        if (times != null && index >= 0 && index < times.length) {
            return timeScale().at(times[index]);
        }
        if (banded) {
            var band = plotWidth / points;
            return left + index * band + band / 2;
        }
        return points == 1 ? left : Scale.linear(0, points - 1, left, right()).at(index);
    }

    /// Which point `x` is over — [#xOf]'s inverse, clamped to the ends.
    ///
    /// Clamped rather than answering "none": a pointer just past the last point
    /// is still asking about the last point, and a crosshair that vanished in
    /// the last few pixels of a plot would look like a bug in the chart rather
    /// than like a decision.
    int indexAt(double x, int points, boolean banded) {
        if (points <= 0) {
            return -1;
        }
        if (times != null && times.length > 0) {
            // **The nearest point in time**, which on an unevenly sampled series
            // is not the nearest index: a reader pointing at a gap in the
            // sampling means the reading on one side of it, and which side is a
            // question about pixels rather than about position in a list.
            var wanted = timeScale().from(x);
            var nearest = 0;
            var closest = Double.POSITIVE_INFINITY;
            for (var i = 0; i < times.length && i < points; i++) {
                var distance = Math.abs(times[i] - wanted);
                if (distance < closest) {
                    closest = distance;
                    nearest = i;
                }
            }
            return nearest;
        }
        if (banded) {
            var band = plotWidth / points;
            return clamp((int) Math.floor((x - left) / band), points);
        }
        if (points == 1) {
            return 0;
        }
        var span = plotWidth / (points - 1);
        return clamp((int) Math.round((x - left) / span), points);
    }

    /// Whether `x` is inside the plot area — outside the gutter, and short of the
    /// far edge by a hair.
    ///
    /// The gutter is not the plot: a pointer over the axis labels is not over a
    /// point, and a crosshair that jumped to the first point whenever the mouse
    /// crossed the numbers would be a chart that reacts to being read.
    boolean holds(double x, double yPosition) {
        return x >= left && x <= right() && yPosition >= top && yPosition <= bottom();
    }

    /// Milliseconds to horizontal positions, for a chart with a time axis.
    ///
    /// Built on demand rather than held, because a `record` with an array in it
    /// is compared by identity anyway and this is three field reads.
    Scale timeScale() {
        var first = times[0];
        var last = times[times.length - 1];
        return Scale.linear(first, last, left, right());
    }

    /// Whether this chart's x is time.
    boolean isTimed() {
        return times != null && times.length > 0;
    }

    private static int clamp(int index, int points) {
        return index < 0 ? 0 : index >= points ? points - 1 : index;
    }
}
