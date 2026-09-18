package io.github.digitalsmile.goldberry.natives.blend2d;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Objects;

import org.slf4j.Logger;

import io.github.digitalsmile.goldberry.log.Logs;
import io.github.digitalsmile.goldberry.natives.blend2d.enums.BlendCompOp;
import io.github.digitalsmile.goldberry.natives.blend2d.enums.BlendStrokeCap;
import io.github.digitalsmile.goldberry.natives.blend2d.enums.BlendStrokeJoin;
import io.github.digitalsmile.goldberry.natives.blend2d.enums.BlendTransformOp;
import io.github.digitalsmile.goldberry.natives.blend2d.error.BlendException;
import io.github.digitalsmile.goldberry.natives.layout.Layouts;

/// A rendering context — the thing that actually draws.
///
/// Created over a [BlendImage], used for one frame, and closed. Blend2D is built
/// around exactly this pattern: `begin` attaches the context to an image and
/// takes a reference to it, `end` detaches and makes the pixels safe to read.
///
/// ## Coordinates
///
/// A context created through [#on(BlendImage, double)] is scaled once, and
/// everything drawn into it afterwards is in **logical** coordinates. That is
/// not a convenience — it is the whole fractional-DPI story on the paint side.
/// A rectangle at logical `x = 10.5` on a 1.5&times; display lands at physical
/// 15.75, and Blend2D antialiases the edge across two pixels rather than
/// snapping it to one and moving the rectangle by a quarter of a pixel.
///
/// ## Colours
///
/// Colours are `0xAARRGGBB` and **not** premultiplied, even though the image
/// they land in is. Blend2D premultiplies a style itself when it composites, so
/// premultiplying first would apply alpha twice and darken every translucent
/// fill. This is the opposite of what writing pixels by hand requires, and it is
/// the single easiest thing to get wrong when moving from one to the other.
///
/// ## Threads
///
/// A context created with a non-zero thread count renders **asynchronously**:
/// draw calls are recorded and workers execute them over horizontal bands. The
/// pixels are not complete until [#close()] returns, which is the same rule that
/// already applied — a synchronous context may have work in flight too — but it
/// is the rule this makes load-bearing. See ADR-0042.
///
/// Asking for threads is a request, not a demand: if Blend2D cannot acquire any,
/// the context is begun synchronously instead and [#threadCount()] reports zero.
/// A frame that draws is better than a frame that throws because a thread pool
/// was busy.
///
/// Confined to the thread that created it, and must be closed.
public final class BlendContext implements AutoCloseable {

    private static final Logger LOG = Logs.of(BlendContext.class);

    private static final long CREATE_THREAD_COUNT = Layouts.BL_CONTEXT_CREATE_INFO.offsetOf("thread_count");

    private final Blend2dContext calls = Blend2dContext.get();
    private final Arena arena;
    private final MemorySegment context;
    private final MemorySegment rect;
    private final MemorySegment sourceRect;
    private final MemorySegment origin;
    private final MemorySegment matrix;
    private final Thread owner = Thread.currentThread();
    private final BlendImage image;
    private final double scale;
    private final int threads;

    private boolean ended;

    private static final long RECT_X = Layouts.BL_RECT.offsetOf("x");
    private static final long RECT_Y = Layouts.BL_RECT.offsetOf("y");
    private static final long RECT_W = Layouts.BL_RECT.offsetOf("w");
    private static final long RECT_H = Layouts.BL_RECT.offsetOf("h");

    private static final long RECT_I_X = Layouts.BL_RECT_I.offsetOf("x");
    private static final long RECT_I_Y = Layouts.BL_RECT_I.offsetOf("y");
    private static final long RECT_I_W = Layouts.BL_RECT_I.offsetOf("w");
    private static final long RECT_I_H = Layouts.BL_RECT_I.offsetOf("h");

    private static final long POINT_X = Layouts.BL_POINT.offsetOf("x");
    private static final long POINT_Y = Layouts.BL_POINT.offsetOf("y");

