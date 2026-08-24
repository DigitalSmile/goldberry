package io.github.digitalsmile.goldberry.widgets.data.linechart;

import io.github.digitalsmile.goldberry.text.Paragraph;
import io.github.digitalsmile.goldberry.widgets.data.Scale;
import io.github.digitalsmile.goldberry.widgets.data.Ticks;
import java.util.List;

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
record PlotGeometry(
        double left, double top, double plotWidth, double plotHeight,
        double gutter, double lineHeight, Scale y) {

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
            List<Paragraph> labels, boolean hasXLabels, Ticks.Labelling labelling,
            double domainMin, double domainMax, double width, double height) {

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
        return new PlotGeometry(left, top, plotWidth, plotHeight, gutter, lineHeight,
                Scale.linear(
                        Math.min(labelling.min(), domainMin),
                        Math.max(labelling.max(), domainMax),
                        top + plotHeight, top));
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

    private static int clamp(int index, int points) {
        return index < 0 ? 0 : index >= points ? points - 1 : index;
    }
}
