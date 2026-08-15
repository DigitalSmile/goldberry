package io.github.digitalsmile.goldberry.natives.blend2d;

import io.github.digitalsmile.goldberry.natives.NativeLibrary;
import io.github.digitalsmile.goldberry.natives.layout.Layouts;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;

/// Blend2D's image and rendering-context calls.
///
/// Package-private for the reason [Yoga][io.github.digitalsmile.goldberry.natives.yoga]'s
/// binding class is: nothing above `:natives` drives Blend2D directly, so
/// [BlendImage] and [BlendContext] are the only way in, and there is no second
/// path that reaches `bl_context_destroy` without the wrapper that knows whether
/// the context is still attached.
///
/// **Every function returns `BLResult`.** Blend2D has no other error channel —
/// no `GetError`, no errno, no exceptions (it is compiled `-fno-exceptions`). A
/// non-zero result is turned into a [BlendException] here, at the boundary, so
/// no caller can drop one by forgetting to check.
///
/// **Blend2D's enums cross as `uint32_t`.** `BL_DEFINE_ENUM` expands to
/// `enum NAME : uint32_t` in C++ and a plain enum in C, and the values are
/// checked against the compiled library through the layout table.
final class Blend2D {

    private static final Linker LINKER = Linker.nativeLinker();

    private static final long BUILD_INFO_SIZE = Layouts.BL_RUNTIME_BUILD_INFO.byteSize();
    private static final long MAJOR_OFFSET = Layouts.BL_RUNTIME_BUILD_INFO.offsetOf("major_version");
    private static final long MINOR_OFFSET = Layouts.BL_RUNTIME_BUILD_INFO.offsetOf("minor_version");
    private static final long PATCH_OFFSET = Layouts.BL_RUNTIME_BUILD_INFO.offsetOf("patch_version");
    private static final long COMPILER_OFFSET = Layouts.BL_RUNTIME_BUILD_INFO.offsetOf("compiler_info");
    private static final long COMPILER_SIZE = Layouts.BL_RUNTIME_BUILD_INFO.sizeOf("compiler_info");

    private static final long METRICS_SIZE = Layouts.BL_FONT_METRICS.offsetOf("size");
    private static final long METRICS_ASCENT = Layouts.BL_FONT_METRICS.offsetOf("ascent");
    private static final long METRICS_DESCENT = Layouts.BL_FONT_METRICS.offsetOf("descent");
    private static final long METRICS_LINE_GAP = Layouts.BL_FONT_METRICS.offsetOf("line_gap");
    private static final long METRICS_X_HEIGHT = Layouts.BL_FONT_METRICS.offsetOf("x_height");
    private static final long METRICS_CAP_HEIGHT = Layouts.BL_FONT_METRICS.offsetOf("cap_height");

    private static final long IMAGE_DATA_PIXELS = Layouts.BL_IMAGE_DATA.offsetOf("pixel_data");
    private static final long IMAGE_DATA_STRIDE = Layouts.BL_IMAGE_DATA.offsetOf("stride");
    private static final long IMAGE_DATA_FORMAT = Layouts.BL_IMAGE_DATA.offsetOf("format");

    private static final class Holder {
        private static final Blend2D INSTANCE = new Blend2D(NativeLibrary.get().lookup());
    }

    private final MethodHandle runtimeQueryInfo;

    private final MethodHandle imageInitAsFromData;
    private final MethodHandle imageDestroy;
    private final MethodHandle imageGetData;

    private final MethodHandle contextInitAs;
    private final MethodHandle contextEnd;
    private final MethodHandle contextDestroy;
    private final MethodHandle contextFlush;
    private final MethodHandle contextApplyTransformOp;
    private final MethodHandle contextSetCompOp;
    private final MethodHandle contextClearAll;
    private final MethodHandle contextFillAllRgba32;
    private final MethodHandle contextFillRectDRgba32;
    private final MethodHandle contextFillGlyphRunDRgba32;

    private final MethodHandle fontDataInit;
    private final MethodHandle fontDataCreateFromData;
    private final MethodHandle fontDataDestroy;
    private final MethodHandle fontFaceInit;
    private final MethodHandle fontFaceCreateFromData;
    private final MethodHandle fontFaceDestroy;
    private final MethodHandle fontInit;
    private final MethodHandle fontCreateFromFace;
    private final MethodHandle fontDestroy;
    private final MethodHandle fontGetMetrics;

