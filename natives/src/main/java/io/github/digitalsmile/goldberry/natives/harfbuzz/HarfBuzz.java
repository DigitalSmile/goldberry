package io.github.digitalsmile.goldberry.natives.harfbuzz;

import io.github.digitalsmile.goldberry.natives.harfbuzz.calls.HarfBuzzCalls;
import io.github.digitalsmile.goldberry.natives.NativeLibrary;
import io.github.digitalsmile.goldberry.natives.layout.Layouts;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import io.github.digitalsmile.goldberry.natives.harfbuzz.enums.MemoryMode;
import io.github.digitalsmile.goldberry.natives.harfbuzz.enums.TextDirection;

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

    private final HarfBuzzCalls calls;

    private HarfBuzz(SymbolLookup lookup) {
        this.calls = HarfBuzzCalls.bind(lookup);
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
            calls.version().call(major, minor, micro);
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
        return calls.blobCreate().call(data, length, MemoryMode.DUPLICATE.nativeValue(),
                MemorySegment.NULL, MemorySegment.NULL);
    }

    void blobDestroy(MemorySegment blob) {
        calls.blobDestroy().call(blob);
    }

    MemorySegment faceCreate(MemorySegment blob, int index) {
        return calls.faceCreate().call(blob, index);
    }

    void faceDestroy(MemorySegment face) {
        calls.faceDestroy().call(face);
    }

    /// HarfBuzz's immortal empty face — a valid face with no glyphs.
    ///
    /// It is a singleton HarfBuzz owns, so it must **not** be destroyed. That is
    /// why [ShapedFont] tracks whether its face was borrowed.
    MemorySegment faceEmpty() {
        return calls.faceGetEmpty().call();
    }

    /// The face's units per em — the grid its outlines are designed on.
    ///
    /// It is also the scale HarfBuzz reports advances in when nothing has set
    /// one, which is what makes it the number the Blend2D side has to agree
    /// with (ADR-0034). Commonly 1000 for a PostScript-flavoured face and 2048
    /// for a TrueType one, and free to be anything.
    int faceUpem(MemorySegment face) {
        return calls.faceGetUpem().call(face);
    }

    MemorySegment fontCreate(MemorySegment face) {
        return calls.fontCreate().call(face);
    }

    void fontDestroy(MemorySegment font) {
        calls.fontDestroy().call(font);
    }

    void fontScale(MemorySegment font, int xScale, int yScale) {
        calls.fontSetScale().call(font, xScale, yScale);
    }

    // --- buffers -----------------------------------------------------------

    MemorySegment bufferCreate() {
        return calls.bufferCreate().call();
    }

    void bufferDestroy(MemorySegment buffer) {
        calls.bufferDestroy().call(buffer);
    }

    void bufferReset(MemorySegment buffer) {
        calls.bufferReset().call(buffer);
    }

    /// Adds UTF-16 code units, with context either side of the part to shape.
    ///
    /// `itemOffset` and `itemLength` select what is *shaped*; everything else in
    /// `text` is context the shaper may look at for joining behaviour but does
    /// not produce glyphs for. That distinction is what makes shaping one word
    /// of a paragraph give the same result as shaping the paragraph.
    ///
    /// UTF-16 is why this is the natural entry point: a Java String already is
    /// UTF-16, so the text crosses without being transcoded.
    void bufferAddUtf16(
            MemorySegment buffer, MemorySegment text, int textLength, int itemOffset, int itemLength) {
        calls.bufferAddUtf16().call(buffer, text, textLength, itemOffset, itemLength);
    }

    void bufferGuessSegmentProperties(MemorySegment buffer) {
        calls.bufferGuessSegmentProperties().call(buffer);
    }

    void bufferDirection(MemorySegment buffer, TextDirection direction) {
        calls.bufferSetDirection().call(buffer, direction.nativeValue());
    }

    TextDirection bufferDirection(MemorySegment buffer) {
        int value;
        value = calls.bufferGetDirection().call(buffer);
        return TextDirection.of(value);
    }

    void bufferScript(MemorySegment buffer, int scriptTag) {
        calls.bufferSetScript().call(buffer, scriptTag);
    }

    void bufferLanguage(MemorySegment buffer, MemorySegment language) {
        calls.bufferSetLanguage().call(buffer, language);
    }

    int bufferLength(MemorySegment buffer) {
        return calls.bufferGetLength().call(buffer);
    }

    /// A four-character script tag, e.g. `Latn`.
    int scriptFromString(MemorySegment name, int length) {
        return calls.scriptFromString().call(name, length);
    }

    /// An `hb_language_t`, which is an interned pointer HarfBuzz owns forever
    /// and that must not be freed.
    MemorySegment languageFromString(MemorySegment name, int length) {
        return calls.languageFromString().call(name, length);
    }

    // --- shaping -----------------------------------------------------------

    /// Shapes `buffer` with `font`.
    ///
    /// Features are NULL/0 until the CSS layer has `font-feature-settings` to
    /// compile into them.
    void shape(MemorySegment font, MemorySegment buffer) {
        calls.shape().call(font, buffer, MemorySegment.NULL, 0);
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

        var infos = calls.bufferGetGlyphInfos().call(buffer, MemorySegment.NULL);
        var positions = calls.bufferGetGlyphPositions().call(buffer, MemorySegment.NULL);

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

    private static IllegalStateException failure(String name, Throwable cause) {
        return new IllegalStateException(name + "() failed", cause);
    }
}
