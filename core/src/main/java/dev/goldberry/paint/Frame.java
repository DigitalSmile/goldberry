package dev.goldberry.paint;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.Window;
import dev.goldberry.css.value.Affine;
import dev.goldberry.image.Image;
import dev.goldberry.natives.blend2d.BlendContext;
import dev.goldberry.natives.blend2d.BlendFont;
import dev.goldberry.natives.blend2d.BlendGlyphBuffer;
import dev.goldberry.natives.blend2d.BlendGradient;
import dev.goldberry.natives.blend2d.BlendImage;
import dev.goldberry.natives.blend2d.BlendPath;
import dev.goldberry.natives.blend2d.enums.BlendCompOp;
import dev.goldberry.natives.blend2d.enums.BlendStrokeCap;
import dev.goldberry.natives.blend2d.enums.BlendStrokeJoin;
import dev.goldberry.paint.geom.Dasher;
import dev.goldberry.paint.stroke.Cap;
import dev.goldberry.paint.stroke.Join;
import dev.goldberry.paint.stroke.Stroke;
import dev.goldberry.render.GpuContent;
import dev.goldberry.render.GpuPlacement;
import dev.goldberry.render.PixelBuffer;
import dev.goldberry.render.model.DisplayScale;
import dev.goldberry.render.model.LogicalSize;
import dev.goldberry.render.model.PhysicalRect;
import dev.goldberry.render.model.PhysicalSize;
import dev.goldberry.render.window.BackendWindow;
import dev.goldberry.render.window.GpuSurface;

/// The surface a [Window] paints into.
///
/// Coordinates are **logical**, like everything an application writes: a
/// rectangle at `(10, 10)` is ten points from the corner whether the display runs
/// at 100% or 150%. That conversion is not arithmetic done here — the Blend2D
/// context is scaled once when the frame begins, so a fractional coordinate
/// reaches the rasterizer intact and is antialiased across the physical pixels
/// it actually covers. A rectangle at logical `x = 10.5` on a 1.5&times; display
/// lands at physical 15.75 and looks like it; snapping it to 15 or 16 would move
/// it by a third of a logical pixel (ADR-0031).
///
/// Colours are `0xAARRGGBB` — the packing everyone already knows from CSS and
/// Java 2D — and are **not** premultiplied. The buffer underneath is, and
/// Blend2D converts when it composites. Callers who premultiply first get a
/// frame that is visibly too dark with nothing reporting a problem.
///
/// The physical size is available for the rare code that needs it, but reaching
/// for it usually means something is about to be wrong on somebody's laptop.
///
/// A frame is valid only for the duration of the paint callback it was handed
/// to. [Window] ends it before presenting, because pixels a context has not
/// finished with are not pixels worth showing.
///
/// ## GPU layers
///
/// A frame over a window's [GpuSurface] can place content the GPU draws --
/// [#gpuLayer] -- in paint order among everything else: what is painted after
/// it is above it, and what was painted before it and under it is hidden, as it
/// would be under an opaque box. Over a composited window the layer's box
/// becomes a hole the compositor fills; otherwise the layer is rendered, read
/// back and drawn here (`docs/gpu-plan.md`, D3 and D4; ADR-0481).
public final class Frame {

    private final PixelBuffer buffer;
    private final DisplayScale scale;
    private final BlendImage image;
    private final BlendContext context;

    /// Rasterizer paths lent out by [#borrowPath()], never more than a few.
    ///
    /// A `BlendPath` is a confined `Arena` and a `bl_path_init`, so one per shape
    /// per frame is an allocation the frame can trivially avoid -- and used to
    /// avoid only where somebody remembered to. `BoxPainter` pooled one and
    /// threaded it through its own public signature; `:widgets` pooled none and
    /// opened four arenas per chart per frame. The pool is here now, in the one
    /// place that can see every drawing call (ADR-0277).
    ///
    /// A free list rather than a single scratch, because the borrows nest: a
    /// `canvas` painter filling a [Path] runs inside `BoxPainter`, which is
    /// holding one of these for the box's own border.
    private final List<BlendPath> paths = new ArrayList<>();

    /// The transform in force, in logical coordinates, kept in Java.
    ///
    /// Blend2D will not hand its matrix back — `bl_context_get_transform` is not
    /// on the export list, and putting it there would add a symbol to a boundary
    /// for something the painter already knows. Every change to this frame's
    /// matrix goes through [#transform] or [#resetTransform()], so mirroring it
    /// here costs six doubles and is what lets [#concat] compose with what a
    /// caller cannot see (ADR-0068, ADR-0390).
    private Affine matrix = Affine.IDENTITY;

    /// The transforms and clips [#save()] pushed, popped by [#restore()].
    ///
    /// The rasterizer's own stack holds the same values; this is the Java half of
    /// it, so that a `canvas` painter which saved, composed and restored leaves
    /// the mirror agreeing with the context rather than a transform behind.
    private final Deque<Saved> saved = new ArrayDeque<>();

    /// What one [#save()] pushed of the state this class mirrors.
    private record Saved(Affine matrix, Clip clip) {}

    /// The clip in force, in **physical** pixels, kept in Java for the reason
    /// [#matrix] is: Blend2D will not hand it back, and a GPU layer's scissor is
    /// the part of it the clip lets through (ADR-0481).
    ///
    /// A clip under a transform that is not a scale and a translation is its
    /// bounding box, which is what Blend2D clips to as well. The region
    /// [#repaintOnly] confines a frame to is not in it: that is which part of the
    /// frame is being repainted, not which part of a layer can be seen.
    private Clip clip = Clip.NONE;

    /// The region [#repaintOnly] is running its body in, in physical pixels, or
    /// null outside one.
    private @Nullable Clip region;

    /// How this frame shows GPU layers, or null when it cannot. See [#gpuLayer].
    private @Nullable GpuSurface gpu;

