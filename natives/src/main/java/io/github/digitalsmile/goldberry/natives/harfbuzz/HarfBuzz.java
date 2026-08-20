package io.github.digitalsmile.goldberry.natives.harfbuzz;

import io.github.digitalsmile.goldberry.natives.Downcalls;
import io.github.digitalsmile.goldberry.natives.NativeLibrary;
import io.github.digitalsmile.goldberry.natives.layout.Layouts;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;

/// HarfBuzz's shaping calls.
///
/// Package-private, like Yoga's and Blend2D's binding classes: [ShapedFont] and
/// [ShapingBuffer] are the only way in, so nothing can reach `hb_font_destroy`
/// without the wrapper that knows whether the font is still alive.
///
/// HarfBuzz reports almost nothing. Most functions return void, and the ones
/// that can fail return an object that is *empty* rather than null —
/// `hb_face_create` on nonsense bytes gives a face with no glyphs, not an
/// error. So the checking here is about arguments, before the call.
final class HarfBuzz {

    static final long GLYPH_INFO_STRIDE = Layouts.HB_GLYPH_INFO.byteSize();
    static final long GLYPH_POSITION_STRIDE = Layouts.HB_GLYPH_POSITION.byteSize();

    private static final long INFO_CODEPOINT = Layouts.HB_GLYPH_INFO.offsetOf("codepoint");
    private static final long INFO_CLUSTER = Layouts.HB_GLYPH_INFO.offsetOf("cluster");
    private static final long POS_X_ADVANCE = Layouts.HB_GLYPH_POSITION.offsetOf("x_advance");
    private static final long POS_Y_ADVANCE = Layouts.HB_GLYPH_POSITION.offsetOf("y_advance");
    private static final long POS_X_OFFSET = Layouts.HB_GLYPH_POSITION.offsetOf("x_offset");
    private static final long POS_Y_OFFSET = Layouts.HB_GLYPH_POSITION.offsetOf("y_offset");

    private static final class Holder {
        private static final HarfBuzz INSTANCE = new HarfBuzz(NativeLibrary.get().lookup());
    }

    private final MemorySegment version;
    private final MemorySegment blobCreate;
    private final MemorySegment blobDestroy;
    private final MemorySegment faceCreate;
    private final MemorySegment faceDestroy;
    private final MemorySegment faceGetEmpty;
    private final MemorySegment faceGetUpem;
    private final MemorySegment fontCreate;
    private final MemorySegment fontDestroy;
    private final MemorySegment fontSetScale;
    private final MemorySegment bufferCreate;
    private final MemorySegment bufferDestroy;
    private final MemorySegment bufferReset;
    private final MemorySegment bufferAddUtf16;
    private final MemorySegment bufferGuessSegmentProperties;
    private final MemorySegment bufferSetDirection;
    private final MemorySegment bufferGetDirection;
    private final MemorySegment bufferSetScript;
    private final MemorySegment bufferSetLanguage;
    private final MemorySegment bufferGetLength;
    private final MemorySegment bufferGetGlyphInfos;
    private final MemorySegment bufferGetGlyphPositions;
    private final MemorySegment scriptFromString;
    private final MemorySegment languageFromString;
    private final MemorySegment shape;

