package io.github.digitalsmile.goldberry.natives.harfbuzz;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Objects;

/// A font, as HarfBuzz sees it: a typeface plus the size it is being used at.
///
/// HarfBuzz splits what most people call "a font" into three objects — a blob
/// (the file's bytes), a face (the typeface inside it), and a font (the face at
/// a scale). This owns all three and closes them together, because nothing above
/// this module has a reason to hold one without the others.
///
/// **This is not the rasterizer's font.** HarfBuzz decides which glyphs to draw
/// and where; Blend2D draws them from the same file's outlines. The two are
/// separate objects over the same bytes, and keeping them so is what
/// `docs/ARCHITECTURE.md` §6 means by shaping and rendering being different
/// jobs.
///
/// Confined to the thread that created it, and must be closed.
public final class ShapedFont implements AutoCloseable {

    /// The scale a font gets if nothing sets one.
    ///
    /// HarfBuzz's own default is the face's units-per-em, so advances come back
    /// in font design units. That is the right default for a cache — it does not
    /// change with the point size — but it means a caller who forgets to scale
    /// gets numbers around 2048 rather than around 16, which reads as a layout
    /// bug rather than a unit mismatch.
    public static final int UNSCALED = 0;

    private final HarfBuzz harfbuzz = HarfBuzz.get();
    private final Thread owner = Thread.currentThread();
    private final MemorySegment blob;
    private final MemorySegment face;
    private final MemorySegment font;

    /// Whether the face is HarfBuzz's immortal empty one, which must not be
    /// destroyed.
    private final boolean borrowedFace;

    /// The x scale last set, or [#UNSCALED] if none has been.
    private int xScale = UNSCALED;

    private boolean closed;

    private ShapedFont(MemorySegment blob, MemorySegment face, boolean borrowedFace) {
        this.blob = blob;
        this.face = face;
        this.borrowedFace = borrowedFace;
        this.font = harfbuzz.fontCreate(face);
    }

    /// Loads a font from the bytes of a font file.
    ///
    /// The bytes are copied into HarfBuzz's own memory rather than referenced,
    /// so the array may be collected immediately afterwards. The alternative
    /// would be promising that a Java array outlives the face, which nothing
    /// here can promise.
    ///
    /// A file HarfBuzz cannot parse does not fail: it produces a face with no
    /// glyphs, and shaping through it yields `.notdef` for everything. That is
    /// HarfBuzz's design, not an oversight to paper over here — but it does mean
    /// "the text came out as boxes" and "the font failed to load" look the same
    /// from Java.
    ///
    /// @param data      a font file's contents
    /// @param faceIndex which face, for a collection; 0 for an ordinary font
    public static ShapedFont fromBytes(byte[] data, int faceIndex) {
        Objects.requireNonNull(data, "data");
        if (data.length == 0) {
            throw new IllegalArgumentException("a font file with no bytes in it is not a font");
        }
        if (faceIndex < 0) {
            throw new IllegalArgumentException("face index must not be negative: " + faceIndex);
        }

        var harfbuzz = HarfBuzz.get();
        // Confined to this call: hb_blob_create copies with MEMORY_MODE_DUPLICATE,
        // so nothing native refers to this segment once it returns.
        try (var arena = Arena.ofConfined()) {
            var segment = arena.allocate(ValueLayout.JAVA_BYTE, data.length);
            MemorySegment.copy(data, 0, segment, ValueLayout.JAVA_BYTE, 0, data.length);

            var blob = harfbuzz.blobCreate(segment, data.length);
            MemorySegment face;
            try {
                face = harfbuzz.faceCreate(blob, faceIndex);
            } catch (RuntimeException | Error e) {
                harfbuzz.blobDestroy(blob);
                throw e;
            }
            try {
                return new ShapedFont(blob, face, false);
            } catch (RuntimeException | Error e) {
                harfbuzz.faceDestroy(face);
                harfbuzz.blobDestroy(blob);
                throw e;
            }
        }
    }

    /// @see #fromBytes(byte[], int)
    public static ShapedFont fromBytes(byte[] data) {
        return fromBytes(data, 0);
    }

    /// A font with no glyphs, over HarfBuzz's immortal empty face.
    ///
    /// Every character shapes to `.notdef` with a zero advance. That makes it
    /// useless for drawing and genuinely useful for everything else: the glyph
    /// count, the cluster mapping, the direction and the UTF-16 crossing are all
    /// exercised by it, and none of them needs a font file to exist. It is how
    /// the shaping tests run on a machine with no fonts installed.
    public static ShapedFont empty() {
        var harfbuzz = HarfBuzz.get();
        return new ShapedFont(MemorySegment.NULL, harfbuzz.faceEmpty(), true);
    }

    /// Sets the units advances and offsets come back in.
    ///
    /// The usual choice is the pixel size in 26.6 fixed point — `size * 64` — so
    /// that a 16px font reports advances a sixty-fourth of a pixel apart. Passing
    /// the pixel size directly instead gives whole-pixel advances and visibly
    /// uneven spacing, because every fractional advance is truncated.
    /// A run shaped after this is no longer in design units, so it can no longer
    /// be handed to Blend2D unchanged — see [#isDesignUnits()].
    public void setScale(int xScale, int yScale) {
        requireUsable();
        harfbuzz.fontScale(font, xScale, yScale);
        this.xScale = xScale;
    }

    /// The face's units per em — the grid the outlines were designed on, and the
    /// units advances come back in while the font is [unscaled][#UNSCALED].
    ///
    /// Commonly 1000 or 2048, and not guaranteed to be either.
    public int unitsPerEm() {
        requireUsable();
        return harfbuzz.faceUpem(face);
    }

    /// Whether shaping with this font reports positions in **font design units**.
    ///
    /// True while no scale has been set, and true again if one is set to the
    /// face's own units per em. It matters because that is precisely the
    /// condition under which a [GlyphRun] can be handed to Blend2D without
    /// converting it: Blend2D applies `size / units-per-em` itself, so a run
    /// that has already been scaled has the size applied twice (ADR-0034).
    public boolean isDesignUnits() {
        requireUsable();
        return xScale == UNSCALED || xScale == unitsPerEm();
    }

    /// Whether the font has been closed.
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        requireOwner();
        closed = true;
        harfbuzz.fontDestroy(font);
        // The empty face is a singleton HarfBuzz keeps forever, and destroying
        // it would decrement a reference count that was never ours to take.
        if (!borrowedFace) {
            harfbuzz.faceDestroy(face);
            harfbuzz.blobDestroy(blob);
        }
    }

    MemorySegment pointer() {
        requireUsable();
        return font;
    }

    private void requireUsable() {
        requireOwner();
        if (closed) {
            throw new IllegalStateException("this ShapedFont has been closed");
        }
    }

    private void requireOwner() {
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException(
                    "a ShapedFont belongs to the thread that created it, and this is not it");
        }
    }

    @Override
    public String toString() {
        return "ShapedFont[" + (borrowedFace ? "empty" : "loaded") + (closed ? ", closed" : "") + "]";
    }
}
