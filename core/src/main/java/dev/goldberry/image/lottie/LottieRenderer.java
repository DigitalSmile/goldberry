package dev.goldberry.image.lottie;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import dev.goldberry.css.value.Affine;
import dev.goldberry.paint.Frame;
import dev.goldberry.paint.Gradient;
import dev.goldberry.paint.Path;
import dev.goldberry.paint.stroke.Dash;
import dev.goldberry.paint.stroke.Stroke;

/// Draws a [Composition] at a moment, onto a [Frame], through the toolkit's own
/// vector painter.
///
/// Every shape becomes a [Path] and every paint a call on the frame, under the
/// transform its layers and groups add up to: so a stroke is as thick as the
/// document says at whatever size it is drawn, a gradient turns with its
/// group, and the picture is crisp at any scale because nothing in it was ever
/// a pixel. A matte, a mask and a gradient stroke are composited offscreen,
/// in a [Scratch] the size of the box.
///
/// Made once per composition and safe to share between threads: the indexes it
/// builds are fixed when it is made, and a paint keeps its state on the stack.
public final class LottieRenderer {

    /// How deep parents and precomps may nest, which a document with a loop in
    /// it would otherwise do for ever.
    private static final int MAX_DEPTH = 32;

    private final Composition composition;

    /// Each layer list's layers by `ind`, for parents and named mattes.
    private final IdentityHashMap<List<Layer>, Map<Integer, Layer>> indexes = new IdentityHashMap<>();

    /// Reads a Lottie document, gzipped or not, from `bytes`'s position to its
    /// limit — see [LottieReader] for what is refused.
    ///
    /// @throws dev.goldberry.image.ImageDecodeException when the bytes are not a
    ///         Lottie document this can draw
    public static LottieRenderer read(ByteBuffer bytes) {
        return new LottieRenderer(LottieReader.read(bytes));
    }

    /// A renderer for `composition`.
    LottieRenderer(Composition composition) {
        this.composition = composition;
        index(composition.layers());
        composition.precomps().values().forEach(this::index);
    }

    private void index(List<Layer> layers) {
        var byIndex = new HashMap<Integer, Layer>();
        for (var layer : layers) {
            byIndex.putIfAbsent(layer.index(), layer);
        }
        indexes.put(layers, Map.copyOf(byIndex));
    }

    /// The canvas's width, in the document's units.
    public double width() {
        return composition.width();
    }

    /// The canvas's height, in the document's units.
    public double height() {
        return composition.height();
    }

    /// Frames a second.
    public double frameRate() {
        return composition.frameRate();
    }

    /// The first frame.
    public double inPoint() {
        return composition.inPoint();
    }

    /// The frame after the last.
    public double outPoint() {
        return composition.outPoint();
    }

    /// How many frames one pass is.
    public double frames() {
        return composition.frames();
    }

    /// Draws the composition as it is at `frame`, its canvas stretched over the
    /// rectangle `(x, y, width, height)` of `target` and clipped to it.
    ///
    /// @param frame a frame number in the composition's own time
    public void paint(Frame target, double frame, double x, double y, double width, double height) {
        if (!(width > 0) || !(height > 0)) {
            return;
        }
        var fit = Affine.scale(width / composition.width(), height / composition.height())
                .then(Affine.translate(x, y));
        target.save();
        try {
            target.clipTo(x, y, width, height);
            var surface =
                    new Surface(target, fit, x, y, width, height, target.scale().factor());
            layers(composition.layers(), frame, Affine.IDENTITY, 1, surface, 0);
        } finally {
            target.restore();
        }
    }

    /// Where drawing lands: a frame, the matrix from the composition's
    /// canvas into it, and the box the canvas covers there.
    private record Surface(Frame frame, Affine base, double x, double y, double width, double height, float scale) {

        /// Runs `body` with the frame's transform multiplied by `space`, the
        /// matrix from the item's own units to the composition's.
        void draw(Affine space, Consumer<Frame> body) {
            var m = space.then(base);
            frame.save();
            try {
                frame.concat(m.a(), m.b(), m.c(), m.d(), m.e(), m.f());
                body.accept(frame);
            } finally {
                frame.restore();
            }
        }

