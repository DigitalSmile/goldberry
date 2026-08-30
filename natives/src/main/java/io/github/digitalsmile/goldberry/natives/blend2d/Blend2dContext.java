package io.github.digitalsmile.goldberry.natives.blend2d;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;

import io.github.digitalsmile.goldberry.natives.NativeLibrary;
import io.github.digitalsmile.goldberry.natives.blend2d.calls.ContextCalls;
import io.github.digitalsmile.goldberry.natives.blend2d.enums.BlendCompOp;
import io.github.digitalsmile.goldberry.natives.blend2d.enums.BlendStrokeCap;
import io.github.digitalsmile.goldberry.natives.blend2d.enums.BlendStrokeJoin;
import io.github.digitalsmile.goldberry.natives.blend2d.enums.BlendTransformOp;
import io.github.digitalsmile.goldberry.natives.blend2d.error.BlendException;

/// Blend2D's context calls, behind [BlendContext] — everything that draws.
///
/// Package-private for [BlendImage]'s reason: the wrapper owns the handle
/// and knows whether the context has been ended.
final class Blend2dContext {

    private static final class Holder {
        private static final Blend2dContext INSTANCE =
                new Blend2dContext(NativeLibrary.get().lookup());
    }

    private final ContextCalls calls;

    private Blend2dContext(SymbolLookup lookup) {
        this.calls = ContextCalls.bind(lookup);
    }

    static Blend2dContext get() {
        return Holder.INSTANCE;
    }

    /// Begins rendering into `image`.
    ///
    /// `createInfo` may be [MemorySegment#NULL], which asks for the defaults: a
    /// synchronous context on the calling thread. Blend2D's banded
    /// multithreading is a `thread_count` away and deliberately not taken yet —
    /// see ADR-0031.
    void contextBegin(MemorySegment context, MemorySegment image, MemorySegment createInfo) {
        check("bl_context_init_as", calls.contextInitAs().call(context, image, createInfo));
    }

    void contextEnd(MemorySegment context) {
        check("bl_context_end", calls.contextEnd().call(context));
    }

    void contextDestroy(MemorySegment context) {
        check("bl_context_destroy", calls.contextDestroy().call(context));
    }

    void contextFlush(MemorySegment context, int flags) {
        check("bl_context_flush", calls.contextFlush().call(context, flags));
    }

    /// Applies a transform whose operand is a pair of doubles —
    /// [BlendTransformOp#SCALE] or [BlendTransformOp#TRANSLATE].
    ///
    /// The operand crosses as `const void*`, so nothing on either side checks
    /// that the shape matches the operation. Restricting this method to the two
    /// ops that read a `BLPoint` is what keeps that unchecked cast honest;
    /// [BlendTransformOp#RESET] takes no operand and would read two doubles that
    /// were never written.
    void contextTransform(MemorySegment context, BlendTransformOp op, double x, double y) {
        if (op != BlendTransformOp.SCALE && op != BlendTransformOp.TRANSLATE) {
            throw new IllegalArgumentException(op + " does not take a pair of doubles, and its operand crosses as void*"
                    + " — nothing downstream would catch the mismatch");
        }
        try (var arena = Arena.ofConfined()) {
            var point = arena.allocate(ValueLayout.JAVA_DOUBLE, 2);
            point.setAtIndex(ValueLayout.JAVA_DOUBLE, 0, x);
            point.setAtIndex(ValueLayout.JAVA_DOUBLE, 1, y);
            check(
                    "bl_context_apply_transform_op",
                    calls.contextApplyTransformOp().call(context, op.nativeValue(), point));
        }
    }

    /// Replaces the context's transform with the matrix in `matrix`.
    ///
    /// `matrix` must be a [io.github.digitalsmile.goldberry.natives.layout.Layouts#BL_MATRIX2D]
    /// the caller owns and keeps alive for the call. Passed in rather than
    /// allocated here because this runs once per transformed box per frame, and a
    /// confined arena per call to hold forty-eight bytes Blend2D reads and does
    /// not keep is the same trade [BlendContext] already made for `BLRect`.
    void contextTransform(MemorySegment context, MemorySegment matrix) {
        check(
                "bl_context_apply_transform_op",
                calls.contextApplyTransformOp().call(context, BlendTransformOp.ASSIGN.nativeValue(), matrix));
    }

    void contextCompOp(MemorySegment context, BlendCompOp compOp) {
        check("bl_context_set_comp_op", calls.contextSetCompOp().call(context, compOp.nativeValue()));
    }