    private static final long MATRIX_M00 = Layouts.BL_MATRIX2D.offsetOf("m00");
    private static final long MATRIX_M01 = Layouts.BL_MATRIX2D.offsetOf("m01");
    private static final long MATRIX_M10 = Layouts.BL_MATRIX2D.offsetOf("m10");
    private static final long MATRIX_M11 = Layouts.BL_MATRIX2D.offsetOf("m11");
    private static final long MATRIX_M20 = Layouts.BL_MATRIX2D.offsetOf("m20");
    private static final long MATRIX_M21 = Layouts.BL_MATRIX2D.offsetOf("m21");

    private BlendContext(BlendImage image, double scale, int requestedThreads) {
        this.image = image;
        this.scale = scale;
        this.arena = Arena.ofConfined();
        int started;
        try {
            this.context = arena.allocate(Layouts.BL_OBJECT_DETAIL.layout());
            // One BLRect, reused for every fillRect. A frame issues thousands of
            // these and each one is four doubles that Blend2D reads and does not
            // keep -- allocating per call would put a confined arena on the hot
            // path to hold sixteen bytes for the duration of one call.
            this.rect = arena.allocate(Layouts.BL_RECT.layout());
            // And one BLRectI beside it, for the source rectangle of a cropped
            // blit -- four ints rather than four doubles, because it addresses an
            // image's own pixels (ADR-0283). Allocated with the rest whether or
            // not this context ever blits a crop: it is sixteen bytes in an arena
            // that is being set up anyway, and a lazily-allocated one would be a
            // second lifetime to reason about.
            this.sourceRect = arena.allocate(Layouts.BL_RECT_I.layout());
            // One BLPoint, reused for every glyph run, for the same reason.
            this.origin = arena.allocate(Layouts.BL_POINT.layout());
            // One BLMatrix2D, reused for every transformed box. A frame with an
            // animating control sets this on the way into the subtree and back
            // on the way out, twice per box per frame.
            this.matrix = arena.allocate(Layouts.BL_MATRIX2D.layout());

            started = begin(requestedThreads);
        } catch (RuntimeException | Error e) {
            arena.close();
            throw e;
        }
        this.threads = started;

        if (scale != 1.0) {
            try {
                calls.contextTransform(context, BlendTransformOp.SCALE, scale, scale);
            } catch (RuntimeException | Error e) {
                // The context is attached but unusable. Detach before rethrowing
                // or the image is left with a reference nothing will release.
                closeQuietly();
                throw e;
            }
        }
    }

    /// Attaches the context, asking for `requested` worker threads.
    ///
    /// @return the thread count actually in force, which is zero when Blend2D
    ///         would not give us the threads and we chose to draw anyway
    private int begin(int requested) {
        if (requested == 0) {
            // NULL create-info asks for the defaults: synchronous, on this
            // thread. This is still the right answer for a small surface, where
            // handing work to a band scheduler costs more than doing it.
            calls.contextBegin(context, image.pointer(), MemorySegment.NULL);
            return 0;
        }

        // Zero-filled by the arena, which is what the other five fields want:
        // no flags, default CPU features, default queue and state limits, origin
        // at (0,0). Only thread_count is asked for.
        var createInfo = arena.allocate(Layouts.BL_CONTEXT_CREATE_INFO.layout());
        createInfo.set(ValueLayout.JAVA_INT, CREATE_THREAD_COUNT, requested);
        try {
            calls.contextBegin(context, image.pointer(), createInfo);
            return requested;
        } catch (BlendException e) {
            // Blend2D refuses asynchronous mode when it cannot acquire a worker
            // -- a thread pool at its limit, or a process that has run out. The
            // frame is still drawable; only slower. Falling back here rather
            // than setting BL_CONTEXT_CREATE_FLAG_FALLBACK_TO_SYNC keeps the
            // decision in Java, where it can be logged and tested, and avoids a
            // magic constant that nothing in the layout table checks.
            LOG.debug("Blend2D refused {} worker thread(s) ({}); painting synchronously", requested, e.getMessage());
            calls.contextBegin(context, image.pointer(), MemorySegment.NULL);
            return 0;
        }
    }