    /// The GPU layers placed so far, in paint order.
    private final List<GpuPlacement> placements = new ArrayList<>();

    /// The read-back layers' pixels drawn so far, held until [#end()]: a
    /// threaded context may still be reading them after the blit returns.
    /// Never queried, because holding them is the whole job.
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private final List<ByteBuffer> drawnPixels = new ArrayList<>();

    private int borrowed;

    private boolean ended;

    /// A frame over a buffer someone else owns, painted with the worker count
    /// [PaintThreads] chooses for its size.
    ///
    /// The caller keeps the buffer and must [#end()] the frame before reading it.
    /// Public because [Window] is in another package now: a frame is the paint
    /// package's surface and the shell is what lends it a buffer
    /// (ADR-0172).
    public static Frame over(PixelBuffer buffer, DisplayScale scale) {
        return new Frame(buffer, scale);
    }

    /// [#over(PixelBuffer, DisplayScale)] with the Blend2D worker count pinned
    /// rather than left to [PaintThreads] -- what the paint benchmark sweeps.
    public static Frame over(PixelBuffer buffer, DisplayScale scale, int threadCount) {
        return new Frame(buffer, scale, threadCount);
    }

    /// A frame over a buffer someone else owns, whose GPU layers `gpu` shows:
    /// what [Window] paints a window's frames with, with the surface
    /// [BackendWindow#gpuSurface()] gave it (ADR-0481).
    ///
    /// The caller keeps the buffer and must [#end()] the frame before reading it,
    /// and then hands [#gpuPlacements()] back to `gpu`.
    public static Frame over(PixelBuffer buffer, DisplayScale scale, GpuSurface gpu) {
        var frame = new Frame(buffer, scale);
        frame.gpu = Objects.requireNonNull(gpu, "gpu");
        return frame;
    }

    Frame(PixelBuffer buffer, DisplayScale scale) {
        this(buffer, scale, PaintThreads.forSurface(buffer.size()));
    }

    Frame(PixelBuffer buffer, DisplayScale scale, int threadCount) {
        this.buffer = buffer;
        this.scale = scale;

        // The image is a view over the buffer, not a copy of it: when the
        // platform lends its own surface, Blend2D rasterizes straight into the
        // memory that will be presented, and the frame costs no blit at all.
        this.image = BlendImage.wrapping(
                buffer.pixels(), buffer.size().width(), buffer.size().height(), buffer.stride());
        try {
            this.context = BlendContext.on(image, scale.factor(), threadCount);
        } catch (RuntimeException | Error e) {
            image.close();
            throw e;
        }
    }

    /// The size to paint in, in logical pixels.
    public LogicalSize size() {
        return scale.toLogical(buffer.size());
    }

    /// The size of the underlying buffer, in real pixels.
    public PhysicalSize pixelSize() {
        return buffer.size();
    }

    /// The display scale this frame is being rasterized at.
    public DisplayScale scale() {
        return scale;
    }

    /// How many Blend2D workers are rasterizing this frame; zero for synchronous
    /// painting on the calling thread.
    ///
    /// What [PaintThreads] asked for and what Blend2D gave can differ, and this
    /// is the second of the two — so a diagnostic reports what happened rather
    /// than what was intended.
    public int threadCount() {
        return context.threadCount();
    }

    /// Fills the whole frame, **replacing** whatever is there.
    ///
    /// A replacement rather than a blend, because this is what a background is:
    /// blending a translucent colour over the previous frame composites onto it,
    /// so the same call every frame would darken until it was opaque.
    ///
    /// @param argb a colour as `0xAARRGGBB`, not premultiplied
    public void fill(int argb) {
        requireOpen();
        context.clearTo(argb);
    }

    /// Fills a rectangle given in logical coordinates, blending over what is
    /// already there.
    ///
    /// Clipped to the frame rather than throwing: a rectangle that runs off the
    /// edge is ordinary in a UI, and layout has not run yet to prevent it.
    ///
    /// @param argb a colour as `0xAARRGGBB`, not premultiplied
    public void fillRect(float x, float y, float width, float height, int argb) {
        requireOpen();
        context.fillRect(x, y, width, height, argb);
    }

    /// Draws staged glyphs with `(x, baseline)` on the baseline.
    ///
    /// **Package-private, and that is the end of `docs/gaps.md` G14.** This was
    /// the last method in the toolkit's public surface with a `:natives` type in
    /// its signature, and the reason `:core` could not drop `requires transitive`
    /// (ADR-0290). [GlyphPen] is in this package now and is its only caller;
    /// [Font][dev.goldberry.text.font.Font] is the public way
    /// to draw text and always was.
    ///
    /// The primitive, not the text API. It takes a font and a buffer of
    /// positioned glyphs because that is what a rasterizer draws; deciding
    /// *which* glyphs, at what positions, is shaping.
    ///
    /// `baseline` is the line the letters sit on — an `a` is above it, a `g`
    /// hangs below — so the top of a line of text is `baseline - ascent`.
    ///
    /// The glyph buffer's offsets and advances are in the font's **design
    /// units**, not in the logical coordinates everything else here uses. That
    /// asymmetry is Blend2D's: the font's own matrix converts them, which is
    /// what lets one shaping result be drawn at any size (ADR-0034).
    ///
    /// @param argb a colour as `0xAARRGGBB`, not premultiplied
    void drawGlyphs(double x, double baseline, BlendFont font, BlendGlyphBuffer glyphs, int argb) {
        requireOpen();
        context.fillGlyphRun(x, baseline, font, glyphs, argb);
    }

    /// Fills `path`.
    ///
    /// The one fill an application writes. [Path] is a value in logical
    /// coordinates; turning it into something the rasterizer can draw is this
    /// frame's business and happens against a pooled path, so building a shape
    /// costs two Java arrays and no native memory at all (ADR-0277).
    ///
    /// @param argb a colour as `0xAARRGGBB`, not premultiplied
    public void fillPath(Path path, int argb) {
        fillPath(0, 0, path, argb);
    }

