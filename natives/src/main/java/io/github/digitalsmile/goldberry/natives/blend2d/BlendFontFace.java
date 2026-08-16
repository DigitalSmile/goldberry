package io.github.digitalsmile.goldberry.natives.blend2d;

import io.github.digitalsmile.goldberry.natives.layout.Layouts;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Objects;

/// A typeface, as Blend2D sees it — everything about a font except the size.
///
/// Blend2D splits a font into three objects: data (the file's bytes), a face
/// (the typeface inside it), and a font (the face at a size). The first two are
/// what a size-independent typeface *is*, and they are also the expensive two —
/// the bytes are a megabyte and a half for Inter, and the face is what parsing
/// them produces. This owns those two so that N sizes cost one copy rather than
/// N (ADR-0044).
///
/// ## Ownership
///
/// The bytes are copied into an arena this object owns, and Blend2D is pointed
/// at them rather than given them: `bl_font_data_create_from_data` takes external
/// data, and the destroy callback is NULL because the memory is Java's. The copy
/// is what makes that safe — a caller's `byte[]` has no address the collector
/// will leave alone.
///
/// **A face must outlive every [BlendFont] made from it.** Blend2D keeps a
/// reference, so closing the face first leaves a font reading memory that has
/// been unmapped. `:core`'s `FontFace` is what makes that ordering a matter of
/// scope rather than of discipline.
///
/// Confined to the thread that created it, and must be closed.
public final class BlendFontFace implements AutoCloseable {

    private final Blend2D blend2d = Blend2D.get();
    private final Thread owner = Thread.currentThread();
    private final Arena arena;
    private final MemorySegment fontData;
    private final MemorySegment face;

    private boolean closed;

    private BlendFontFace(byte[] data, int faceIndex) {
        this.arena = Arena.ofConfined();

        // Allocated one step at a time, and unwound one step at a time, because
        // each `create` can fail and a half-built chain still holds native
        // objects that have to be destroyed in the right order.
        var stage = 0;
        MemorySegment stagedData = null;
        MemorySegment stagedFace = null;
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
        } catch (RuntimeException | Error e) {
            unwind(stage, stagedData, stagedFace);
            arena.close();
            throw e;
        }

        this.fontData = stagedData;
        this.face = stagedFace;
    }

    /// Parses a typeface from the bytes of a font file.
    ///
    /// @param data      a font file's contents, copied rather than referenced
    /// @param faceIndex which face, for a collection; 0 for an ordinary font
    /// @throws BlendException if the bytes are not a font Blend2D can read —
    ///         unlike HarfBuzz, which hands back an empty face instead
    public static BlendFontFace fromBytes(byte[] data, int faceIndex) {
        Objects.requireNonNull(data, "data");
        if (data.length == 0) {
            throw new IllegalArgumentException("a font file with no bytes in it is not a font");
        }
        if (faceIndex < 0) {
            throw new IllegalArgumentException("face index must not be negative: " + faceIndex);
        }
        return new BlendFontFace(data, faceIndex);
    }

    /// @see #fromBytes(byte[], int)
    public static BlendFontFace fromBytes(byte[] data) {
        return fromBytes(data, 0);
    }

    /// Whether the face has been closed.
    public boolean isClosed() {
        return closed;
    }

    /// Releases the face and the data, then the bytes they were reading.
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
            unwind(2, fontData, face);
        } finally {
            arena.close();
        }
    }

    MemorySegment pointer() {
        requireOwner();
        if (closed) {
            throw new IllegalStateException("this BlendFontFace has been closed");
        }
        return face;
    }

    /// Destroys whichever of the two objects were created, innermost first.
    ///
    /// `stage` is how far construction got: 1 means the data was initialised, 2
    /// that the face was too. An object that was `init`ed but whose `create`
    /// failed still holds Blend2D's default instance and must be destroyed —
    /// which is why the stage counter is incremented before the create rather
    /// than after it.
    private void unwind(int stage, MemorySegment fontData, MemorySegment face) {
        if (stage >= 2) {
            destroyQuietly(() -> blend2d.fontFaceDestroy(face));
        }
        if (stage >= 1) {
            destroyQuietly(() -> blend2d.fontDataDestroy(fontData));
        }
    }

    /// Releases one object without letting its failure hide another's.
    static void destroyQuietly(Runnable destroy) {
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
                    "a BlendFontFace belongs to the thread that created it, and this is not it");
        }
    }

    @Override
    public String toString() {
        return "BlendFontFace[" + (closed ? "closed" : "open") + "]";
    }
}
