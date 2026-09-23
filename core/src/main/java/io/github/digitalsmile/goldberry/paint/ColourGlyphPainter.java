package io.github.digitalsmile.goldberry.paint;

import java.util.Objects;
import java.util.function.BiConsumer;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.css.value.Affine;
import io.github.digitalsmile.goldberry.natives.blend2d.BlendGradient;
import io.github.digitalsmile.goldberry.natives.blend2d.BlendMatrix;
import io.github.digitalsmile.goldberry.natives.blend2d.enums.BlendCompOp;
import io.github.digitalsmile.goldberry.natives.blend2d.enums.BlendExtendMode;
import io.github.digitalsmile.goldberry.render.model.PhysicalSize;
import io.github.digitalsmile.goldberry.text.font.sfnt.ColorPaint;
import io.github.digitalsmile.goldberry.text.font.sfnt.ColorPaint.ColorLine;
import io.github.digitalsmile.goldberry.text.font.sfnt.ColorPaints;
import io.github.digitalsmile.goldberry.text.font.sfnt.CompositeMode;

/// Draws one `COLR` version 1 colour glyph — a paint graph — onto a [Frame]
/// ([ADR-0456]).
///
/// ## The coordinate spaces
///
/// A graph is written in the face's **design units, y up**. The painter
/// concatenates one matrix onto the frame — scale by `size / unitsPerEm`, flip
/// y, move to the glyph's origin on the baseline — and then draws every node in
/// design units. A `PaintTransform` concatenates its own matrix on top of that
/// and restores it afterwards, exactly as a `canvas` painter composes with
/// whatever the tree has set (ADR-0390).
///
/// ## A glyph is a clip, and a clip is a fill
///
/// `PaintGlyph` says "draw this paint inside that outline". The rasterizer has no
/// path clip, and needs none for what fonts actually write: the paint under a
/// glyph is nearly always a colour or a gradient, perhaps under a transform, and
/// **filling the outline with it is the same picture as clipping to it**. So a
/// glyph node becomes one `fillPath`, and a transform between the glyph and its
/// gradient becomes the gradient's own matrix rather than the frame's, because
/// the outline must not move with it.
///
/// The rare glyph whose paint is itself a graph — layers, another glyph, a
/// composite — is drawn as the composite it means: the subtree offscreen, kept
/// only where the outline is (`SRC_IN`).
///
/// ## Composites are drawn offscreen
///
/// `PaintComposite` blends two finished pictures, so both are rendered into
/// [Layer]s first — the backdrop, then the source blitted onto it with the
/// operator the font names — and the result is drawn onto the frame. The layers
/// are exactly the glyph's clip box on the device, snapped outwards to whole
/// pixels, so they line up with the frame pixel for pixel. Noto uses this for its
/// waving flags and for nothing else; a flag costs two small allocations, and a
/// line of prose costs none.
///
/// ## What is approximate, and written down
///
/// - The four HSL composite modes have no rasterizer operator and are drawn as
///   source-over.
/// - A sweep gradient's stops outside `[0°, 360°]` are clamped into it, and a
///   sweep that repeats or reflects is drawn padded.
/// - A radial gradient whose stops run past a circle of radius zero is clamped
///   at zero.
/// - A `PaintColrGlyph` draws the glyph it names without that glyph's own clip
///   box, and stops following references [#MAX_REFERENCES] deep.
///
/// None of these occurs in the shipped face.
final class ColourGlyphPainter {

    /// How many `PaintColrGlyph` references one glyph may follow before the rest
    /// are taken to be a cycle.
    static final int MAX_REFERENCES = 16;

    private final GlyphFace face;
    private final ColorPaints paints;

    ColourGlyphPainter(GlyphFace face) {
        this.face = Objects.requireNonNull(face, "face");
        this.paints = face.paints();
    }

