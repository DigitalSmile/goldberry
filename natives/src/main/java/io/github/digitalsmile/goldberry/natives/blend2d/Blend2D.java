package io.github.digitalsmile.goldberry.natives.blend2d;

import io.github.digitalsmile.goldberry.natives.blend2d.calls.Blend2DCalls;
import io.github.digitalsmile.goldberry.natives.NativeLibrary;
import io.github.digitalsmile.goldberry.natives.layout.Layouts;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import io.github.digitalsmile.goldberry.natives.blend2d.enums.BlendCompOp;
import io.github.digitalsmile.goldberry.natives.blend2d.enums.BlendDataAccess;
import io.github.digitalsmile.goldberry.natives.blend2d.enums.BlendFormat;
import io.github.digitalsmile.goldberry.natives.blend2d.enums.BlendGlyphPlacementType;
import io.github.digitalsmile.goldberry.natives.blend2d.enums.BlendRuntimeInfoType;
import io.github.digitalsmile.goldberry.natives.blend2d.enums.BlendStrokeCap;
import io.github.digitalsmile.goldberry.natives.blend2d.enums.BlendStrokeJoin;
import io.github.digitalsmile.goldberry.natives.blend2d.enums.BlendTransformOp;
import io.github.digitalsmile.goldberry.natives.blend2d.error.BlendException;

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

    private final Blend2DCalls calls;

    private Blend2D(SymbolLookup lookup) {
        this.calls = Blend2DCalls.bind(lookup);
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
            check("bl_runtime_query_info", calls.runtimeQueryInfo().call(BlendRuntimeInfoType.BUILD.nativeValue(), info));

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
    /// `BLResult bl_image_init_as_from_data(BLImageCore*, int w, int h, BLFormat,`
    /// `void* pixel_data, intptr_t stride, BLDataAccessFlags,`
    /// `BLDestroyExternalDataFunc, void* user_data)`
    ///
    /// `intptr_t` is 8 bytes on every target here — the "pointer" scalar row is
    /// what says so. It is signed: a negative stride means the image starts at
    /// the bottom-left, which Goldberry never produces but must not silently
    /// reinterpret.
    void imageInitFromData(
            MemorySegment image, int width, int height, BlendFormat format,
            MemorySegment pixels, long stride) {

        int result;
        result = calls.imageInitAsFromData().call(image, width, height, format.nativeValue(),
                pixels, stride, BlendDataAccess.READ_WRITE.nativeValue(), MemorySegment.NULL,
                MemorySegment.NULL);
        check("bl_image_init_as_from_data", result);
    }

    void imageDestroy(MemorySegment image) {
        check("bl_image_destroy", calls.imageDestroy().call(image));
    }

    /// Reads back where Blend2D thinks the pixels are.
    ///
    /// Used by the tests rather than by the paint path: it is how "the image
    /// really is a view over the buffer we passed" becomes an assertion about
    /// an address rather than a claim in a comment.
    ImageData imageData(MemorySegment image) {
        try (var arena = Arena.ofConfined()) {
            var data = arena.allocate(Layouts.BL_IMAGE_DATA.layout());
            check("bl_image_get_data", calls.imageGetData().call(image, data));
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
            check("bl_context_apply_transform_op",
                    calls.contextApplyTransformOp().call(
                            context, op.nativeValue(), point));
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
        check("bl_context_apply_transform_op",
                calls.contextApplyTransformOp().call(
                        context, BlendTransformOp.ASSIGN.nativeValue(), matrix));
    }

    void contextCompOp(MemorySegment context, BlendCompOp compOp) {
        check("bl_context_set_comp_op",
                calls.contextSetCompOp().call(context,
                        compOp.nativeValue()));
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
        check("bl_context_fill_rect_d_rgba32",
                calls.contextFillRectDRgba32().call(
                        context, rect, argb));
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
            MemorySegment context, MemorySegment origin, MemorySegment font,
            MemorySegment glyphRun, int argb) {
        check("bl_context_fill_glyph_run_d_rgba32",
                calls.contextFillGlyphRunDRgba32().call(
                        context, origin, font, glyphRun, argb));
    }

    // --- paths and strokes (ADR-0043) -----------------------------------------
    //
    // Every path command is (BLPathCore*, doubles...) returning BLResult, which
    // is what makes this a long list of near-identical rows rather than a design.

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

    void contextStrokePath(
            MemorySegment context, MemorySegment origin, MemorySegment path, int argb) {
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
    /// `bl_context_restore_clipping` rather than a save/restore pair around the
    /// clip: Blend2D's `bl_context_save` is not exported and does not need to be,
    /// because there is only ever one clip depth in this frame path.
    void contextRestoreClipping(MemorySegment context) {
        int result;
        result = calls.contextRestoreClipping().call(context);
        check("bl_context_restore_clipping", result);
    }

    void pathInit(MemorySegment path) {
        check("bl_path_init", calls.pathInit().call(path));
    }

    void pathDestroy(MemorySegment path) {
        check("bl_path_destroy", calls.pathDestroy().call(path));
    }

    void pathReset(MemorySegment path) {
        check("bl_path_reset", calls.pathReset().call(path));
    }

    /// How many vertices the path holds. Used by the tests, which is how "the
    /// parser really issued the commands" becomes a number rather than a claim.
    /// `size_t`, not `BLResult` — the one path call that is not an operation.
    long pathSize(MemorySegment path) {
        return calls.pathGetSize().call(path);
    }

    void pathMoveTo(MemorySegment path, double x, double y) {
        int result;
        result = calls.pathMoveTo().call(path, x, y);
        check("bl_path_move_to", result);
    }

    void pathLineTo(MemorySegment path, double x, double y) {
        int result;
        result = calls.pathLineTo().call(path, x, y);
        check("bl_path_line_to", result);
    }

    void pathQuadTo(MemorySegment path, double x1, double y1, double x2, double y2) {
        int result;
        result = calls.pathQuadTo().call(path, x1, y1, x2, y2);
        check("bl_path_quad_to", result);
    }

    void pathCubicTo(
            MemorySegment path,
            double x1, double y1, double x2, double y2, double x3, double y3) {
        int result;
        result = calls.pathCubicTo().call(path, x1, y1, x2, y2, x3, y3);
        check("bl_path_cubic_to", result);
    }

    /// SVG's `S` and `T`: the first control point is the reflection of the
    /// previous one. Blend2D does that reflection itself, against the command it
    /// actually recorded — which is the definition SVG gives, and not the one a
    /// caller tracking "the last control point" in Java would arrive at after a
    /// `Z` or a bare `M`.
    void pathSmoothQuadTo(MemorySegment path, double x2, double y2) {
        int result;
        result = calls.pathSmoothQuadTo().call(path, x2, y2);
        check("bl_path_smooth_quad_to", result);
    }

    void pathSmoothCubicTo(MemorySegment path, double x2, double y2, double x3, double y3) {
        int result;
        result = calls.pathSmoothCubicTo().call(path, x2, y2, x3, y3);
        check("bl_path_smooth_cubic_to", result);
    }

    /// `BLResult bl_path_elliptic_arc_to(BLPathCore*, double rx, double ry,`
    /// `double x_axis_rotation, bool large_arc, bool sweep, double x1, double y1)`
    ///
    /// SVG's `A` command, argument for argument and flag for flag. The two
    /// `bool`s are C `_Bool`, one byte — `JAVA_BOOLEAN`, not `JAVA_INT`, which
    /// would put four bytes where the ABI expects one and shift every argument
    /// after them.
    void pathEllipticArcTo(
            MemorySegment path,
            double rx, double ry, double rotation, boolean largeArc, boolean sweep,
            double x, double y) {
        check("bl_path_elliptic_arc_to", calls.pathEllipticArcTo()
                .call(path, rx, ry, rotation, largeArc, sweep, x, y));
    }

    void pathClose(MemorySegment path) {
        check("bl_path_close", calls.pathClose().call(path));
    }

    // --- fonts ---------------------------------------------------------------

    /// The three font objects. Each `create` **replaces** what the handle holds,
    /// so each one has to be `init`ed first — Blend2D releases the previous
    /// instance, and releasing an uninitialised one reads a pointer that was never
    /// written.
    void fontDataInit(MemorySegment fontData) {
        check("bl_font_data_init", calls.fontDataInit().call(fontData));
    }

    /// Points `fontData` at a font file's bytes, which Blend2D does **not** copy.
    ///
    /// The destroy callback and its user data are NULL for the same reason
    /// [#imageInitFromData] passes NULL: the bytes belong to Java, and handing
    /// Blend2D a free function for memory it did not allocate is how a heap gets
    /// corrupted. The caller keeps them alive instead.
    /// `BLResult bl_font_data_create_from_data(BLFontDataCore*, const void* data,`
    /// `size_t data_size, BLDestroyExternalDataFunc, void* user_data)`
    void fontDataCreate(MemorySegment fontData, MemorySegment bytes, long length) {
        int result;
        result = calls.fontDataCreateFromData().call(fontData, bytes, length, MemorySegment.NULL,
                MemorySegment.NULL);
        check("bl_font_data_create_from_data", result);
    }

    void fontDataDestroy(MemorySegment fontData) {
        check("bl_font_data_destroy", calls.fontDataDestroy().call(fontData));
    }

    void fontFaceInit(MemorySegment face) {
        check("bl_font_face_init", calls.fontFaceInit().call(face));
    }

    /// Reads face `index` out of `fontData`.
    ///
    /// Unlike HarfBuzz, Blend2D **reports** a file it cannot parse: a corrupt or
    /// non-font blob fails here with a `BLResult` rather than producing an empty
    /// face that silently shapes to `.notdef`. That difference is worth knowing
    /// when the two disagree about the same bytes.
    void fontFaceCreate(MemorySegment face, MemorySegment fontData, int index) {
        int result;
        result = calls.fontFaceCreateFromData().call(face, fontData, index);
        check("bl_font_face_create_from_data", result);
    }

    void fontFaceDestroy(MemorySegment face) {
        check("bl_font_face_destroy", calls.fontFaceDestroy().call(face));
    }

    void fontInit(MemorySegment font) {
        check("bl_font_init", calls.fontInit().call(font));
    }

    /// Sizes `face` at `size` units per em.
    ///
    /// This is where the font matrix comes from — `size / units-per-em` — and
    /// therefore where the units of every glyph placement are decided. See
    /// [BlendGlyphPlacementType].
    /// The size is a `float`, not a double: Blend2D's own choice, and the one
    /// place in the paint path where a coordinate narrows.
    void fontCreate(MemorySegment font, MemorySegment face, float size) {
        int result;
        result = calls.fontCreateFromFace().call(font, face, size);
        check("bl_font_create_from_face", result);
    }

    void fontDestroy(MemorySegment font) {
        check("bl_font_destroy", calls.fontDestroy().call(font));
    }

    /// The font's metrics, already scaled by its size.
    BlendFontMetrics fontMetrics(MemorySegment font) {
        try (var arena = Arena.ofConfined()) {
            var metrics = arena.allocate(Layouts.BL_FONT_METRICS.layout());
            check("bl_font_get_metrics", calls.fontGetMetrics().call(font, metrics));
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
    //
    // Four helpers rather than four `try` blocks, for the shapes that carry no
    // arguments worth naming: `BLResult f(pointers...)`, which is most of
    // Blend2D. The rest call their [Downcalls] constant directly, because the
    // symbol's name belongs in the failure and these cannot give it one.

    /// Turns a `BLResult` into an exception unless it is `BL_SUCCESS`.
    private static void check(String operation, int result) {
        if (result != 0) {
            throw new BlendException(operation, result);
        }
    }

    /// A failed downcall — the arena that held an argument closed under the
    /// call, or a [Downcalls] constant that does not match the C prototype. Not
    /// a Blend2D error: Blend2D reports its own failures through `BLResult`.
    private static IllegalStateException failure(String name, Throwable cause) {
        return new IllegalStateException(name + " could not be invoked", cause);
    }
}