        /// A cleared raster covering this surface's box, and the surface that
        /// draws into it with the same mapping.
        @Nullable
        Offscreen offscreen() {
            var pixelWidth = (int) Math.ceil(width * scale);
            var pixelHeight = (int) Math.ceil(height * scale);
            if (pixelWidth <= 0 || pixelHeight <= 0) {
                return null;
            }
            var scratch = new Scratch(pixelWidth, pixelHeight, scale);
            var inner = new Surface(
                    scratch.frame(),
                    base.then(Affine.translate(-x, -y)),
                    0,
                    0,
                    pixelWidth / (double) scale,
                    pixelHeight / (double) scale,
                    scale);
            return new Offscreen(scratch, inner);
        }

        /// Draws a finished raster over the box.
        void place(Scratch scratch) {
            frame.drawImage(scratch.image(), x, y, Math.ceil(width * scale) / scale, Math.ceil(height * scale) / scale);
        }
    }

    /// A raster and the surface over it.
    private record Offscreen(Scratch scratch, Surface surface) {}

    // ---------------------------------------------------------------- layers

    private void layers(List<Layer> layers, double frame, Affine space, double alpha, Surface surface, int depth) {
        if (depth > MAX_DEPTH) {
            return;
        }
        var byIndex = indexes.getOrDefault(layers, Map.of());
        for (var i = layers.size() - 1; i >= 0; i--) {
            var layer = layers.get(i);
            if (layer.isMatte() || !layer.showsAt(frame)) {
                continue;
            }
            var matte = layer.matte() == Layer.Matte.NONE ? null : matteOf(layers, i, byIndex);
            if (matte != null) {
                matted(layer, matte, byIndex, frame, space, alpha, surface, depth);
            } else if (hasMasks(layer)) {
                masked(layer, byIndex, frame, space, alpha, surface, depth);
            } else {
                layer(layer, byIndex, frame, space, alpha, surface, depth);
            }
        }
    }

    /// The layer that is `layers.get(at)`'s matte: the one it names, or the one
    /// directly above it.
    private static @Nullable Layer matteOf(List<Layer> layers, int at, Map<Integer, Layer> byIndex) {
        var named = layers.get(at).matteIndex();
        if (named != null) {
            return byIndex.get(named);
        }
        return at > 0 ? layers.get(at - 1) : null;
    }

    private static boolean hasMasks(Layer layer) {
        for (var mask : layer.masks()) {
            if (mask.mode() != Layer.Mask.Mode.NONE) {
                return true;
            }
        }
        return false;
    }

    /// One layer's own drawing, straight onto `surface`.
    private void layer(
            Layer layer,
            Map<Integer, Layer> byIndex,
            double frame,
            Affine space,
            double alpha,
            Surface surface,
            int depth) {
        var local = layer.localFrame(frame);
        var opacity = alpha * layer.transform().opacityAt(local);
        if (opacity <= 0) {
            return;
        }
        var matrix = matrixOf(layer, byIndex, frame, 0).then(space);
        switch (layer.content()) {
            case Layer.Shapes(var items) -> {
                var group = evaluate(items, Transform.NONE, local);
                trim(group);
                draw(group, matrix, opacity, surface);
            }
            case Layer.Solid(var argb, var width, var height) -> {
                if (width > 0 && height > 0) {
                    var rect = Path.builder()
                            .moveTo(0, 0)
                            .lineTo(width, 0)
                            .lineTo(width, height)
                            .lineTo(0, height)
                            .close()
                            .build();
                    surface.draw(matrix, f -> f.fillPath(rect, fade(argb, opacity)));
                }
            }
            case Layer.Precomp(var reference, var width, var height, var remap) -> {
                var children = composition.precomps().get(reference);
                if (children == null) {
                    return;
                }
                var time = remap != null ? remap.scalar(local) * composition.frameRate() : local / layer.stretch();
                precomp(children, time, matrix, width, height, opacity, surface, depth);
            }
            case Layer.Nothing() -> {
                // A null layer: a transform for its children, and no picture.
            }
        }
    }

    private void precomp(
            List<Layer> children,
            double time,
            Affine matrix,
            double width,
            double height,
            double opacity,
            Surface surface,
            int depth) {
        if (!(width > 0) || !(height > 0)) {
            layers(children, time, matrix, opacity, surface, depth + 1);
            return;
        }
        // Clipped to the precomp's box. The clip is set under the layer's
        // matrix and the matrix is then taken back off, so the children draw
        // under the surface's own transform like every other layer.
        var m = matrix.then(surface.base());
        var inverse = m.invert();
        if (inverse == null) {
            return;
        }
        var frame = surface.frame();
        frame.save();
        try {
            frame.concat(m.a(), m.b(), m.c(), m.d(), m.e(), m.f());
            frame.clipTo(0, 0, width, height);
            frame.concat(inverse.a(), inverse.b(), inverse.c(), inverse.d(), inverse.e(), inverse.f());
            layers(children, time, matrix, opacity, surface, depth + 1);
        } finally {
            frame.restore();
        }
    }

