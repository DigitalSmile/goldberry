package io.github.digitalsmile.goldberry.natives.blend2d;

import io.github.digitalsmile.goldberry.natives.layout.Layouts;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Objects;

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
/// Confined to the thread that created it, and must be closed.
public final class BlendContext implements AutoCloseable {

    private final Blend2D blend2d = Blend2D.get();
    private final Arena arena;
    private final MemorySegment context;
    private final MemorySegment rect;
    private final MemorySegment origin;
    private final Thread owner = Thread.currentThread();
    private final BlendImage image;
    private final double scale;

    private boolean ended;

    private static final long RECT_X = Layouts.BL_RECT.offsetOf("x");
    private static final long RECT_Y = Layouts.BL_RECT.offsetOf("y");
    private static final long RECT_W = Layouts.BL_RECT.offsetOf("w");
    private static final long RECT_H = Layouts.BL_RECT.offsetOf("h");

    private static final long POINT_X = Layouts.BL_POINT.offsetOf("x");
    private static final long POINT_Y = Layouts.BL_POINT.offsetOf("y");

    private BlendContext(BlendImage image, double scale) {
        this.image = image;
        this.scale = scale;
        this.arena = Arena.ofConfined();
        try {
            this.context = arena.allocate(Layouts.BL_OBJECT_DETAIL.layout());
            // One BLRect, reused for every fillRect. A frame issues thousands of
            // these and each one is four doubles that Blend2D reads and does not
            // keep -- allocating per call would put a confined arena on the hot
            // path to hold sixteen bytes for the duration of one call.
            this.rect = arena.allocate(Layouts.BL_RECT.layout());
            // One BLPoint, reused for every glyph run, for the same reason.
            this.origin = arena.allocate(Layouts.BL_POINT.layout());

            // NULL create-info asks for the defaults: synchronous, on this
            // thread. Blend2D's banded multithreading is a thread_count away and
            // deliberately not taken until there is a frame worth measuring.
            blend2d.contextBegin(context, image.pointer(), MemorySegment.NULL);
        } catch (RuntimeException | Error e) {
            arena.close();
            throw e;
        }

        if (scale != 1.0) {
            try {
                blend2d.contextTransform(context, BlendTransformOp.SCALE, scale, scale);
            } catch (RuntimeException | Error e) {
                // The context is attached but unusable. Detach before rethrowing
                // or the image is left with a reference nothing will release.
                closeQuietly();
                throw e;
            }
        }
    }

    /// Begins rendering into `image` in physical pixels.
    public static BlendContext on(BlendImage image) {
        return on(image, 1.0);
    }

    /// Begins rendering into `image` with `scale` physical pixels per logical
    /// pixel.
    ///
    /// @throws IllegalArgumentException if the scale is not a positive, finite
    ///         number — a zero or negative scale collapses or mirrors the frame,
    ///         and Blend2D would accept it
    public static BlendContext on(BlendImage image, double scale) {
        Objects.requireNonNull(image, "image");
        if (!Double.isFinite(scale) || scale <= 0) {
            throw new IllegalArgumentException(
                    "a display scale must be a positive, finite number of physical pixels per"
                            + " logical pixel, and " + scale + " is not");
        }
        return new BlendContext(image, scale);
    }

    /// The scale this context was created with.
    public double scale() {
        return scale;
    }

    /// Fills everything, blending over what is already there.
    ///
    /// @param argb a colour as `0xAARRGGBB`, not premultiplied
    public void fillAll(int argb) {
        requireUsable();
        blend2d.contextFillAll(context, argb);
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
        blend2d.contextCompOp(context, BlendCompOp.SRC_COPY);
        try {
            blend2d.contextFillAll(context, argb);
        } finally {
            // Restored unconditionally: leaving SRC_COPY set would make every
            // subsequent fill in the frame punch a hole instead of blending.
            blend2d.contextCompOp(context, BlendCompOp.SRC_OVER);
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
            throw new IllegalArgumentException(
                    "a rectangle with a NaN coordinate cannot be drawn, and Blend2D would"
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
        blend2d.contextFillRect(context, rect, argb);
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
    public void fillGlyphRun(
            double x, double y, BlendFont font, BlendGlyphBuffer glyphs, int argb) {
        requireUsable();
        Objects.requireNonNull(font, "font");
        Objects.requireNonNull(glyphs, "glyphs");
        // NaN before the emptiness test, for the reason fillRect checks it
        // there: a NaN baseline draws nothing at all, and an arithmetic bug in a
        // layout pass would look like text that simply did not appear.
        if (Double.isNaN(x) || Double.isNaN(y)) {
            throw new IllegalArgumentException(
                    "a glyph run with a NaN origin cannot be drawn, and Blend2D would silently"
                            + " draw nothing: " + x + "," + y);
        }
        if (glyphs.isEmpty()) {
            return;
        }
        origin.set(ValueLayout.JAVA_DOUBLE, POINT_X, x);
        origin.set(ValueLayout.JAVA_DOUBLE, POINT_Y, y);
        blend2d.contextFillGlyphRun(context, origin, font.pointer(), glyphs.pointer(), argb);
    }

    /// Whether the context has been closed.
    public boolean isClosed() {
        return ended;
    }

    /// Finishes the frame and detaches from the image.
    ///
    /// Until this returns the pixels are not guaranteed to be complete — a
    /// context may have work queued — so presenting before closing shows a
    /// half-drawn frame. Closing twice does nothing.
    @Override
    public void close() {
        if (ended) {
            return;
        }
        requireOwner();
        ended = true;
        try {
            blend2d.contextEnd(context);
            blend2d.contextDestroy(context);
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
            blend2d.contextEnd(context);
            blend2d.contextDestroy(context);
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
            throw new IllegalStateException(
                    "a BlendContext belongs to the thread that created it, and this is not it");
        }
    }

    @Override
    public String toString() {
        return "BlendContext[" + image + " @" + scale + "x" + (ended ? ", closed" : "") + "]";
    }
}