    /// Fills `path`, with the path's own origin placed at logical `(x, y)`.
    ///
    /// The origin moves the shape without transforming the frame, which is what
    /// lets one 24x24 icon path be drawn at several places in a frame without
    /// being rebuilt and without a `save`/`restore` pair around each one.
    ///
    /// @param argb a colour as `0xAARRGGBB`, not premultiplied
    public void fillPath(double x, double y, Path path, int argb) {
        requireOpen();
        Objects.requireNonNull(path, "path");
        if (path.isEmpty()) {
            return;
        }
        var scratch = borrowPath();
        try {
            path.replayInto(scratch);
            context.fillPath(x, y, scratch, argb);
        } finally {
            releasePath();
        }
    }

    /// Fills `path` with `gradient`.
    ///
    /// The ramp is placed in **this frame's** coordinates and not the path's, so
    /// one gradient fills a run of figures at the strength each one sits at —
    /// which is what a chart's bands need and what makes [Gradient] a value
    /// rather than something attached to a shape (ADR-0207).
    public void fillPath(Path path, Gradient gradient) {
        fillPath(0, 0, path, gradient);
    }

    /// Fills `path` with `gradient`, with the path's own origin at `(x, y)`.
    ///
    /// **The origin moves the path and not the ramp.** A gradient is a statement
    /// about a region of the surface rather than about a shape, so drawing the
    /// same path at two origins samples two parts of one ramp rather than
    /// repeating the first (ADR-0207).
    public void fillPath(double x, double y, Path path, Gradient gradient) {
        requireOpen();
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(gradient, "gradient");
        if (path.isEmpty()) {
            return;
        }
        var scratch = borrowPath();
        try {
            path.replayInto(scratch);
            // The native gradient exists for the length of the call. Blend2D
            // retains its own reference when the style is set, so closing it
            // here is safe and is what keeps `Gradient` a value with no lifetime.
            try (var ramp = toBlend(gradient)) {
                context.fillPath(x, y, scratch, ramp);
            }
        } finally {
            releasePath();
        }
    }

    /// Strokes `path` with `stroke`.
    ///
    /// The style travels with the call rather than being frame state, for
    /// [Stroke]'s reason: Blend2D's is context state, and a frame that set it
    /// once would leak the last icon's weight into whatever drew next.
    ///
    /// **A dashed stroke is a solid stroke of a different path.** Blend2D stores
    /// a dash array and never strokes with it, so the cutting happens here,
    /// through [Dasher] — see ADR-0278. A solid stroke pays nothing for that:
    /// the dasher hands back the very path it was given.
    ///
    /// @param argb a colour as `0xAARRGGBB`, not premultiplied
    public void strokePath(Path path, Stroke stroke, int argb) {
        strokePath(0, 0, path, stroke, argb);
    }

    /// Strokes `path` with `stroke`, with the path's own origin at `(x, y)`.
    ///
    /// The dashing happens in the path's own coordinates and the result is then
    /// placed, so where the dashes fall does not depend on where the shape is
    /// drawn — two copies of one path at two origins are dashed alike.
    ///
    /// @param argb a colour as `0xAARRGGBB`, not premultiplied
    public void strokePath(double x, double y, Path path, Stroke stroke, int argb) {
        requireOpen();
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(stroke, "stroke");

        var drawn = Dasher.dash(path, stroke.dash());
        if (drawn.isEmpty()) {
            return;
        }
        var scratch = borrowPath();
        try {
            drawn.replayInto(scratch);
            context.strokeWidth(stroke.width());
            context.strokeCaps(toBlend(stroke.cap()));
            context.strokeJoin(toBlend(stroke.join()));
            context.strokeMiterLimit(stroke.miterLimit());
            context.strokePath(x, y, scratch, argb);
        } finally {
            releasePath();
        }
    }

    /// Fills `path`, with the path's own origin placed at logical `(x, y)`.
    ///
    /// Package-private since ADR-0277: `BlendPath` is a `:natives` type and this
    /// package is as far as it goes. `:core`'s own painters keep it because they
    /// build into a pooled path and reset it between shapes, which is cheaper
    /// than a value they would throw away on the next box.
    ///
    /// @param argb a colour as `0xAARRGGBB`, not premultiplied
    void fillPath(double x, double y, BlendPath path, int argb) {
        requireOpen();
        context.fillPath(x, y, path, argb);
    }

    /// The same, filled **even-odd**: a sub-path inside another is a hole.
    ///
    /// Package-private and deliberately not on [Path], which is where a public
    /// fill rule would have to live. A `Path` is a value describing a shape, and
    /// a fill rule is a statement about how to read one — attaching it to the
    /// value would mean every path in the toolkit carries an answer to a
    /// question only one drawing asks, and adding it to the public
    /// [#fillPath(double, double, Path, int)] would be API surface nothing has
    /// requested (ADR-0427).
    ///
    /// That one drawing is a drop shadow with the box's own rectangle cut out of
    /// it. When something else needs a hole, this is what it grows out of.
    ///
    /// @param argb a colour as `0xAARRGGBB`, not premultiplied
    void fillPathEvenOdd(double x, double y, BlendPath path, int argb) {
        requireOpen();
        context.fillPathEvenOdd(x, y, path, argb);
    }

