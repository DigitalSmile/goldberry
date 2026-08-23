package io.github.digitalsmile.goldberry.widgets.data.linechart;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.value.CssColor;
import io.github.digitalsmile.goldberry.natives.blend2d.BlendPath;
import io.github.digitalsmile.goldberry.natives.blend2d.enums.BlendStrokeCap;
import io.github.digitalsmile.goldberry.natives.blend2d.enums.BlendStrokeJoin;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.paint.Frame;
import io.github.digitalsmile.goldberry.render.model.LogicalSize;
import io.github.digitalsmile.goldberry.text.Paragraph;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.data.Lttb;
import io.github.digitalsmile.goldberry.widgets.data.Scale;
import io.github.digitalsmile.goldberry.widgets.data.Series;
import io.github.digitalsmile.goldberry.widgets.data.SeriesPalette;
import io.github.digitalsmile.goldberry.widgets.data.Ticks;
import java.util.ArrayList;
import java.util.List;

/// The drawn half of a [LineChart] — a **part**, so it is CSS-selectable and not
/// constructible
/// ([ADR-0065](../../../../../../../../book/src/adr/0065-a-part-is-styleable-and-not-constructible.md)).
///
/// Everything here happens in two places on purpose.
///
/// **In `render`**, which has the cascade and the text stack: the tick labelling,
/// the series colours, and the *shaping* of the labels. Shaping is 56 µs and a
/// widget tree is rebuilt every frame, so a chart that shaped its own axis inside
/// the painter would re-shape five unchanged numbers sixty times a second
/// ([ADR-0037](../../../../../../../../book/src/adr/0037-what-the-text-path-costs.md)).
/// The labels depend on the data and the tick count, neither of which is a size,
/// so none of it needs to wait for layout.
///
/// **In the painter**, which has the size: where the gridlines go, where the
/// polylines go, and how wide the label gutter turned out to be. The gutter is
/// measured from the shaped paragraphs, so an axis reading `1,000,000` reserves
/// more room than one reading `5` without anybody writing a number down.
public record ChartPlot(List<Series> series, List<String> categories, Mode mode)
        implements Widget.Leaf, Styled, Paints {

    /// What the plot draws. One part rather than three, because the axes, the
    /// gridlines, the gutter measurement and the label collision rule are the
    /// same for all of them — and three copies of that would be three chances to
    /// have a chart whose gridlines are a pixel off its labels.
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

    ChartPlot(List<Series> series, List<String> categories) {
        this(series, categories, Mode.LINE);
    }

    /// How many y labels to aim for. Five is what a dashboard-sized chart reads
    /// well at; `Ticks` treats it as a preference and will answer four or six if
    /// those are rounder.
    private static final int Y_LABELS = 5;

    /// The gap between the label gutter and the plot area, and between the plot
    /// and the x labels.
    private static final double GAP = 6;

    /// How thick a series line is. §8's subset has no `stroke-width`, and 2px is
    /// what a chart's line takes where a `sparkline`'s takes 1.5 — this one is
    /// drawn at full size and read from further away.
    private static final double STROKE = 2;

    @Override
    public String cssType() {
        return "chart-plot";
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        var min = series.stream().mapToDouble(Series::min).min().orElse(0);
        var max = series.stream().mapToDouble(Series::max).max().orElse(0);
        if (mode == Mode.AREA) {
            // A stack is as tall as its total, not as its tallest member.
            max = Math.max(max, stackedMax());
        }
        if (mode != Mode.LINE) {
            // **A bar or a band must start at zero.** A bar chart with a
            // non-zero baseline makes a 3% difference look like a doubling, and
            // it is the single most common way a chart lies. A line chart is the
            // one form where a zoomed baseline is honest, because a line encodes
            // its value by position rather than by area.
            min = Math.min(min, 0);
            max = Math.max(max, 0);
        }
        if (!Double.isFinite(min) || !Double.isFinite(max)) {
            min = 0;
            max = 0;
        }

        // The labelling, and the colours, decided here where the cascade is.
        var labelling = Ticks.extended(min, max, Y_LABELS);
        var labels = new ArrayList<Paragraph>(labelling.count());
        for (var value : labelling.values()) {
            labels.add(context.paragraph(style, format(value, labelling.step())));
        }
        var xLabels = new ArrayList<Paragraph>(categories.size());
        for (var category : categories) {
            xLabels.add(context.paragraph(style, category));
        }
        var colours = new ArrayList<Integer>(series.size());
        for (var i = 0; i < series.size(); i++) {
            colours.add(SeriesPalette.of(context, i));
        }

        // The gridlines are the text colour at low alpha rather than a token of
        // their own: §14's recessive grid, and one fewer thing for a theme to
        // define. `--gb-border` would have been the other candidate and is a
        // *structural* line, which a gridline is not.
        var grid = CssColor.fade(style.color(), 0.14);
        var ink = style.color();
        var plot = new Painted(labelling, List.copyOf(labels), List.copyOf(xLabels),
                List.copyOf(colours), grid, ink, series, mode);
        return Box.of().style(style).painting(plot::paint);
    }

    /// The tallest column of a stack — what an [Mode#AREA] axis has to reach.
    private double stackedMax() {
        var longest = series.stream().mapToInt(s -> s.values().size()).max().orElse(0);
        var tallest = 0.0;
        for (var i = 0; i < longest; i++) {
            var total = 0.0;
            for (var one : series) {
                if (i < one.values().size()) {
                    total += Math.max(0, one.values().get(i));
                }
            }
            tallest = Math.max(tallest, total);
        }
        return tallest;
    }

    /// A number as an axis label.
    ///
    /// **The root locale, deliberately**, which is the rule `hud` states and for
    /// a stronger reason here: a golden image of a chart formatted in the
    /// machine's locale is a test that passes in one country. An application that
    /// wants grouped thousands or a currency formats its own — which is the same
    /// answer `statistic` gives, and why that widget takes a `String`.
    ///
    /// The decimals come from the *step*, not from the value: an axis stepping by
    /// 0.5 labels `1.0` rather than `1`, because a column of labels where one has
    /// a decimal point and the rest do not is a column that reads as ragged.
    private static String format(double value, double step) {
        if (step >= 1 || step == 0) {
            return String.format(java.util.Locale.ROOT, "%.0f", value);
        }
        var decimals = Math.min(6, (int) Math.ceil(-Math.log10(step)));
        return String.format(java.util.Locale.ROOT, "%." + decimals + "f", value);
    }

    /// Everything the painter needs, decided while the cascade was in hand.
    private record Painted(
            Ticks.Labelling labelling, List<Paragraph> labels, List<Paragraph> xLabels,
            List<Integer> colours, int grid, int ink, List<Series> series, Mode mode) {

        void paint(Frame frame, LogicalSize size) {
            if (labels.isEmpty() || size.width() <= 0 || size.height() <= 0) {
                return;
            }

            // The gutter is what the labels turned out to need. Measured rather
            // than guessed, so `1,000,000` and `5` both fit exactly.
            var gutter = 0.0;
            var lineHeight = 0.0;
            for (var label : labels) {
                var layout = label.layout(Paragraph.UNCONSTRAINED);
                gutter = Math.max(gutter, layout.width());
                lineHeight = Math.max(lineHeight, layout.height());
            }
            // Half a line under the plot even with no x labels: the lowest y
            // label is centred on the baseline, so without it the bottom half of
            // it is cut off by the edge of the box -- which the showcase's `p99
            // latency` card showed as a "120" with no bottom.
            var bottom = xLabels.isEmpty() ? lineHeight / 2 : lineHeight + GAP;

            var left = gutter + GAP;
            var plotWidth = size.width() - left;
            var plotHeight = size.height() - bottom - lineHeight / 2;
            if (plotWidth <= 0 || plotHeight <= 0) {
                return;
            }
            // Half a line height of headroom at the top, so the topmost label's
            // text is not clipped by the box it is drawn in.
            var top = lineHeight / 2;
            var y = Scale.linear(labelling.min(), labelling.max(), top + plotHeight, top);

            paintGrid(frame, left, size.width(), y, lineHeight, gutter);
            switch (mode) {
                case LINE -> paintLines(frame, left, plotWidth, y);
                case AREA -> paintBands(frame, left, plotWidth, y);
                case BAR -> paintBars(frame, left, plotWidth, y);
            }
            paintCategories(frame, left, plotWidth, top + plotHeight + GAP);
        }

        /// A gridline and its label per tick.
        private void paintGrid(
                Frame frame, double left, double width, Scale y, double lineHeight,
                double gutter) {

            var values = labelling.values();
            for (var i = 0; i < values.size(); i++) {
                var at = y.at(values.get(i));
                frame.fillRect((float) left, (float) at, (float) (width - left), 1, grid);
                // Right-aligned against the plot, which is what makes a column of
                // numbers of different widths readable.
                var label = labels.get(i);
                var layout = label.layout(Paragraph.UNCONSTRAINED);
                label.paint(frame, gutter - layout.width(), at - lineHeight / 2,
                        Paragraph.UNCONSTRAINED, ink);
            }
        }

        private void paintLines(Frame frame, double left, double plotWidth, Scale y) {
            try (var path = BlendPath.create()) {
                for (var s = 0; s < series.size(); s++) {
                    var values = series.get(s).values();
                    if (values.size() < 2) {
                        continue;
                    }
                    // One point per pixel at most, keeping the shape rather than
                    // the stride -- see Lttb.
                    var kept = Lttb.indices(values, Math.max(3, (int) Math.ceil(plotWidth) + 1));
                    var x = Scale.linear(0, values.size() - 1, left, left + plotWidth);

                    path.reset();
                    path.moveTo(x.at(kept[0]), y.at(values.get(kept[0])));
                    for (var i = 1; i < kept.length; i++) {
                        path.lineTo(x.at(kept[i]), y.at(values.get(kept[i])));
                    }
                    frame.strokePath(0, 0, path, STROKE,
                            BlendStrokeCap.ROUND, BlendStrokeJoin.ROUND, colours.get(s));
                }
            }
        }

        /// Stacked bands, drawn back to front so each sits on the one below.
        ///
        /// The running total per index is the band's top and the previous total
        /// is its bottom, which is what makes the bands add up to the number a
        /// reader assumes an area chart is showing.
        private void paintBands(Frame frame, double left, double plotWidth, Scale y) {
            var longest = series.stream().mapToInt(s -> s.values().size()).max().orElse(0);
            if (longest < 2) {
                return;
            }
            var x = Scale.linear(0, longest - 1, left, left + plotWidth);
            var beneath = new double[longest];

            try (var path = BlendPath.create()) {
                for (var s = 0; s < series.size(); s++) {
                    var values = series.get(s).values();
                    var top = new double[longest];
                    for (var i = 0; i < longest; i++) {
                        var value = i < values.size() ? Math.max(0, values.get(i)) : 0;
                        top[i] = beneath[i] + value;
                    }

                    path.reset();
                    path.moveTo(x.at(0), y.at(top[0]));
                    for (var i = 1; i < longest; i++) {
                        path.lineTo(x.at(i), y.at(top[i]));
                    }
                    // Back along the band beneath, so the fill is the difference
                    // between the two rather than everything under the top.
                    for (var i = longest - 1; i >= 0; i--) {
                        path.lineTo(x.at(i), y.at(beneath[i]));
                    }
                    path.closeSubPath();
                    // Nearly opaque: a stack's bands do not overlap, so there is
                    // nothing to see through them, and translucency here would
                    // only mix each band with the gridlines behind it.
                    frame.fillPath(0, 0, path, CssColor.fade(colours.get(s), 0.85));

                    System.arraycopy(top, 0, beneath, 0, longest);
                }
            }
        }

        /// A bar per point, grouped side by side when there is more than one
        /// series.
        ///
        /// The band per category is the plot divided by the point count; the bars
        /// share it with a gap between groups, which is what makes a group read
        /// as one category rather than as *n* separate ones.
        private void paintBars(Frame frame, double left, double plotWidth, Scale y) {
            var points = series.stream().mapToInt(s -> s.values().size()).max().orElse(0);
            if (points == 0 || series.isEmpty()) {
                return;
            }
            var band = plotWidth / points;
            // A fifth of the band as the gap between groups, which is the
            // proportion that reads as grouped at every size anybody uses.
            var groupWidth = band * 0.8;
            var barWidth = groupWidth / series.size();
            var zero = y.at(0);

            for (var i = 0; i < points; i++) {
                var bandLeft = left + i * band + (band - groupWidth) / 2;
                for (var s = 0; s < series.size(); s++) {
                    var values = series.get(s).values();
                    if (i >= values.size()) {
                        continue;
                    }
                    var at = y.at(values.get(i));
                    // Drawn from the baseline in whichever direction the value
                    // went, so a negative bar hangs below zero rather than
                    // being drawn upside down or not at all.
                    var barTop = Math.min(at, zero);
                    var height = Math.abs(at - zero);
                    frame.fillRect(
                            (float) (bandLeft + s * barWidth), (float) barTop,
                            (float) Math.max(1, barWidth - 1), (float) Math.max(1, height),
                            colours.get(s));
                }
            }
        }

        /// The x labels, one per category, centred under their point.
        ///
        /// Every *n*th label when they would collide, because a chart with
        /// overlapping labels is unreadable in a way that a chart with fewer is
        /// not. This is the legibility term `Ticks` leaves out, applied where the
        /// widths are finally known.
        private void paintCategories(Frame frame, double left, double plotWidth, double baseline) {
            if (xLabels.isEmpty()) {
                return;
            }
            // Bars sit *in* a band and lines sit *on* a point, so a label is
            // centred on the band's middle for one and on the point for the
            // other. Getting this wrong puts every bar chart's labels half a
            // band to the left, which looks like a rounding error and is not.
            var x = mode == Mode.BAR
                    ? Scale.linear(0, xLabels.size(), left, left + plotWidth)
                    : Scale.linear(0, Math.max(1, xLabels.size() - 1), left, left + plotWidth);
            var bandOffset = mode == Mode.BAR ? plotWidth / xLabels.size() / 2 : 0;
            var widest = 0.0;
            for (var label : xLabels) {
                widest = Math.max(widest, label.layout(Paragraph.UNCONSTRAINED).width());
            }
            var perLabel = plotWidth / Math.max(1, xLabels.size());
            var stride = Math.max(1, (int) Math.ceil((widest + GAP) / Math.max(1, perLabel)));

            for (var i = 0; i < xLabels.size(); i += stride) {
                var label = xLabels.get(i);
                var layout = label.layout(Paragraph.UNCONSTRAINED);
                // Centred on its point, then **pulled back inside the plot**.
                // The first and last labels are centred on the edges, so half of
                // each hangs outside the box and is clipped -- which the golden
                // showed as a "Sun" reading "Su". Nudging beats dropping them:
                // the ends of a time axis are the two labels a reader most wants.
                var centred = x.at(i) + bandOffset - layout.width() / 2;
                var clamped = Math.max(left,
                        Math.min(centred, left + plotWidth - layout.width()));
                label.paint(frame, clamped, baseline, Paragraph.UNCONSTRAINED, ink);
            }
        }
    }
}
