package io.github.digitalsmile.goldberry.natives.blend2d;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;

import io.github.digitalsmile.goldberry.natives.NativeLibrary;
import io.github.digitalsmile.goldberry.natives.blend2d.calls.FontCalls;
import io.github.digitalsmile.goldberry.natives.blend2d.error.BlendException;
import io.github.digitalsmile.goldberry.natives.layout.Layouts;

/// Blend2D's font calls, behind [BlendFont] and [BlendFontFace].
///
/// Three objects rather than one: the bytes, the face read out of them, and
/// the face at a size. Each `create` **replaces** what its handle holds, so
/// each handle is `init`ed first.
final class Blend2dFont {

    private static final long METRICS_SIZE = Layouts.BL_FONT_METRICS.offsetOf("size");

    private static final long METRICS_ASCENT = Layouts.BL_FONT_METRICS.offsetOf("ascent");

    private static final long METRICS_DESCENT = Layouts.BL_FONT_METRICS.offsetOf("descent");

    private static final long METRICS_LINE_GAP = Layouts.BL_FONT_METRICS.offsetOf("line_gap");

    private static final long METRICS_X_HEIGHT = Layouts.BL_FONT_METRICS.offsetOf("x_height");

    private static final long METRICS_CAP_HEIGHT = Layouts.BL_FONT_METRICS.offsetOf("cap_height");

    private static final class Holder {
        private static final Blend2dFont INSTANCE =
                new Blend2dFont(NativeLibrary.get().lookup());
    }

    private final FontCalls calls;

    private Blend2dFont(SymbolLookup lookup) {
        this.calls = FontCalls.bind(lookup);
    }

    static Blend2dFont get() {
        return Holder.INSTANCE;
    }

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
        result = calls.fontDataCreateFromData().call(fontData, bytes, length, MemorySegment.NULL, MemorySegment.NULL);
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

    /// A `BLResult` that is not `BL_SUCCESS` is the call reporting a problem, not
    /// the crossing failing -- so it is raised as a [BlendException] naming the
    /// operation, and not as the [IllegalStateException] a holder raises.
    private static void check(String operation, int result) {
        if (result != 0) {
            throw new BlendException(operation, result);
        }
    }
}