    /// Draws `glyphId` with its origin at logical `(x, baseline)`, `size`
    /// logical units to the em.
    ///
    /// @param textArgb the colour of the text around it, for the parts of the
    ///        glyph that follow the text rather than the palette
    /// @return false, having drawn nothing, when the glyph has no paint graph
    boolean draw(Frame frame, int glyphId, double x, double baseline, double size, int textArgb) {
        var root = paints.paint(glyphId);
        if (root == null) {
            return false;
        }
        var scale = size / face.unitsPerEm();
        frame.save();
        try {
            frame.concat(scale, 0, 0, -scale, x, baseline);
            var box = bounds(glyphId);
            var area = deviceBounds(frame, box);
            new Walk(textArgb, box, area).paint(frame, root, 0);
        } finally {
            frame.restore();
        }
        return true;
    }

    /// The glyph's clip box in design units, or the em square with a quarter-em
    /// margin around it when the font does not say — generous enough for any
    /// glyph drawn to the em, which is what an emoji is.
    private ColorPaints.ClipBox bounds(int glyphId) {
        var box = paints.clipBox(glyphId);
        if (box != null && !box.isEmpty()) {
            return box;
        }
        var em = face.unitsPerEm();
        return new ColorPaints.ClipBox(-em / 4.0, -em / 4.0, em * 1.25, em * 1.25);
    }

    /// Where `box` lands on the device, in physical pixels, snapped outwards and
    /// cut to the frame — or null when none of it is on the frame.
    ///
    /// Computed once, at the root, under the placement matrix: a composite deep
    /// inside a transformed subtree still renders the whole glyph's area, because
    /// the clip box is the glyph's and not the subtree's.
    private static int @Nullable [] deviceBounds(Frame frame, ColorPaints.ClipBox box) {
        var matrix = frame.matrix();
        var factor = frame.scale().factor();
        double[] xs = {box.xMin(), box.xMax(), box.xMin(), box.xMax()};
        double[] ys = {box.yMin(), box.yMin(), box.yMax(), box.yMax()};
        var left = Double.POSITIVE_INFINITY;
        var top = Double.POSITIVE_INFINITY;
        var right = Double.NEGATIVE_INFINITY;
        var bottom = Double.NEGATIVE_INFINITY;
        for (var i = 0; i < 4; i++) {
            var px = matrix.mapX(xs[i], ys[i]) * factor;
            var py = matrix.mapY(xs[i], ys[i]) * factor;
            left = Math.min(left, px);
            top = Math.min(top, py);
            right = Math.max(right, px);
            bottom = Math.max(bottom, py);
        }
        var pixels = frame.pixelSize();
        var x0 = Math.max(0, (int) Math.floor(left));
        var y0 = Math.max(0, (int) Math.floor(top));
        var x1 = Math.min(pixels.width(), (int) Math.ceil(right));
        var y1 = Math.min(pixels.height(), (int) Math.ceil(bottom));
        return x1 > x0 && y1 > y0 ? new int[] {x0, y0, x1 - x0, y1 - y0} : null;
    }

    /// One glyph's drawing onto one surface: the text colour, and the rectangles
    /// offscreen work is sized by.
    ///
    /// A new walk per surface rather than one per glyph, because `device` is in
    /// the pixels of the surface being drawn on. Noto's flags nest one composite
    /// inside another, and the inner one is drawn onto the outer one's layer —
    /// whose area starts at its own corner, not at the frame's.
    ///
    /// @param box    the glyph's clip box, in design units
    /// @param device that box on the surface, `{x, y, width, height}` in physical
    ///               pixels, or null when none of it is on the surface
    private final class Walk {

        private final int textArgb;
        private final ColorPaints.ClipBox box;
        private final int @Nullable [] device;

        Walk(int textArgb, ColorPaints.ClipBox box, int @Nullable [] device) {
            this.textArgb = textArgb;
            this.box = box;
            this.device = device;
        }

