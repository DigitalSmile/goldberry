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
record ChartPlot(List<Series> series, List<String> categories) implements Widget.Leaf, Styled,
        Paints {

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
                List.copyOf(colours), grid, ink, series);
        return Box.of().style(style).painting(plot::paint);
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
            List<Integer> colours, int grid, int ink, List<Series> series) {

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
            var bottom = xLabels.isEmpty() ? 0 : lineHeight + GAP;

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
            paintSeries(frame, left, plotWidth, y);
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

        private void paintSeries(Frame frame, double left, double plotWidth, Scale y) {
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
            var x = Scale.linear(0, Math.max(1, xLabels.size() - 1), left, left + plotWidth);
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
                var centred = x.at(i) - layout.width() / 2;
                var clamped = Math.max(left,
                        Math.min(centred, left + plotWidth - layout.width()));
                label.paint(frame, clamped, baseline, Paragraph.UNCONSTRAINED, ink);
            }
        }
    }
}
