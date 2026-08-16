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
    private final BlendFontFace face;
    private final boolean ownsFace;
    private final MemorySegment font;
    private final float size;

    private boolean closed;

    private BlendFont(BlendFontFace face, boolean ownsFace, float size) {
        this.face = face;
        this.ownsFace = ownsFace;
        this.size = size;
        this.arena = Arena.ofConfined();

        MemorySegment stagedFont = null;
        var created = false;
        try {
            stagedFont = arena.allocate(Layouts.BL_OBJECT_DETAIL.layout());
            blend2d.fontInit(stagedFont);
            created = true;
            blend2d.fontCreate(stagedFont, face.pointer(), size);
        } catch (RuntimeException | Error e) {
            // An object that was `init`ed but whose `create` failed still holds
            // Blend2D's default instance and has to be destroyed.
            if (created) {
                var initialised = stagedFont;
                BlendFontFace.destroyQuietly(() -> blend2d.fontDestroy(initialised));
            }
            arena.close();
            if (ownsFace) {
                face.close();
            }
            throw e;
        }

        this.font = stagedFont;
    }

    /// A font at `size` over a face somebody else owns.
    ///
    /// **The face must outlive this font.** That is the whole point — one face,
    /// many sizes, one copy of the bytes (ADR-0044) — and it is also the way to
    /// get it wrong: closing the face first leaves this reading unmapped memory.
    /// Closing *this* leaves the face untouched.
    ///
    /// @param face the typeface, which is not closed by [#close()]
    /// @param size the em size, in the rendering context's units
    public static BlendFont on(BlendFontFace face, double size) {
        Objects.requireNonNull(face, "face");
        requireUsableSize(size);
        return new BlendFont(face, false, (float) size);
    }

    /// Loads a font from the bytes of a font file, at `size` units per em.
    ///
    /// Parses a face of its own and closes it with the font. Use
    /// [#on(BlendFontFace, double)] when more than one size is wanted from the
    /// same file, which is what a UI at more than one text size is.
    ///
    /// @param data      a font file's contents, copied rather than referenced
    /// @param faceIndex which face, for a collection; 0 for an ordinary font
    /// @param size      the em size, in the rendering context's units
    /// @throws BlendException if the bytes are not a font Blend2D can read —
    ///         unlike HarfBuzz, which hands back an empty face instead
    public static BlendFont fromBytes(byte[] data, int faceIndex, double size) {
        requireUsableSize(size);
        // The face is validated by BlendFontFace, so the argument checks that
        // belong to it live there and are not repeated here.
        return new BlendFont(BlendFontFace.fromBytes(data, faceIndex), true, (float) size);
    }

    /// @see #fromBytes(byte[], int, double)
    public static BlendFont fromBytes(byte[] data, double size) {
        return fromBytes(data, 0, size);
    }

    private static void requireUsableSize(double size) {
        if (!Double.isFinite(size) || size <= 0) {
            throw new IllegalArgumentException(
                    "a font size must be a positive, finite number of units per em, and "
                            + size + " is not");
        }
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

    /// The face this font was created over.
    public BlendFontFace face() {
        return face;
    }

    /// Releases the font object, and the face too if this one made it.
    ///
    /// A font created by [#on(BlendFontFace, double)] leaves its face alone:
    /// other sizes are using it.
    @Override
    public void close() {
        if (closed) {
            return;
        }
        requireOwner();
        closed = true;
        try {
            BlendFontFace.destroyQuietly(() -> blend2d.fontDestroy(font));
        } finally {
            try {
                arena.close();
            } finally {
                if (ownsFace) {
                    face.close();
                }
            }
        }
    }

    MemorySegment pointer() {
        requireOwner();
        if (closed) {
            throw new IllegalStateException("this BlendFont has been closed");
        }
        return font;
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
