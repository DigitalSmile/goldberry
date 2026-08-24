package io.github.digitalsmile.goldberry.widgets.data.linechart;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.value.CssColor;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.handler.Handles;
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
import java.util.function.IntConsumer;

/// The drawn half of a chart — the `chart-plot` part itself, and the node the
/// pointer lands on.
///
/// A **part**, so it is CSS-selectable and not constructible
/// ([ADR-0065](../../../../../../../../book/src/adr/0065-a-part-is-styleable-and-not-constructible.md)).
/// [ChartPlot] is the widget a chart builds and this is what it builds: the
/// split exists because a hovered point is *state* and a widget is a value, so
/// something above the drawing has to remember which point that is.
///
/// Everything here happens in two places on purpose.
///
/// **In `render`**, which has the cascade and the text stack: the tick
/// labelling, the series colours, and the *shaping* of every label — including
/// the readout's, because by then the hovered index is known and only one point
/// needs writing out. Shaping is 56 µs and a widget tree is rebuilt every frame,
/// so a chart that shaped its own axis inside the painter would re-shape five
/// unchanged numbers sixty times a second
/// ([ADR-0037](../../../../../../../../book/src/adr/0037-what-the-text-path-costs.md)).
///
/// **In the painter**, which has the size: where the gridlines go, where the
/// polylines go, and how wide the label gutter turned out to be — all of it
/// through [PlotGeometry], which the pointer reads back through
/// [PaintedGeometry].
///
/// @param isolated the series shown alone, or -1 for all of them
/// @param hovered the point the pointer is over, or -1 for none
/// @param painted where the painter leaves its geometry for the pointer
///                (ADR-0054)
/// @param onHover what to tell the state when the hovered point changes
record ChartSurface(
        List<Series> series, List<String> categories, ChartPlot.Mode mode,
        int isolated, int hovered, PaintedGeometry painted, IntConsumer onHover)
        implements Widget.Leaf, Styled, Paints, Handles {

    /// How many y labels to aim for. Five is what a dashboard-sized chart reads
    /// well at; `Ticks` treats it as a preference and will answer four or six if
    /// those are rounder.
    private static final int Y_LABELS = 5;

    /// How thick a series line is. §8's subset has no `stroke-width`, and 2px is
    /// what a chart's line takes where a `sparkline`'s takes 1.5 — this one is
    /// drawn at full size and read from further away.
    private static final double STROKE = 2;

    /// The radius of the disc on a hovered point.
    ///
    /// 4, which with the ring around it clears §2.2's 8px hit target — not that
    /// anything is aiming at it: the whole plot is the hit target, and a marker
    /// this size is what makes "this point" legible against a 2px line.
    private static final double MARKER = 4;

    /// The padding inside the readout, and the gap between its rows.
    private static final double READOUT_PADDING = 8;
    private static final double READOUT_GAP = 4;

    /// How far the readout sits from the crosshair it belongs to.
    private static final double READOUT_OFFSET = 10;

    @Override
    public String cssType() {
        return "chart-plot";
    }

    /// Whether this chart's points sit *in* a band rather than *on* a position.
    ///
    /// A bar occupies a band and a line passes through a point, and every piece
    /// of x arithmetic in a chart needs to know which — the label offset, the
    /// crosshair, and the pointer mapping. One question, asked in one place.
    private boolean banded() {
        return mode == ChartPlot.Mode.BAR;
    }

    /// How many points the x axis has — the longest series'.
    ///
    /// The longest rather than the first: a series that stops early is a series
    /// with missing data at the end, and an axis sized to it would draw the
    /// others off the edge.
    private int points() {
        return series.stream().mapToInt(s -> s.values().size()).max().orElse(0);
    }

    /// Whether series `index` is drawn at all.
    ///
    /// Everything else about a series keeps its **original index**, because the
    /// index is the colour (ADR-0194): filtering the list would redraw an
    /// isolated fourth series in the first slot's hue and its own legend swatch
    /// would then disagree with it.
    private boolean shows(int index) {
        return isolated < 0 || isolated == index;
    }

    /// The series that are drawn, which is one of them when a legend entry has
    /// isolated it.
    private List<Series> shown() {
        var visible = new ArrayList<Series>(series.size());
        for (var i = 0; i < series.size(); i++) {
            if (shows(i)) {
                visible.add(series.get(i));
            }
        }
        return visible;
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        // **Over what is shown**, so isolating a small series rescales the axis
        // to it. That is the point of isolating one: a series that was a flat
        // line at the bottom of a chart scaled to a big one has nothing to read,
        // and re-labelling the axis is what turns it back into a chart.
        var shown = shown();
        var min = shown.stream().mapToDouble(Series::min).min().orElse(0);
        var max = shown.stream().mapToDouble(Series::max).max().orElse(0);
        if (mode == ChartPlot.Mode.AREA) {
            // A stack is as tall as its total, not as its tallest member.
            max = Math.max(max, stackedMax());
        }
        if (mode != ChartPlot.Mode.LINE) {
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
                List.copyOf(colours), grid, ink, series, mode, isolated,
                readout(style, context), hovered, painted);
        return Box.of().style(style).painting(plot::paint);
    }

    /// What the hovered point reads as, shaped — or null when nothing is
    /// hovered.
    ///
    /// **Shaped here and not in the painter**, which is only possible because
    /// the hovered index is part of this widget: one point's worth of text,
    /// re-shaped when the pointer moves to a different point and served from the
    /// cache when it moves within one (ADR-0037).
    private Readout readout(ComputedStyle style, Context context) {
        var index = hovered;
        if (index < 0 || index >= points() || series.isEmpty()) {
            return null;
        }
        // The HUD's tokens, not the surface's. A readout is a floating overlay
        // over content the reader is looking *through* it at, which is exactly
        // what `hud` is, and a chart that invented a fourth surface token would
        // be a chart the theme cannot restyle with the others (ADR-0195's
        // mechanism, `hud`'s palette).
        var title = index < categories.size() && !categories.get(index).isBlank()
                ? categories.get(index)
                : "#" + (index + 1);
        var rows = new ArrayList<Row>(series.size());
        for (var s = 0; s < series.size(); s++) {
            if (!shows(s)) {
                continue;
            }
            var values = series.get(s).values();
            if (index >= values.size()) {
                // A series that stops early has nothing at this point, and a row
                // reading zero would be a lie about missing data. Left out.
                continue;
            }
            rows.add(new Row(SeriesPalette.of(context, s),
                    context.paragraph(style, series.get(s).name()),
                    context.paragraph(style, readable(values.get(index)))));
        }
        if (rows.isEmpty()) {
            return null;
        }
        return new Readout(context.paragraph(style, title), List.copyOf(rows),
                context.color("--gb-hud-bg", 0xE61C212A),
                context.color("--gb-hud-border", 0x26FFFFFF),
                context.color("--gb-hud-text", 0xFFECEFF4),
                context.color("--gb-hud-text-muted", 0xFFA3ADBE));
    }

    /// The crosshair follows the pointer, and lets go when it leaves.
    ///
    /// `MOVED` and not `PRESSED`: reading a chart is not clicking on one, and a
    /// crosshair that needed a button held would be a chart you have to grab to
    /// read. Nothing is consumed — a chart inside a `scroll` must still scroll,
    /// and a right-click must still reach the context menu.
    @Override
    public void onPointer(PointerEvent event) {
        if (onHover == null) {
            return;
        }
        switch (event.kind()) {
            case EXITED -> onHover.accept(-1);
            case MOVED, ENTERED, PRESSED, RELEASED, CLICKED -> onHover.accept(at(event));
            case WHEEL -> {
                // A wheel over a chart is a scroll of whatever is behind it, and
                // the point under the pointer is about to be a different one.
                // Dropping the crosshair is more honest than moving it to where
                // the content used to be.
                onHover.accept(-1);
            }
            default -> {
            }
        }
    }

    /// Which point `event` is over, or -1 when it is not over the plot.
    ///
    /// Against the geometry of the **painted** frame (ADR-0054): the gutter
    /// depends on the shaped axis labels, which an event has no way to reach.
    /// Null before the first paint, which a pointer cannot reach in an
    /// application and a test can.
    private int at(PointerEvent event) {
        var geometry = painted == null ? null : painted.geometry();
        if (geometry == null) {
            return -1;
        }
        var local = event.local();
        if (!geometry.holds(local.x(), local.y())) {
            // Over the axis labels, or in the margin under them. A crosshair
            // that snapped to the first point whenever the pointer crossed the
            // numbers would be a chart reacting to being read.
            return -1;
        }
        return geometry.indexAt(local.x(), points(), banded());
    }

    /// The tallest column of a stack — what an [ChartPlot.Mode#AREA] axis has to
    /// reach.
    private double stackedMax() {
        var longest = points();
        var tallest = 0.0;
        for (var i = 0; i < longest; i++) {
            var total = 0.0;
            for (var s = 0; s < series.size(); s++) {
                var one = series.get(s);
                if (shows(s) && i < one.values().size()) {
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

    /// A number as a **readout**, which is a different question from an axis
    /// label.
    ///
    /// An axis rounds to its step, because a column of labels has to line up and
    /// the reader interpolates between them. A readout is the opposite: it exists
    /// to say what the number *is*, so it keeps the value's own precision. An
    /// axis stepping by 1000 beside a readout saying `1000` for 1043.7 would be a
    /// tooltip with no reason to exist.
    ///
    /// Up to three decimals, trailing zeros trimmed, root locale for
    /// [#format]'s reason.
    static String readable(double value) {
        if (!Double.isFinite(value)) {
            return String.valueOf(value);
        }
        if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            return String.format(java.util.Locale.ROOT, "%.0f", value);
        }
        var text = String.format(java.util.Locale.ROOT, "%.3f", value);
        // Trailing zeros only, and only after a decimal point: `1.500` reads as
        // `1.5` and `1500` must stay `1500`.
        var trimmed = text.indexOf('.') < 0 ? text
                : text.replaceAll("0+$", "").replaceAll("\\.$", "");
        return trimmed.isEmpty() || "-".equals(trimmed) ? "0" : trimmed;
    }

    /// One line of a readout: a series' colour, its name, and its value here.
    private record Row(int colour, Paragraph name, Paragraph value) {
    }

    /// The hovered point, written out — shaped in `render`, placed in the
    /// painter.
    private record Readout(
            Paragraph title, List<Row> rows, int background, int border, int ink, int muted) {
    }

    /// Everything the painter needs, decided while the cascade was in hand.
    private record Painted(
            Ticks.Labelling labelling, List<Paragraph> labels, List<Paragraph> xLabels,
            List<Integer> colours, int grid, int ink, List<Series> series, ChartPlot.Mode mode,
            int isolated, Readout readout, int hovered, PaintedGeometry painted) {

        private boolean shows(int index) {
            return isolated < 0 || isolated == index;
        }

        private boolean banded() {
            return mode == ChartPlot.Mode.BAR;
        }

        private int points() {
            return series.stream().mapToInt(s -> s.values().size()).max().orElse(0);
        }

        void paint(Frame frame, LogicalSize size) {
            var geometry = PlotGeometry.of(
                    labels, !xLabels.isEmpty(), labelling, size.width(), size.height());
            // Left for the pointer that arrives after this frame, including the
            // null: a plot that has become too small to draw is one no point can
            // be hovered in.
            if (painted != null) {
                painted.paintedAs(geometry);
            }
            if (geometry == null) {
                return;
            }

            paintGrid(frame, geometry, size.width());
            switch (mode) {
                case LINE -> paintLines(frame, geometry);
                case AREA -> paintBands(frame, geometry);
                case BAR -> paintBars(frame, geometry);
            }
            paintCategories(frame, geometry);
            // Last, so it is over the data rather than under it — which is the
            // whole point of a crosshair, and the order CSS paints content in
            // anyway.
            paintHover(frame, geometry);
        }

        /// A gridline and its label per tick.
        private void paintGrid(Frame frame, PlotGeometry geometry, double width) {
            var values = labelling.values();
            for (var i = 0; i < values.size(); i++) {
                var at = geometry.y().at(values.get(i));
                frame.fillRect((float) geometry.left(), (float) at,
                        (float) (width - geometry.left()), 1, grid);
                // Right-aligned against the plot, which is what makes a column of
                // numbers of different widths readable.
                var label = labels.get(i);
                var layout = label.layout(Paragraph.UNCONSTRAINED);
                label.paint(frame, geometry.gutter() - layout.width(),
                        at - geometry.lineHeight() / 2, Paragraph.UNCONSTRAINED, ink);
            }
        }

        private void paintLines(Frame frame, PlotGeometry geometry) {
            try (var path = BlendPath.create()) {
                for (var s = 0; s < series.size(); s++) {
                    if (!shows(s)) {
                        continue;
                    }
                    var values = series.get(s).values();
                    if (values.size() < 2) {
                        continue;
                    }
                    // One point per pixel at most, keeping the shape rather than
                    // the stride -- see Lttb.
                    var kept = Lttb.indices(values,
                            Math.max(3, (int) Math.ceil(geometry.plotWidth()) + 1));
                    var x = Scale.linear(0, values.size() - 1, geometry.left(), geometry.right());

                    path.reset();
                    path.moveTo(x.at(kept[0]), geometry.y().at(values.get(kept[0])));
                    for (var i = 1; i < kept.length; i++) {
                        path.lineTo(x.at(kept[i]), geometry.y().at(values.get(kept[i])));
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
        private void paintBands(Frame frame, PlotGeometry geometry) {
            var longest = points();
            if (longest < 2) {
                return;
            }
            var x = Scale.linear(0, longest - 1, geometry.left(), geometry.right());
            var beneath = new double[longest];

            try (var path = BlendPath.create()) {
                for (var s = 0; s < series.size(); s++) {
                    if (!shows(s)) {
                        continue;
                    }
                    var values = series.get(s).values();
                    var top = new double[longest];
                    for (var i = 0; i < longest; i++) {
                        var value = i < values.size() ? Math.max(0, values.get(i)) : 0;
                        top[i] = beneath[i] + value;
                    }

                    path.reset();
                    path.moveTo(x.at(0), geometry.y().at(top[0]));
                    for (var i = 1; i < longest; i++) {
                        path.lineTo(x.at(i), geometry.y().at(top[i]));
                    }
                    // Back along the band beneath, so the fill is the difference
                    // between the two rather than everything under the top.
                    for (var i = longest - 1; i >= 0; i--) {
                        path.lineTo(x.at(i), geometry.y().at(beneath[i]));
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
        private void paintBars(Frame frame, PlotGeometry geometry) {
            var points = points();
            if (points == 0 || series.isEmpty()) {
                return;
            }
            var band = geometry.plotWidth() / points;
            // A fifth of the band as the gap between groups, which is the
            // proportion that reads as grouped at every size anybody uses.
            var groupWidth = band * 0.8;
            var barWidth = groupWidth / series.size();
            var zero = geometry.y().at(0);

            for (var i = 0; i < points; i++) {
                var bandLeft = geometry.left() + i * band + (band - groupWidth) / 2;
                for (var s = 0; s < series.size(); s++) {
                    if (!shows(s)) {
                        continue;
                    }
                    var values = series.get(s).values();
                    if (i >= values.size()) {
                        continue;
                    }
                    var at = geometry.y().at(values.get(i));
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
        private void paintCategories(Frame frame, PlotGeometry geometry) {
            if (xLabels.isEmpty()) {
                return;
            }
            var baseline = geometry.bottom() + PlotGeometry.GAP;
            var widest = 0.0;
            for (var label : xLabels) {
                widest = Math.max(widest, label.layout(Paragraph.UNCONSTRAINED).width());
            }
            var perLabel = geometry.plotWidth() / Math.max(1, xLabels.size());
            var stride = Math.max(1,
                    (int) Math.ceil((widest + PlotGeometry.GAP) / Math.max(1, perLabel)));

            for (var i = 0; i < xLabels.size(); i += stride) {
                var label = xLabels.get(i);
                var layout = label.layout(Paragraph.UNCONSTRAINED);
                // Centred on its point -- which is a band's middle for bars and
                // the point itself for lines, one question asked in one place --
                // then **pulled back inside the plot**. The first and last labels
                // are centred on the edges, so half of each hangs outside the box
                // and is clipped, which the golden showed as a "Sun" reading
                // "Su". Nudging beats dropping them: the ends of a time axis are
                // the two labels a reader most wants.
                var centred = geometry.xOf(i, xLabels.size(), banded()) - layout.width() / 2;
                var clamped = Math.max(geometry.left(),
                        Math.min(centred, geometry.right() - layout.width()));
                label.paint(frame, clamped, baseline, Paragraph.UNCONSTRAINED, ink);
            }
        }

        /// The crosshair, the markers on it, and the readout beside it.
        private void paintHover(Frame frame, PlotGeometry geometry) {
            var points = points();
            if (hovered < 0 || hovered >= points || readout == null) {
                return;
            }
            var x = geometry.xOf(hovered, points, banded());

            if (banded()) {
                // A **band highlight** rather than a line: a bar owns a width, and
                // a hairline down the middle of a group of bars points at the gap
                // between two of them. The rectangle says "this category", which
                // is what a bar chart's x actually is.
                var band = geometry.plotWidth() / points;
                frame.fillRect((float) (x - band / 2), (float) geometry.top(),
                        (float) band, (float) geometry.plotHeight(),
                        CssColor.fade(ink, 0.08));
            } else {
                frame.fillRect((float) x, (float) geometry.top(), 1,
                        (float) geometry.plotHeight(), CssColor.fade(ink, 0.45));
                paintMarkers(frame, geometry, x);
            }
            paintReadout(frame, geometry, x);
        }

        /// A disc on each series' value at the hovered point.
        ///
        /// Only where a series *has* a position there — a line encodes by
        /// position, so the marker is the answer to "which value"; a stacked band
        /// has no single position to mark, and a disc on the top of a stack would
        /// mark the running total rather than the series.
        private void paintMarkers(Frame frame, PlotGeometry geometry, double x) {
            if (mode != ChartPlot.Mode.LINE) {
                return;
            }
            try (var dot = BlendPath.create()) {
                for (var s = 0; s < series.size(); s++) {
                    if (!shows(s)) {
                        continue;
                    }
                    var values = series.get(s).values();
                    if (hovered >= values.size()) {
                        continue;
                    }
                    var y = geometry.y().at(values.get(hovered));
                    // Two half-arcs, because SVG's `A` -- which is what a
                    // Blend2D path takes -- cannot draw a full circle in one
                    // segment: the start and end points would coincide and the
                    // arc is undefined. Getting it wrong gives a wedge, a square
                    // or nothing, and all three look plausible until a test
                    // reads the corners of the bounding box.
                    dot.reset();
                    dot.moveTo(x - MARKER, y);
                    dot.ellipticArcTo(MARKER, MARKER, 0, false, true, x + MARKER, y);
                    dot.ellipticArcTo(MARKER, MARKER, 0, false, true, x - MARKER, y);
                    dot.closeSubPath();
                    // A ring in the readout's background rather than in the
                    // surface's: the marker sits on the series line, and a ring
                    // the colour of the page would cut the line in half wherever
                    // the chart is on a card.
                    frame.strokePath(0, 0, dot, 2,
                            BlendStrokeCap.BUTT, BlendStrokeJoin.MITER_CLIP,
                            readout.background());
                    frame.fillPath(0, 0, dot, colours.get(s));
                }
            }
        }

        /// The readout, placed beside the crosshair and inside the plot.
        ///
        /// **Pinned to the top of the plot** rather than following the pointer's
        /// y. A readout that tracked both axes would flicker up and down as the
        /// pointer wandered along a flat line, and it would cover the very point
        /// it is describing; pinned, it moves once per point and never over the
        /// marker.
        ///
        /// **Flipped at the middle.** Right of the crosshair while the crosshair
        /// is in the left half, left of it after that — so it is never clipped by
        /// the edge it is approaching, which a fixed side would be for half of
        /// every chart.
        private void paintReadout(Frame frame, PlotGeometry geometry, double x) {
            var lineHeight = geometry.lineHeight();
            var titleLayout = readout.title().layout(Paragraph.UNCONSTRAINED);

            var swatch = Math.min(8, lineHeight);
            var swatchGap = 6;
            var valueGap = 12;
            var widest = titleLayout.width();
            for (var row : readout.rows()) {
                widest = Math.max(widest, swatch + swatchGap
                        + row.name().layout(Paragraph.UNCONSTRAINED).width() + valueGap
                        + row.value().layout(Paragraph.UNCONSTRAINED).width());
            }
            var width = widest + READOUT_PADDING * 2;
            var height = READOUT_PADDING * 2 + lineHeight
                    + readout.rows().size() * (lineHeight + READOUT_GAP);

            var onTheRight = x < geometry.left() + geometry.plotWidth() / 2;
            var left = onTheRight ? x + READOUT_OFFSET : x - READOUT_OFFSET - width;
            // Inside the plot whichever side it went, because a readout is not
            // allowed to be the one thing in the chart that hangs over the axis
            // labels.
            left = Math.max(geometry.left(), Math.min(left, geometry.right() - width));
            var top = geometry.top();

            try (var path = BlendPath.create()) {
                io.github.digitalsmile.goldberry.paint.RoundRect.addTo(
                        path, 0, 0, width, height, 6);
                frame.fillPath(left, top, path, readout.background());
                path.reset();
                io.github.digitalsmile.goldberry.paint.RoundRect.addTo(
                        path, 0.5, 0.5, width - 1, height - 1, 5.5);
                frame.strokePath(left, top, path, 1,
                        BlendStrokeCap.BUTT, BlendStrokeJoin.MITER_CLIP, readout.border());
            }

            var y = top + READOUT_PADDING;
            readout.title().paint(frame, left + READOUT_PADDING, y,
                    Paragraph.UNCONSTRAINED, readout.ink());
            y += lineHeight + READOUT_GAP;
            for (var row : readout.rows()) {
                frame.fillRect((float) (left + READOUT_PADDING),
                        (float) (y + (lineHeight - swatch) / 2),
                        (float) swatch, (float) swatch, row.colour());
                row.name().paint(frame, left + READOUT_PADDING + swatch + swatchGap, y,
                        Paragraph.UNCONSTRAINED, readout.muted());
                var valueLayout = row.value().layout(Paragraph.UNCONSTRAINED);
                // Right-aligned against the box, so a column of numbers lines up
                // the way the axis labels do.
                row.value().paint(frame,
                        left + width - READOUT_PADDING - valueLayout.width(), y,
                        Paragraph.UNCONSTRAINED, readout.ink());
                y += lineHeight + READOUT_GAP;
            }
        }
    }
}