        void paint(Frame frame, ColorPaint node, int references) {
            switch (node) {
                case ColorPaint.Layers layers -> {
                    for (var layer : layers.layers()) {
                        paint(frame, layer, references);
                    }
                }
                case ColorPaint.Glyph glyph ->
                    fill(frame, face.outline(glyph.glyphId()), glyph.paint(), Affine.IDENTITY, references);
                case ColorPaint.ColrGlyph reference -> {
                    var target = paints.paint(reference.glyphId());
                    if (target != null && references < MAX_REFERENCES) {
                        paint(frame, target, references + 1);
                    }
                }
                case ColorPaint.Transform transform -> {
                    if (!transform.isInvertible()) {
                        // Collapsed onto a line: nothing to see, and nothing a
                        // gradient could be evaluated through.
                        return;
                    }
                    frame.save();
                    try {
                        frame.concat(
                                transform.xx(),
                                transform.yx(),
                                transform.xy(),
                                transform.yy(),
                                transform.dx(),
                                transform.dy());
                        paint(frame, transform.paint(), references);
                    } finally {
                        frame.restore();
                    }
                }
                case ColorPaint.Composite composite ->
                    composite(
                            frame,
                            (walk, backdrop) -> walk.paint(backdrop, composite.backdrop(), references),
                            (walk, source) -> walk.paint(source, composite.source(), references),
                            operator(composite.mode()));
                // A fill with nothing around it reaches as far as the glyph is
                // allowed to: its clip box.
                case ColorPaint.Solid _,
                        ColorPaint.LinearGradient _,
                        ColorPaint.RadialGradient _,
                        ColorPaint.SweepGradient _ -> fill(frame, clipPath(), node, Affine.IDENTITY, references);
            }
        }

        /// Fills `shape` with `brush`, whose coordinates `space` maps into the
        /// shape's.
        private void fill(Frame frame, Path shape, ColorPaint brush, Affine space, int references) {
            if (shape.isEmpty()) {
                return;
            }
            switch (brush) {
                case ColorPaint.Solid solid ->
                    frame.fillPath(shape, solid.colour().resolve(textArgb));
                case ColorPaint.Transform transform -> {
                    if (transform.isInvertible()) {
                        var inner = new Affine(
                                transform.xx(),
                                transform.yx(),
                                transform.xy(),
                                transform.yy(),
                                transform.dx(),
                                transform.dy());
                        fill(frame, shape, transform.paint(), inner.then(space), references);
                    }
                }
                case ColorPaint.LinearGradient _, ColorPaint.RadialGradient _, ColorPaint.SweepGradient _ -> {
                    try (var gradient = gradient(brush, space, textArgb)) {
                        if (gradient != null) {
                            frame.fillPath(shape, gradient);
                        }
                    }
                }
                // Anything else under a glyph is a picture, not a brush: drawn
                // offscreen and kept only inside the outline.
                case ColorPaint.Layers _, ColorPaint.Glyph _, ColorPaint.ColrGlyph _, ColorPaint.Composite _ ->
                    composite(
                            frame,
                            (_, mask) -> mask.fillPath(shape, 0xFF000000),
                            (walk, picture) -> {
                                picture.save();
                                try {
                                    picture.concat(space.a(), space.b(), space.c(), space.d(), space.e(), space.f());
                                    walk.paint(picture, brush, references);
                                } finally {
                                    picture.restore();
                                }
                            },
                            BlendCompOp.SRC_IN);
            }
        }