    void contextClearAll(MemorySegment context) {
        check("bl_context_clear_all", calls.contextClearAll().call(context));
    }

    /// Fills the whole clip box with a straight-alpha `0xAARRGGBB`.
    ///
    /// Blend2D premultiplies the style itself when it composites, so the colour
    /// crossing here is **not** premultiplied even though the target image is.
    /// Premultiplying it first would darken every translucent fill twice.
    void contextFillAll(MemorySegment context, int argb) {
        check("bl_context_fill_all_rgba32", calls.contextFillAllRgba32().call(context, argb));
    }

    /// Fills a rectangle in the context's current user space.
    void contextFillRect(MemorySegment context, MemorySegment rect, int argb) {
        check("bl_context_fill_rect_d_rgba32", calls.contextFillRectDRgba32().call(context, rect, argb));
    }

    /// Fills a run of positioned glyphs, with `origin` on the baseline.
    ///
    /// `glyphRun` is a descriptor pointing at arrays the caller still owns, so
    /// those arrays must outlive the call — which they do, because
    /// [BlendGlyphBuffer] holds all three in one arena.
    /// `BLResult bl_context_fill_glyph_run_d_rgba32(BLContextCore*,`
    /// `const BLPoint* origin, const BLFontCore*, const BLGlyphRun*, uint32_t)`
    ///
    /// The `_d` suffix is the origin's type: doubles, so a baseline can land
    /// between physical pixels. The `_i` variant takes a `BLPointI` and is not
    /// bound, because rounding the baseline is exactly what ADR-0031 went to some
    /// trouble to stop doing for rectangles.
    void contextFillGlyphRun(
            MemorySegment context, MemorySegment origin, MemorySegment font, MemorySegment glyphRun, int argb) {
        check(
                "bl_context_fill_glyph_run_d_rgba32",
                calls.contextFillGlyphRunDRgba32().call(context, origin, font, glyphRun, argb));
    }

    /// Stroke state (ADR-0043). The width is in the context's own units, so a
    /// scaled context strokes in logical pixels like everything else.
    void contextSetStrokeWidth(MemorySegment context, double width) {
        int result;
        result = calls.contextSetStrokeWidth().call(context, width);
        check("bl_context_set_stroke_width", result);
    }

    /// `_caps`, plural: it sets both ends at once. The singular
    /// `bl_context_set_stroke_cap` takes a `BLStrokeCapPosition` as well, and
    /// nothing wants a path capped differently at each end.
    void contextSetStrokeCaps(MemorySegment context, BlendStrokeCap cap) {
        int result;
        result = calls.contextSetStrokeCaps().call(context, cap.nativeValue());
        check("bl_context_set_stroke_caps", result);
    }

    void contextSetStrokeJoin(MemorySegment context, BlendStrokeJoin join) {
        int result;
        result = calls.contextSetStrokeJoin().call(context, join.nativeValue());
        check("bl_context_set_stroke_join", result);
    }

    /// `BLResult bl_context_{fill,stroke}_path_d_rgba32(BLContextCore*,`
    /// `const BLPoint* origin, const BLPathCore*, uint32_t)`
    ///
    /// The origin translates the path without transforming the context, which is
    /// what lets one 24×24 icon path be drawn at several places in a frame
    /// without being rebuilt or the context's transform being saved.
    void contextFillPath(MemorySegment context, MemorySegment origin, MemorySegment path, int argb) {
        int result;
        result = calls.contextFillPathDRgba32().call(context, origin, path, argb);
        check("bl_context_fill_path_d_rgba32", result);
    }

    /// Fills a path with whatever style is set, rather than with a colour of its
    /// own.
    ///
    /// `BLResult bl_context_fill_path_d(BLContextCore*, const BLPoint* origin,`
    /// `const BLPathCore*)`
    ///
    /// The only styleless drawing call bound, and the only way a gradient
    /// reaches a path (ADR-0207). Its caller is responsible for what the style
    /// is when it runs and for what it is afterwards.
    void contextFillPathStyled(MemorySegment context, MemorySegment origin, MemorySegment path) {
        int result;
        result = calls.contextFillPathD().call(context, origin, path);
        check("bl_context_fill_path_d", result);
    }

    /// Sets an object -- a `BLGradientCore` -- as the fill style.
    ///
    /// Blend2D reads the object's type tag out of its own first bytes, so this
    /// one call takes any style object. It **retains** what it is given, which
    /// is what lets [BlendGradient#close()] be safe the moment the fill has been
    /// issued.
    void contextSetFillStyle(MemorySegment context, MemorySegment style) {
        int result;
        result = calls.contextSetFillStyle().call(context, style);
        check("bl_context_set_fill_style", result);
    }