    /// The matrix from `layer`'s own units to its composition's, its parents'
    /// transforms included.
    private static Affine matrixOf(Layer layer, Map<Integer, Layer> byIndex, double frame, int depth) {
        var own = layer.transform().matrixAt(layer.localFrame(frame));
        var parentIndex = layer.parent();
        if (parentIndex == null || depth > MAX_DEPTH) {
            return own;
        }
        var parent = byIndex.get(parentIndex);
        if (parent == null || parent == layer) {
            return own;
        }
        return own.then(matrixOf(parent, byIndex, frame, depth + 1));
    }

    /// A layer cut out by its matte.
    private void matted(
            Layer layer,
            Layer matte,
            Map<Integer, Layer> byIndex,
            double frame,
            Affine space,
            double alpha,
            Surface surface,
            int depth) {
        var content = surface.offscreen();
        var shape = surface.offscreen();
        if (content == null || shape == null) {
            return;
        }
        if (hasMasks(layer)) {
            masked(layer, byIndex, frame, space, alpha, content.surface(), depth);
        } else {
            layer(layer, byIndex, frame, space, alpha, content.surface(), depth);
        }
        content.scratch().finish();
        if (matte.showsAt(frame)) {
            layer(matte, byIndex, frame, space, 1, shape.surface(), depth);
        }
        shape.scratch().finish();
        var factor = switch (layer.matte()) {
            case ALPHA, NONE -> shape.scratch().alpha();
            case ALPHA_INVERTED -> inverted(shape.scratch().alpha());
            case LUMA -> shape.scratch().luma();
            case LUMA_INVERTED -> inverted(shape.scratch().luma());
        };
        content.scratch().multiply(factor);
        surface.place(content.scratch());
    }

    /// A layer cut out by its masks.
    private void masked(
            Layer layer,
            Map<Integer, Layer> byIndex,
            double frame,
            Affine space,
            double alpha,
            Surface surface,
            int depth) {
        var content = surface.offscreen();
        if (content == null) {
            return;
        }
        layer(layer, byIndex, frame, space, alpha, content.surface(), depth);
        content.scratch().finish();
        var local = layer.localFrame(frame);
        var matrix = matrixOf(layer, byIndex, frame, 0).then(space);
        float[] coverage = null;
        for (var mask : layer.masks()) {
            if (mask.mode() == Layer.Mask.Mode.NONE) {
                continue;
            }
            var raster = surface.offscreen();
            if (raster == null) {
                return;
            }
            var path = path(List.of(Contour.of(mask.path().at(local))));
            var opacity = Math.clamp(mask.opacity().scalar(local) / 100, 0, 1);
            raster.surface().draw(matrix, f -> f.fillPath(path, fade(0xFFFFFFFF, opacity)));
            raster.scratch().finish();
            var alphas = raster.scratch().alpha();
            if (coverage == null) {
                // Starting from nothing when the first mask adds, and from
                // everything when it takes away.
                coverage = new float[alphas.length];
                if (mask.mode() != Layer.Mask.Mode.ADD && mask.mode() != Layer.Mask.Mode.LIGHTEN) {
                    Arrays.fill(coverage, 1);
                }
            }
            combine(coverage, alphas, mask);
        }
        if (coverage == null) {
            surface.place(content.scratch());
            return;
        }
        var factor = new int[coverage.length];
        for (var i = 0; i < factor.length; i++) {
            factor[i] = Math.round(Math.clamp(coverage[i], 0, 1) * 255);
        }
        content.scratch().multiply(factor);
        surface.place(content.scratch());
    }

    private static void combine(float[] coverage, int[] alphas, Layer.Mask mask) {
        for (var i = 0; i < coverage.length; i++) {
            var a = alphas[i] / 255f;
            if (mask.inverted()) {
                a = 1 - a;
            }
            var c = coverage[i];
            coverage[i] = switch (mask.mode()) {
                case ADD -> c + a - c * a;
                case SUBTRACT -> c * (1 - a);
                case INTERSECT -> c * a;
                case LIGHTEN -> Math.max(c, a);
                case DARKEN -> Math.min(c, a);
                case DIFFERENCE -> Math.abs(c - a);
                case NONE -> c;
            };
        }
    }