    /// Fills `path` with `gradient`, with the path's own origin placed at
    /// logical `(x, y)`.
    ///
    /// **The origin moves the path and not the ramp.** A gradient is a statement
    /// about a *region of the surface* rather than about a shape: one placed
    /// from the top of a plot to its baseline is the same ramp for every band
    /// filled through it, which is what lets a chart build one gradient and draw
    /// several figures in it
    /// (ADR-0207).
    ///
    /// Its coordinates are logical, like everything else here.
    ///
    /// The gradient is the caller's to close, and it may be closed as soon as
    /// this returns — Blend2D retains its own reference for the fill.
    void fillPath(double x, double y, BlendPath path, BlendGradient gradient) {
        requireOpen();
        context.fillPath(x, y, path, gradient);
    }

    /// Fills `path` with a rasterizer gradient the caller built and will close.
    ///
    /// Package-private, for [ColourGlyphPainter]: a COLRv1 glyph's gradients are
    /// radial and conic as well as linear, extend by repeating and reflecting as
    /// well as padding, and carry a matrix of their own — none of which the
    /// public [Gradient] value says, nor needs to for anything an application
    /// draws (ADR-0456).
    void fillPath(Path path, BlendGradient gradient) {
        requireOpen();
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(gradient, "gradient");
        if (path.isEmpty()) {
            return;
        }
        var scratch = borrowPath();
        try {
            path.replayInto(scratch);
            context.fillPath(0, 0, scratch, gradient);
        } finally {
            releasePath();
        }
    }

    /// Composites `layer` at logical `(x, y)` with `compOp` rather than by
    /// drawing it over.
    ///
    /// Package-private, for [ColourGlyphPainter]'s `PaintComposite`: a waving
    /// flag is its stripes with a shading layer soft-lit onto them, and that is
    /// one layer blitted onto another with an operator the font names
    /// (ADR-0456). The operator is context state and goes back to source-over
    /// before this returns, whatever the blit did.
    void drawLayer(double x, double y, Layer layer, BlendCompOp compOp) {
        requireOpen();
        Objects.requireNonNull(layer, "layer");
        Objects.requireNonNull(compOp, "compOp");
        var size = layer.size();
        var factor = scale.factor();
        try (var view = BlendImage.wrapping(
                layer.pixels().pixels(),
                size.width(),
                size.height(),
                layer.pixels().stride())) {
            context.compOp(compOp);
            try {
                context.blitScaled(x, y, size.width() / factor, size.height() / factor, view);
            } finally {
                context.compOp(BlendCompOp.SRC_OVER);
            }
        }
    }

    /// The transform in force, in logical coordinates — what [#transform] and
    /// [#concat] have made of it.
    ///
    /// Package-private: a painter that renders part of a picture offscreen has
    /// to know where on the device that part lands, so that the offscreen layer
    /// is exactly as large as the part and lines up with the pixels it will be
    /// composited back onto. Everything else composes with [#concat] and never
    /// needs to read the matrix back.
    Affine matrix() {
        return matrix;
    }

    /// Strokes `path`, with the path's own origin placed at logical `(x, y)`.
    ///
    /// The stroke style travels with the call rather than being frame state.
    /// Blend2D's is context state, so a frame that set it once would leak the
    /// last icon's weight into whatever drew next — and the bug would be a
    /// hairline somewhere else entirely.
    ///
    /// @param width the stroke width in logical pixels
    /// @param argb  a colour as `0xAARRGGBB`, not premultiplied
    void strokePath(
            double x, double y, BlendPath path, double width, BlendStrokeCap cap, BlendStrokeJoin join, int argb) {
        requireOpen();
        context.strokeWidth(width);
        context.strokeCaps(cap);
        context.strokeJoin(join);
        context.strokePath(x, y, path, argb);
    }

    /// Replaces the frame's transform with `[a b c d e f]`, in logical
    /// coordinates.
    ///
    /// ```
    ///   x' = a·x + c·y + e
    ///   y' = b·x + d·y + f
    /// ```
    ///
    /// Six doubles rather than the toolkit's own matrix type, and deliberately:
    /// that type is the *computed value of a CSS property* and lives in the
    /// cascade, while a `Frame` is the surface underneath everything and knows
    /// nothing about stylesheets. What crosses is the numbers, the same way they
    /// cross into Blend2D one layer further down.
    ///
    /// **There is no push and no pop.** Each call states the whole transform, so
    /// a caller drawing a transformed subtree accumulates the stack itself and
    /// sets an absolute matrix per node. That is not a limitation worked around —
    /// it is what lets hit testing invert the same matrix the painter used,
    /// rather than a second one built from the same inputs by different code
    /// (ADR-0068). A painter that does not know what it is drawing under — which
    /// is every `canvas` painter — wants [#concat] instead.
    ///
    /// The display scale is **not** the caller's to apply: it is already on the
    /// context and is composed with this. A frame at 150% given `translate(10, 0)`
    /// moves by ten logical pixels, which is fifteen device ones, exactly as
    /// every other call on this class behaves.
    public void transform(double a, double b, double c, double d, double e, double f) {
        requireOpen();
        // The rasterizer checks the six numbers, so its refusal is the one a
        // caller sees; the mirror is only updated once the call has stood.
        context.transform(a, b, c, d, e, f);
        matrix = new Affine(a, b, c, d, e, f);
    }