        /// Renders `backdrop`, blends `source` onto it with `operator`, and draws
        /// the result — each painter handed a frame already in this glyph's
        /// design units, and the walk that draws onto it.
        private void composite(
                Frame frame, BiConsumer<Walk, Frame> backdrop, BiConsumer<Walk, Frame> source, BlendCompOp operator) {
            var area = device;
            if (area == null) {
                return;
            }
            var factor = frame.scale().factor();
            var size = new PhysicalSize(area[2], area[3]);
            var originX = area[0] / factor;
            var originY = area[1] / factor;
            // The frame's matrix, moved so that the layer's corner is the
            // device pixel the area starts at.
            var local = frame.matrix().then(Affine.translate(-originX, -originY));
            // On a layer the area is the whole layer, from its own corner.
            var inner = new Walk(textArgb, box, new int[] {0, 0, area[2], area[3]});

            try (var under = Layer.of(size);
                    var over = Layer.of(size)) {
                over.paint(frame.scale(), layer -> {
                    layer.transform(local.a(), local.b(), local.c(), local.d(), local.e(), local.f());
                    source.accept(inner, layer);
                });
                under.paint(frame.scale(), layer -> {
                    layer.transform(local.a(), local.b(), local.c(), local.d(), local.e(), local.f());
                    backdrop.accept(inner, layer);
                    layer.resetTransform();
                    layer.drawLayer(0, 0, over, operator);
                });
                frame.save();
                try {
                    frame.resetTransform();
                    frame.drawLayer(originX, originY, under, 1);
                } finally {
                    frame.restore();
                }
            }
        }

        /// The clip box as a rectangle to fill, in design units.
        private Path clipPath() {
            return Path.rect(box.xMin(), box.yMin(), box.xMax() - box.xMin(), box.yMax() - box.yMin());
        }
    }

    /// The rasterizer's gradient for one of the three gradient nodes, placed
    /// through `space`, or null when its geometry describes no gradient at all.
    ///
    /// ## Stops outside `[0, 1]`
    ///
    /// A font may place a stop before the start of its geometry or after the
    /// end; the rasterizer takes stops between 0 and 1 only. So the geometry is
    /// **stretched to the stops** — the start moved to where the first stop
    /// falls and the end to where the last one does — and the stops rescaled
    /// into the new span. The picture is the same and every stop is in range,
    /// which is how Skia and FreeType read the same table.
    static @Nullable BlendGradient gradient(ColorPaint node, Affine space, int textArgb) {
        var matrix = new BlendMatrix(space.a(), space.b(), space.c(), space.d(), space.e(), space.f());
        return switch (node) {
            case ColorPaint.LinearGradient linear -> {
                var line = linear.line();
                var end = linear.normal();
                var first = line.stops().getFirst().offset();
                var last = line.stops().getLast().offset();
                var dx = end[0] - linear.x0();
                var dy = end[1] - linear.y0();
                if (dx == 0 && dy == 0) {
                    yield null;
                }
                var span = last - first;
                double x0 = linear.x0() + dx * first;
                double y0 = linear.y0() + dy * first;
                double x1 = span > 0 ? linear.x0() + dx * last : x0 + dx;
                double y1 = span > 0 ? linear.y0() + dy * last : y0 + dy;
                yield stops(BlendGradient.linear(x0, y0, x1, y1, extend(line), matrix), line, first, span, textArgb);
            }
            case ColorPaint.RadialGradient radial -> {
                var line = radial.line();
                var first = line.stops().getFirst().offset();
                var last = line.stops().getLast().offset();
                var span = last - first;
                var at = span > 0 ? last : first + 1;
                // Circle 0 of the font is where the first stop sits, which is the
                // rasterizer's *focal* circle.
                yield stops(
                        BlendGradient.radial(
                                lerp(radial.x0(), radial.x1(), at),
                                lerp(radial.y0(), radial.y1(), at),
                                Math.max(0, lerp(radial.r0(), radial.r1(), at)),
                                lerp(radial.x0(), radial.x1(), first),
                                lerp(radial.y0(), radial.y1(), first),
                                Math.max(0, lerp(radial.r0(), radial.r1(), first)),
                                extend(line),
                                matrix),
                        line,
                        first,
                        span,
                        textArgb);
            }
            case ColorPaint.SweepGradient sweep -> {
                var line = sweep.line();
                // One turn from the positive x axis: a stop's offset along the
                // ramp is its angle over 360, clamped into the turn.
                var gradient = BlendGradient.conic(sweep.centerX(), sweep.centerY(), 0, BlendExtendMode.PAD, matrix);
                try {
                    var range = sweep.endAngle() - sweep.startAngle();
                    for (var stop : line.stops()) {
                        var angle = sweep.startAngle() + stop.offset() * range;
                        gradient.addStop(
                                Math.clamp(angle / 360, 0.0, 1.0), stop.colour().resolve(textArgb));
                    }
                } catch (RuntimeException | Error e) {
                    gradient.close();
                    throw e;
                }
                yield gradient;
            }
            default -> throw new IllegalArgumentException("not a gradient: " + node);
        };
    }