    private Blend2D(SymbolLookup lookup) {
        this.runtimeQueryInfo = downcall(lookup, "bl_runtime_query_info", FunctionDescriptor.of(
                ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

        // BLResult bl_image_init_as_from_data(BLImageCore*, int w, int h, BLFormat,
        //     void* pixel_data, intptr_t stride, BLDataAccessFlags,
        //     BLDestroyExternalDataFunc, void* user_data)
        //
        // intptr_t is 8 bytes on every target here -- the "pointer" scalar row is
        // what says so. It is signed: a negative stride means the image starts at
        // the bottom-left, which Goldberry never produces but must not silently
        // reinterpret.
        this.imageInitAsFromData = downcall(lookup, "bl_image_init_as_from_data",
                FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.JAVA_LONG,
                        ValueLayout.JAVA_INT,
                        ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS));
        this.imageDestroy = downcall(lookup, "bl_image_destroy", resultOf(ValueLayout.ADDRESS));
        this.imageGetData = downcall(lookup, "bl_image_get_data",
                resultOf(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

        this.contextInitAs = downcall(lookup, "bl_context_init_as",
                resultOf(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.contextEnd = downcall(lookup, "bl_context_end", resultOf(ValueLayout.ADDRESS));
        this.contextDestroy = downcall(lookup, "bl_context_destroy", resultOf(ValueLayout.ADDRESS));
        this.contextFlush = downcall(lookup, "bl_context_flush",
                resultOf(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.contextApplyTransformOp = downcall(lookup, "bl_context_apply_transform_op",
                resultOf(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
        this.contextSetCompOp = downcall(lookup, "bl_context_set_comp_op",
                resultOf(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.contextClearAll = downcall(lookup, "bl_context_clear_all", resultOf(ValueLayout.ADDRESS));
        this.contextFillAllRgba32 = downcall(lookup, "bl_context_fill_all_rgba32",
                resultOf(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.contextFillRectDRgba32 = downcall(lookup, "bl_context_fill_rect_d_rgba32",
                resultOf(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        // BLResult bl_context_fill_glyph_run_d_rgba32(BLContextCore*,
        //     const BLPoint* origin, const BLFontCore*, const BLGlyphRun*, uint32_t)
        //
        // The `_d` suffix is the origin's type: doubles, so a baseline can land
        // between physical pixels. The `_i` variant takes a BLPointI and is not
        // bound, because rounding the baseline is exactly what ADR-0031 went to
        // some trouble to stop doing for rectangles.
        this.contextFillGlyphRunDRgba32 = downcall(lookup, "bl_context_fill_glyph_run_d_rgba32",
                resultOf(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                        ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

        // The three font objects. Each `create` REPLACES what the handle holds,
        // so each one has to be `init`ed first -- Blend2D releases the previous
        // instance, and releasing an uninitialised one reads a pointer that was
        // never written.
        this.fontDataInit = downcall(lookup, "bl_font_data_init", resultOf(ValueLayout.ADDRESS));
        // BLResult bl_font_data_create_from_data(BLFontDataCore*, const void* data,
        //     size_t data_size, BLDestroyExternalDataFunc, void* user_data)
        this.fontDataCreateFromData = downcall(lookup, "bl_font_data_create_from_data",
                resultOf(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
                        ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        this.fontDataDestroy = downcall(lookup, "bl_font_data_destroy",
                resultOf(ValueLayout.ADDRESS));

        this.fontFaceInit = downcall(lookup, "bl_font_face_init", resultOf(ValueLayout.ADDRESS));
        this.fontFaceCreateFromData = downcall(lookup, "bl_font_face_create_from_data",
                resultOf(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        this.fontFaceDestroy = downcall(lookup, "bl_font_face_destroy",
                resultOf(ValueLayout.ADDRESS));

        this.fontInit = downcall(lookup, "bl_font_init", resultOf(ValueLayout.ADDRESS));
        // The size is a `float`, not a double: Blend2D's own choice, and the one
        // place in the paint path where a coordinate narrows.
        this.fontCreateFromFace = downcall(lookup, "bl_font_create_from_face",
                resultOf(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_FLOAT));
        this.fontDestroy = downcall(lookup, "bl_font_destroy", resultOf(ValueLayout.ADDRESS));
        this.fontGetMetrics = downcall(lookup, "bl_font_get_metrics",
                resultOf(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    }

    static Blend2D get() {
        return Holder.INSTANCE;
    }

    // --- runtime -----------------------------------------------------------

    /// The Blend2D that was statically linked into `libgoldberry`.
    BlendVersion version() {
        try (var arena = Arena.ofConfined()) {
            var info = arena.allocate(BUILD_INFO_SIZE, Layouts.BL_RUNTIME_BUILD_INFO.byteAlignment());
            // The type and the buffer must agree: asking for SYSTEM with a
            // BUILD-sized allocation writes past the end. Pairing them here is
            // what makes that unrepresentable rather than merely documented.
            check("bl_runtime_query_info", invoke(
                    runtimeQueryInfo, BlendRuntimeInfoType.BUILD.nativeValue(), info));

            var compiler = info.asSlice(COMPILER_OFFSET, COMPILER_SIZE)
                    .toArray(ValueLayout.JAVA_BYTE);
            var end = 0;
            while (end < compiler.length && compiler[end] != 0) {
                end++;
            }

            return new BlendVersion(
                    info.get(ValueLayout.JAVA_INT, MAJOR_OFFSET),
                    info.get(ValueLayout.JAVA_INT, MINOR_OFFSET),
                    info.get(ValueLayout.JAVA_INT, PATCH_OFFSET),
                    new String(compiler, 0, end, StandardCharsets.UTF_8));
        }
    }

    // --- images ------------------------------------------------------------

    /// Initialises `image` as a view over pixels Blend2D does not own.
    ///
    /// The destroy callback and its user data are both NULL: the buffer's
    /// lifetime is Java's, and telling Blend2D to free it would be handing it
    /// memory it did not allocate.
    void imageInitFromData(
            MemorySegment image, int width, int height, BlendFormat format,
            MemorySegment pixels, long stride) {

        int result;
        try {
            result = (int) imageInitAsFromData.invokeExact(
                    image, width, height, format.nativeValue(), pixels, stride,
                    BlendDataAccess.READ_WRITE.nativeValue(),
                    MemorySegment.NULL, MemorySegment.NULL);
        } catch (Throwable t) {
            throw failure("bl_image_init_as_from_data", t);
        }
        check("bl_image_init_as_from_data", result);
    }

    void imageDestroy(MemorySegment image) {
        check("bl_image_destroy", invoke(imageDestroy, image));
    }

    /// Reads back where Blend2D thinks the pixels are.
    ///
    /// Used by the tests rather than by the paint path: it is how "the image
    /// really is a view over the buffer we passed" becomes an assertion about
    /// an address rather than a claim in a comment.
    ImageData imageData(MemorySegment image) {
        try (var arena = Arena.ofConfined()) {
            var data = arena.allocate(Layouts.BL_IMAGE_DATA.layout());
            check("bl_image_get_data", invoke(imageGetData, image, data));
            return new ImageData(
                    data.get(ValueLayout.ADDRESS, IMAGE_DATA_PIXELS).address(),
                    data.get(ValueLayout.JAVA_LONG, IMAGE_DATA_STRIDE),
                    BlendFormat.of(data.get(ValueLayout.JAVA_INT, IMAGE_DATA_FORMAT)));
        }
    }

    /// What `bl_image_get_data` reported. Addresses as `long`, because a raw
    /// [MemorySegment] may not leave this module and a test only needs to
    /// compare the number.
    record ImageData(long pixels, long stride, BlendFormat format) {
    }

    // --- contexts ----------------------------------------------------------

    /// Begins rendering into `image`.
    ///
    /// `createInfo` may be [MemorySegment#NULL], which asks for the defaults: a
    /// synchronous context on the calling thread. Blend2D's banded
    /// multithreading is a `thread_count` away and deliberately not taken yet —
    /// see ADR-0031.
    void contextBegin(MemorySegment context, MemorySegment image, MemorySegment createInfo) {
        check("bl_context_init_as", invoke(contextInitAs, context, image, createInfo));
    }

    void contextEnd(MemorySegment context) {
        check("bl_context_end", invoke(contextEnd, context));
    }

    void contextDestroy(MemorySegment context) {
        check("bl_context_destroy", invoke(contextDestroy, context));
    }

    void contextFlush(MemorySegment context, int flags) {
        try {
            check("bl_context_flush", (int) contextFlush.invokeExact(context, flags));
        } catch (Throwable t) {
            throw failure("bl_context_flush", t);
        }
    }

    /// Applies a transform whose operand is a pair of doubles — [
    /// BlendTransformOp#SCALE] or [BlendTransformOp#TRANSLATE].
    ///
    /// The operand crosses as `const void*`, so nothing on either side checks
    /// that the shape matches the operation. Restricting this method to the two
    /// ops that read a `BLPoint` is what keeps that unchecked cast honest;
    /// [BlendTransformOp#RESET] takes no operand and would read two doubles that
    /// were never written.
    void contextTransform(MemorySegment context, BlendTransformOp op, double x, double y) {
        if (op != BlendTransformOp.SCALE && op != BlendTransformOp.TRANSLATE) {
            throw new IllegalArgumentException(
                    op + " does not take a pair of doubles, and its operand crosses as void*"
                            + " — nothing downstream would catch the mismatch");
        }
        try (var arena = Arena.ofConfined()) {
            var point = arena.allocate(ValueLayout.JAVA_DOUBLE, 2);
            point.setAtIndex(ValueLayout.JAVA_DOUBLE, 0, x);
            point.setAtIndex(ValueLayout.JAVA_DOUBLE, 1, y);
            try {
                check("bl_context_apply_transform_op",
                        (int) contextApplyTransformOp.invokeExact(context, op.nativeValue(), point));
            } catch (Throwable t) {
                throw failure("bl_context_apply_transform_op", t);
            }
        }
    }

    void contextCompOp(MemorySegment context, BlendCompOp compOp) {
        try {
            check("bl_context_set_comp_op",
                    (int) contextSetCompOp.invokeExact(context, compOp.nativeValue()));
        } catch (Throwable t) {
            throw failure("bl_context_set_comp_op", t);
        }
    }

    void contextClearAll(MemorySegment context) {
        check("bl_context_clear_all", invoke(contextClearAll, context));
    }

    /// Fills the whole clip box with a straight-alpha `0xAARRGGBB`.
    ///
    /// Blend2D premultiplies the style itself when it composites, so the colour
    /// crossing here is **not** premultiplied even though the target image is.
    /// Premultiplying it first would darken every translucent fill twice.
    void contextFillAll(MemorySegment context, int argb) {
        try {
            check("bl_context_fill_all_rgba32",
                    (int) contextFillAllRgba32.invokeExact(context, argb));
        } catch (Throwable t) {
            throw failure("bl_context_fill_all_rgba32", t);
        }
    }

    /// Fills a rectangle in the context's current user space.
    void contextFillRect(MemorySegment context, MemorySegment rect, int argb) {
        try {
            check("bl_context_fill_rect_d_rgba32",
                    (int) contextFillRectDRgba32.invokeExact(context, rect, argb));
        } catch (Throwable t) {
            throw failure("bl_context_fill_rect_d_rgba32", t);
        }
    }

    /// Fills a run of positioned glyphs, with `origin` on the baseline.
    ///
    /// `glyphRun` is a descriptor pointing at arrays the caller still owns, so
    /// those arrays must outlive the call — which they do, because
    /// [BlendGlyphBuffer] holds all three in one arena.
    void contextFillGlyphRun(
            MemorySegment context, MemorySegment origin, MemorySegment font,
            MemorySegment glyphRun, int argb) {
        try {
            check("bl_context_fill_glyph_run_d_rgba32",
                    (int) contextFillGlyphRunDRgba32.invokeExact(
                            context, origin, font, glyphRun, argb));
        } catch (Throwable t) {
            throw failure("bl_context_fill_glyph_run_d_rgba32", t);
        }
    }

    // --- fonts ---------------------------------------------------------------

    void fontDataInit(MemorySegment fontData) {
        check("bl_font_data_init", invoke(fontDataInit, fontData));
    }

    /// Points `fontData` at a font file's bytes, which Blend2D does **not** copy.
    ///
    /// The destroy callback and its user data are NULL for the same reason
    /// [#imageInitFromData] passes NULL: the bytes belong to Java, and handing
    /// Blend2D a free function for memory it did not allocate is how a heap gets
    /// corrupted. The caller keeps them alive instead.
    void fontDataCreate(MemorySegment fontData, MemorySegment bytes, long length) {
        int result;
        try {
            result = (int) fontDataCreateFromData.invokeExact(
                    fontData, bytes, length, MemorySegment.NULL, MemorySegment.NULL);
        } catch (Throwable t) {
            throw failure("bl_font_data_create_from_data", t);
        }
        check("bl_font_data_create_from_data", result);
    }

    void fontDataDestroy(MemorySegment fontData) {
        check("bl_font_data_destroy", invoke(fontDataDestroy, fontData));
    }

    void fontFaceInit(MemorySegment face) {
        check("bl_font_face_init", invoke(fontFaceInit, face));
    }

    /// Reads face `index` out of `fontData`.
    ///
    /// Unlike HarfBuzz, Blend2D **reports** a file it cannot parse: a corrupt or
    /// non-font blob fails here with a `BLResult` rather than producing an empty
    /// face that silently shapes to `.notdef`. That difference is worth knowing
    /// when the two disagree about the same bytes.
    void fontFaceCreate(MemorySegment face, MemorySegment fontData, int index) {
        int result;
        try {
            result = (int) fontFaceCreateFromData.invokeExact(face, fontData, index);
        } catch (Throwable t) {
            throw failure("bl_font_face_create_from_data", t);
        }
        check("bl_font_face_create_from_data", result);
    }

    void fontFaceDestroy(MemorySegment face) {
        check("bl_font_face_destroy", invoke(fontFaceDestroy, face));
    }

    void fontInit(MemorySegment font) {
        check("bl_font_init", invoke(fontInit, font));
    }

    /// Sizes `face` at `size` units per em.
    ///
    /// This is where the font matrix comes from — `size / units-per-em` — and
    /// therefore where the units of every glyph placement are decided. See
    /// [BlendGlyphPlacementType].
    void fontCreate(MemorySegment font, MemorySegment face, float size) {
        int result;
        try {
            result = (int) fontCreateFromFace.invokeExact(font, face, size);
        } catch (Throwable t) {
            throw failure("bl_font_create_from_face", t);
        }
        check("bl_font_create_from_face", result);
    }

    void fontDestroy(MemorySegment font) {
        check("bl_font_destroy", invoke(fontDestroy, font));
    }

    /// The font's metrics, already scaled by its size.
    BlendFontMetrics fontMetrics(MemorySegment font) {
        try (var arena = Arena.ofConfined()) {
            var metrics = arena.allocate(Layouts.BL_FONT_METRICS.layout());
            check("bl_font_get_metrics", invoke(fontGetMetrics, font, metrics));
            return new BlendFontMetrics(
                    metrics.get(ValueLayout.JAVA_FLOAT, METRICS_SIZE),
                    metrics.get(ValueLayout.JAVA_FLOAT, METRICS_ASCENT),
                    metrics.get(ValueLayout.JAVA_FLOAT, METRICS_DESCENT),
                    metrics.get(ValueLayout.JAVA_FLOAT, METRICS_LINE_GAP),
                    metrics.get(ValueLayout.JAVA_FLOAT, METRICS_X_HEIGHT),
                    metrics.get(ValueLayout.JAVA_FLOAT, METRICS_CAP_HEIGHT));
        }
    }

    // --- plumbing ----------------------------------------------------------

    /// `BLResult f(...)` — every Blend2D function has this return type.
    private static FunctionDescriptor resultOf(java.lang.foreign.MemoryLayout... arguments) {
        return FunctionDescriptor.of(ValueLayout.JAVA_INT, arguments);
    }

    private static int invoke(MethodHandle handle, MemorySegment argument) {
        try {
            return (int) handle.invokeExact(argument);
        } catch (Throwable t) {
            throw failure("a Blend2D call", t);
        }
    }

    private static int invoke(MethodHandle handle, MemorySegment first, MemorySegment second) {
        try {
            return (int) handle.invokeExact(first, second);
        } catch (Throwable t) {
            throw failure("a Blend2D call", t);
        }
    }

    private static int invoke(MethodHandle handle, int first, MemorySegment second) {
        try {
            return (int) handle.invokeExact(first, second);
        } catch (Throwable t) {
            throw failure("a Blend2D call", t);
        }
    }

    private static int invoke(
            MethodHandle handle, MemorySegment first, MemorySegment second, MemorySegment third) {
        try {
            return (int) handle.invokeExact(first, second, third);
        } catch (Throwable t) {
            throw failure("a Blend2D call", t);
        }
    }

    /// Turns a `BLResult` into an exception unless it is `BL_SUCCESS`.
    private static void check(String operation, int result) {
        if (result != 0) {
            throw new BlendException(operation, result);
        }
    }

    /// A failed downcall — a descriptor that does not match the signature, not a
    /// Blend2D error. Blend2D reports its own failures through `BLResult`.
    private static IllegalStateException failure(String name, Throwable cause) {
        return new IllegalStateException(name + " could not be invoked", cause);
    }

    // Restricted: see GoldberryShim.downcall -- same obligation, same reason.
    @SuppressWarnings("restricted")
    private static MethodHandle downcall(SymbolLookup lookup, String symbol, FunctionDescriptor descriptor) {
        var address = lookup.find(symbol).orElseThrow(() -> new UnsatisfiedLinkError(
                "libgoldberry does not export " + symbol
                        + " — is it listed in natives/src/main/cmake/exports/goldberry.symbols?"));
        return LINKER.downcallHandle(address, descriptor);
    }
}
