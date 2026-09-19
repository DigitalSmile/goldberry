package io.github.digitalsmile.goldberry.natives.blend2d.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import io.github.digitalsmile.goldberry.natives.Downcalls;

/// Blend2D’s `BLContext` — everything that draws.
///
/// One holder per function: its handle, its address, and a `call` whose
/// parameters are the C prototype’s. See [Downcalls] for why the handle is a
/// `static final` constant and why these live in a package of their own.
public record ContextCalls(
        ContextInitAs contextInitAs,
        ContextEnd contextEnd,
        ContextDestroy contextDestroy,
        ContextApplyTransformOp contextApplyTransformOp,
        ContextSetCompOp contextSetCompOp,
        ContextSetFillRule contextSetFillRule,
        ContextFillAllRgba32 contextFillAllRgba32,
        ContextFillRectDRgba32 contextFillRectDRgba32,
        ContextFillGlyphRunDRgba32 contextFillGlyphRunDRgba32,
        ContextSetStrokeWidth contextSetStrokeWidth,
        ContextSetStrokeCaps contextSetStrokeCaps,
        ContextSetStrokeJoin contextSetStrokeJoin,
        ContextSetStrokeMiterLimit contextSetStrokeMiterLimit,
        ContextFillPathDRgba32 contextFillPathDRgba32,
        ContextFillPathD contextFillPathD,
        ContextSetFillStyle contextSetFillStyle,
        ContextSetFillStyleRgba32 contextSetFillStyleRgba32,
        ContextStrokePathDRgba32 contextStrokePathDRgba32,
        ContextBlitImageD contextBlitImageD,
        ContextBlitScaledImageD contextBlitScaledImageD,
        ContextSetGlobalAlpha contextSetGlobalAlpha,
        ContextClipToRectD contextClipToRectD,
        ContextRestoreClipping contextRestoreClipping,
        ContextSave contextSave,
        ContextRestore contextRestore) {

    /// Binds every function above, failing if the library exports none of them.
    ///
    /// @param lookup the loaded `libgoldberry`
    public static ContextCalls bind(SymbolLookup lookup) {
        return new ContextCalls(
                new ContextInitAs(lookup),
                new ContextEnd(lookup),
                new ContextDestroy(lookup),
                new ContextApplyTransformOp(lookup),
                new ContextSetCompOp(lookup),
                new ContextSetFillRule(lookup),
                new ContextFillAllRgba32(lookup),
                new ContextFillRectDRgba32(lookup),
                new ContextFillGlyphRunDRgba32(lookup),
                new ContextSetStrokeWidth(lookup),
                new ContextSetStrokeCaps(lookup),
                new ContextSetStrokeJoin(lookup),
                new ContextSetStrokeMiterLimit(lookup),
                new ContextFillPathDRgba32(lookup),
                new ContextFillPathD(lookup),
                new ContextSetFillStyle(lookup),
                new ContextSetFillStyleRgba32(lookup),
                new ContextStrokePathDRgba32(lookup),
                new ContextBlitImageD(lookup),
                new ContextBlitScaledImageD(lookup),
                new ContextSetGlobalAlpha(lookup),
                new ContextClipToRectD(lookup),
                new ContextRestoreClipping(lookup),
                new ContextSave(lookup),
                new ContextRestore(lookup));
    }

    /// Begins rendering into `image`.
    ///
    /// `int bl_context_init_as(void*, void*, void*)`
    public static final class ContextInitAs {

        private static final MethodHandle FD_bl_context_init_as =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));

        private final MemorySegment address;

        ContextInitAs(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_context_init_as");
        }

        /// Calls `bl_context_init_as`.
        ///
        /// @param context an uninitialised `BLContextCore` to take over
        /// @param image the image to draw into
        /// @param createInfo a `BLContextCreateInfo`, which is where the worker count is asked for
        public int call(MemorySegment context, MemorySegment image, MemorySegment createInfo) {
            try {
                return (int) FD_bl_context_init_as.invokeExact(address, context, image, createInfo);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_context_init_as", t);
            }
        }
    }

    /// Flushes whatever is still in flight and detaches the context from its image.
    ///
    /// Pixels a context has not finished with are not pixels worth showing, which
    /// is why this happens before presenting and not after.
    ///
    /// `int bl_context_end(void*)`
    public static final class ContextEnd {

        private static final MethodHandle FD_bl_context_end = Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        ContextEnd(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_context_end");
        }

        /// Calls `bl_context_end`.
        ///
        /// @param context the context to finish
        public int call(MemorySegment context) {
            try {
                return (int) FD_bl_context_end.invokeExact(address, context);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_context_end", t);
            }
        }
    }

    /// Releases the context. Separate from [ContextCalls.ContextEnd]: ending stops the
    /// drawing, destroying gives back the memory.
    ///
    /// `int bl_context_destroy(void*)`
    public static final class ContextDestroy {

        private static final MethodHandle FD_bl_context_destroy =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        ContextDestroy(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_context_destroy");
        }

        /// Calls `bl_context_destroy`.
        ///
        /// @param context the context to release
        public int call(MemorySegment context) {
            try {
                return (int) FD_bl_context_destroy.invokeExact(address, context);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_context_destroy", t);
            }
        }
    }

    /// Applies one transform operation to the context’s matrix.
    ///
    /// One entry point for every kind of transform, because that is the shape
    /// Blend2D exports: the op says how many doubles `data` holds.
    ///
    /// `int bl_context_apply_transform_op(void*, int, void*)`
    public static final class ContextApplyTransformOp {

        private static final MethodHandle FD_bl_context_apply_transform_op =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS));

        private final MemorySegment address;

        ContextApplyTransformOp(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_context_apply_transform_op");
        }

        /// Calls `bl_context_apply_transform_op`.
        ///
        /// @param context the context to transform
        /// @param op a `BLTransformOp` — which of translate, scale, rotate or a full matrix
        /// @param data the operands that op expects, as doubles
        public int call(MemorySegment context, int op, MemorySegment data) {
            try {
                return (int) FD_bl_context_apply_transform_op.invokeExact(address, context, op, data);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_context_apply_transform_op", t);
            }
        }
    }

    /// Sets the compositing operator subsequent drawing blends with.
    ///
    /// `int bl_context_set_comp_op(void*, int)`
    public static final class ContextSetCompOp {

        private static final MethodHandle FD_bl_context_set_comp_op =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        ContextSetCompOp(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_context_set_comp_op");
        }

        /// Calls `bl_context_set_comp_op`.
        ///
        /// @param context the context to set it on
        /// @param compOp a `BLCompOp`
        public int call(MemorySegment context, int compOp) {
            try {
                return (int) FD_bl_context_set_comp_op.invokeExact(address, context, compOp);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_context_set_comp_op", t);
            }
        }
    }

    /// Sets which points a filled path encloses.
    ///
    /// Context state, and the only piece of it that can change what a *path*
    /// means rather than what colour it comes out — a shape with a sub-path
    /// inside another is one ring under even-odd and a disc under non-zero
    /// (ADR-0427).
    ///
    /// `int bl_context_set_fill_rule(void*, int)`
    public static final class ContextSetFillRule {

        private static final MethodHandle FD_bl_context_set_fill_rule =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        ContextSetFillRule(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_context_set_fill_rule");
        }

        /// Calls `bl_context_set_fill_rule`.
        ///
        /// @param context the context to set it on
        /// @param fillRule a `BLFillRule`
        public int call(MemorySegment context, int fillRule) {
            try {
                return (int) FD_bl_context_set_fill_rule.invokeExact(address, context, fillRule);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_context_set_fill_rule", t);
            }
        }
    }

    /// Fills the whole clip region, blending over what is there.
    ///
    /// `int bl_context_fill_all_rgba32(void*, int)`
    public static final class ContextFillAllRgba32 {

        private static final MethodHandle FD_bl_context_fill_all_rgba32 =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        ContextFillAllRgba32(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_context_fill_all_rgba32");
        }

        /// Calls `bl_context_fill_all_rgba32`.
        ///
        /// @param context the context to fill
        /// @param argb a colour as `0xAARRGGBB`, straight alpha
        public int call(MemorySegment context, int argb) {
            try {
                return (int) FD_bl_context_fill_all_rgba32.invokeExact(address, context, argb);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_context_fill_all_rgba32", t);
            }
        }
    }

    /// Fills a rectangle.
    ///
    /// The `_d` suffix is the rectangle’s type: doubles, so a fractional edge is
    /// antialiased across the pixels it covers rather than snapped (ADR-0031).
    ///
    /// `int bl_context_fill_rect_d_rgba32(void*, void*, int)`
    public static final class ContextFillRectDRgba32 {

        private static final MethodHandle FD_bl_context_fill_rect_d_rgba32 =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        ContextFillRectDRgba32(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_context_fill_rect_d_rgba32");
        }

        /// Calls `bl_context_fill_rect_d_rgba32`.
        ///
        /// @param context the context to fill in
        /// @param rect a `BLRect` — four doubles in the context’s own units
        /// @param argb a colour as `0xAARRGGBB`, straight alpha
        public int call(MemorySegment context, MemorySegment rect, int argb) {
            try {
                return (int) FD_bl_context_fill_rect_d_rgba32.invokeExact(address, context, rect, argb);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_context_fill_rect_d_rgba32", t);
            }
        }
    }

    /// Draws a run of positioned glyphs.
    ///
    /// The `_d` suffix is the origin’s type: doubles, so a baseline can land
    /// between physical pixels. The `_i` variant takes a `BLPointI` and is not
    /// bound, because rounding the baseline is what ADR-0031 went to some trouble
    /// to stop doing for rectangles.
    ///
    /// `int bl_context_fill_glyph_run_d_rgba32(void*, void*, void*, void*, int)`
    public static final class ContextFillGlyphRunDRgba32 {

        private static final MethodHandle FD_bl_context_fill_glyph_run_d_rgba32 =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        ContextFillGlyphRunDRgba32(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_context_fill_glyph_run_d_rgba32");
        }

        /// Calls `bl_context_fill_glyph_run_d_rgba32`.
        ///
        /// @param context the context to draw into
        /// @param origin a `BLPoint` baseline origin
        /// @param font the `BLFont` to render with
        /// @param glyphRun a `BLGlyphRun` of ids and positions, normally HarfBuzz’s output
        /// @param argb a colour as `0xAARRGGBB`, straight alpha
        public int call(
                MemorySegment context, MemorySegment origin, MemorySegment font, MemorySegment glyphRun, int argb) {
            try {
                return (int) FD_bl_context_fill_glyph_run_d_rgba32.invokeExact(
                        address, context, origin, font, glyphRun, argb);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_context_fill_glyph_run_d_rgba32", t);
            }
        }
    }

    /// Sets the width subsequent strokes are drawn at (ADR-0043).
    ///
    /// `int bl_context_set_stroke_width(void*, double)`
    public static final class ContextSetStrokeWidth {

        private static final MethodHandle FD_bl_context_set_stroke_width =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_DOUBLE));

        private final MemorySegment address;

        ContextSetStrokeWidth(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_context_set_stroke_width");
        }

        /// Calls `bl_context_set_stroke_width`.
        ///
        /// @param context the context to set it on
        /// @param width in the context’s own units, so a scaled context strokes in logical pixels
        public int call(MemorySegment context, double width) {
            try {
                return (int) FD_bl_context_set_stroke_width.invokeExact(address, context, width);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_context_set_stroke_width", t);
            }
        }
    }

    /// Sets both ends’ cap at once.
    ///
    /// `_caps`, plural. The singular `bl_context_set_stroke_cap` takes a
    /// `BLStrokeCapPosition` as well, and nothing wants a path capped differently
    /// at each end.
    ///
    /// `int bl_context_set_stroke_caps(void*, int)`
    public static final class ContextSetStrokeCaps {

        private static final MethodHandle FD_bl_context_set_stroke_caps =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        ContextSetStrokeCaps(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_context_set_stroke_caps");
        }

        /// Calls `bl_context_set_stroke_caps`.
        ///
        /// @param context the context to set it on
        /// @param cap a `BLStrokeCap`
        public int call(MemorySegment context, int cap) {
            try {
                return (int) FD_bl_context_set_stroke_caps.invokeExact(address, context, cap);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_context_set_stroke_caps", t);
            }
        }
    }

    /// Sets how subsequent strokes turn corners.
    ///
    /// `int bl_context_set_stroke_join(void*, int)`
    public static final class ContextSetStrokeJoin {

        private static final MethodHandle FD_bl_context_set_stroke_join =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        ContextSetStrokeJoin(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_context_set_stroke_join");
        }

        /// Calls `bl_context_set_stroke_join`.
        ///
        /// @param context the context to set it on
        /// @param join a `BLStrokeJoin`
        public int call(MemorySegment context, int join) {
            try {
                return (int) FD_bl_context_set_stroke_join.invokeExact(address, context, join);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_context_set_stroke_join", t);
            }
        }
    }

    /// Sets how far a mitered corner may run out before it is cut off.
    ///
    /// A multiple of the stroke width, not a length. SVG's and CSS's default is
    /// 4, and Blend2D's is too — so this is only ever called to depart from it
    /// (ADR-0278).
    ///
    /// `int bl_context_set_stroke_miter_limit(void*, double)`
    public static final class ContextSetStrokeMiterLimit {

        private static final MethodHandle FD_bl_context_set_stroke_miter_limit =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_DOUBLE));

        private final MemorySegment address;

        ContextSetStrokeMiterLimit(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_context_set_stroke_miter_limit");
        }

        /// Calls `bl_context_set_stroke_miter_limit`.
        ///
        /// @param context the context to set it on
        /// @param miterLimit the limit, as a multiple of the stroke width
        public int call(MemorySegment context, double miterLimit) {
            try {
                return (int) FD_bl_context_set_stroke_miter_limit.invokeExact(address, context, miterLimit);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_context_set_stroke_miter_limit", t);
            }
        }
    }

    /// Fills a path, translated by `origin`.
    ///
    /// The origin moves the path without transforming the context, which is what
    /// lets one 24×24 icon path be drawn at several places in a frame without
    /// being rebuilt or the context’s transform being saved and restored.
    ///
    /// `int bl_context_fill_path_d_rgba32(void*, void*, void*, int)`
    public static final class ContextFillPathDRgba32 {

        private static final MethodHandle FD_bl_context_fill_path_d_rgba32 =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        ContextFillPathDRgba32(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_context_fill_path_d_rgba32");
        }

        /// Calls `bl_context_fill_path_d_rgba32`.
        ///
        /// @param context the context to fill in
        /// @param origin a `BLPoint` the path is translated by
        /// @param path the `BLPath` to fill
        /// @param argb a colour as `0xAARRGGBB`, straight alpha
        public int call(MemorySegment context, MemorySegment origin, MemorySegment path, int argb) {
            try {
                return (int) FD_bl_context_fill_path_d_rgba32.invokeExact(address, context, origin, path, argb);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_context_fill_path_d_rgba32", t);
            }
        }
    }

    /// Fills a path with the style **currently set**, translated by `origin`.
    ///
    /// The one drawing call here with no `_rgba32` suffix, and that is the whole
    /// point of it: every other fill states its colour in the call, which is
    /// what keeps a frame free of style state nobody set back. A gradient cannot
    /// be an argument — it is an object with stops — so it goes on the context
    /// through [ContextSetFillStyle] and this is what draws with it (ADR-0207).
    ///
    /// `int bl_context_fill_path_d(void*, void*, void*)`
    public static final class ContextFillPathD {

        private static final MethodHandle FD_bl_context_fill_path_d =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));

        private final MemorySegment address;

        ContextFillPathD(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_context_fill_path_d");
        }

        /// Calls `bl_context_fill_path_d`.
        ///
        /// @param context the context to fill in
        /// @param origin a `BLPoint` the path is translated by
        /// @param path the `BLPath` to fill
        public int call(MemorySegment context, MemorySegment origin, MemorySegment path) {
            try {
                return (int) FD_bl_context_fill_path_d.invokeExact(address, context, origin, path);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_context_fill_path_d", t);
            }
        }
    }

    /// Sets an object — a gradient, here — as the fill style.
    ///
    /// `style` is a `const BLUnknown*`: Blend2D reads the object's own type tag
    /// out of its first eight bytes, so a `BLGradientCore` and a `BLPatternCore`
    /// go through the same call. It **retains** what it is given, so the caller's
    /// gradient may be released immediately afterwards and the context keeps
    /// drawing with it.
    ///
    /// `int bl_context_set_fill_style(void*, const void*)`
    public static final class ContextSetFillStyle {

        private static final MethodHandle FD_bl_context_set_fill_style =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

        private final MemorySegment address;

        ContextSetFillStyle(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_context_set_fill_style");
        }

        /// Calls `bl_context_set_fill_style`.
        ///
        /// @param context the context to set it on
        /// @param style the object to fill with
        public int call(MemorySegment context, MemorySegment style) {
            try {
                return (int) FD_bl_context_set_fill_style.invokeExact(address, context, style);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_context_set_fill_style", t);
            }
        }
    }

    /// Puts a plain colour back as the fill style.
    ///
    /// Bound for exactly one reason: a gradient left on the context would be
    /// held by it until something replaced it, and the next caller to reach for
    /// the styleless fill would draw with a ramp it never asked for. Nothing in
    /// the toolkit *reads* the fill style, so a set is only ever undone by
    /// another set.
    ///
    /// `int bl_context_set_fill_style_rgba32(void*, unsigned int)`
    public static final class ContextSetFillStyleRgba32 {

        private static final MethodHandle FD_bl_context_set_fill_style_rgba32 =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        ContextSetFillStyleRgba32(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_context_set_fill_style_rgba32");
        }

        /// Calls `bl_context_set_fill_style_rgba32`.
        ///
        /// @param context the context to set it on
        /// @param argb a colour as `0xAARRGGBB`, straight alpha
        public int call(MemorySegment context, int argb) {
            try {
                return (int) FD_bl_context_set_fill_style_rgba32.invokeExact(address, context, argb);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_context_set_fill_style_rgba32", t);
            }
        }
    }

    /// Strokes a path, translated by `origin`, at the width and caps set above.
    ///
    /// `int bl_context_stroke_path_d_rgba32(void*, void*, void*, int)`
    public static final class ContextStrokePathDRgba32 {

        private static final MethodHandle FD_bl_context_stroke_path_d_rgba32 =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        ContextStrokePathDRgba32(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_context_stroke_path_d_rgba32");
        }

        /// Calls `bl_context_stroke_path_d_rgba32`.
        ///
        /// @param context the context to stroke in
        /// @param origin a `BLPoint` the path is translated by
        /// @param path the `BLPath` to stroke
        /// @param argb a colour as `0xAARRGGBB`, straight alpha
        public int call(MemorySegment context, MemorySegment origin, MemorySegment path, int argb) {
            try {
                return (int) FD_bl_context_stroke_path_d_rgba32.invokeExact(address, context, origin, path, argb);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_context_stroke_path_d_rgba32", t);
            }
        }
    }

    /// Composites an image at a point, one source pixel per destination pixel.
    ///
    /// How a promoted layer gets back onto its parent (ADR-0071). `imageArea` is
    /// always NULL here — a layer always wants the whole raster — so no
    /// `BLRectI` ever crosses.
    ///
    /// `int bl_context_blit_image_d(void*, void*, void*, void*)`
    public static final class ContextBlitImageD {

        private static final MethodHandle FD_bl_context_blit_image_d =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS));

        private final MemorySegment address;

        ContextBlitImageD(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_context_blit_image_d");
        }

        /// Calls `bl_context_blit_image_d`.
        ///
        /// @param context the context to blit into
        /// @param origin a `BLPoint` — where the image’s top-left lands
        /// @param image the source image
        /// @param imageArea a `const BLRectI*` sub-rectangle of the source, or NULL for all of it
        public int call(MemorySegment context, MemorySegment origin, MemorySegment image, MemorySegment imageArea) {
            try {
                return (int) FD_bl_context_blit_image_d.invokeExact(address, context, origin, image, imageArea);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_context_blit_image_d", t);
            }
        }
    }

    /// Composites an image into a destination rectangle, scaling to fit.
    ///
    /// What reconciles a layer’s raster, measured in physical pixels, with a
    /// context measured in logical ones (ADR-0157).
    ///
    /// `int bl_context_blit_scaled_image_d(void*, void*, void*, void*)`
    public static final class ContextBlitScaledImageD {

        private static final MethodHandle FD_bl_context_blit_scaled_image_d =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS));

        private final MemorySegment address;

        ContextBlitScaledImageD(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_context_blit_scaled_image_d");
        }

        /// Calls `bl_context_blit_scaled_image_d`.
        ///
        /// @param context the context to blit into
        /// @param rect a `BLRect` destination, in the context’s own units
        /// @param image the source image
        /// @param imageArea a `const BLRectI*` sub-rectangle of the source, or NULL for all of it
        public int call(MemorySegment context, MemorySegment rect, MemorySegment image, MemorySegment imageArea) {
            try {
                return (int) FD_bl_context_blit_scaled_image_d.invokeExact(address, context, rect, image, imageArea);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_context_blit_scaled_image_d", t);
            }
        }
    }

    /// Sets an alpha multiplier applied to everything drawn afterwards.
    ///
    /// `int bl_context_set_global_alpha(void*, double)`
    public static final class ContextSetGlobalAlpha {

        private static final MethodHandle FD_bl_context_set_global_alpha =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_DOUBLE));

        private final MemorySegment address;

        ContextSetGlobalAlpha(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_context_set_global_alpha");
        }

        /// Calls `bl_context_set_global_alpha`.
        ///
        /// @param context the context to set it on
        /// @param alpha 0 to 1
        public int call(MemorySegment context, double alpha) {
            try {
                return (int) FD_bl_context_set_global_alpha.invokeExact(address, context, alpha);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_context_set_global_alpha", t);
            }
        }
    }

    /// Restricts drawing to a rectangle, intersecting with the clip already in force.
    ///
    /// How a frame is painted only inside its damage (ADR-0072).
    ///
    /// `int bl_context_clip_to_rect_d(void*, void*)`
    public static final class ContextClipToRectD {

        private static final MethodHandle FD_bl_context_clip_to_rect_d =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

        private final MemorySegment address;

        ContextClipToRectD(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_context_clip_to_rect_d");
        }

        /// Calls `bl_context_clip_to_rect_d`.
        ///
        /// @param context the context to clip
        /// @param rect a `BLRect` — four doubles in the context’s own units
        public int call(MemorySegment context, MemorySegment rect) {
            try {
                return (int) FD_bl_context_clip_to_rect_d.invokeExact(address, context, rect);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_context_clip_to_rect_d", t);
            }
        }
    }

    /// Undoes the last clip, restoring the region in force before it.
    ///
    /// `int bl_context_restore_clipping(void*)`
    public static final class ContextRestoreClipping {

        private static final MethodHandle FD_bl_context_restore_clipping =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        ContextRestoreClipping(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_context_restore_clipping");
        }

        /// Calls `bl_context_restore_clipping`.
        ///
        /// @param context the context to unclip
        public int call(MemorySegment context) {
            try {
                return (int) FD_bl_context_restore_clipping.invokeExact(address, context);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_context_restore_clipping", t);
            }
        }
    }

    /// Pushes the whole context state — clip, transform, style, alpha.
    ///
    /// Bound when `canvas` arrived: an application's painter runs inside whatever
    /// the tree had already set up, and `restore_clipping` goes back to the whole
    /// frame rather than to the previous region (ADR-0193).
    ///
    /// `int bl_context_save(void*, void*)`
    public static final class ContextSave {

        private static final MethodHandle FD_bl_context_save =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

        private final MemorySegment address;

        ContextSave(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_context_save");
        }

        /// Calls `bl_context_save`.
        ///
        /// @param context the context to save
        /// @param cookie a `BLContextCookie` to stamp, or NULL for an unguarded save
        public int call(MemorySegment context, MemorySegment cookie) {
            try {
                return (int) FD_bl_context_save.invokeExact(address, context, cookie);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_context_save", t);
            }
        }
    }

    /// Pops what [ContextSave] pushed.
    ///
    /// `int bl_context_restore(void*, void*)`
    public static final class ContextRestore {

        private static final MethodHandle FD_bl_context_restore =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

        private final MemorySegment address;

        ContextRestore(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_context_restore");
        }

        /// Calls `bl_context_restore`.
        ///
        /// @param context the context to restore
        /// @param cookie the cookie the matching save stamped, or NULL
        public int call(MemorySegment context, MemorySegment cookie) {
            try {
                return (int) FD_bl_context_restore.invokeExact(address, context, cookie);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_context_restore", t);
            }
        }
    }
}