    /// **Multiplies** the frame's transform by `[a b c d e f]`, in logical
    /// coordinates.
    ///
    /// ```
    ///   x' = a·x + c·y + e
    ///   y' = b·x + d·y + f
    /// ```
    ///
    /// The caller's matrix is applied **first**, to the coordinates it draws in,
    /// and whatever was already in force is applied to the result. So a painter
    /// may turn a shape without knowing where the thing it is painting into sits.
    /// That is the difference from [#transform], which replaces: inside a
    /// `canvas` the matrix already carries the translation that puts the canvas
    /// on screen, the painter cannot read it back, and replacing it draws at the
    /// window's corner (ADR-0390, `docs/gaps.md` G46).
    ///
    /// **This does not push anything.** [#save()] and [#restore()] are the
    /// state stack, here as they are for the clip, and a painter that composed
    /// without saving leaves the frame turned for whatever draws next.
    ///
    /// The display scale is not the caller's to apply, exactly as in [#transform]:
    /// at 150% a concatenated `translate(10, 0)` moves ten logical pixels.
    ///
    /// The composition is done in Java rather than by the rasterizer's own
    /// compose operation. Blend2D has one, but its enumerator is not among the
    /// constants the layout verifier checks against the compiled library, and
    /// adding it would mean changing the native build for arithmetic that is six
    /// multiplies — arithmetic which must agree exactly with what hit testing
    /// inverts, and therefore has one implementation (ADR-0068).
    public void concat(double a, double b, double c, double d, double e, double f) {
        requireOpen();
        var composed = new Affine(a, b, c, d, e, f).then(matrix);
        context.transform(composed.a(), composed.b(), composed.c(), composed.d(), composed.e(), composed.f());
        matrix = composed;
    }

    /// Back to untransformed logical coordinates.
    public void resetTransform() {
        requireOpen();
        context.resetTransform();
        matrix = Affine.IDENTITY;
    }

    /// Composites `layer` with its top-left corner at logical `(x, y)`, faded to
    /// `alpha`.
    ///
    /// **This is what makes `opacity` a group.** The layer was rasterized at full
    /// strength; fading happens once, here, to the finished raster. Fading each
    /// shape as it was drawn gives a different answer wherever two of them
    /// overlap — the lower one shows through the upper — and CSS specifies this
    /// one (ADR-0071).
    ///
    /// The layer's pixels are its own; this reads them and copies. Nothing here
    /// takes ownership, so the same layer can be composited into several frames
    /// and kept across them, which is the point of it having a lifetime at all.
    ///
    /// @param alpha 0 to 1
    public void drawLayer(double x, double y, Layer layer, double alpha) {
        requireOpen();
        java.util.Objects.requireNonNull(layer, "layer");
        if (alpha <= 0) {
            // Nothing to show, and a blit is a full copy of the layer's area.
            return;
        }
        var size = layer.size();
        // The raster is in PHYSICAL pixels and this context is in LOGICAL ones,
        // so the blit has to say how big the raster is in the context's units or
        // it is drawn one raster pixel per logical unit -- which is right at 1x
        // and twice the size at 2x
        // (ADR-0157).
        //
        // Derived from the raster rather than from the bounds the caller laid
        // out: `Layer.of` rounds the physical size *up*, so at a fractional scale
        // the raster is a fraction of a pixel larger than the box, and dividing
        // it back is what maps it one-for-one onto the device. Asking the caller
        // for the logical size instead would squash it by that fraction and
        // leave a seam.
        var factor = scale.factor();
        // A view over the layer's pixels, made and dropped here. An image is a
        // view and views are cheap; holding one across frames would mean holding
        // a native handle to a buffer whose lifetime is the layer's, not this
        // frame's.
        try (var image = BlendImage.wrapping(
                layer.pixels().pixels(),
                size.width(),
                size.height(),
                layer.pixels().stride())) {
            var faded = alpha < 1;
            if (faded) {
                context.globalAlpha(alpha);
            }
            try {
                context.blitScaled(x, y, size.width() / factor, size.height() / factor, image);
            } finally {
                if (faded) {
                    // Context state, so it must go back: the next thing drawn on
                    // this frame did not ask to be faded, and the bug would show
                    // up somewhere else entirely.
                    context.globalAlpha(1);
                }
            }
        }
    }

    /// Draws `image` at its natural size, with its top-left corner at logical
    /// `(x, y)`.
    ///
    /// **Natural size means one image pixel per device pixel**, not per logical
    /// unit: a 64&times;64 icon covers 64 logical pixels at 100% and 32 at 200%,
    /// and is crisp on both. The alternative — 64 logical units everywhere —
    /// would double its physical size on a retina display and smear it, which is
    /// the bug ADR-0157 found in layers and is the same arithmetic here.
    ///
    /// An image that is meant to *scale* with the interface rather than stay
    /// pixel-exact is one whose size the caller states, which is the overload
    /// below.
    public void drawImage(Image image, double x, double y) {
        Objects.requireNonNull(image, "image");
        var factor = scale.factor();
        drawImage(image, image.bounds(), x, y, image.width() / factor, image.height() / factor, 1);
    }

    /// Draws `image` into the logical rectangle `(x, y, width, height)`, scaled to
    /// fit it.
    ///
    /// Nothing here preserves the aspect ratio: a caller that wants `contain` or
    /// `cover` behaviour is doing layout, and knows both the image's size and the
    /// box's. Doing it silently would make the two cases indistinguishable.
    public void drawImage(Image image, double x, double y, double width, double height) {
        drawImage(image, x, y, width, height, 1);
    }

    /// The same, faded to `alpha`.
    ///
    /// One image faded once — unlike [#drawLayer], there is no group here for the
    /// fade to mean anything subtler than "draw this more faintly".
    ///
    /// @param alpha 0 to 1
    public void drawImage(Image image, double x, double y, double width, double height, double alpha) {
        Objects.requireNonNull(image, "image");
        drawImage(image, image.bounds(), x, y, width, height, alpha);
    }