    private HarfBuzz(SymbolLookup lookup) {
        this.version = Downcalls.symbol(lookup, "hb_version");

        // hb_blob_t *hb_blob_create(const char *data, unsigned int length,
        //     hb_memory_mode_t mode, void *user_data, hb_destroy_func_t destroy)
        this.blobCreate = Downcalls.symbol(lookup, "hb_blob_create");
        this.blobDestroy = Downcalls.symbol(lookup, "hb_blob_destroy");
        this.faceCreate = Downcalls.symbol(lookup, "hb_face_create");
        this.faceDestroy = Downcalls.symbol(lookup, "hb_face_destroy");
        this.faceGetEmpty = Downcalls.symbol(lookup, "hb_face_get_empty");
        this.faceGetUpem = Downcalls.symbol(lookup, "hb_face_get_upem");
        this.fontCreate = Downcalls.symbol(lookup, "hb_font_create");
        this.fontDestroy = Downcalls.symbol(lookup, "hb_font_destroy");
        this.fontSetScale = Downcalls.symbol(lookup, "hb_font_set_scale");

        this.bufferCreate = Downcalls.symbol(lookup, "hb_buffer_create");
        this.bufferDestroy = Downcalls.symbol(lookup, "hb_buffer_destroy");
        this.bufferReset = Downcalls.symbol(lookup, "hb_buffer_reset");
        // void hb_buffer_add_utf16(hb_buffer_t*, const uint16_t *text,
        //     int text_length, unsigned int item_offset, int item_length)
        //
        // UTF-16 is why this is the natural entry point: a Java String already
        // is UTF-16, so the text crosses without being transcoded.
        this.bufferAddUtf16 = Downcalls.symbol(lookup, "hb_buffer_add_utf16");
        this.bufferGuessSegmentProperties =
                Downcalls.symbol(lookup, "hb_buffer_guess_segment_properties");
        this.bufferSetDirection = Downcalls.symbol(lookup, "hb_buffer_set_direction");
        this.bufferGetDirection = Downcalls.symbol(lookup, "hb_buffer_get_direction");
        this.bufferSetScript = Downcalls.symbol(lookup, "hb_buffer_set_script");
        this.bufferSetLanguage = Downcalls.symbol(lookup, "hb_buffer_set_language");
        this.bufferGetLength = Downcalls.symbol(lookup, "hb_buffer_get_length");
        this.bufferGetGlyphInfos = Downcalls.symbol(lookup, "hb_buffer_get_glyph_infos");
        this.bufferGetGlyphPositions = Downcalls.symbol(lookup, "hb_buffer_get_glyph_positions");

        this.scriptFromString = Downcalls.symbol(lookup, "hb_script_from_string");
        this.languageFromString = Downcalls.symbol(lookup, "hb_language_from_string");

        // void hb_shape(hb_font_t*, hb_buffer_t*, const hb_feature_t*, unsigned)
        // Features are NULL/0 until the CSS layer has font-feature-settings to
        // compile into them.
        this.shape = Downcalls.symbol(lookup, "hb_shape");
    }

    static HarfBuzz get() {
        return Holder.INSTANCE;
    }

    /// The HarfBuzz statically linked into `libgoldberry`.
    HarfBuzzVersion version() {
        try (var arena = Arena.ofConfined()) {
            var out = arena.allocate(ValueLayout.JAVA_INT, 3);
            var major = out.asSlice(0, 4);
            var minor = out.asSlice(4, 4);
            var micro = out.asSlice(8, 4);
            try {
                Downcalls.VOID__PTR_PTR_PTR.invokeExact(version, major, minor, micro);
            } catch (Throwable t) {
                throw failure("hb_version", t);
            }
            return new HarfBuzzVersion(
                    out.getAtIndex(ValueLayout.JAVA_INT, 0),
                    out.getAtIndex(ValueLayout.JAVA_INT, 1),
                    out.getAtIndex(ValueLayout.JAVA_INT, 2));
        }
    }

    // --- fonts -------------------------------------------------------------

    /// Copies `data` into a blob HarfBuzz owns.
    ///
    /// Always [MemoryMode#DUPLICATE]: the alternative is promising that a Java
    /// array's memory outlives the face, which nothing here can promise.
    MemorySegment blobCreate(MemorySegment data, int length) {
        try {
            return (MemorySegment) Downcalls.PTR__PTR_INT_INT_PTR_PTR.invokeExact(blobCreate,
                    data, length, MemoryMode.DUPLICATE.nativeValue(),
                    MemorySegment.NULL, MemorySegment.NULL);
        } catch (Throwable t) {
            throw failure("hb_blob_create", t);
        }
    }

    void blobDestroy(MemorySegment blob) {
        callVoid(blobDestroy, "hb_blob_destroy", blob);
    }

    MemorySegment faceCreate(MemorySegment blob, int index) {
        try {
            return (MemorySegment) Downcalls.PTR__PTR_INT.invokeExact(faceCreate, blob, index);
        } catch (Throwable t) {
            throw failure("hb_face_create", t);
        }
    }

    void faceDestroy(MemorySegment face) {
        callVoid(faceDestroy, "hb_face_destroy", face);
    }

    /// HarfBuzz's immortal empty face — a valid face with no glyphs.
    ///
    /// It is a singleton HarfBuzz owns, so it must **not** be destroyed. That is
    /// why [ShapedFont] tracks whether its face was borrowed.
    MemorySegment faceEmpty() {
        try {
            return (MemorySegment) Downcalls.PTR__VOID.invokeExact(faceGetEmpty);
        } catch (Throwable t) {
            throw failure("hb_face_get_empty", t);
        }
    }

