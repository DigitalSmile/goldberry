package io.github.digitalsmile.goldberry.widgets.data.donutchart;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.natives.blend2d.BlendPath;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.paint.Frame;
import io.github.digitalsmile.goldberry.render.model.LogicalSize;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.data.SeriesPalette;
import java.util.ArrayList;
import java.util.List;

/// The ring of a [DonutChart] — a **part**, so it is CSS-selectable and not
/// constructible.
///
/// ## Twelve o'clock, clockwise
///
/// Where a donut starts is not arbitrary: a reader's eye goes to the top and
/// travels clockwise, so the first slice starts at −π/2 and each one follows.
/// Starting at three o'clock — which is where the maths starts if nobody
/// intervenes — puts the largest slice somewhere nobody looks first.
///
/// ## The hole
///
/// 0.62 of the outer radius. Below about a half it reads as a pie with a dot
/// punched in it; above about three quarters the slices become arcs too thin to
/// compare. The number is here rather than in a stylesheet because §8's subset
/// has no way to express "a fraction of the smaller dimension", and a fixed
/// pixel radius would make a donut in a small panel a ring and one in a large
/// panel a hoop.
record DonutPlot(List<Double> values, List<String> labels) implements Widget.Leaf, Styled, Paints {

    /// The hole, as a fraction of the outer radius. See the class note.
    private static final double HOLE = 0.62;

    /// The gap between slices, in radians at the outer edge — §14's 2px surface
    /// gap, expressed as an angle because that is what an arc takes.
    private static final double GAP = 0.012;

    @Override
    public String cssType() {
        return "donut-plot";
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        var colours = new ArrayList<Integer>(values.size());
        for (var i = 0; i < values.size(); i++) {
            colours.add(SeriesPalette.of(context, i));
        }
        var painted = new Painted(List.copyOf(values), List.copyOf(colours));
        return Box.of().style(style).painting(painted::paint);
    }

    private record Painted(List<Double> values, List<Integer> colours) {

        void paint(Frame frame, LogicalSize size) {
            var total = values.stream().mapToDouble(Double::doubleValue).filter(v -> v > 0).sum();
            if (total <= 0 || size.width() <= 0 || size.height() <= 0) {
                // Every share is zero, so there is no whole to be part of. A ring
                // drawn anyway would be a chart asserting a division nobody made.
                return;
            }

            var cx = size.width() / 2;
            var cy = size.height() / 2;
            var outer = Math.min(size.width(), size.height()) / 2;
            var inner = outer * HOLE;
            if (outer <= 1) {
                return;
            }

            // Twelve o'clock, clockwise -- see the class note.
            var angle = -Math.PI / 2;
            try (var path = BlendPath.create()) {
                for (var i = 0; i < values.size(); i++) {
                    var value = Math.max(0, values.get(i));
                    if (value <= 0) {
                        continue;
                    }
                    var sweep = value / total * Math.PI * 2;
                    // The gap comes out of the slice rather than being drawn over
                    // it, so a slice's *area* stays its share -- painting a
                    // separator on top would make every slice slightly smaller
                    // than the number it stands for.
                    var from = angle + GAP / 2;
                    var to = angle + sweep - GAP / 2;
                    if (to > from) {
                        arc(path, cx, cy, outer, inner, from, to);
                        frame.fillPath(0, 0, path, colours.get(i));
                    }
                    angle += sweep;
                }
            }
        }

        /// One slice: out along the outer edge, in at the end, back along the
        /// inner edge.
        ///
        /// Two arcs rather than one, because SVG's `A` — which is what Blend2D's
        /// path takes — draws an arc *to* a point and a full circle has nowhere
        /// to go. `largeArc` is the flag that decides which way round a sweep of
        /// more than half a turn goes, and getting it wrong draws the complement
        /// of the slice, which is a picture that is exactly wrong rather than
        /// obviously wrong.
        private static void arc(BlendPath path, double cx, double cy,
                double outer, double inner, double from, double to) {

            var large = (to - from) > Math.PI;
            path.reset();
            path.moveTo(cx + outer * Math.cos(from), cy + outer * Math.sin(from));
            path.ellipticArcTo(outer, outer, 0, large, true,
                    cx + outer * Math.cos(to), cy + outer * Math.sin(to));
            path.lineTo(cx + inner * Math.cos(to), cy + inner * Math.sin(to));
            path.ellipticArcTo(inner, inner, 0, large, false,
                    cx + inner * Math.cos(from), cy + inner * Math.sin(from));
            path.closeSubPath();
        }
    }
}