    /// Draws the part of `image` inside `source` into the logical rectangle
    /// `(x, y, width, height)`, faded to `alpha`.
    ///
    /// **The crop, and the one overload with two coordinate spaces in it.**
    /// `source` is in the image's own pixels — it is which pixels to take — and
    /// the destination is in logical units, which is where they go. They are
    /// deliberately not related: a caller drawing a 200&times;200 region into a
    /// 100&times;100 box is asking for it to be scaled down, and one drawing it
    /// into a 200&times;200 box at 200% is asking for it to stay pixel-exact
    /// (ADR-0283).
    ///
    /// @param source the region of the image to draw, which must lie inside it
    /// @param alpha 0 to 1
    /// @throws IllegalArgumentException if `source` runs outside the image, or is
    ///         empty, or the destination rectangle is not positive
    public void drawImage(
            Image image, PhysicalRect source, double x, double y, double width, double height, double alpha) {
        requireOpen();
        Objects.requireNonNull(image, "image");
        Objects.requireNonNull(source, "source");
        if (alpha <= 0) {
            // Invisible, and a blit is a full read of the source region.
            return;
        }
        if (!(alpha <= 1)) {
            throw new IllegalArgumentException("an alpha is between 0 and 1, and " + alpha + " is not");
        }
        if (source.isEmpty()) {
            throw new IllegalArgumentException("there is nothing to draw: the source rectangle is " + source);
        }
        // Checked here rather than left to Blend2D, which intersects an
        // out-of-range source rectangle with the image and draws the overlap. That
        // silently draws something smaller than was asked for, in the wrong place
        // -- a crop whose numbers came from a document that was edited elsewhere
        // is exactly the case that needs to be told.
        if (!source.fitsWithin(image.size())) {
            throw new IllegalArgumentException(
                    "the source rectangle " + source + " is not inside a " + image.size() + " image");
        }
        if (!(width > 0) || !(height > 0)) {
            throw new IllegalArgumentException(
                    "an image is drawn into a positive rectangle, and " + width + "x" + height + " is not one");
        }

        var buffer = image.pixels();
        // A view over the image's pixels, made and dropped here -- exactly as
        // `drawLayer` does, and for the same reason: an image is a view, views are
        // cheap, and holding one across frames would mean holding a native handle
        // to a buffer whose lifetime is the application's.
        try (var view = BlendImage.wrapping(
                buffer.pixels(), buffer.size().width(), buffer.size().height(), buffer.stride())) {
            var faded = alpha < 1;
            if (faded) {
                context.globalAlpha(alpha);
            }
            try {
                context.blitScaled(x, y, width, height, view, source.x(), source.y(), source.width(), source.height());
            } finally {
                if (faded) {
                    // Context state, so it goes back -- see `drawLayer`.
                    context.globalAlpha(1);
                }
            }
        }
    }

    /// Restricts everything drawn afterwards to `(x, y, width, height)`, in
    /// logical coordinates.
    ///
    /// What makes a **partial repaint** possible: a frame clipped to the region
    /// that changed rasterizes only that region, and the rest of the buffer keeps
    /// the pixels the last frame left there. That last clause is the whole
    /// correctness condition, and it is not this class's to promise — see
    /// [BackendWindow#retainsFrameContents()]
    /// (ADR-0072).
    ///
    /// Intersected with any clip already in force. [#resetClip()] undoes it.
    public void clipTo(double x, double y, double width, double height) {
        requireOpen();
        context.clipTo(x, y, width, height);
        // After the call, which is what refuses a clip that is not one.
        clip = clip.intersect(toPixels(Clip.of(x, y, width, height)));
    }

    /// Back to the whole frame -- or, inside a [#save()], to the clip in force
    /// when it was made, which is where Blend2D's own reset goes.
    public void resetClip() {
        requireOpen();
        context.resetClip();
        var top = saved.peek();
        clip = top == null ? Clip.NONE : top.clip();
    }

    /// Pushes clip, transform, style and alpha, so that whatever is drawn next
    /// can be undone exactly.
    ///
    /// **For handing the frame to somebody else.** Every painter inside the
    /// toolkit knows what it set and unsets it; a `canvas`'s painter is an
    /// application's, runs inside whatever clip and transform the tree already
    /// established, and may leave anything at all behind. [#resetClip()] cannot
    /// undo that — it goes back to the *whole frame*, so a canvas inside a
    /// `scroll` would paint over the viewport's edge — which is why this exists
    /// and why the export list grew a state stack for it
    /// (ADR-0193).
    ///
    /// Must be paired with [#restore()], and the pair is the caller's to balance.
    public void save() {
        requireOpen();
        context.save();
        saved.push(new Saved(matrix, clip));
    }

    /// Pops what [#save()] pushed.
    public void restore() {
        requireOpen();
        context.restore();
        var top = saved.poll();
        if (top != null) {
            matrix = top.matrix();
            clip = top.clip();
        }
    }

    /// Runs `body` with everything it draws confined to `(x, y, width, height)`,
    /// in logical coordinates: the part of the frame being **repainted**, when
    /// the rest still holds the last frame's pixels (ADR-0072).
    ///
    /// A clip, as far as the pixels go, with two differences that matter to a
    /// partial repaint:
    ///
    /// - **It outlasts [#resetClip()].** Inside `body` a reset goes back to the
    ///   region rather than to the whole frame, so a scroll view that widens its
    ///   clip again when its subtree ends cannot widen it past what is being
    ///   repainted.
    /// - **It does not scissor GPU layers.** A layer placed in `body` is placed
    ///   with the part of it the other clips let through, whether or not that
    ///   lies inside the region: which part of the frame is repainted is not
    ///   which part of a video can be seen, and a layer only half inside the
    ///   damage still shows whole (ADR-0481).
    ///
    /// Scoped, like a pass on the GPU, so the region cannot be left in force:
    /// clip and transform are back to what they were when `body` returns or
    /// throws. A region of no area repaints nothing, and `body` does not run.
    ///
    /// @throws IllegalStateException when called inside another's `body`
    public void repaintOnly(double x, double y, double width, double height, Runnable body) {
        requireOpen();
        Objects.requireNonNull(body, "body");
        if (region != null) {
            throw new IllegalStateException(
                    "repaintOnly does not nest: this frame is already repainting only " + region);
        }
        if (!(width > 0) || !(height > 0)) {
            return;
        }
        // Two saves around the clip: the outer one is what puts the clip back
        // afterwards, and the inner one is what Blend2D's reset returns to
        // inside `body`, which is how the region outlasts a reset.
        context.save();
        try {
            context.clipTo(x, y, width, height);
            context.save();
            saved.push(new Saved(matrix, clip));
            var depth = saved.size();
            region = toPixels(Clip.of(x, y, width, height));
            try {
                body.run();
            } finally {
                region = null;
                // Whatever `body` saved and did not restore goes with it, so
                // the mirror agrees with the context again below.
                while (saved.size() > depth) {
                    saved.pop();
                }
                var top = saved.pop();
                matrix = top.matrix();
                clip = top.clip();
                context.restore();
            }
        } finally {
            context.restore();
        }
    }

