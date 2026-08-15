package io.github.digitalsmile.goldberry.natives.blend2d;

import io.github.digitalsmile.goldberry.natives.layout.Layouts;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/// Positioned glyphs, staged in the shape Blend2D reads them in.
///
/// A `BLGlyphRun` is a descriptor: two pointers into memory the caller owns,
/// plus the stride of each array. This holds all of it — the glyph ids, the
/// placements, and the descriptor pointing at both — in one arena, so the
/// pointers cannot outlive what they point at.
///
/// **Meant to be reused.** A frame draws hundreds of runs and a paragraph
/// reshapes on every width a layout pass proposes, so a buffer allocated per run
/// would put native allocation on the hot path. [#clear()] keeps the memory and
/// forgets the contents; the buffer grows when it has to and never shrinks.
///
/// ## The units
///
/// Offsets and advances are in **font design units** — the same numbers
/// HarfBuzz reports when nothing has set a scale on it. Blend2D multiplies them
/// by the font matrix, `size / units-per-em`, which is what turns them into
/// pixels. Staging numbers that have already been scaled to pixels applies the
/// size twice, produces text roughly `units-per-em / size` too wide, and
/// reports nothing. See ADR-0034 and [BlendGlyphPlacementType].
///
/// Confined to the thread that created it, and must be closed.
public final class BlendGlyphBuffer implements AutoCloseable {

    /// The stride of the glyph-id array: one `uint32_t` per glyph.
    private static final int GLYPH_STRIDE = 4;

    private static final long PLACEMENT_STRIDE = Layouts.BL_GLYPH_PLACEMENT.byteSize();
    private static final long PLACEMENT_OFFSET = Layouts.BL_GLYPH_PLACEMENT.offsetOf("placement");
    private static final long ADVANCE_OFFSET = Layouts.BL_GLYPH_PLACEMENT.offsetOf("advance");

    private static final long RUN_GLYPH_DATA = Layouts.BL_GLYPH_RUN.offsetOf("glyph_data");
    private static final long RUN_PLACEMENT_DATA = Layouts.BL_GLYPH_RUN.offsetOf("placement_data");
    private static final long RUN_SIZE = Layouts.BL_GLYPH_RUN.offsetOf("size");
    private static final long RUN_RESERVED = Layouts.BL_GLYPH_RUN.offsetOf("reserved");
    private static final long RUN_PLACEMENT_TYPE = Layouts.BL_GLYPH_RUN.offsetOf("placement_type");
    private static final long RUN_GLYPH_ADVANCE = Layouts.BL_GLYPH_RUN.offsetOf("glyph_advance");
    private static final long RUN_PLACEMENT_ADVANCE =
            Layouts.BL_GLYPH_RUN.offsetOf("placement_advance");
    private static final long RUN_FLAGS = Layouts.BL_GLYPH_RUN.offsetOf("flags");

    private final Thread owner = Thread.currentThread();

    private Arena arena;
    private MemorySegment glyphIds;
    private MemorySegment placements;
    private MemorySegment run;
    private int capacity;
    private int size;

    private boolean closed;

    private BlendGlyphBuffer(int capacity) {
        allocate(capacity);
    }

    /// A buffer with room for `glyphs` before it has to grow.
    ///
    /// The capacity is a hint and nothing more: adding past it reallocates
    /// rather than failing. Sizing it to the longest run a caller expects is
    /// what keeps that reallocation out of the frame path.
    public static BlendGlyphBuffer withCapacity(int glyphs) {
        if (glyphs < 0) {
            throw new IllegalArgumentException("a capacity must not be negative: " + glyphs);
        }
        return new BlendGlyphBuffer(Math.max(glyphs, 1));
    }

    /// A buffer sized for an ordinary line of text.
    public static BlendGlyphBuffer create() {
        return withCapacity(128);
    }

    /// Forgets the glyphs, keeping the memory.
    public void clear() {
        requireUsable();
        size = 0;
    }