    /// Adds `line`'s stops to `gradient`, rescaled from `[first, first + span]`
    /// into `[0, 1]`. A span of zero — every stop at one offset — is a solid in
    /// the last stop's colour, which is what a ramp of no length looks like.
    private static BlendGradient stops(
            BlendGradient gradient, ColorLine line, double first, double span, int textArgb) {
        try {
            if (span <= 0) {
                gradient.addStop(0, line.stops().getLast().colour().resolve(textArgb));
                return gradient;
            }
            for (var stop : line.stops()) {
                gradient.addStop(
                        Math.clamp((stop.offset() - first) / span, 0.0, 1.0),
                        stop.colour().resolve(textArgb));
            }
            return gradient;
        } catch (RuntimeException | Error e) {
            gradient.close();
            throw e;
        }
    }

    private static double lerp(double from, double to, double t) {
        return from + (to - from) * t;
    }

    private static BlendExtendMode extend(ColorLine line) {
        return switch (line.extend()) {
            case PAD -> BlendExtendMode.PAD;
            case REPEAT -> BlendExtendMode.REPEAT;
            case REFLECT -> BlendExtendMode.REFLECT;
        };
    }

    /// The rasterizer's operator for a font's composite mode.
    static BlendCompOp operator(CompositeMode mode) {
        return switch (mode) {
            case CLEAR -> BlendCompOp.CLEAR;
            case SRC -> BlendCompOp.SRC_COPY;
            case DEST -> BlendCompOp.DST_COPY;
            case SRC_OVER -> BlendCompOp.SRC_OVER;
            case DEST_OVER -> BlendCompOp.DST_OVER;
            case SRC_IN -> BlendCompOp.SRC_IN;
            case DEST_IN -> BlendCompOp.DST_IN;
            case SRC_OUT -> BlendCompOp.SRC_OUT;
            case DEST_OUT -> BlendCompOp.DST_OUT;
            case SRC_ATOP -> BlendCompOp.SRC_ATOP;
            case DEST_ATOP -> BlendCompOp.DST_ATOP;
            case XOR -> BlendCompOp.XOR;
            case PLUS -> BlendCompOp.PLUS;
            case SCREEN -> BlendCompOp.SCREEN;
            case OVERLAY -> BlendCompOp.OVERLAY;
            case DARKEN -> BlendCompOp.DARKEN;
            case LIGHTEN -> BlendCompOp.LIGHTEN;
            case COLOR_DODGE -> BlendCompOp.COLOR_DODGE;
            case COLOR_BURN -> BlendCompOp.COLOR_BURN;
            case HARD_LIGHT -> BlendCompOp.HARD_LIGHT;
            case SOFT_LIGHT -> BlendCompOp.SOFT_LIGHT;
            case DIFFERENCE -> BlendCompOp.DIFFERENCE;
            case EXCLUSION -> BlendCompOp.EXCLUSION;
            case MULTIPLY -> BlendCompOp.MULTIPLY;
            // No rasterizer operator: see the class note.
            case HSL_HUE, HSL_SATURATION, HSL_COLOR, HSL_LUMINOSITY -> BlendCompOp.SRC_OVER;
        };
    }
}
