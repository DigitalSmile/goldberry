package io.github.digitalsmile.goldberry.natives.blend2d;

import io.github.digitalsmile.goldberry.natives.layout.Layouts;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Objects;

/// A font, as Blend2D sees it: a typeface plus the size it is drawn at.
///
/// Blend2D splits a font into three objects — data (the file's bytes), a face
/// (the typeface inside it), and a font (the face at a size) — which is the same
/// three-way split HarfBuzz makes, for the same reasons and with entirely
/// separate objects. This owns all three and closes them together.
///
/// **This is not the shaper's font.** `ShapedFont` decides *which* glyphs to
/// draw and where; this one draws them. Both are built over the same bytes and
/// neither knows about the other, which `docs/ARCHITECTURE.md` §6 asks for and
/// ADR-0034 explains the cost of: the two agree on nothing automatically, so the
/// units they exchange have to be stated rather than assumed.
///
/// ## The size, and what it means for a glyph run
///
/// The size given here is what produces the **font matrix**, `size /
/// units-per-em`, and Blend2D multiplies every glyph placement in an
/// [BlendGlyphPlacementType#ADVANCE_OFFSET] run by it. So a run handed to
/// [BlendContext#fillGlyphRun] must be in **font design units** — the units
/// HarfBuzz reports when nothing has set a scale on it. Setting a scale on the
/// shaping font *and* a size here applies the size twice.
///
/// The size is in the rendering context's units, so on a scaled context it is
/// logical: a 16-point font is 16 points on every display, and the context's
/// transform is what turns that into 24 physical pixels at 150%.
///
/// ## Ownership
///
/// The font's bytes are copied into an arena this object owns, and Blend2D is
/// pointed at them rather than given them: `bl_font_data_create_from_data` takes
/// external data, and the destroy callback is NULL because the memory is Java's.
/// The copy is what makes that safe — a caller's `byte[]` has no address the
/// collector will leave alone.
///
/// Confined to the thread that created it, and must be closed.
public final class BlendFont implements AutoCloseable {

    private final Blend2D blend2d = Blend2D.get();
    private final Thread owner = Thread.currentThread();
    private final Arena arena;
    private final MemorySegment fontData;
    private final MemorySegment face;
    private final MemorySegment font;
    private final float size;

    private boolean closed;

    private BlendFont(byte[] data, int faceIndex, float size) {
        this.size = size;
        this.arena = Arena.ofConfined();

        // Allocated one step at a time, and unwound one step at a time, because
        // each `create` can fail and a half-built chain still holds native
        // objects that have to be destroyed in the right order.
        var stage = 0;
        MemorySegment stagedData = null;
        MemorySegment stagedFace = null;
        MemorySegment stagedFont = null;
        try {
            var bytes = arena.allocate(ValueLayout.JAVA_BYTE, data.length);
            MemorySegment.copy(data, 0, bytes, ValueLayout.JAVA_BYTE, 0, data.length);

            stagedData = arena.allocate(Layouts.BL_OBJECT_DETAIL.layout());
            blend2d.fontDataInit(stagedData);
            stage = 1;
            blend2d.fontDataCreate(stagedData, bytes, data.length);

            stagedFace = arena.allocate(Layouts.BL_OBJECT_DETAIL.layout());
            blend2d.fontFaceInit(stagedFace);
            stage = 2;
            blend2d.fontFaceCreate(stagedFace, stagedData, faceIndex);

            stagedFont = arena.allocate(Layouts.BL_OBJECT_DETAIL.layout());
            blend2d.fontInit(stagedFont);
            stage = 3;
            blend2d.fontCreate(stagedFont, stagedFace, size);
        } catch (RuntimeException | Error e) {
            unwind(stage, stagedData, stagedFace, stagedFont);
            arena.close();
            throw e;
        }

        this.fontData = stagedData;
        this.face = stagedFace;
        this.font = stagedFont;
    }

    /// Loads a font from the bytes of a font file, at `size` units per em.
    ///
    /// @param data      a font file's contents, copied rather than referenced
    /// @param faceIndex which face, for a collection; 0 for an ordinary font
    /// @param size      the em size, in the rendering context's units
    /// @throws BlendException if the bytes are not a font Blend2D can read —
    ///         unlike HarfBuzz, which hands back an empty face instead
    public static BlendFont fromBytes(byte[] data, int faceIndex, double size) {
        Objects.requireNonNull(data, "data");
        if (data.length == 0) {
            throw new IllegalArgumentException("a font file with no bytes in it is not a font");
        }
        if (faceIndex < 0) {
            throw new IllegalArgumentException("face index must not be negative: " + faceIndex);
        }
        if (!Double.isFinite(size) || size <= 0) {
            throw new IllegalArgumentException(
                    "a font size must be a positive, finite number of units per em, and "
                            + size + " is not");
        }
        return new BlendFont(data, faceIndex, (float) size);
    }

    /// @see #fromBytes(byte[], int, double)
    public static BlendFont fromBytes(byte[] data, double size) {
        return fromBytes(data, 0, size);
    }

    /// The em size this font was created at.
    public float size() {
        return size;
    }

    /// The font's metrics at that size, already scaled.
    public BlendFontMetrics metrics() {
        return blend2d.fontMetrics(pointer());
    }

    /// Whether the font has been closed.
    public boolean isClosed() {
        return closed;
    }

    /// Releases all three objects, then the bytes they were reading.
    ///
    /// Order matters: the bytes are freed by closing the arena, and Blend2D's
    /// font data still points at them until it is destroyed.
    @Override
    public void close() {
        if (closed) {
            return;
        }
        requireOwner();
        closed = true;
        try {
            unwind(3, fontData, face, font);
        } finally {
            arena.close();
        }
    }

    MemorySegment pointer() {
        requireOwner();
        if (closed) {
            throw new IllegalStateException("this BlendFont has been closed");
        }
        return font;
    }

    /// Destroys whichever of the three objects were successfully created,
    /// innermost first.
    ///
    /// `stage` is how far construction got: 1 means the data was initialised, 2
    /// that the face was too, 3 that all three were. An object that was `init`ed
    /// but whose `create` failed still holds Blend2D's default instance and must
    /// be destroyed — which is why the stage counter is incremented before the
    /// create rather than after it.
    private void unwind(
            int stage, MemorySegment fontData, MemorySegment face, MemorySegment font) {
        if (stage >= 3) {
            destroyQuietly(() -> blend2d.fontDestroy(font));
        }
        if (stage >= 2) {
            destroyQuietly(() -> blend2d.fontFaceDestroy(face));
        }
        if (stage >= 1) {
            destroyQuietly(() -> blend2d.fontDataDestroy(fontData));
        }
    }

    /// Releases one object without letting its failure hide another's.
    ///
    /// A destroy that fails leaves nothing a caller could do about it, and
    /// throwing here would skip the two objects below it in the chain — trading
    /// a report for a leak.
    private static void destroyQuietly(Runnable destroy) {
        try {
            destroy.run();
        } catch (RuntimeException | Error ignored) {
            // Nothing above can act on this, and stopping here would leak the
            // rest of the chain.
        }
    }

    private void requireOwner() {
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException(
                    "a BlendFont belongs to the thread that created it, and this is not it");
        }
    }

    @Override
    public String toString() {
        return "BlendFont[" + size + "px" + (closed ? ", closed" : "") + "]";
    }
}