    /// Puts a plain colour back as the fill style, releasing whatever object was
    /// there.
    void contextSetFillStyle(MemorySegment context, int argb) {
        int result;
        result = calls.contextSetFillStyleRgba32().call(context, argb);
        check("bl_context_set_fill_style_rgba32", result);
    }

    void contextStrokePath(MemorySegment context, MemorySegment origin, MemorySegment path, int argb) {
        int result;
        result = calls.contextStrokePathDRgba32().call(context, origin, path, argb);
        check("bl_context_stroke_path_d_rgba32", result);
    }

    /// Draws `image` with its top-left corner at the `BLPoint` in `origin`.
    ///
    /// The whole image: `img_area` crosses as NULL, which Blend2D reads as the
    /// full source rectangle.
    /// Compositing a layer back onto its parent (ADR-0071). The last argument is
    /// a `const BLRectI*` naming a sub-rectangle of the source, and it is always
    /// NULL here — Blend2D reads that as the whole image, which is what a layer
    /// always wants — so no `BLRectI` ever crosses.
    void contextBlitImage(MemorySegment context, MemorySegment origin, MemorySegment image) {
        int result;
        result = calls.contextBlitImageD().call(context, origin, image, MemorySegment.NULL);
        check("bl_context_blit_image_d", result);
    }

    /// The same, into `rect` -- a `BLRect` of four doubles in the context's own
    /// units, so the image is drawn to that size rather than one pixel per unit.
    /// The same, into a destination `BLRect` rather than at a point — which is
    /// what reconciles a raster measured in physical pixels with a context
    /// measured in logical ones (ADR-0157). Same NULL `img_area`.
    void contextBlitScaledImage(MemorySegment context, MemorySegment rect, MemorySegment image) {
        int result;
        result = calls.contextBlitScaledImageD().call(context, rect, image, MemorySegment.NULL);
        check("bl_context_blit_scaled_image_d", result);
    }

    /// Scales the alpha of everything drawn after it, including a blitted image.
    ///
    /// This is what makes a layer a *group*: the subtree is rasterized at full
    /// strength into its own image and the whole result is faded once, rather
    /// than each shape in it being faded separately.
    void contextGlobalAlpha(MemorySegment context, double alpha) {
        int result;
        result = calls.contextSetGlobalAlpha().call(context, alpha);
        check("bl_context_set_global_alpha", result);
    }

    /// Restricts drawing to the `BLRect` in `rect`, intersected with whatever
    /// clip is already in force.
    /// Restricting a frame to the region that changed (ADR-0072). The rect is a
    /// `BLRect` — four doubles, in the context's own units, so a clip is stated in
    /// logical coordinates like every other call on the context.
    void contextClipToRect(MemorySegment context, MemorySegment rect) {
        int result;
        result = calls.contextClipToRectD().call(context, rect);
        check("bl_context_clip_to_rect_d", result);
    }

    /// Back to the whole image.
    ///
    /// **Not to the previous clip** — this is why [#contextSave] exists. For the
    /// frame path one depth is all there is and this is the cheaper call; for a
    /// `canvas`, whose painter runs inside whatever the tree already set up,
    /// going back to the whole image would paint over a scroll viewport's edge
    /// (ADR-0193).
    void contextRestoreClipping(MemorySegment context) {
        int result;
        result = calls.contextRestoreClipping().call(context);
        check("bl_context_restore_clipping", result);
    }

    /// Pushes clip, transform, style and alpha, so that whatever a caller does
    /// next can be undone exactly.
    ///
    /// The cookie is NULL: it is Blend2D's guard against a mismatched pair, and
    /// the only pairs here are the two lines of [#contextRestore]'s one caller.
    void contextSave(MemorySegment context) {
        int result;
        result = calls.contextSave().call(context, MemorySegment.NULL);
        check("bl_context_save", result);
    }

    /// Pops what [#contextSave] pushed.
    void contextRestore(MemorySegment context) {
        int result;
        result = calls.contextRestore().call(context, MemorySegment.NULL);
        check("bl_context_restore", result);
    }

    /// A `BLResult` that is not `BL_SUCCESS` is the call reporting a problem, not
    /// the crossing failing -- so it is raised as a [BlendException] naming the
    /// operation, and not as the [IllegalStateException] a holder raises.
    private static void check(String operation, int result) {
        if (result != 0) {
            throw new BlendException(operation, result);
        }
    }
}
