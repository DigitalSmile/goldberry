package io.github.digitalsmile.goldberry.natives.blend2d.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import io.github.digitalsmile.goldberry.natives.Downcalls;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

/// Blend2D's image, context, path and font functions, one holder each.
///
/// Every one of them returns a `BLResult`, which is zero on success --
/// [io.github.digitalsmile.goldberry.natives.blend2d.error.BlendResultCode] is
/// what reads it, and the binding is what checks it. A holder's `call` reports
/// only the crossing failing, not the result being bad.
///
/// See [io.github.digitalsmile.goldberry.natives.calls] for why the holders
/// live in a package of their own.
public record Blend2DCalls(
        RuntimeQueryInfo runtimeQueryInfo,
        ImageInitAsFromData imageInitAsFromData,
        ImageDestroy imageDestroy,
        ImageGetData imageGetData,
        ContextInitAs contextInitAs,
        ContextEnd contextEnd,
        ContextDestroy contextDestroy,
        ContextFlush contextFlush,
        ContextApplyTransformOp contextApplyTransformOp,
        ContextSetCompOp contextSetCompOp,
        ContextClearAll contextClearAll,
        ContextFillAllRgba32 contextFillAllRgba32,
        ContextFillRectDRgba32 contextFillRectDRgba32,
        ContextFillGlyphRunDRgba32 contextFillGlyphRunDRgba32,
        ContextSetStrokeWidth contextSetStrokeWidth,
        ContextSetStrokeCaps contextSetStrokeCaps,
        ContextSetStrokeJoin contextSetStrokeJoin,
        ContextFillPathDRgba32 contextFillPathDRgba32,
        ContextStrokePathDRgba32 contextStrokePathDRgba32,
        ContextBlitImageD contextBlitImageD,
        ContextBlitScaledImageD contextBlitScaledImageD,
        ContextSetGlobalAlpha contextSetGlobalAlpha,
        ContextClipToRectD contextClipToRectD,
        ContextRestoreClipping contextRestoreClipping,
        PathInit pathInit,
        PathDestroy pathDestroy,
        PathReset pathReset,
        PathGetSize pathGetSize,
        PathMoveTo pathMoveTo,
        PathLineTo pathLineTo,
        PathQuadTo pathQuadTo,
        PathCubicTo pathCubicTo,
        PathSmoothQuadTo pathSmoothQuadTo,
        PathSmoothCubicTo pathSmoothCubicTo,
        PathEllipticArcTo pathEllipticArcTo,
        PathClose pathClose,
        FontDataInit fontDataInit,
        FontDataCreateFromData fontDataCreateFromData,
        FontDataDestroy fontDataDestroy,
        FontFaceInit fontFaceInit,
        FontFaceCreateFromData fontFaceCreateFromData,
        FontFaceDestroy fontFaceDestroy,
        FontInit fontInit,
        FontCreateFromFace fontCreateFromFace,
        FontDestroy fontDestroy,
        FontGetMetrics fontGetMetrics) {

    /// Binds every function above.
    public static Blend2DCalls bind(SymbolLookup lookup) {
        return new Blend2DCalls(
                new RuntimeQueryInfo(lookup),
                new ImageInitAsFromData(lookup),
                new ImageDestroy(lookup),
                new ImageGetData(lookup),
                new ContextInitAs(lookup),
                new ContextEnd(lookup),
                new ContextDestroy(lookup),
                new ContextFlush(lookup),
                new ContextApplyTransformOp(lookup),
                new ContextSetCompOp(lookup),
                new ContextClearAll(lookup),
                new ContextFillAllRgba32(lookup),
                new ContextFillRectDRgba32(lookup),
                new ContextFillGlyphRunDRgba32(lookup),
                new ContextSetStrokeWidth(lookup),
                new ContextSetStrokeCaps(lookup),
                new ContextSetStrokeJoin(lookup),
                new ContextFillPathDRgba32(lookup),
                new ContextStrokePathDRgba32(lookup),
                new ContextBlitImageD(lookup),
                new ContextBlitScaledImageD(lookup),
                new ContextSetGlobalAlpha(lookup),
                new ContextClipToRectD(lookup),
                new ContextRestoreClipping(lookup),
                new PathInit(lookup),
                new PathDestroy(lookup),
                new PathReset(lookup),
                new PathGetSize(lookup),
                new PathMoveTo(lookup),
                new PathLineTo(lookup),
                new PathQuadTo(lookup),
                new PathCubicTo(lookup),
                new PathSmoothQuadTo(lookup),
                new PathSmoothCubicTo(lookup),
                new PathEllipticArcTo(lookup),
                new PathClose(lookup),
                new FontDataInit(lookup),
                new FontDataCreateFromData(lookup),
                new FontDataDestroy(lookup),
                new FontFaceInit(lookup),
                new FontFaceCreateFromData(lookup),
                new FontFaceDestroy(lookup),
                new FontInit(lookup),
                new FontCreateFromFace(lookup),
                new FontDestroy(lookup),
                new FontGetMetrics(lookup));
    }

    /// `int bl_runtime_query_info(int, void*)`
    public static final class RuntimeQueryInfo {

        private static final MethodHandle FD_bl_runtime_query_info =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, JAVA_INT, ADDRESS));

        private final MemorySegment address;

        RuntimeQueryInfo(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_runtime_query_info");
        }

        public int call(int a1, MemorySegment a2) {
            try {
                return (int) FD_bl_runtime_query_info.invokeExact(address, a1, a2);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_runtime_query_info", t);
            }
        }
    }

    /// `int bl_image_init_as_from_data(void*, int, int, int, void*, int64_t, int, void*, void*)`
    public static final class ImageInitAsFromData {

        private static final MethodHandle FD_bl_image_init_as_from_data =
                Downcalls.link(FunctionDescriptor.of(

                                JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS,
                                JAVA_LONG, JAVA_INT, ADDRESS, ADDRESS));

        private final MemorySegment address;

        ImageInitAsFromData(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_image_init_as_from_data");
        }

        public int call(
            MemorySegment a1, int a2, int a3, int a4, MemorySegment a5, long a6, int a7,
            MemorySegment a8, MemorySegment a9) {
            try {
                return (int) FD_bl_image_init_as_from_data.invokeExact(
                        address, a1, a2, a3, a4, a5, a6, a7, a8, a9);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_image_init_as_from_data", t);
            }
        }
    }

    /// `int bl_image_destroy(void*)`
    public static final class ImageDestroy {

        private static final MethodHandle FD_bl_image_destroy =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        ImageDestroy(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_image_destroy");
        }

        public int call(MemorySegment a1) {
            try {
                return (int) FD_bl_image_destroy.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_image_destroy", t);
            }
        }
    }

    /// `int bl_image_get_data(void*, void*)`
    public static final class ImageGetData {

        private static final MethodHandle FD_bl_image_get_data =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

        private final MemorySegment address;

        ImageGetData(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_image_get_data");
        }

        public int call(MemorySegment a1, MemorySegment a2) {
            try {
                return (int) FD_bl_image_get_data.invokeExact(address, a1, a2);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_image_get_data", t);
            }
        }
    }

    /// `int bl_context_init_as(void*, void*, void*)`
    public static final class ContextInitAs {

        private static final MethodHandle FD_bl_context_init_as =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));

        private final MemorySegment address;

        ContextInitAs(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_context_init_as");
        }

        public int call(MemorySegment a1, MemorySegment a2, MemorySegment a3) {
            try {
                return (int) FD_bl_context_init_as.invokeExact(address, a1, a2, a3);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_context_init_as", t);
            }
        }
    }

    /// `int bl_context_end(void*)`
    public static final class ContextEnd {

        private static final MethodHandle FD_bl_context_end =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        ContextEnd(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_context_end");
        }

        public int call(MemorySegment a1) {
            try {
                return (int) FD_bl_context_end.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_context_end", t);
            }
        }
    }

    /// `int bl_context_destroy(void*)`
    public static final class ContextDestroy {

        private static final MethodHandle FD_bl_context_destroy =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        ContextDestroy(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_context_destroy");
        }

        public int call(MemorySegment a1) {
            try {
                return (int) FD_bl_context_destroy.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_context_destroy", t);
            }
        }
    }

    /// `int bl_context_flush(void*, int)`
    public static final class ContextFlush {

        private static final MethodHandle FD_bl_context_flush =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        ContextFlush(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_context_flush");
        }

        public int call(MemorySegment a1, int a2) {
            try {
                return (int) FD_bl_context_flush.invokeExact(address, a1, a2);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_context_flush", t);
            }
        }
    }

    /// `int bl_context_apply_transform_op(void*, int, void*)`
    public static final class ContextApplyTransformOp {

        private static final MethodHandle FD_bl_context_apply_transform_op =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS));

        private final MemorySegment address;

        ContextApplyTransformOp(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_context_apply_transform_op");
        }

        public int call(MemorySegment a1, int a2, MemorySegment a3) {
            try {
                return (int) FD_bl_context_apply_transform_op.invokeExact(address, a1, a2, a3);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_context_apply_transform_op", t);
            }
        }
    }

    /// `int bl_context_set_comp_op(void*, int)`
    public static final class ContextSetCompOp {

        private static final MethodHandle FD_bl_context_set_comp_op =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        ContextSetCompOp(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_context_set_comp_op");
        }

        public int call(MemorySegment a1, int a2) {
            try {
                return (int) FD_bl_context_set_comp_op.invokeExact(address, a1, a2);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_context_set_comp_op", t);
            }
        }
    }

    /// `int bl_context_clear_all(void*)`
    public static final class ContextClearAll {

        private static final MethodHandle FD_bl_context_clear_all =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        ContextClearAll(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_context_clear_all");
        }

        public int call(MemorySegment a1) {
            try {
                return (int) FD_bl_context_clear_all.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_context_clear_all", t);
            }
        }
    }

    /// `int bl_context_fill_all_rgba32(void*, int)`
    public static final class ContextFillAllRgba32 {

        private static final MethodHandle FD_bl_context_fill_all_rgba32 =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        ContextFillAllRgba32(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_context_fill_all_rgba32");
        }

        public int call(MemorySegment a1, int a2) {
            try {
                return (int) FD_bl_context_fill_all_rgba32.invokeExact(address, a1, a2);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_context_fill_all_rgba32", t);
            }
        }
    }

    /// `int bl_context_fill_rect_d_rgba32(void*, void*, int)`
    public static final class ContextFillRectDRgba32 {

        private static final MethodHandle FD_bl_context_fill_rect_d_rgba32 =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        ContextFillRectDRgba32(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_context_fill_rect_d_rgba32");
        }

        public int call(MemorySegment a1, MemorySegment a2, int a3) {
            try {
                return (int) FD_bl_context_fill_rect_d_rgba32.invokeExact(address, a1, a2, a3);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_context_fill_rect_d_rgba32", t);
            }
        }
    }

    /// `int bl_context_fill_glyph_run_d_rgba32(void*, void*, void*, void*, int)`
    public static final class ContextFillGlyphRunDRgba32 {

        private static final MethodHandle FD_bl_context_fill_glyph_run_d_rgba32 =
                Downcalls.link(FunctionDescriptor.of(
                        JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        ContextFillGlyphRunDRgba32(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_context_fill_glyph_run_d_rgba32");
        }

        public int call(
            MemorySegment a1, MemorySegment a2, MemorySegment a3, MemorySegment a4, int a5) {
            try {
                return (int) FD_bl_context_fill_glyph_run_d_rgba32.invokeExact(
                        address, a1, a2, a3, a4, a5);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_context_fill_glyph_run_d_rgba32", t);
            }
        }
    }

    /// `int bl_context_set_stroke_width(void*, double)`
    public static final class ContextSetStrokeWidth {

        private static final MethodHandle FD_bl_context_set_stroke_width =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_DOUBLE));

        private final MemorySegment address;

        ContextSetStrokeWidth(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_context_set_stroke_width");
        }

        public int call(MemorySegment a1, double a2) {
            try {
                return (int) FD_bl_context_set_stroke_width.invokeExact(address, a1, a2);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_context_set_stroke_width", t);
            }
        }
    }

    /// `int bl_context_set_stroke_caps(void*, int)`
    public static final class ContextSetStrokeCaps {

        private static final MethodHandle FD_bl_context_set_stroke_caps =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        ContextSetStrokeCaps(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_context_set_stroke_caps");
        }

        public int call(MemorySegment a1, int a2) {
            try {
                return (int) FD_bl_context_set_stroke_caps.invokeExact(address, a1, a2);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_context_set_stroke_caps", t);
            }
        }
    }

    /// `int bl_context_set_stroke_join(void*, int)`
    public static final class ContextSetStrokeJoin {

        private static final MethodHandle FD_bl_context_set_stroke_join =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        ContextSetStrokeJoin(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_context_set_stroke_join");
        }

        public int call(MemorySegment a1, int a2) {
            try {
                return (int) FD_bl_context_set_stroke_join.invokeExact(address, a1, a2);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_context_set_stroke_join", t);
            }
        }
    }

    /// `int bl_context_fill_path_d_rgba32(void*, void*, void*, int)`
    public static final class ContextFillPathDRgba32 {

        private static final MethodHandle FD_bl_context_fill_path_d_rgba32 =
                Downcalls.link(FunctionDescriptor.of(
                        JAVA_INT, ADDRESS, ADDRESS, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        ContextFillPathDRgba32(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_context_fill_path_d_rgba32");
        }

        public int call(MemorySegment a1, MemorySegment a2, MemorySegment a3, int a4) {
            try {
                return (int) FD_bl_context_fill_path_d_rgba32.invokeExact(address, a1, a2, a3, a4);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_context_fill_path_d_rgba32", t);
            }
        }
    }

    /// `int bl_context_stroke_path_d_rgba32(void*, void*, void*, int)`
    public static final class ContextStrokePathDRgba32 {

        private static final MethodHandle FD_bl_context_stroke_path_d_rgba32 =
                Downcalls.link(FunctionDescriptor.of(
                        JAVA_INT, ADDRESS, ADDRESS, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        ContextStrokePathDRgba32(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_context_stroke_path_d_rgba32");
        }

        public int call(MemorySegment a1, MemorySegment a2, MemorySegment a3, int a4) {
            try {
                return (int) FD_bl_context_stroke_path_d_rgba32.invokeExact(
                        address, a1, a2, a3, a4);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_context_stroke_path_d_rgba32", t);
            }
        }
    }

    /// `int bl_context_blit_image_d(void*, void*, void*, void*)`
    public static final class ContextBlitImageD {

        private static final MethodHandle FD_bl_context_blit_image_d =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS));

        private final MemorySegment address;

        ContextBlitImageD(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_context_blit_image_d");
        }

        public int call(MemorySegment a1, MemorySegment a2, MemorySegment a3, MemorySegment a4) {
            try {
                return (int) FD_bl_context_blit_image_d.invokeExact(address, a1, a2, a3, a4);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_context_blit_image_d", t);
            }
        }
    }

    /// `int bl_context_blit_scaled_image_d(void*, void*, void*, void*)`
    public static final class ContextBlitScaledImageD {

        private static final MethodHandle FD_bl_context_blit_scaled_image_d =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS));

        private final MemorySegment address;

        ContextBlitScaledImageD(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_context_blit_scaled_image_d");
        }

        public int call(MemorySegment a1, MemorySegment a2, MemorySegment a3, MemorySegment a4) {
            try {
                return (int) FD_bl_context_blit_scaled_image_d.invokeExact(address, a1, a2, a3, a4);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_context_blit_scaled_image_d", t);
            }
        }
    }

    /// `int bl_context_set_global_alpha(void*, double)`
    public static final class ContextSetGlobalAlpha {

        private static final MethodHandle FD_bl_context_set_global_alpha =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_DOUBLE));

        private final MemorySegment address;

        ContextSetGlobalAlpha(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_context_set_global_alpha");
        }

        public int call(MemorySegment a1, double a2) {
            try {
                return (int) FD_bl_context_set_global_alpha.invokeExact(address, a1, a2);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_context_set_global_alpha", t);
            }
        }
    }

    /// `int bl_context_clip_to_rect_d(void*, void*)`
    public static final class ContextClipToRectD {

        private static final MethodHandle FD_bl_context_clip_to_rect_d =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

        private final MemorySegment address;

        ContextClipToRectD(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_context_clip_to_rect_d");
        }

        public int call(MemorySegment a1, MemorySegment a2) {
            try {
                return (int) FD_bl_context_clip_to_rect_d.invokeExact(address, a1, a2);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_context_clip_to_rect_d", t);
            }
        }
    }

    /// `int bl_context_restore_clipping(void*)`
    public static final class ContextRestoreClipping {

        private static final MethodHandle FD_bl_context_restore_clipping =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        ContextRestoreClipping(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_context_restore_clipping");
        }

        public int call(MemorySegment a1) {
            try {
                return (int) FD_bl_context_restore_clipping.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_context_restore_clipping", t);
            }
        }
    }

    /// `int bl_path_init(void*)`
    public static final class PathInit {

        private static final MethodHandle FD_bl_path_init =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        PathInit(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_path_init");
        }

        public int call(MemorySegment a1) {
            try {
                return (int) FD_bl_path_init.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_path_init", t);
            }
        }
    }

    /// `int bl_path_destroy(void*)`
    public static final class PathDestroy {

        private static final MethodHandle FD_bl_path_destroy =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        PathDestroy(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_path_destroy");
        }

        public int call(MemorySegment a1) {
            try {
                return (int) FD_bl_path_destroy.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_path_destroy", t);
            }
        }
    }

    /// `int bl_path_reset(void*)`
    public static final class PathReset {

        private static final MethodHandle FD_bl_path_reset =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        PathReset(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_path_reset");
        }

        public int call(MemorySegment a1) {
            try {
                return (int) FD_bl_path_reset.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_path_reset", t);
            }
        }
    }

    /// `int64_t bl_path_get_size(void*)`
    public static final class PathGetSize {

        private static final MethodHandle FD_bl_path_get_size =
                Downcalls.link(FunctionDescriptor.of(JAVA_LONG, ADDRESS));

        private final MemorySegment address;

        PathGetSize(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_path_get_size");
        }

        public long call(MemorySegment a1) {
            try {
                return (long) FD_bl_path_get_size.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_path_get_size", t);
            }
        }
    }

    /// `int bl_path_move_to(void*, double, double)`
    public static final class PathMoveTo {

        private static final MethodHandle FD_bl_path_move_to =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_DOUBLE, JAVA_DOUBLE));

        private final MemorySegment address;

        PathMoveTo(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_path_move_to");
        }

        public int call(MemorySegment a1, double a2, double a3) {
            try {
                return (int) FD_bl_path_move_to.invokeExact(address, a1, a2, a3);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_path_move_to", t);
            }
        }
    }

    /// `int bl_path_line_to(void*, double, double)`
    public static final class PathLineTo {

        private static final MethodHandle FD_bl_path_line_to =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_DOUBLE, JAVA_DOUBLE));

        private final MemorySegment address;

        PathLineTo(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_path_line_to");
        }

        public int call(MemorySegment a1, double a2, double a3) {
            try {
                return (int) FD_bl_path_line_to.invokeExact(address, a1, a2, a3);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_path_line_to", t);
            }
        }
    }

    /// `int bl_path_quad_to(void*, double, double, double, double)`
    public static final class PathQuadTo {

        private static final MethodHandle FD_bl_path_quad_to =
                Downcalls.link(FunctionDescriptor.of(
                        JAVA_INT, ADDRESS, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE));

        private final MemorySegment address;

        PathQuadTo(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_path_quad_to");
        }

        public int call(MemorySegment a1, double a2, double a3, double a4, double a5) {
            try {
                return (int) FD_bl_path_quad_to.invokeExact(address, a1, a2, a3, a4, a5);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_path_quad_to", t);
            }
        }
    }

    /// `int bl_path_cubic_to(void*, double, double, double, double, double, double)`
    public static final class PathCubicTo {

        private static final MethodHandle FD_bl_path_cubic_to =
                Downcalls.link(FunctionDescriptor.of(

                                JAVA_INT, ADDRESS, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE,
                                JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE));

        private final MemorySegment address;

        PathCubicTo(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_path_cubic_to");
        }

        public int call(
            MemorySegment a1, double a2, double a3, double a4, double a5, double a6, double a7) {
            try {
                return (int) FD_bl_path_cubic_to.invokeExact(address, a1, a2, a3, a4, a5, a6, a7);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_path_cubic_to", t);
            }
        }
    }

    /// `int bl_path_smooth_quad_to(void*, double, double)`
    public static final class PathSmoothQuadTo {

        private static final MethodHandle FD_bl_path_smooth_quad_to =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_DOUBLE, JAVA_DOUBLE));

        private final MemorySegment address;

        PathSmoothQuadTo(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_path_smooth_quad_to");
        }

        public int call(MemorySegment a1, double a2, double a3) {
            try {
                return (int) FD_bl_path_smooth_quad_to.invokeExact(address, a1, a2, a3);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_path_smooth_quad_to", t);
            }
        }
    }

    /// `int bl_path_smooth_cubic_to(void*, double, double, double, double)`
    public static final class PathSmoothCubicTo {

        private static final MethodHandle FD_bl_path_smooth_cubic_to =
                Downcalls.link(FunctionDescriptor.of(
                        JAVA_INT, ADDRESS, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE));

        private final MemorySegment address;

        PathSmoothCubicTo(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_path_smooth_cubic_to");
        }

        public int call(MemorySegment a1, double a2, double a3, double a4, double a5) {
            try {
                return (int) FD_bl_path_smooth_cubic_to.invokeExact(address, a1, a2, a3, a4, a5);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_path_smooth_cubic_to", t);
            }
        }
    }

    /// `int bl_path_elliptic_arc_to(void*, double, double, double, _Bool, _Bool, double, double)`
    public static final class PathEllipticArcTo {

        private static final MethodHandle FD_bl_path_elliptic_arc_to =
                Downcalls.link(FunctionDescriptor.of(

                                JAVA_INT, ADDRESS, JAVA_DOUBLE, JAVA_DOUBLE, JAVA_DOUBLE,
                                JAVA_BOOLEAN, JAVA_BOOLEAN, JAVA_DOUBLE, JAVA_DOUBLE));

        private final MemorySegment address;

        PathEllipticArcTo(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_path_elliptic_arc_to");
        }

        public int call(
            MemorySegment a1, double a2, double a3, double a4, boolean a5, boolean a6, double a7,
            double a8) {
            try {
                return (int) FD_bl_path_elliptic_arc_to.invokeExact(
                        address, a1, a2, a3, a4, a5, a6, a7, a8);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_path_elliptic_arc_to", t);
            }
        }
    }

    /// `int bl_path_close(void*)`
    public static final class PathClose {

        private static final MethodHandle FD_bl_path_close =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        PathClose(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_path_close");
        }

        public int call(MemorySegment a1) {
            try {
                return (int) FD_bl_path_close.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_path_close", t);
            }
        }
    }

    /// `int bl_font_data_init(void*)`
    public static final class FontDataInit {

        private static final MethodHandle FD_bl_font_data_init =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        FontDataInit(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_font_data_init");
        }

        public int call(MemorySegment a1) {
            try {
                return (int) FD_bl_font_data_init.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_font_data_init", t);
            }
        }
    }

    /// `int bl_font_data_create_from_data(void*, void*, int64_t, void*, void*)`
    public static final class FontDataCreateFromData {

        private static final MethodHandle FD_bl_font_data_create_from_data =
                Downcalls.link(FunctionDescriptor.of(
                        JAVA_INT, ADDRESS, ADDRESS, JAVA_LONG, ADDRESS, ADDRESS));

        private final MemorySegment address;

        FontDataCreateFromData(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_font_data_create_from_data");
        }

        public int call(
            MemorySegment a1, MemorySegment a2, long a3, MemorySegment a4, MemorySegment a5) {
            try {
                return (int) FD_bl_font_data_create_from_data.invokeExact(
                        address, a1, a2, a3, a4, a5);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_font_data_create_from_data", t);
            }
        }
    }

    /// `int bl_font_data_destroy(void*)`
    public static final class FontDataDestroy {

        private static final MethodHandle FD_bl_font_data_destroy =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        FontDataDestroy(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_font_data_destroy");
        }

        public int call(MemorySegment a1) {
            try {
                return (int) FD_bl_font_data_destroy.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_font_data_destroy", t);
            }
        }
    }

    /// `int bl_font_face_init(void*)`
    public static final class FontFaceInit {

        private static final MethodHandle FD_bl_font_face_init =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        FontFaceInit(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_font_face_init");
        }

        public int call(MemorySegment a1) {
            try {
                return (int) FD_bl_font_face_init.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_font_face_init", t);
            }
        }
    }

    /// `int bl_font_face_create_from_data(void*, void*, int)`
    public static final class FontFaceCreateFromData {

        private static final MethodHandle FD_bl_font_face_create_from_data =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        FontFaceCreateFromData(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_font_face_create_from_data");
        }

        public int call(MemorySegment a1, MemorySegment a2, int a3) {
            try {
                return (int) FD_bl_font_face_create_from_data.invokeExact(address, a1, a2, a3);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_font_face_create_from_data", t);
            }
        }
    }

    /// `int bl_font_face_destroy(void*)`
    public static final class FontFaceDestroy {

        private static final MethodHandle FD_bl_font_face_destroy =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        FontFaceDestroy(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_font_face_destroy");
        }

        public int call(MemorySegment a1) {
            try {
                return (int) FD_bl_font_face_destroy.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_font_face_destroy", t);
            }
        }
    }

    /// `int bl_font_init(void*)`
    public static final class FontInit {

        private static final MethodHandle FD_bl_font_init =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        FontInit(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_font_init");
        }

        public int call(MemorySegment a1) {
            try {
                return (int) FD_bl_font_init.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_font_init", t);
            }
        }
    }

    /// `int bl_font_create_from_face(void*, void*, float)`
    public static final class FontCreateFromFace {

        private static final MethodHandle FD_bl_font_create_from_face =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_FLOAT));

        private final MemorySegment address;

        FontCreateFromFace(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_font_create_from_face");
        }

        public int call(MemorySegment a1, MemorySegment a2, float a3) {
            try {
                return (int) FD_bl_font_create_from_face.invokeExact(address, a1, a2, a3);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_font_create_from_face", t);
            }
        }
    }

    /// `int bl_font_destroy(void*)`
    public static final class FontDestroy {

        private static final MethodHandle FD_bl_font_destroy =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        FontDestroy(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_font_destroy");
        }

        public int call(MemorySegment a1) {
            try {
                return (int) FD_bl_font_destroy.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_font_destroy", t);
            }
        }
    }

    /// `int bl_font_get_metrics(void*, void*)`
    public static final class FontGetMetrics {

        private static final MethodHandle FD_bl_font_get_metrics =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

        private final MemorySegment address;

        FontGetMetrics(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "bl_font_get_metrics");
        }

        public int call(MemorySegment a1, MemorySegment a2) {
            try {
                return (int) FD_bl_font_get_metrics.invokeExact(address, a1, a2);
            } catch (Throwable t) {
                throw Downcalls.failure("bl_font_get_metrics", t);
            }
        }
    }
}