    /// The face's units per em — the grid its outlines are designed on.
    ///
    /// It is also the scale HarfBuzz reports advances in when nothing has set
    /// one, which is what makes it the number the Blend2D side has to agree
    /// with (ADR-0034). Commonly 1000 for a PostScript-flavoured face and 2048
    /// for a TrueType one, and free to be anything.
    int faceUpem(MemorySegment face) {
        try {
            return (int) Downcalls.INT__PTR.invokeExact(faceGetUpem, face);
        } catch (Throwable t) {
            throw failure("hb_face_get_upem", t);
        }
    }

    MemorySegment fontCreate(MemorySegment face) {
        try {
            return (MemorySegment) Downcalls.PTR__PTR.invokeExact(fontCreate, face);
        } catch (Throwable t) {
            throw failure("hb_font_create", t);
        }
    }

    void fontDestroy(MemorySegment font) {
        callVoid(fontDestroy, "hb_font_destroy", font);
    }

    void fontScale(MemorySegment font, int xScale, int yScale) {
        try {
            Downcalls.VOID__PTR_INT_INT.invokeExact(fontSetScale, font, xScale, yScale);
        } catch (Throwable t) {
            throw failure("hb_font_set_scale", t);
        }
    }

    // --- buffers -----------------------------------------------------------

    MemorySegment bufferCreate() {
        try {
            return (MemorySegment) Downcalls.PTR__VOID.invokeExact(bufferCreate);
        } catch (Throwable t) {
            throw failure("hb_buffer_create", t);
        }
    }

    void bufferDestroy(MemorySegment buffer) {
        callVoid(bufferDestroy, "hb_buffer_destroy", buffer);
    }

    void bufferReset(MemorySegment buffer) {
        callVoid(bufferReset, "hb_buffer_reset", buffer);
    }

    /// Adds UTF-16 code units, with context either side of the part to shape.
    ///
    /// `itemOffset` and `itemLength` select what is *shaped*; everything else in
    /// `text` is context the shaper may look at for joining behaviour but does
    /// not produce glyphs for. That distinction is what makes shaping one word
    /// of a paragraph give the same result as shaping the paragraph.
    void bufferAddUtf16(
            MemorySegment buffer, MemorySegment text, int textLength, int itemOffset, int itemLength) {
        try {
            Downcalls.VOID__PTR_PTR_INT_INT_INT.invokeExact(
                    bufferAddUtf16, buffer, text, textLength, itemOffset, itemLength);
        } catch (Throwable t) {
            throw failure("hb_buffer_add_utf16", t);
        }
    }

    void bufferGuessSegmentProperties(MemorySegment buffer) {
        callVoid(bufferGuessSegmentProperties, "hb_buffer_guess_segment_properties", buffer);
    }

    void bufferDirection(MemorySegment buffer, TextDirection direction) {
        try {
            Downcalls.VOID__PTR_INT.invokeExact(
                    bufferSetDirection, buffer, direction.nativeValue());
        } catch (Throwable t) {
            throw failure("hb_buffer_set_direction", t);
        }
    }

    TextDirection bufferDirection(MemorySegment buffer) {
        int value;
        try {
            value = (int) Downcalls.INT__PTR.invokeExact(bufferGetDirection, buffer);
        } catch (Throwable t) {
            throw failure("hb_buffer_get_direction", t);
        }
        return TextDirection.of(value);
    }

    void bufferScript(MemorySegment buffer, int scriptTag) {
        try {
            Downcalls.VOID__PTR_INT.invokeExact(bufferSetScript, buffer, scriptTag);
        } catch (Throwable t) {
            throw failure("hb_buffer_set_script", t);
        }
    }

    void bufferLanguage(MemorySegment buffer, MemorySegment language) {
        try {
            Downcalls.VOID__PTR_PTR.invokeExact(bufferSetLanguage, buffer, language);
        } catch (Throwable t) {
            throw failure("hb_buffer_set_language", t);
        }
    }

    int bufferLength(MemorySegment buffer) {
        try {
            return (int) Downcalls.INT__PTR.invokeExact(bufferGetLength, buffer);
        } catch (Throwable t) {
            throw failure("hb_buffer_get_length", t);
        }
    }