    private static int[] inverted(int[] values) {
        for (var i = 0; i < values.length; i++) {
            values[i] = 255 - values[i];
        }
        return values;
    }

    // ---------------------------------------------------------------- shapes

    /// A group as it is at one moment: its matrix and opacity, and its items
    /// with their geometry worked out.
    private static final class Group {

        final Affine matrix;
        final double opacity;
        final List<Object> items = new ArrayList<>();

        Group(Affine matrix, double opacity) {
            this.matrix = matrix;
            this.opacity = opacity;
        }
    }

    /// One shape's outline at one moment, which a trim may replace.
    private static final class Outline {

        List<Contour> contours;

        Outline(List<Contour> contours) {
            this.contours = contours;
        }
    }

    /// A trim at one moment.
    private record Cut(double start, double end, double offset, boolean individually) {}

    /// A paint with the frame it is drawn at.
    private record Painted(Shape.Paint paint, double frame) {}

    private static Group evaluate(List<Shape> items, Transform transform, double frame) {
        var group = new Group(transform.matrixAt(frame), transform.opacityAt(frame));
        for (var item : items) {
            switch (item) {
                case Shape.Group(var children, var inner) -> group.items.add(evaluate(children, inner, frame));
                case Shape.Geometry geometry -> group.items.add(new Outline(Shapes.outline(geometry, frame)));
                case Shape.Paint paint -> group.items.add(new Painted(paint, frame));
                case Shape.Trim(var start, var end, var offset, var individually) ->
                    group.items.add(new Cut(
                            start.scalar(frame) / 100,
                            end.scalar(frame) / 100,
                            offset.scalar(frame) / 360,
                            individually));
            }
        }
        return group;
    }

    /// Applies every trim to the outlines before it, the innermost groups'
    /// first.
    private static void trim(Group group) {
        for (var item : group.items) {
            if (item instanceof Group inner) {
                trim(inner);
            }
        }
        for (var i = 0; i < group.items.size(); i++) {
            if (!(group.items.get(i) instanceof Cut cut)) {
                continue;
            }
            var outlines = new ArrayList<Outline>();
            gather(group.items, i, outlines);
            if (cut.individually()) {
                together(outlines, cut);
            } else {
                for (var outline : outlines) {
                    outline.contours = Trimming.each(outline.contours, cut.start(), cut.end(), cut.offset());
                }
            }
        }
    }

    /// One trim over several outlines laid end to end, each keeping its own
    /// share of the range — so a nested group's paints still draw what is left
    /// of its own shapes.
    private static void together(List<Outline> outlines, Cut cut) {
        var range = Trimming.range(cut.start(), cut.end(), cut.offset());
        if (range == null) {
            return;
        }
        var all = new ArrayList<Contour>();
        var owners = new ArrayList<Outline>();
        for (var outline : outlines) {
            for (var contour : outline.contours) {
                all.add(contour);
                owners.add(outline);
            }
            outline.contours = new ArrayList<>();
        }
        if (range[1] <= range[0]) {
            return;
        }
        var kept = Trimming.together(all, range[0], range[1]);
        for (var k = 0; k < kept.size(); k++) {
            owners.get(k).contours.addAll(kept.get(k));
        }
    }

    /// Every outline in `items` before `end`, nested groups included.
    private static void gather(List<Object> items, int end, List<Outline> out) {
        for (var i = 0; i < end; i++) {
            switch (items.get(i)) {
                case Outline outline -> out.add(outline);
                case Group inner -> gather(inner.items, inner.items.size(), out);
                default -> {
                    // Paints and trims have no outline.
                }
            }
        }
    }

    /// The contours a paint at `end` draws, in its group's units.
    private static void contours(List<Object> items, int end, Affine into, List<Contour> out) {
        for (var i = 0; i < end; i++) {
            switch (items.get(i)) {
                case Outline outline -> {
                    for (var contour : outline.contours) {
                        out.add(into.isIdentity() ? contour : contour.transformed(into));
                    }
                }
                case Group inner -> contours(inner.items, inner.items.size(), inner.matrix.then(into), out);
                default -> {
                    // Paints and trims have no outline.
                }
            }
        }
    }

