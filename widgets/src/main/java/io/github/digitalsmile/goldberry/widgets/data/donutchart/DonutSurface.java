package io.github.digitalsmile.goldberry.widgets.data.donutchart;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.css.ComputedStyle;
import io.github.digitalsmile.goldberry.css.value.CssColor;
import io.github.digitalsmile.goldberry.input.event.KeyEvent;
import io.github.digitalsmile.goldberry.input.event.PointerEvent;
import io.github.digitalsmile.goldberry.input.handler.Handles;
import io.github.digitalsmile.goldberry.natives.blend2d.BlendPath;
import io.github.digitalsmile.goldberry.paint.Box;
import io.github.digitalsmile.goldberry.paint.Frame;
import io.github.digitalsmile.goldberry.render.model.LogicalSize;
import io.github.digitalsmile.goldberry.text.Paragraph;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widget.style.Paints;
import io.github.digitalsmile.goldberry.widget.style.Styled;
import io.github.digitalsmile.goldberry.widgets.data.SeriesPalette;

/// The ring itself — the `donut-plot` part, and the node the pointer lands on.
///
/// A **part**, so it is CSS-selectable and not constructible
/// (ADR-0065).
/// [DonutPlot] is what a `donut-chart` builds and this is what *it* builds; the
/// split is there because a hovered slice is state and a widget is a value
/// (ADR-0198).
///
/// ## The readout goes in the hole
///
/// An axis chart's readout has to be placed and flipped and kept inside the plot.
/// A donut already has a reserved empty circle in the middle of it, which is the
/// one place on the chart that cannot cover the data and cannot be clipped by the
/// box — so the hovered slice's name and share are written there, and the other
/// slices fade so the ring says which one it is talking about.
///
/// @param values   one number per slice
/// @param labels   one name per slice, for the readout
/// @param hovered  the slice being read, or -1 for none
/// @param onHover  where the pointer says the readout is, absolutely — and
///                 whether that changed anything
/// @param onWalk   a **relative** step, for the keyboard: a widget is the
///                 description the last frame was built from, so two arrows
///                 between two frames would both be computed from the slice
///                 before either of them
record DonutSurface(
        List<Double> values,
        List<String> labels,
        int hovered,
        java.util.function.IntPredicate onHover,
        java.util.function.IntPredicate onWalk)
        implements Widget.Leaf, Styled, Paints, Handles {

    /// The hole, as a fraction of the outer radius.
    ///
    /// Below about a half it reads as a pie with a dot punched in it; above about
    /// three quarters the slices become arcs too thin to compare. The number is
    /// here rather than in a stylesheet because §8's subset has no way to express
    /// "a fraction of the smaller dimension", and a fixed pixel radius would make
    /// a donut in a small panel a ring and one in a large panel a hoop.
    static final double HOLE = 0.62;

    /// The gap between slices, in radians at the outer edge — §14's 2px surface
    /// gap, expressed as an angle because that is what an arc takes.
    private static final double GAP = 0.012;

    /// What a slice nobody is reading fades to while another is being read.
    private static final double FADED = 0.3;

    @Override
    public String cssType() {
        return "donut-plot";
    }

    /// **Focusable**, so the chart is reachable without a pointer.
    ///
    /// §2.2 requires everything to be reachable and `charts.md` §3.5 says so
    /// again with feeling: a browser dashboard is a pointer surface and a desktop
    /// application is not. A donut with nothing in it is not a Tab stop, because
    /// there is nothing there to read.
    @Override
    public boolean isFocusable() {
        return drawn() > 0;
    }

    /// How many slices are actually drawn — a zero share is not one of them.
    private int drawn() {
        return (int) values.stream().filter(v -> v > 0).count();
    }

    @Override
    public Box render(ComputedStyle style, List<Box> children, Context context) {
        var colours = new ArrayList<Integer>(values.size());
        for (var i = 0; i < values.size(); i++) {
            colours.add(SeriesPalette.of(context, i));
        }
        var painted = new Painted(List.copyOf(values), List.copyOf(colours), hovered, readout(style, context));
        return Box.of().style(style).painting(painted::paint);
    }

    /// The hovered slice, written out — or null when nothing is hovered.
    ///
    /// Shaped in `render` for
    /// [io.github.digitalsmile.goldberry.widgets.data.linechart.ChartSurface]'s
    /// reason: the hovered index is part of this widget, so this is two strings
    /// rather than two per slice, and moving between slices re-shapes two of them
    /// (ADR-0037).
    private @Nullable Readout readout(ComputedStyle style, Context context) {
        if (hovered < 0 || hovered >= values.size() || !(values.get(hovered) > 0)) {
            return null;
        }
        var total = values.stream()
                .mapToDouble(Double::doubleValue)
                .filter(v -> v > 0)
                .sum();
        if (total <= 0) {
            return null;
        }
        var name = hovered < labels.size() && labels.get(hovered) != null ? labels.get(hovered) : "";
        return new Readout(
                context.paragraph(style, name),
                context.paragraph(style, share(values.get(hovered), total)),
                style.color(),
                context.color("--gb-hud-text-muted", 0xFFA3ADBE));
    }

    /// A slice's share, as a percentage a reader can compare.
    ///
    /// **The share and not the value**, which is the one place a donut differs
    /// from every other chart's readout: a part-to-whole chart is *about* the
    /// proportion, and a reader who wanted the raw number wanted a bar chart.
    /// Rounded to whole percent, because a donut is not a table — with `<1%` for
    /// a slice that would otherwise round away to nothing it plainly is not.
    static String share(double value, double total) {
        var percent = value / total * 100;
        if (percent > 0 && percent < 0.5) {
            return "<1%";
        }
        return String.format(java.util.Locale.ROOT, "%.0f%%", percent);
    }

    /// The pointer picks a slice, and lets go when it leaves the ring.
    // `IntPredicate` is the right type and most of these call sites ignore its
    // answer on purpose. The boolean means "was that handled", and it is only
    // interesting where this widget must decide whether to consume the event --
    // which is the one `if (onHover.test(-1))` below. Everywhere else the state
    // is being *told* where the readout went, and there is nothing to decide.
    @SuppressWarnings("ReturnValueIgnored")
    @Override
    public void onPointer(PointerEvent event) {
        if (onHover == null) {
            return;
        }
        switch (event.kind()) {
            case EXITED -> onHover.test(-1);
            case MOVED, ENTERED, PRESSED, RELEASED, CLICKED -> onHover.test(at(event));
            default -> {}
        }
    }

    /// `Left` and `Right` walk the slices, `Home` and `End` are the ends,
    /// `Escape` lets go.
    ///
    /// `Right` goes **clockwise**, which is the direction the ring is drawn in —
    /// an arrow that moved anticlockwise through a clockwise chart would be a
    /// control disagreeing with its own picture.
    ///
    /// **`Up` and `Down` are left alone**, deliberately, and it is not because
    /// there is nothing for them to mean here: a chart is very often inside a
    /// `scroll`, and a focused widget that consumed the vertical arrows would
    /// swallow the keys that move the page. Two arrows are enough to reach every
    /// slice.
    // `IntPredicate` is the right type and most of these call sites ignore its
    // answer on purpose. The boolean means "was that handled", and it is only
    // interesting where this widget must decide whether to consume the event --
    // which is the one `if (onHover.test(-1))` below. Everywhere else the state
    // is being *told* where the readout went, and there is nothing to decide.
    @SuppressWarnings("ReturnValueIgnored")
    @Override
    public void onKey(KeyEvent event) {
        if (onHover == null
                || onWalk == null
                || event.kind() != KeyEvent.Kind.PRESSED
                || !event.modifiers().none()) {
            return;
        }
        if (values.isEmpty()) {
            return;
        }
        switch (event.key()) {
            case RIGHT -> {
                onWalk.test(1);
                event.consume();
            }
            case LEFT -> {
                onWalk.test(-1);
                event.consume();
            }
            case HOME -> {
                onHover.test(first(1));
                event.consume();
            }
            case END -> {
                onHover.test(first(-1));
                event.consume();
            }
            case ESCAPE -> {
                // Consumed only if it cleared something, so `Escape` still
                // closes the dialog a chart happens to be sitting in.
                if (onHover.test(-1)) {
                    event.consume();
                }
            }
            default -> {}
        }
    }

    /// The first drawn slice from whichever end `direction` starts at.
    ///
    /// An absolute answer, so it may be computed here: which end of the ring is
    /// which does not depend on where the readout currently is.
    private int first(int direction) {
        if (direction > 0) {
            for (var i = 0; i < values.size(); i++) {
                if (values.get(i) > 0) {
                    return i;
                }
            }
        } else {
            for (var i = values.size() - 1; i >= 0; i--) {
                if (values.get(i) > 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /// Which slice `event` is on, or -1 when it is on none.
    private int at(PointerEvent event) {
        var local = event.local();
        var geometry = DonutGeometry.of(local.width(), local.height(), HOLE);
        return geometry == null ? -1 : geometry.sliceAt(local.x(), local.y(), values);
    }

    /// The hovered slice's name and share, shaped.
    private record Readout(Paragraph name, Paragraph share, int ink, int muted) {}

    private record Painted(List<Double> values, List<Integer> colours, int hovered, Readout readout) {

        void paint(Frame frame, LogicalSize size) {
            var total = values.stream()
                    .mapToDouble(Double::doubleValue)
                    .filter(v -> v > 0)
                    .sum();
            var geometry = DonutGeometry.of(size.width(), size.height(), HOLE);
            if (total <= 0 || geometry == null) {
                // Every share is zero, so there is no whole to be part of. A ring
                // drawn anyway would be a chart asserting a division nobody made.
                return;
            }

            var angle = DonutGeometry.START;
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
                        arc(path, geometry, from, to);
                        // Faded when another slice is being read, so the ring says
                        // which one the hole is talking about. Nothing is faded
                        // when nothing is hovered, which is what makes a golden
                        // image of a donut the donut.
                        var colour =
                                hovered < 0 || hovered == i ? colours.get(i) : CssColor.fade(colours.get(i), FADED);
                        frame.fillPath(0, 0, path, colour);
                    }
                    angle += sweep;
                }
            }
            paintReadout(frame, geometry);
        }

        /// The name and the share, centred in the hole.
        ///
        /// **Only what fits.** The hole is a circle and text is a rectangle, so a
        /// line wider than the chord across it would run out over the ring; a
        /// slice called "Uncategorised traffic" gets its share and no name, and
        /// the faded ring plus the legend still say which slice it is. Shrinking
        /// the text instead would mean a readout whose size depends on its
        /// content, which reads as a wobble as the pointer crosses the chart.
        private void paintReadout(Frame frame, DonutGeometry geometry) {
            if (readout == null) {
                return;
            }
            var nameLayout = readout.name().layout(Paragraph.UNCONSTRAINED);
            var shareLayout = readout.share().layout(Paragraph.UNCONSTRAINED);
            // The widest rectangle that fits in the hole is its chord at the
            // text's own height; a fraction of the diameter is the same idea
            // without the arithmetic being load-bearing.
            var room = geometry.inner() * 1.7;
            var showName = nameLayout.width() <= room && !readout.name().text().isBlank();
            var height = shareLayout.height() + (showName ? nameLayout.height() : 0);
            var top = geometry.cy() - height / 2;

            if (showName) {
                readout.name()
                        .paint(
                                frame,
                                geometry.cx() - nameLayout.width() / 2,
                                top,
                                Paragraph.UNCONSTRAINED,
                                readout.muted());
                top += nameLayout.height();
            }
            readout.share()
                    .paint(frame, geometry.cx() - shareLayout.width() / 2, top, Paragraph.UNCONSTRAINED, readout.ink());
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
        private static void arc(BlendPath path, DonutGeometry geometry, double from, double to) {

            var cx = geometry.cx();
            var cy = geometry.cy();
            var outer = geometry.outer();
            var inner = geometry.inner();
            var large = (to - from) > Math.PI;
            path.reset();
            path.moveTo(cx + outer * Math.cos(from), cy + outer * Math.sin(from));
            path.ellipticArcTo(outer, outer, 0, large, true, cx + outer * Math.cos(to), cy + outer * Math.sin(to));
            path.lineTo(cx + inner * Math.cos(to), cy + inner * Math.sin(to));
            path.ellipticArcTo(inner, inner, 0, large, false, cx + inner * Math.cos(from), cy + inner * Math.sin(from));
            path.closeSubPath();
        }
    }
}