    /// A four-character script tag, e.g. `Latn`.
    int scriptFromString(MemorySegment name, int length) {
        try {
            return (int) Downcalls.INT__PTR_INT.invokeExact(scriptFromString, name, length);
        } catch (Throwable t) {
            throw failure("hb_script_from_string", t);
        }
    }

    /// An `hb_language_t`, which is an interned pointer HarfBuzz owns forever
    /// and that must not be freed.
    MemorySegment languageFromString(MemorySegment name, int length) {
        try {
            return (MemorySegment) Downcalls.PTR__PTR_INT.invokeExact(
                    languageFromString, name, length);
        } catch (Throwable t) {
            throw failure("hb_language_from_string", t);
        }
    }

    // --- shaping -----------------------------------------------------------

    void shape(MemorySegment font, MemorySegment buffer) {
        try {
            Downcalls.VOID__PTR_PTR_PTR_INT.invokeExact(shape, font, buffer, MemorySegment.NULL, 0);
        } catch (Throwable t) {
            throw failure("hb_shape", t);
        }
    }

    /// Reads the shaped glyphs out of HarfBuzz's own arrays.
    ///
    /// The two pointers are into memory the buffer owns and reuses, so the data
    /// is copied into Java arrays here rather than handed out — a caller holding
    /// the raw arrays across a later `reset` would be reading the next run's
    /// glyphs.
    GlyphRun readGlyphs(MemorySegment buffer) {
        var count = bufferLength(buffer);
        if (count == 0) {
            return GlyphRun.EMPTY;
        }

        MemorySegment infos;
        MemorySegment positions;
        try {
            infos = (MemorySegment) Downcalls.PTR__PTR_PTR.invokeExact(
                    bufferGetGlyphInfos, buffer, MemorySegment.NULL);
            positions = (MemorySegment) Downcalls.PTR__PTR_PTR.invokeExact(bufferGetGlyphPositions,
                    buffer, MemorySegment.NULL);
        } catch (Throwable t) {
            throw failure("hb_buffer_get_glyph_infos", t);
        }

        // Both arrive as zero-length segments -- a bare pointer carries no
        // extent -- so they are resized to exactly the stride the layout table
        // verified, times the count HarfBuzz just reported.
        var infoArray = resize(infos, GLYPH_INFO_STRIDE * count);
        var positionArray = resize(positions, GLYPH_POSITION_STRIDE * count);

        var glyphIds = new int[count];
        var clusters = new int[count];
        var xAdvances = new int[count];
        var yAdvances = new int[count];
        var xOffsets = new int[count];
        var yOffsets = new int[count];

        for (var i = 0; i < count; i++) {
            var infoAt = i * GLYPH_INFO_STRIDE;
            glyphIds[i] = infoArray.get(ValueLayout.JAVA_INT, infoAt + INFO_CODEPOINT);
            clusters[i] = infoArray.get(ValueLayout.JAVA_INT, infoAt + INFO_CLUSTER);

            var posAt = i * GLYPH_POSITION_STRIDE;
            xAdvances[i] = positionArray.get(ValueLayout.JAVA_INT, posAt + POS_X_ADVANCE);
            yAdvances[i] = positionArray.get(ValueLayout.JAVA_INT, posAt + POS_Y_ADVANCE);
            xOffsets[i] = positionArray.get(ValueLayout.JAVA_INT, posAt + POS_X_OFFSET);
            yOffsets[i] = positionArray.get(ValueLayout.JAVA_INT, posAt + POS_Y_OFFSET);
        }

        return new GlyphRun(glyphIds, clusters, xAdvances, yAdvances, xOffsets, yOffsets);
    }

    // --- plumbing ----------------------------------------------------------

    // Restricted: a pointer returned from C carries no extent, so it has to be
    // resized before anything can be read through it. The extent here is the
    // stride the layout table verified times the count HarfBuzz reported --
    // which is the only region it is entitled to.
    @SuppressWarnings("restricted")
    private static MemorySegment resize(MemorySegment pointer, long bytes) {
        return pointer.reinterpret(bytes);
    }

    private static void callVoid(MemorySegment function, String name, MemorySegment argument) {
        try {
            Downcalls.VOID__PTR.invokeExact(function, argument);
        } catch (Throwable t) {
            throw failure(name, t);
        }
    }

    private static IllegalStateException failure(String name, Throwable cause) {
        return new IllegalStateException(name + "() failed", cause);
    }
}