    /// Places `content`, which the GPU draws, in the logical rectangle
    /// `(x, y, width, height)` and in paint order (`docs/gpu-plan.md`, D4;
    /// ADR-0481).
    ///
    /// ```java
    /// Box.of().painting((frame, size) -> {
    ///     if (!frame.gpuLayer(video, 0, 0, size.width(), size.height())) {
    ///         paintThePosterInstead(frame, size);
    ///     }
    /// });
    /// ```
    ///
    /// What is painted after this is above the layer, and what was painted
    /// before it under its box is hidden: a GPU layer is **opaque**, as an
    /// opaque box would be. How it gets there depends on the frame's surface:
    ///
    /// - **Composited**: the box is cleared to transparent -- a hole -- and the
    ///   compositor draws the layer under the frame when it is presented.
    /// - **Read back**: the layer is rendered on the GPU now, downloaded, and
    ///   its pixels replace the box's. This blocks until the GPU is done.
    ///
    /// Either way the layer is recorded in [#gpuPlacements()], with the part of
    /// it the clips in force let through as its scissor, and the two ways show
    /// the same pixels.
    ///
    /// **An axis-aligned rectangle of whole pixels.** The box goes through the
    /// transform in force and is rounded to the nearest physical pixel on each
    /// edge; under a rotation it is the bounding box. Clips are rectangles in
    /// the same way, so a rounded clip scissors a layer to its bounding box.
    ///
    /// A layer the clips hide entirely is not placed and not rendered: a video
    /// scrolled out of view costs nothing.
    ///
    /// @return false when this frame cannot show GPU content -- no GPU, a
    ///         nested frame such as an `opacity` group's, or a read-back that
    ///         failed -- and nothing was drawn: the caller paints what it shows
    ///         instead. True otherwise, including when nothing of it can be seen
    /// @throws IllegalArgumentException when a coordinate is not finite
    public boolean gpuLayer(GpuContent content, double x, double y, double width, double height) {
        requireOpen();
        Objects.requireNonNull(content, "content");
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(width) || !Double.isFinite(height)) {
            throw new IllegalArgumentException("a GPU layer is placed at finite coordinates, not " + width + "x"
                    + height + " at (" + x + ", " + y + ")");
        }
        var surface = gpu;
        if (surface == null) {
            return false;
        }
        if (!(width > 0) || !(height > 0)) {
            return true;
        }
        var target = round(toPixels(Clip.of(x, y, width, height)));
        if (target.isEmpty()) {
            return true;
        }
        var scissor = intersect(
                target,
                round(clip.intersect(
                        Clip.of(0, 0, buffer.size().width(), buffer.size().height()))));
        if (scissor.isEmpty()) {
            return true;
        }
        // Outside the region being repainted this frame's pixels are last
        // frame's, which already show the layer where it is: nothing to draw.
        var painted = region == null || !intersect(scissor, round(region)).isEmpty();
        switch (surface) {
            case GpuSurface.Composited _ -> {
                if (painted) {
                    punch(target);
                }
            }
            case GpuSurface.ReadBack readBack -> {
                if (painted) {
                    var pixels = readBack.render(content, target.size());
                    if (pixels.isEmpty()) {
                        return false;
                    }
                    replace(target, pixels.get());
                }
            }
        }
        placements.add(new GpuPlacement(content, target, scissor));
        return true;
    }

    /// Whether this frame can show GPU layers at all: it was made over a
    /// window's [GpuSurface]. False with the GPU off, on a window with none, and
    /// in a frame nested in an `opacity` group or a promoted layer. Where it is
    /// true, [#gpuLayer] can still fail, when the GPU cannot render the layer.
    public boolean hasGpu() {
        return gpu != null;
    }

    /// The GPU layers [#gpuLayer] placed on this frame, in paint order. Readable
    /// after [#end()], which is when the frame's owner reads it.
    public List<GpuPlacement> gpuPlacements() {
        return List.copyOf(placements);
    }

    /// Clears `target`, in physical pixels, to transparent black under the clip
    /// in force: the hole a composited layer shows through.
    private void punch(PhysicalRect target) {
        context.save();
        try {
            context.transformToPixels();
            context.compOp(BlendCompOp.SRC_COPY);
            context.fillRect(target.x(), target.y(), target.width(), target.height(), 0x00000000);
        } finally {
            context.compOp(BlendCompOp.SRC_OVER);
            context.restore();
        }
    }

    /// Replaces `target`, in physical pixels, with `pixels` under the clip in
    /// force: a read-back layer, pixel for pixel, as the compositor would have
    /// drawn it -- replacing rather than blending, as its quad does.
    private void replace(PhysicalRect target, PixelBuffer pixels) {
        if (!pixels.size().equals(target.size())) {
            throw new IllegalStateException(
                    "a read-back layer came back " + pixels.size() + " for a " + target.size() + " box");
        }
        // Direct memory, which is all Blend2D can read, and held until `end`:
        // the context may be threaded, and then the blit is only queued when
        // this returns. What a read-back gives is on the heap, since the GPU's
        // own memory is handed back as soon as it is copied out.
        var source = pixels.pixels();
        if (!source.isDirect()) {
            var direct = ByteBuffer.allocateDirect(source.remaining()).order(ByteOrder.nativeOrder());
            direct.put(0, source, source.position(), source.remaining());
            source = direct;
        }
        drawnPixels.add(source);
        try (var view =
                BlendImage.wrapping(source, pixels.size().width(), pixels.size().height(), pixels.stride())) {
            context.save();
            try {
                context.transformToPixels();
                context.compOp(BlendCompOp.SRC_COPY);
                context.blit(target.x(), target.y(), view);
            } finally {
                context.compOp(BlendCompOp.SRC_OVER);
                context.restore();
            }
        }
    }

    /// `logical`, a rectangle under the transform in force, as its bounding box
    /// in physical pixels.
    private Clip toPixels(Clip logical) {
        var mapped = logical.map(matrix);
        var factor = scale.factor();
        return new Clip(
                mapped.left() * factor, mapped.top() * factor, mapped.right() * factor, mapped.bottom() * factor);
    }

    /// `pixels` with each edge rounded to the nearest whole pixel: the one rule
    /// both a layer's hole and its quad are placed by. Empty when it covers no
    /// whole pixel; clamped to the range of an `int`.
    private static PhysicalRect round(Clip pixels) {
        var left = edge(pixels.left());
        var top = edge(pixels.top());
        var right = edge(pixels.right());
        var bottom = edge(pixels.bottom());
        if (right <= left || bottom <= top) {
            return new PhysicalRect(left, top, 0, 0);
        }
        return new PhysicalRect(left, top, right - left, bottom - top);
    }

    private static int edge(double value) {
        return (int) Math.max(Integer.MIN_VALUE / 2, Math.min(Integer.MAX_VALUE / 2, Math.round(value)));
    }

    private static PhysicalRect intersect(PhysicalRect a, PhysicalRect b) {
        var left = Math.max(a.x(), b.x());
        var top = Math.max(a.y(), b.y());
        var right = Math.min(a.right(), b.right());
        var bottom = Math.min(a.bottom(), b.bottom());
        if (right <= left || bottom <= top) {
            return new PhysicalRect(left, top, 0, 0);
        }
        return new PhysicalRect(left, top, right - left, bottom - top);
    }

    /// Finishes the frame, so the pixels are complete before anything presents
    /// them.
    ///
    /// Finishes the frame: flushes whatever the Blend2D context still has in
    /// flight and releases it, so the buffer holds the whole picture.
    ///
    /// Ending a frame is the caller-of-[#over]'s job -- [Window]'s, in the
    /// toolkit -- and an application that ends the frame it was handed to paint
    /// invalidates its own canvas halfway through. That used to be enforced by
    /// this being package-private, and is now enforced by the frame itself:
    /// ending twice is a no-op and painting afterwards throws, so the mistake is
    /// loud rather than a half-drawn window
    /// (ADR-0172).
    public void end() {
        if (ended) {
            return;
        }
        ended = true;
        try {
            context.close();
        } finally {
            try {
                // The pool's paths are native allocations of this frame's, and
                // the frame is what owns them -- so they go back before the
                // image does, whatever the context did on its way out.
                paths.forEach(BlendPath::close);
                paths.clear();
                borrowed = 0;
            } finally {
                image.close();
                // The context has joined its workers, so nothing reads these.
                drawnPixels.clear();
            }
        }
    }

    /// A rasterizer path to build into, reset and ready.
    ///
    /// Borrowed and returned rather than allocated: see [#paths]. Must be paired
    /// with [#releasePath()] in a `finally`, because a painter that throws
    /// half-way must not leave the frame believing a path is still out.
    BlendPath borrowPath() {
        if (borrowed == paths.size()) {
            paths.add(BlendPath.create());
        }
        var path = paths.get(borrowed++);
        path.reset();
        return path;
    }

    void releasePath() {
        borrowed--;
    }

    /// A [Gradient] as the rasterizer's own, for the length of one fill.
    private static BlendGradient toBlend(Gradient gradient) {
        return switch (gradient) {
            case Gradient.Linear linear -> {
                var ramp = BlendGradient.linear(linear.x1(), linear.y1(), linear.x2(), linear.y2());
                try {
                    for (var stop : linear.stops()) {
                        ramp.addStop(stop.offset(), stop.argb());
                    }
                } catch (RuntimeException | Error e) {
                    ramp.close();
                    throw e;
                }
                yield ramp;
            }
        };
    }

    /// A toolkit [Cap] as the rasterizer's own.
    ///
    /// A `switch` rather than an ordinal: the two enumerations agree today and
    /// the C one is not alphabetical — round is 2, with a reversed round at 3 —
    /// so an ordinal would be a coincidence the next upstream bump could take
    /// away silently.
    private static BlendStrokeCap toBlend(Cap cap) {
        return switch (cap) {
            case BUTT -> BlendStrokeCap.BUTT;
            case SQUARE -> BlendStrokeCap.SQUARE;
            case ROUND -> BlendStrokeCap.ROUND;
        };
    }

    /// A toolkit [Join] as the rasterizer's own.
    ///
    /// [Join#MITER] is Blend2D's `MITER_CLIP`, which is the variant that cuts the
    /// spike off at the limit — SVG's `miter` with `stroke-miterlimit`, and the
    /// reason the limit had to be bound before this could be honest (ADR-0278).
    private static BlendStrokeJoin toBlend(Join join) {
        return switch (join) {
            case MITER -> BlendStrokeJoin.MITER_CLIP;
            case BEVEL -> BlendStrokeJoin.BEVEL;
            case ROUND -> BlendStrokeJoin.ROUND;
        };
    }

    private void requireOpen() {
        if (ended) {
            throw new IllegalStateException("this frame has already been presented — a Frame is valid only inside the"
                    + " paint callback it was handed to");
        }
    }
}
