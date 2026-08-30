package io.github.digitalsmile.goldberry.widgets.data.sparkline;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.value.CssColor;
import io.github.digitalsmile.goldberry.kdl.KdlNode;
import io.github.digitalsmile.goldberry.natives.blend2d.BlendPath;
import io.github.digitalsmile.goldberry.natives.blend2d.enums.BlendStrokeCap;
import io.github.digitalsmile.goldberry.natives.blend2d.enums.BlendStrokeJoin;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.paint.Frame;
import io.github.digitalsmile.goldberry.render.model.LogicalSize;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.attr.Attributed;
import io.github.digitalsmile.goldberry.widget.attr.Attributes;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;
import io.github.digitalsmile.goldberry.widgets.data.Lttb;
import io.github.digitalsmile.goldberry.widgets.data.Scale;
import io.github.digitalsmile.goldberry.widgets.markup.Markup;
import io.github.digitalsmile.goldberry.widgets.markup.Wiring;

/// A trend with no axes — `docs/core-widgets.md` §11's `sparkline`, and the child
/// `statistic` has been specified to hold since M2 (ADR-0164).
///
/// ```java
/// new Sparkline(List.of(4.0, 9.0, 7.0, 12.0, 11.0)).fill(true).marker(true);
/// ```
///
/// **The first chart, and the smallest.** No axes, no legend, no tooltip: a
/// sparkline is a shape beside a number, read for its direction rather than its
/// values. Everything the other four will need — scales, ticks, a legend — it
/// deliberately does not have.
///
/// ## It is one series, so it takes `color`
///
/// A series palette is for telling series *apart*
/// ([ADR-0194](../../../../../../../../book/src/adr/0194-a-series-colour-is-derived-from-nord-not-taken-from-it.md)),
/// and there is nothing here to tell apart. So a sparkline is drawn in the CSS
/// `color` it inherits, exactly like text — which means it takes a `statistic`'s
/// delta colour by sitting inside it, and an application recolours one with the
/// property it would already reach for. The palette arrives with `line-chart`,
/// which is the first widget that has two of anything.
///
/// ## What it draws
///
/// A polyline across the full width, scaled to the data's own minimum and
/// maximum — **not to zero**. A sparkline's job is the shape of the change, and
/// a series between 1000 and 1004 baselined at zero is a flat line that says
/// nothing. A flat series is centred rather than divided by a zero range.
///
/// Optionally a fill beneath it at low alpha, and a marker on the last point:
/// §11's "current value" affordance, which is the one number a sparkline is
/// sometimes asked to point at.
///
/// ## Long series
///
/// Reduced by [Lttb] to about one point per pixel before drawing. Not for speed:
/// a hundred thousand points in two hundred pixels is five hundred per pixel, and
/// drawing them all means whichever one comes last wins — a picture that omits
/// the spike, which is the one thing a sparkline exists to show.
///
/// @param values     the series, in order; fewer than two draws nothing
/// @param fill       whether to fill beneath the line
/// @param marker     whether to mark the last point
/// @param attributes `id` and `class`, exactly as on the primitives
@Markup("sparkline")
public record Sparkline(List<Double> values, boolean fill, boolean marker, Attributes attributes)
        implements Widget.Leaf, Styled, Paints, Attributed<Sparkline> {

    /// How thick the line is, in logical pixels.
    ///
    /// In Java rather than in a stylesheet because §8's subset has no
    /// `stroke-width` and inventing one for a single widget would be inventing a
    /// language. 1.5 rather than the 2 a full chart's line takes: a sparkline is
    /// drawn at a fraction of the size and a 2px line at 20px tall is a shape
    /// made of stroke.
    private static final double STROKE = 1.5;

    /// The radius of the last-point marker, in logical pixels.
    private static final double MARKER = 2.0;

    /// How faint the fill is under the line.
    private static final double FILL_ALPHA = 0.18;

    public Sparkline {
        values = List.copyOf(values == null ? List.of() : values);
        attributes = attributes == null ? Attributes.NONE : attributes;
    }

    public Sparkline(List<Double> values) {
        this(values, false, false, Attributes.NONE);
    }

    /// This sparkline with the area beneath the line filled.
    public Sparkline fill(boolean value) {
        return new Sparkline(values, value, marker, attributes);
    }

    /// This sparkline with its last point marked — §11's "current value".
    public Sparkline marker(boolean value) {
        return new Sparkline(values, fill, value, attributes);
    }

    @Override
    public String cssType() {
        return "sparkline";
    }

    @Override
    public String id() {
        return attributes.id();
    }

    @Override
    public Set<String> classes() {
        return attributes.classes();
    }

    @Override
    public Object key() {
        return attributes.key();
    }

    @Override
    public Sparkline withAttributes(Attributes value) {
        return new Sparkline(values, fill, marker, value);
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        var argb = style.color();
        return Box.of().style(style).painting((frame, size) -> paint(frame, size, argb));
    }

    /// Draws the series into `size`, from the canvas's own origin.
    ///
    /// Package-private rather than private so the test can drive it against a
    /// size without a layout — the drawing is the whole of this widget, and a
    /// test that had to build a tree to reach it would be testing the tree.
    void paint(Frame frame, LogicalSize size, int argb) {
        if (values.size() < 2 || size.width() <= 0 || size.height() <= 0) {
            // One point is not a trend, and nothing is not a picture. Drawing a
            // dot for a single value would be a chart asserting a shape it does
            // not have.
            return;
        }

        // About one point per pixel. More is drawing into the same column twice;
        // the +1 is so a two-pixel-wide sparkline still has an end to draw to.
        var kept = Lttb.indices(values, Math.max(3, (int) Math.ceil(size.width()) + 1));

        var min = Double.POSITIVE_INFINITY;
        var max = Double.NEGATIVE_INFINITY;
        for (var value : values) {
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        if (!Double.isFinite(min) || !Double.isFinite(max)) {
            return;
        }

        // Inset by half the stroke top and bottom, so the line's own thickness
        // stays inside the box rather than being clipped in half at the extremes
        // -- which is what a maximum drawn at y=0 looks like.
        var inset = STROKE / 2 + (marker ? MARKER : 0);
        var top = inset;
        var bottom = Math.max(inset, size.height() - inset);

        // The y range is **swapped** -- bottom for the minimum -- which is the
        // whole of "a frame's y grows down and a chart's values grow up", stated
        // once where the answer is known. A flat series centres, which is
        // `Scale`'s rule rather than this widget's.
        var x = Scale.linear(0, values.size() - 1, 0, size.width());
        var y = Scale.linear(min, max, bottom, top);

        var points = new ArrayList<double[]>(kept.length);
        for (var index : kept) {
            points.add(new double[] {x.at(index), y.at(values.get(index))});
        }

        // One path per paint. A `BlendPath` is a native allocation, which is why
        // `BoxPainter` keeps one and resets it -- but a painter is handed no such
        // scratch, and a sparkline draws one polyline rather than one per box.
        // Measured before optimised, per the rule the frame path was written by.
        try (var path = BlendPath.create()) {
            if (fill) {
                path.moveTo(points.getFirst()[0], bottom);
                for (var point : points) {
                    path.lineTo(point[0], point[1]);
                }
                path.lineTo(points.getLast()[0], bottom);
                path.closeSubPath();
                frame.fillPath(0, 0, path, CssColor.fade(argb, FILL_ALPHA));
                path.reset();
            }

            path.moveTo(points.getFirst()[0], points.getFirst()[1]);
            for (var point : points.subList(1, points.size())) {
                path.lineTo(point[0], point[1]);
            }
            // Round, like every stroke in the toolkit: a sparkline's turns are
            // sharp enough at this size that a miter join spikes past the box.
            frame.strokePath(0, 0, path, STROKE, BlendStrokeCap.ROUND, BlendStrokeJoin.ROUND, argb);
        }

        if (marker) {
            // A disc, not a square. Two half-arcs, because SVG's `A` -- which is
            // what Blend2D's path takes -- cannot draw a full circle in one
            // segment: the start and end points would coincide and the arc is
            // undefined. The same two-arc trick every SVG circle is made of.
            var last = points.getLast();
            try (var dot = BlendPath.create()) {
                dot.moveTo(last[0] - MARKER, last[1]);
                dot.ellipticArcTo(MARKER, MARKER, 0, false, true, last[0] + MARKER, last[1]);
                dot.ellipticArcTo(MARKER, MARKER, 0, false, true, last[0] - MARKER, last[1]);
                dot.closeSubPath();
                frame.fillPath(0, 0, dot, argb);
            }
        }
    }

    /// Builds a `sparkline` from markup.
    ///
    /// The values come from the node's arguments — `sparkline 4 9 7 12` — which
    /// is the one chart shape small enough to write inline, and exactly what
    /// `content-widgets.md` §3.2 says of inline KDL data: "for small static
    /// data".
    public static Widget inflate(KdlNode node, List<Widget> children, Wiring wiring) {
        var values = new ArrayList<Double>();
        for (var argument : node.arguments()) {
            // Non-numbers are skipped rather than refused: a document is
            // reloaded on every keystroke while it is being written, and a
            // half-typed number should not take the window down.
            if (argument instanceof io.github.digitalsmile.goldberry.kdl.KdlValue.Num number) {
                values.add(number.value());
            }
        }
        return new Sparkline(values, node.booleanProperty("fill"), node.booleanProperty("marker"), Attributes.of(node));
    }
}