    /// Draws a group: its items from the last to the first, so the first is on
    /// top.
    private static void draw(Group group, Affine parent, double alpha, Surface surface) {
        var matrix = group.matrix.then(parent);
        var opacity = alpha * group.opacity;
        if (opacity <= 0) {
            return;
        }
        for (var i = group.items.size() - 1; i >= 0; i--) {
            switch (group.items.get(i)) {
                case Group inner -> draw(inner, matrix, opacity, surface);
                case Painted(var paint, var frame) -> {
                    var contours = new ArrayList<Contour>();
                    contours(group.items, i, Affine.IDENTITY, contours);
                    if (!contours.isEmpty()) {
                        paint(paint, frame, path(contours), matrix, opacity, surface);
                    }
                }
                default -> {
                    // Geometry is drawn by the paints after it.
                }
            }
        }
    }

    private static Path path(List<Contour> contours) {
        var builder = Path.builder();
        for (var contour : contours) {
            contour.appendTo(builder);
        }
        return builder.build();
    }

    private static void paint(
            Shape.Paint paint, double frame, Path path, Affine matrix, double alpha, Surface surface) {
        var opacity = alpha * Math.clamp(paint.opacity().scalar(frame) / 100, 0, 1);
        if (opacity <= 0 || path.isEmpty()) {
            return;
        }
        switch (paint) {
            // An even-odd fill is drawn non-zero: the painter's even-odd rule is
            // not public, and a sticker's paths do not overlap themselves.
            case Shape.Fill fill -> {
                var argb = Colours.argb(fill.color().at(frame), opacity);
                surface.draw(matrix, f -> f.fillPath(path, argb));
            }
            case Shape.GradientFill fill -> {
                var gradient = Colours.gradient(fill.ramp(), frame, opacity);
                if (gradient != null) {
                    surface.draw(matrix, f -> f.fillPath(path, gradient));
                }
            }
            case Shape.Stroke stroke -> {
                var pen = pen(stroke.line(), frame);
                if (pen != null) {
                    var argb = Colours.argb(stroke.color().at(frame), opacity);
                    surface.draw(matrix, f -> f.strokePath(path, pen, argb));
                }
            }
            case Shape.GradientStroke stroke -> {
                var pen = pen(stroke.line(), frame);
                var gradient = Colours.gradient(stroke.ramp(), frame, opacity);
                if (pen != null && gradient != null) {
                    gradientStroke(path, pen, gradient, matrix, surface);
                }
            }
        }
    }

    /// A stroke filled with a gradient: the gradient over the whole box, kept
    /// where the stroke would have drawn.
    private static void gradientStroke(Path path, Stroke pen, Gradient gradient, Affine matrix, Surface surface) {
        var ramp = surface.offscreen();
        var line = surface.offscreen();
        if (ramp == null || line == null) {
            return;
        }
        var inverse = matrix.then(ramp.surface().base()).invert();
        if (inverse == null) {
            return;
        }
        // The box's corners in the group's units, so the ramp covers it all.
        var w = ramp.surface().width();
        var h = ramp.surface().height();
        var cover = Path.builder()
                .moveTo(inverse.mapX(0, 0), inverse.mapY(0, 0))
                .lineTo(inverse.mapX(w, 0), inverse.mapY(w, 0))
                .lineTo(inverse.mapX(w, h), inverse.mapY(w, h))
                .lineTo(inverse.mapX(0, h), inverse.mapY(0, h))
                .close()
                .build();
        ramp.surface().draw(matrix, f -> f.fillPath(cover, gradient));
        ramp.scratch().finish();
        line.surface().draw(matrix, f -> f.strokePath(path, pen, 0xFFFFFFFF));
        line.scratch().finish();
        ramp.scratch().multiply(line.scratch().alpha());
        surface.place(ramp.scratch());
    }

    /// The pen a line draws with at `frame`, or null when it draws nothing.
    private static @Nullable Stroke pen(Shape.Line line, double frame) {
        var width = line.width().scalar(frame);
        if (!(width > 0)) {
            return null;
        }
        var dash = Dash.NONE;
        if (!line.dash().isEmpty()) {
            var lengths = new ArrayList<Double>(line.dash().size());
            var total = 0.0;
            for (var part : line.dash()) {
                var length = Math.max(0, part.scalar(frame));
                lengths.add(length);
                total += length;
            }
            if (total > 0) {
                var offset = line.dashOffset() == null ? 0 : line.dashOffset().scalar(frame);
                dash = new Dash(lengths, Double.isFinite(offset) ? offset : 0);
            }
        }
        return new Stroke(width, line.cap(), line.join(), Math.max(1, line.miterLimit()), dash);
    }

    /// `argb` with its alpha multiplied by `opacity`.
    private static int fade(int argb, double opacity) {
        var alpha = (int) Math.round(((argb >>> 24) & 0xFF) * Math.clamp(opacity, 0, 1));
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }
}