    /// Begins rendering into `image` in physical pixels, synchronously.
    public static BlendContext on(BlendImage image) {
        return on(image, 1.0);
    }

    /// Begins rendering into `image` with `scale` physical pixels per logical
    /// pixel, synchronously.
    ///
    /// @throws IllegalArgumentException if the scale is not a positive, finite
    ///         number — a zero or negative scale collapses or mirrors the frame,
    ///         and Blend2D would accept it
    public static BlendContext on(BlendImage image, double scale) {
        return on(image, scale, 0);
    }

    /// Begins rendering into `image` with `scale` physical pixels per logical
    /// pixel, using `threadCount` Blend2D workers.
    ///
    /// Zero renders synchronously on the calling thread. Anything higher renders
    /// asynchronously, and the pixels are complete only once [#close()] has
    /// returned.
    ///
    /// Nothing here decides *how many*. That is a policy question about the
    /// surface being painted and the machine painting it, and `:natives` is the
    /// mechanism — `:core` picks the number (ADR-0042).
    ///
    /// @throws IllegalArgumentException if the scale is not a positive, finite
    ///         number, or the thread count is negative
    public static BlendContext on(BlendImage image, double scale, int threadCount) {
        Objects.requireNonNull(image, "image");
        if (!Double.isFinite(scale) || scale <= 0) {
            throw new IllegalArgumentException(
                    "a display scale must be a positive, finite number of physical pixels per" + " logical pixel, and "
                            + scale + " is not");
        }
        if (threadCount < 0) {
            throw new IllegalArgumentException("a thread count must not be negative, and " + threadCount + " is."
                    + " Zero means synchronous rendering on the calling thread.");
        }
        return new BlendContext(image, scale, threadCount);
    }

    /// The scale this context was created with.
    public double scale() {
        return scale;
    }

    /// How many Blend2D workers this context is actually using.
    ///
    /// Zero for a synchronous context, and zero when threads were asked for and
    /// refused — so this is what a diagnostic should report, not the number that
    /// was requested.
    public int threadCount() {
        return threads;
    }

    /// Fills everything, blending over what is already there.
    ///
    /// @param argb a colour as `0xAARRGGBB`, not premultiplied
    public void fillAll(int argb) {
        requireUsable();
        calls.contextFillAll(context, argb);
    }

    /// Replaces everything, alpha included.
    ///
    /// The difference from [#fillAll] matters for a background: blending a
    /// translucent colour over the previous frame composites onto it, so the same
    /// call repeated every frame darkens until it is opaque. A background is a
    /// replacement, not a blend.
    ///
    /// @param argb a colour as `0xAARRGGBB`, not premultiplied
    public void clearTo(int argb) {
        requireUsable();
        calls.contextCompOp(context, BlendCompOp.SRC_COPY);
        try {
            calls.contextFillAll(context, argb);
        } finally {
            // Restored unconditionally: leaving SRC_COPY set would make every
            // subsequent fill in the frame punch a hole instead of blending.
            calls.contextCompOp(context, BlendCompOp.SRC_OVER);
        }
    }