    /// Appends one positioned glyph.
    ///
    /// @param glyphId  an index into the font, not a character
    /// @param xOffset  moves the glyph without moving the pen, in design units
    /// @param yOffset  likewise, and positive is **up** — HarfBuzz's convention,
    ///                 which is also Blend2D's for glyph placement
    /// @param xAdvance moves the pen, in design units
    /// @param yAdvance moves the pen vertically; zero for horizontal text
    public void add(int glyphId, int xOffset, int yOffset, int xAdvance, int yAdvance) {
        requireUsable();
        if (size == capacity) {
            grow();
        }
        glyphIds.setAtIndex(ValueLayout.JAVA_INT, size, glyphId);

        var at = size * PLACEMENT_STRIDE;
        placements.set(ValueLayout.JAVA_INT, at + PLACEMENT_OFFSET, xOffset);
        placements.set(ValueLayout.JAVA_INT, at + PLACEMENT_OFFSET + 4, yOffset);
        placements.set(ValueLayout.JAVA_INT, at + ADVANCE_OFFSET, xAdvance);
        placements.set(ValueLayout.JAVA_INT, at + ADVANCE_OFFSET + 4, yAdvance);
        size++;
    }

    /// How many glyphs have been added since the last [#clear()].
    public int size() {
        return size;
    }

    /// Whether nothing has been added.
    public boolean isEmpty() {
        return size == 0;
    }

    /// How many glyphs fit before the next reallocation.
    public int capacity() {
        return capacity;
    }

    /// Whether the buffer has been closed.
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
        arena.close();
    }

    /// The `BLGlyphRun` describing what has been added, with its size brought up
    /// to date.
    ///
    /// The count is written here rather than in [#add] because it is the one
    /// field that changes per fill, and a descriptor whose size disagrees with
    /// its arrays is read past the end without complaint.
    MemorySegment pointer() {
        requireUsable();
        run.set(ValueLayout.JAVA_LONG, RUN_SIZE, size);
        return run;
    }

    /// Allocates a fresh arena and points a new descriptor at it.
    private void allocate(int capacity) {
        this.arena = Arena.ofConfined();
        this.capacity = capacity;
        this.glyphIds = arena.allocate(ValueLayout.JAVA_INT, capacity);
        this.placements = arena.allocate(
                PLACEMENT_STRIDE * capacity, Layouts.BL_GLYPH_PLACEMENT.byteAlignment());
        this.run = arena.allocate(Layouts.BL_GLYPH_RUN.layout());

        run.set(ValueLayout.ADDRESS, RUN_GLYPH_DATA, glyphIds);
        run.set(ValueLayout.ADDRESS, RUN_PLACEMENT_DATA, placements);
        run.set(ValueLayout.JAVA_LONG, RUN_SIZE, 0L);
        // Documented as "must be zero", so it is written rather than left to
        // whatever the allocator handed over.
        run.set(ValueLayout.JAVA_BYTE, RUN_RESERVED, (byte) 0);
        run.set(ValueLayout.JAVA_BYTE, RUN_PLACEMENT_TYPE,
                (byte) BlendGlyphPlacementType.ADVANCE_OFFSET.nativeValue());
        // Strides, not typographic advances. Blend2D walks both arrays by these.
        run.set(ValueLayout.JAVA_BYTE, RUN_GLYPH_ADVANCE, (byte) GLYPH_STRIDE);
        run.set(ValueLayout.JAVA_BYTE, RUN_PLACEMENT_ADVANCE, (byte) PLACEMENT_STRIDE);
        run.set(ValueLayout.JAVA_INT, RUN_FLAGS, 0);
    }

    /// Doubles the capacity, carrying the glyphs already added across.
    ///
    /// The old arena is closed only once everything has been copied out of it:
    /// closing it first would invalidate the segments being read.
    private void grow() {
        var previousArena = arena;
        var previousGlyphs = glyphIds;
        var previousPlacements = placements;
        var previousSize = size;

        allocate(Math.multiplyExact(capacity, 2));
        MemorySegment.copy(previousGlyphs, 0, glyphIds, 0, (long) previousSize * GLYPH_STRIDE);
        MemorySegment.copy(
                previousPlacements, 0, placements, 0, previousSize * PLACEMENT_STRIDE);
        previousArena.close();
    }

    private void requireUsable() {
        requireOwner();
        if (closed) {
            throw new IllegalStateException("this BlendGlyphBuffer has been closed");
        }
    }

    private void requireOwner() {
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException(
                    "a BlendGlyphBuffer belongs to the thread that created it, and this is not it");
        }
    }

    @Override
    public String toString() {
        return closed
                ? "BlendGlyphBuffer[closed]"
                : "BlendGlyphBuffer[" + size + "/" + capacity + " glyphs]";
    }
}
