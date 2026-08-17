package io.github.digitalsmile.goldberry;

import io.github.digitalsmile.goldberry.backend.DisplayScale;
import io.github.digitalsmile.goldberry.backend.LogicalSize;
import io.github.digitalsmile.goldberry.backend.PhysicalSize;
import io.github.digitalsmile.goldberry.backend.PixelBuffer;
import io.github.digitalsmile.goldberry.natives.blend2d.BlendContext;
import io.github.digitalsmile.goldberry.natives.blend2d.BlendFont;
import io.github.digitalsmile.goldberry.natives.blend2d.BlendGlyphBuffer;
import io.github.digitalsmile.goldberry.natives.blend2d.BlendImage;
import io.github.digitalsmile.goldberry.natives.blend2d.BlendPath;
import io.github.digitalsmile.goldberry.natives.blend2d.BlendStrokeCap;
import io.github.digitalsmile.goldberry.natives.blend2d.BlendStrokeJoin;

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
public final class Frame {

    private final PixelBuffer buffer;
    private final DisplayScale scale;
    private final BlendImage image;
    private final BlendContext context;

    private boolean ended;

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
    /// The primitive, not the text API. It takes a font and a buffer of
    /// positioned glyphs because that is what a rasterizer draws; deciding
    /// *which* glyphs, at what positions, is shaping, and
    /// [Font][io.github.digitalsmile.goldberry.text.Font] is what joins the two.
    /// Call that instead unless there is a reason not to.
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
    public void drawGlyphs(
            double x, double baseline, BlendFont font, BlendGlyphBuffer glyphs, int argb) {
        requireOpen();
        context.fillGlyphRun(x, baseline, font, glyphs, argb);
    }

    /// Fills `path`, with the path's own origin placed at logical `(x, y)`.
    ///
    /// @param argb a colour as `0xAARRGGBB`, not premultiplied
    public void fillPath(double x, double y, BlendPath path, int argb) {
        requireOpen();
        context.fillPath(x, y, path, argb);
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
    public void strokePath(
            double x, double y, BlendPath path, double width,
            BlendStrokeCap cap, BlendStrokeJoin join, int argb) {
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
    /// ([ADR-0068](../../../book/src/adr/0068-the-transform-stack-is-java-side.md)).
    ///
    /// The display scale is **not** the caller's to apply: it is already on the
    /// context and is composed with this. A frame at 150% given `translate(10, 0)`
    /// moves by ten logical pixels, which is fifteen device ones, exactly as
    /// every other call on this class behaves.
    public void transform(double a, double b, double c, double d, double e, double f) {
        requireOpen();
        context.transform(a, b, c, d, e, f);
    }

    /// Back to untransformed logical coordinates.
    public void resetTransform() {
        requireOpen();
        context.resetTransform();
    }

    /// Composites `layer` with its top-left corner at logical `(x, y)`, faded to
    /// `alpha`.
    ///
    /// **This is what makes `opacity` a group.** The layer was rasterized at full
    /// strength; fading happens once, here, to the finished raster. Fading each
    /// shape as it was drawn gives a different answer wherever two of them
    /// overlap — the lower one shows through the upper — and CSS specifies this
    /// one ([ADR-0071](../../../book/src/adr/0071-a-layer-is-a-subtrees-raster.md)).
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
        // A view over the layer's pixels, made and dropped here. An image is a
        // view and views are cheap; holding one across frames would mean holding
        // a native handle to a buffer whose lifetime is the layer's, not this
        // frame's.
        try (var image = BlendImage.wrapping(
                layer.pixels().pixels(), size.width(), size.height(),
                layer.pixels().stride())) {
            var faded = alpha < 1;
            if (faded) {
                context.globalAlpha(alpha);
            }
            try {
                context.blit(x, y, image);
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

    /// Restricts everything drawn afterwards to `(x, y, width, height)`, in
    /// logical coordinates.
    ///
    /// What makes a **partial repaint** possible: a frame clipped to the region
    /// that changed rasterizes only that region, and the rest of the buffer keeps
    /// the pixels the last frame left there. That last clause is the whole
    /// correctness condition, and it is not this class's to promise — see
    /// [io.github.digitalsmile.goldberry.backend.BackendWindow#retainsFrameContents()]
    /// ([ADR-0072](../../../book/src/adr/0072-a-partial-repaint-needs-a-promise.md)).
    ///
    /// Intersected with any clip already in force. [#resetClip()] undoes it.
    public void clipTo(double x, double y, double width, double height) {
        requireOpen();
        context.clipTo(x, y, width, height);
    }

    /// Back to the whole frame.
    public void resetClip() {
        requireOpen();
        context.resetClip();
    }

    /// Finishes the frame, so the pixels are complete before anything presents
    /// them.
    ///
    /// Package-private: ending a frame is [Window]'s job, and an application
    /// that could do it would be able to invalidate its own canvas halfway
    /// through painting.
    void end() {
        if (ended) {
            return;
        }
        ended = true;
        try {
            context.close();
        } finally {
            image.close();
        }
    }

    private void requireOpen() {
        if (ended) {
            throw new IllegalStateException(
                    "this frame has already been presented — a Frame is valid only inside the"
                            + " paint callback it was handed to");
        }
    }
}