    /// Fills a rectangle, in the context's coordinates — logical when the
    /// context was scaled.
    ///
    /// A rectangle that runs off the edge is clipped by Blend2D rather than
    /// refused: overflowing content is ordinary in a UI, and the rasterizer
    /// already has to decide what a partially covered pixel looks like.
    ///
    /// @param argb a colour as `0xAARRGGBB`, not premultiplied
    public void fillRect(double x, double y, double width, double height, int argb) {
        requireUsable();
        // NaN is checked on all four before the emptiness test, not inside it: a
        // NaN *position* with a perfectly good width and height passes every
        // size check there is, and Blend2D would rasterize it as nothing at all.
        // An arithmetic bug upstream would then look like a widget that simply
        // did not draw.
        if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(width) || Double.isNaN(height)) {
            throw new IllegalArgumentException("a rectangle with a NaN coordinate cannot be drawn, and Blend2D would"
                    + " silently draw nothing: " + x + "," + y + " " + width + "x" + height);
        }
        if (width <= 0 || height <= 0) {
            // Ordinary: a layout pass produces zero-sized boxes all the time.
            return;
        }
        rect.set(ValueLayout.JAVA_DOUBLE, RECT_X, x);
        rect.set(ValueLayout.JAVA_DOUBLE, RECT_Y, y);
        rect.set(ValueLayout.JAVA_DOUBLE, RECT_W, width);
        rect.set(ValueLayout.JAVA_DOUBLE, RECT_H, height);
        calls.contextFillRect(context, rect, argb);
    }

    /// Draws a run of glyphs with `(x, y)` on the **baseline**.
    ///
    /// The baseline, not the top of the text: `y` is the line the letters sit
    /// on, so an `a` is entirely above it and a `g` hangs below. Placing a
    /// paragraph means adding [BlendFontMetrics#ascent] to the top of the box
    /// and stepping by [BlendFontMetrics#lineHeight] from there.
    ///
    /// Coordinates are the context's own — logical when the context was scaled —
    /// and so is the font's size. What is *not* in those units is the glyph
    /// buffer: its offsets and advances are in font design units, and the font's
    /// matrix is what reconciles the two (ADR-0034).
    ///
    /// An empty buffer draws nothing rather than failing: a blank line is
    /// ordinary, and shaping empty text produces exactly this.
    ///
    /// @param argb a colour as `0xAARRGGBB`, not premultiplied
    public void fillGlyphRun(double x, double y, BlendFont font, BlendGlyphBuffer glyphs, int argb) {
        requireUsable();
        Objects.requireNonNull(font, "font");
        Objects.requireNonNull(glyphs, "glyphs");
        // NaN before the emptiness test, for the reason fillRect checks it
        // there: a NaN baseline draws nothing at all, and an arithmetic bug in a
        // layout pass would look like text that simply did not appear.
        if (Double.isNaN(x) || Double.isNaN(y)) {
            throw new IllegalArgumentException(
                    "a glyph run with a NaN origin cannot be drawn, and Blend2D would silently" + " draw nothing: " + x
                            + "," + y);
        }
        if (glyphs.isEmpty()) {
            return;
        }
        origin.set(ValueLayout.JAVA_DOUBLE, POINT_X, x);
        origin.set(ValueLayout.JAVA_DOUBLE, POINT_Y, y);
        calls.contextFillGlyphRun(context, origin, font.pointer(), glyphs.pointer(), argb);
    }

    /// Replaces the context's transform with `[a b c d e f]`, on top of the
    /// display scale this context was created at.
    ///
    /// Six doubles rather than a matrix type, because a matrix type belongs to
    /// the caller: `:core` has one, this module must not depend on `:core`, and
    /// inventing a second here would be a second place for the field order to be
    /// wrong. What crosses is the numbers.
    ///
    /// **Absolute, not relative.** There is no push and no pop *of the
    /// transform*: `bl_context_save` and `bl_context_restore` are bound —
    /// [#save()] and [#restore()] are right below — but they save the whole
    /// context state and are what an application's own painter runs inside
    /// (ADR-0193), not a transform stack. So every call here states the whole
    /// transform, and [#resetTransform()] is what a caller
    /// uses to get back to plain scaled user space. The scale is folded in here
    /// rather than left to the caller so that a transform set through this method
    /// is in the same logical coordinates as every other drawing call on the
    /// context (ADR-0068).
    public void transform(double a, double b, double c, double d, double e, double f) {
        requireUsable();
        if (!Double.isFinite(a)
                || !Double.isFinite(b)
                || !Double.isFinite(c)
                || !Double.isFinite(d)
                || !Double.isFinite(e)
                || !Double.isFinite(f)) {
            throw new IllegalArgumentException("a transform must be six finite numbers, not [" + a + " " + b + " " + c
                    + " " + d + " " + e + " " + f + "]");
        }
        // Scale first, then the caller's matrix: the caller works in logical
        // pixels, and the display scale is what turns those into device ones.
        // Written as a pre-multiply here rather than as a second call because
        // ASSIGN replaces, so two calls would leave whichever came last.
        matrix.set(ValueLayout.JAVA_DOUBLE, MATRIX_M00, a * scale);
        matrix.set(ValueLayout.JAVA_DOUBLE, MATRIX_M01, b * scale);
        matrix.set(ValueLayout.JAVA_DOUBLE, MATRIX_M10, c * scale);
        matrix.set(ValueLayout.JAVA_DOUBLE, MATRIX_M11, d * scale);
        matrix.set(ValueLayout.JAVA_DOUBLE, MATRIX_M20, e * scale);
        matrix.set(ValueLayout.JAVA_DOUBLE, MATRIX_M21, f * scale);
        calls.contextTransform(context, matrix);
    }

    /// Draws `layer` into the logical rectangle `(x, y, width, height)`.
    ///
    /// The whole image, composited with the current [#globalAlpha(double)] and
    /// the current transform. This is how a subtree rendered into its own image
    /// gets back onto the frame (ADR-0071).
    ///
    /// **The size is not optional and this is the reason there are two blits.**
    /// A layer's raster is allocated in *physical* pixels while this context is
    /// in *logical* ones, so [#blit] -- which draws one raster pixel per logical
    /// unit -- is right only where the two coincide. They coincide at 1x, which
    /// is why nothing noticed until a 2x display drew every faded subtree at
    /// twice its size (ADR-0157).
    ///
    /// @throws IllegalArgumentException if the origin is not drawable, or the
    ///         size is not positive
    public void blitScaled(double x, double y, double width, double height, BlendImage layer) {
        requireUsable();
        Objects.requireNonNull(layer, "layer");
        requireDrawableOrigin(x, y);
        if (!(width > 0) || !(height > 0)) {
            throw new IllegalArgumentException(
                    "a layer is blitted into a positive rectangle, and " + width + "x" + height + " is not one");
        }
        rect.set(ValueLayout.JAVA_DOUBLE, RECT_X, x);
        rect.set(ValueLayout.JAVA_DOUBLE, RECT_Y, y);
        rect.set(ValueLayout.JAVA_DOUBLE, RECT_W, width);
        rect.set(ValueLayout.JAVA_DOUBLE, RECT_H, height);
        calls.contextBlitScaledImage(context, rect, layer.pointer());
    }

    /// [#blitScaled] of a *part* of the image — the source rectangle in the
    /// image's own pixels.
    ///
    /// What a crop is: an application that carries a sub-rectangle of an image
    /// draws that rectangle, rather than drawing the whole thing and clipping it
    /// to what should show (ADR-0283). The two spaces in play are the image's
    /// pixels, which the source rectangle is in, and the context's logical units,
    /// which the destination is in; nothing here relates them, because the caller
    /// asking for a crop is the only one that knows whether it wants the part
    /// stretched or shown at size.
    ///
    /// @param sourceX left edge of the source rectangle, in image pixels
    /// @param sourceY top edge, in image pixels
    /// @param sourceWidth width of the source rectangle, in image pixels
    /// @param sourceHeight height, in image pixels
    /// @throws IllegalArgumentException if the origin is not drawable, or either
    ///         size is not positive
    public void blitScaled(
            double x,
            double y,
            double width,
            double height,
            BlendImage source,
            int sourceX,
            int sourceY,
            int sourceWidth,
            int sourceHeight) {
        requireUsable();
        Objects.requireNonNull(source, "source");
        requireDrawableOrigin(x, y);
        if (!(width > 0) || !(height > 0)) {
            throw new IllegalArgumentException(
                    "an image is blitted into a positive rectangle, and " + width + "x" + height + " is not one");
        }
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            throw new IllegalArgumentException(
                    "a source rectangle is positive, and " + sourceWidth + "x" + sourceHeight + " is not one");
        }
        // Not clamped to the image here. Blend2D intersects the source rectangle
        // with the image itself and draws the intersection, so an out-of-range
        // crop draws less rather than reporting an error -- and a wrapper that
        // rejected it would need the image's size, which is `BlendImage`'s to
        // state and not this context's to re-derive.
        rect.set(ValueLayout.JAVA_DOUBLE, RECT_X, x);
        rect.set(ValueLayout.JAVA_DOUBLE, RECT_Y, y);
        rect.set(ValueLayout.JAVA_DOUBLE, RECT_W, width);
        rect.set(ValueLayout.JAVA_DOUBLE, RECT_H, height);
        sourceRect.set(ValueLayout.JAVA_INT, RECT_I_X, sourceX);
        sourceRect.set(ValueLayout.JAVA_INT, RECT_I_Y, sourceY);
        sourceRect.set(ValueLayout.JAVA_INT, RECT_I_W, sourceWidth);
        sourceRect.set(ValueLayout.JAVA_INT, RECT_I_H, sourceHeight);
        calls.contextBlitScaledImage(context, rect, source.pointer(), sourceRect);
    }

    /// Draws `layer` with its top-left corner at logical `(x, y)`, one image
    /// pixel per logical unit.
    ///
    /// Correct only where the image was rasterized at the context's own scale.
    /// [#blitScaled] is what a [io.github.digitalsmile.goldberry.natives.blend2d.BlendImage]
    /// holding a *physical* raster wants.
    ///
    /// @throws IllegalArgumentException if the origin is not drawable
    public void blit(double x, double y, BlendImage layer) {
        requireUsable();
        Objects.requireNonNull(layer, "layer");
        requireDrawableOrigin(x, y);
        origin.set(ValueLayout.JAVA_DOUBLE, POINT_X, x);
        origin.set(ValueLayout.JAVA_DOUBLE, POINT_Y, y);
        calls.contextBlitImage(context, origin, layer.pointer());
    }

    /// Scales the alpha of everything drawn after this call.
    ///
    /// **This is what makes a layer a group.** A subtree is rasterized into its
    /// own image at full strength, and then the whole result is faded once by
    /// this — which is what CSS `opacity` means, and differs from fading each
    /// shape separately exactly where two of them overlap
    /// (ADR-0064
    /// stated that difference as an open question; ADR-0071 is the answer).
    ///
    /// Context state, not a per-call argument, because Blend2D's is — so a caller
    /// that sets it must set it back, and [BlendContext] does not do that for
    /// anyone.
    ///
    /// @param alpha 0 to 1
    public void globalAlpha(double alpha) {
        requireUsable();
        if (!(alpha >= 0) || !(alpha <= 1)) {
            throw new IllegalArgumentException("a global alpha is between 0 and 1, and " + alpha + " is not");
        }
        calls.contextGlobalAlpha(context, alpha);
    }

    /// Restricts drawing to `(x, y, width, height)` in logical coordinates.
    ///
    /// Intersected with whatever clip is in force, which is Blend2D's behaviour
    /// and is why [#resetClip()] exists rather than a second `clipTo` undoing the
    /// first.
    public void clipTo(double x, double y, double width, double height) {
        requireUsable();
        if (!Double.isFinite(x) || !Double.isFinite(y) || !(width > 0) || !(height > 0)) {
            throw new IllegalArgumentException("a clip needs a finite origin and a positive size, and " + width + "x"
                    + height + "+" + x + "+" + y + " is not");
        }
        rect.set(ValueLayout.JAVA_DOUBLE, RECT_X, x);
        rect.set(ValueLayout.JAVA_DOUBLE, RECT_Y, y);
        rect.set(ValueLayout.JAVA_DOUBLE, RECT_W, width);
        rect.set(ValueLayout.JAVA_DOUBLE, RECT_H, height);
        calls.contextClipToRect(context, rect);
    }

    /// Back to the whole surface.
    public void resetClip() {
        requireUsable();
        calls.contextRestoreClipping(context);
    }

    /// Pushes the whole context state — clip, transform, style, alpha — so that
    /// what a caller does next can be undone exactly.
    ///
    /// What `canvas` needs and the frame path does not: an application's painter
    /// runs inside whatever clip and transform the tree already has, and
    /// [#resetClip()] goes back to the *whole surface* rather than to the region
    /// in force before it (ADR-0193). Must be paired with [#restore()].
    public void save() {
        requireUsable();
        calls.contextSave(context);
    }

    /// Pops what [#save()] pushed.
    public void restore() {
        requireUsable();
        calls.contextRestore(context);
    }

    /// Back to plain scaled user space — what every box that has no transform of
    /// its own is drawn in.
    public void resetTransform() {
        requireUsable();
        transform(1, 0, 0, 1, 0, 0);
    }

    /// Fills `path`, offset so its own origin lands at `(x, y)`.
    ///
    /// The offset is Blend2D's, not a transform: the context's user space is
    /// untouched, so one path built once can be drawn at many places in a frame
    /// without a save/restore around each (ADR-0043).
    ///
    /// @param argb a colour as `0xAARRGGBB`, not premultiplied
    public void fillPath(double x, double y, BlendPath path, int argb) {
        requireUsable();
        Objects.requireNonNull(path, "path");
        requireDrawableOrigin(x, y);
        origin.set(ValueLayout.JAVA_DOUBLE, POINT_X, x);
        origin.set(ValueLayout.JAVA_DOUBLE, POINT_Y, y);
        calls.contextFillPath(context, origin, path.pointer(), argb);
    }

    /// Fills `path` with `gradient`, offset so its own origin lands at `(x, y)`.
    ///
    /// **The gradient is not moved by the origin.** The path is translated and
    /// the ramp is not, because the two answer different questions: a path is a
    /// shape drawn somewhere, and a gradient is a statement about a region of
    /// the surface — one placed from the top of a plot to its baseline is the
    /// same ramp for every band drawn through it, which is what lets a chart
    /// build one and fill several
    /// (ADR-0207).
    ///
    /// The fill style is **put back to opaque black** afterwards. Blend2D's fill
    /// style is context state and every other call on this class states its own
    /// colour, so a gradient left set would be drawn by whatever reached for
    /// [Blend2dContext#contextFillPathStyled] next — and the bug would be
    /// somewhere else in the frame entirely. That is the same rule
    /// [#globalAlpha] states and the opposite of what it does, because a global
    /// alpha has a neutral value and a fill style does not: nothing here can ask
    /// what the style *was*, so restoring it means choosing one.
    ///
    /// @throws IllegalArgumentException if the origin is not drawable
    public void fillPath(double x, double y, BlendPath path, BlendGradient gradient) {
        requireUsable();
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(gradient, "gradient");
        requireDrawableOrigin(x, y);
        origin.set(ValueLayout.JAVA_DOUBLE, POINT_X, x);
        origin.set(ValueLayout.JAVA_DOUBLE, POINT_Y, y);
        calls.contextSetFillStyle(context, gradient.pointer());
        try {
            calls.contextFillPathStyled(context, origin, path.pointer());
        } finally {
            // Unconditionally, including when the fill failed: the context is
            // still usable and the next thing painted did not ask for a ramp.
            calls.contextSetFillStyle(context, 0xFF000000);
        }
    }

    /// Strokes `path` at the current [#strokeWidth], cap and join, offset so its
    /// own origin lands at `(x, y)`.
    ///
    /// @param argb a colour as `0xAARRGGBB`, not premultiplied
    public void strokePath(double x, double y, BlendPath path, int argb) {
        requireUsable();
        Objects.requireNonNull(path, "path");
        requireDrawableOrigin(x, y);
        origin.set(ValueLayout.JAVA_DOUBLE, POINT_X, x);
        origin.set(ValueLayout.JAVA_DOUBLE, POINT_Y, y);
        calls.contextStrokePath(context, origin, path.pointer(), argb);
    }

    /// Sets the stroke width, in the context's own units — logical pixels when
    /// the context was scaled.
    ///
    /// @throws IllegalArgumentException if the width is not a positive, finite
    ///         number. Blend2D accepts a zero width and draws nothing, which is
    ///         indistinguishable from an icon that failed to parse.
    public void strokeWidth(double width) {
        requireUsable();
        if (!Double.isFinite(width) || width <= 0) {
            throw new IllegalArgumentException(
                    "a stroke width must be a positive, finite number, and " + width + " is not");
        }
        calls.contextSetStrokeWidth(context, width);
    }

    /// Sets what both ends of an open sub-path look like.
    public void strokeCaps(BlendStrokeCap cap) {
        requireUsable();
        Objects.requireNonNull(cap, "cap");
        calls.contextSetStrokeCaps(context, cap);
    }

    /// Sets what a corner between two segments looks like.
    public void strokeJoin(BlendStrokeJoin join) {
        requireUsable();
        Objects.requireNonNull(join, "join");
        calls.contextSetStrokeJoin(context, join);
    }

    /// Sets how far a [BlendStrokeJoin#MITER_CLIP] corner may run out before it
    /// is cut off, as a multiple of the stroke width.
    ///
    /// Blend2D's default is 4, which is SVG's and CSS's, so this is only ever
    /// called to depart from it (ADR-0278).
    ///
    /// @throws IllegalArgumentException if the limit is not a finite number of at
    ///         least 1. Below 1 a miter is shorter than the bevel it falls back
    ///         to, which is not a corner any renderer draws.
    public void strokeMiterLimit(double miterLimit) {
        requireUsable();
        if (!Double.isFinite(miterLimit) || miterLimit < 1) {
            throw new IllegalArgumentException(
                    "a miter limit must be a finite number of at least 1, and " + miterLimit + " is not");
        }
        calls.contextSetStrokeMiterLimit(context, miterLimit);
    }

    /// Whether the context has been closed.
    public boolean isClosed() {
        return ended;
    }

    /// NaN checked for the reason [#fillRect] checks it: Blend2D draws nothing
    /// and returns success, so an arithmetic slip upstream looks like an icon
    /// that simply did not appear.
    private static void requireDrawableOrigin(double x, double y) {
        if (Double.isNaN(x) || Double.isNaN(y)) {
            throw new IllegalArgumentException(
                    "a path with a NaN origin cannot be drawn, and Blend2D would silently draw" + " nothing: " + x + ","
                            + y);
        }
    }

    /// Finishes the frame and detaches from the image.
    ///
    /// Until this returns the pixels are not guaranteed to be complete — a
    /// context may have work queued — so presenting before closing shows a
    /// half-drawn frame. With workers attached that is not a caution but the
    /// mechanism: `bl_context_end` is where the calling thread waits for the
    /// bands. Closing twice does nothing.
    @Override
    public void close() {
        if (ended) {
            return;
        }
        requireOwner();
        ended = true;
        try {
            calls.contextEnd(context);
            calls.contextDestroy(context);
        } finally {
            arena.close();
        }
    }

    /// The image this context renders into.
    public BlendImage image() {
        return image;
    }

    /// Detach and release without reporting a further failure. Used when the
    /// constructor fails after `begin` succeeded: the original exception is the
    /// one worth having.
    private void closeQuietly() {
        ended = true;
        try {
            calls.contextEnd(context);
            calls.contextDestroy(context);
        } catch (RuntimeException | Error ignored) {
            // Already failing; a second failure here would replace the cause.
        } finally {
            arena.close();
        }
    }

    private void requireUsable() {
        requireOwner();
        if (ended) {
            throw new IllegalStateException("this BlendContext has been closed");
        }
    }

    private void requireOwner() {
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException("a BlendContext belongs to the thread that created it, and this is not it");
        }
    }

    @Override
    public String toString() {
        return "BlendContext[" + image + " @" + scale + "x"
                + (threads == 0 ? ", sync" : ", " + threads + " threads")
                + (ended ? ", closed" : "") + "]";
    }
}
