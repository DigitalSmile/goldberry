package io.github.digitalsmile.goldberry.natives.blend2d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.natives.layout.Layouts;
import java.lang.foreign.ValueLayout;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The staging buffer, checked by reading back the bytes Blend2D will read.
///
/// Nothing here calls Blend2D — a `BLGlyphRun` is a descriptor the Java side
/// fills in entirely, so what has to be right is the memory, and memory can be
/// examined. That makes these the cheapest tests in the text path and the ones
/// covering the failure nobody would spot: a descriptor whose strides or count
/// are wrong is read past the end of the arrays without a word of complaint.
class BlendGlyphBufferTest {

    private static final long PLACEMENT_STRIDE = Layouts.BL_GLYPH_PLACEMENT.byteSize();

    private static final long RUN_SIZE = Layouts.BL_GLYPH_RUN.offsetOf("size");
    private static final long RUN_RESERVED = Layouts.BL_GLYPH_RUN.offsetOf("reserved");
    private static final long RUN_PLACEMENT_TYPE = Layouts.BL_GLYPH_RUN.offsetOf("placement_type");
    private static final long RUN_GLYPH_ADVANCE = Layouts.BL_GLYPH_RUN.offsetOf("glyph_advance");
    private static final long RUN_PLACEMENT_ADVANCE =
            Layouts.BL_GLYPH_RUN.offsetOf("placement_advance");
    private static final long RUN_FLAGS = Layouts.BL_GLYPH_RUN.offsetOf("flags");

    @Test
    @DisplayName("the descriptor says how Blend2D should walk the arrays")
    void descriptorCarriesTheStrides() {
        try (var glyphs = BlendGlyphBuffer.create()) {
            glyphs.add(42, 0, 0, 100, 0);
            var run = glyphs.pointer();

            assertEquals(1L, run.get(ValueLayout.JAVA_LONG, RUN_SIZE), "one glyph");
            // Four bytes per glyph id and sixteen per placement. Blend2D adds
            // these to a pointer, so a wrong one does not fail -- it reads the
            // middle of the next glyph.
            assertEquals(4, run.get(ValueLayout.JAVA_BYTE, RUN_GLYPH_ADVANCE));
            assertEquals(
                    (byte) PLACEMENT_STRIDE,
                    run.get(ValueLayout.JAVA_BYTE, RUN_PLACEMENT_ADVANCE));
            assertEquals(16, PLACEMENT_STRIDE, "two BLPointI is sixteen bytes");

            // The field that decides the units of everything else (ADR-0034).
            assertEquals(
                    (byte) BlendGlyphPlacementType.ADVANCE_OFFSET.nativeValue(),
                    run.get(ValueLayout.JAVA_BYTE, RUN_PLACEMENT_TYPE));

            assertEquals(0, run.get(ValueLayout.JAVA_BYTE, RUN_RESERVED), "documented as zero");
            assertEquals(0, run.get(ValueLayout.JAVA_INT, RUN_FLAGS));
        }
    }

    @Test
    @DisplayName("a glyph's four numbers land in the order BLGlyphPlacement declares")
    void placementFieldsAreInOrder() {
        try (var glyphs = BlendGlyphBuffer.create()) {
            // Distinct values throughout: with 0s or repeats, a swapped offset
            // and advance would read as a pass.
            glyphs.add(7, 11, 22, 33, 44);

            var placements = placementsOf(glyphs);
            assertEquals(11, placements[0], "offset x");
            assertEquals(22, placements[1], "offset y");
            assertEquals(33, placements[2], "advance x");
            assertEquals(44, placements[3], "advance y");
            assertEquals(7, glyphIdAt(glyphs, 0));
        }
    }

    @Test
    @DisplayName("growing past the capacity keeps the glyphs already staged")
    void growthPreservesContents() {
        try (var glyphs = BlendGlyphBuffer.withCapacity(2)) {
            for (var i = 0; i < 9; i++) {
                glyphs.add(i, i, -i, i * 10, 0);
            }

            assertEquals(9, glyphs.size());
            assertTrue(glyphs.capacity() >= 9, "it grew");

            // The reallocation copies into a new arena and closes the old one.
            // Every glyph staged before the move has to survive it -- and the
            // descriptor has to point at the new arrays, which is what reading
            // through pointer() checks.
            var placements = placementsOf(glyphs);
            for (var i = 0; i < 9; i++) {
                assertEquals(i, glyphIdAt(glyphs, i), "glyph id " + i);
                assertEquals(i, placements[i * 4], "offset x " + i);
                assertEquals(-i, placements[i * 4 + 1], "offset y " + i);
                assertEquals(i * 10, placements[i * 4 + 2], "advance x " + i);
            }
        }
    }

    @Test
    @DisplayName("clearing keeps the memory and forgets the glyphs")
    void clearKeepsCapacity() {
        try (var glyphs = BlendGlyphBuffer.withCapacity(64)) {
            glyphs.add(1, 0, 0, 10, 0);
            glyphs.add(2, 0, 0, 10, 0);
            var capacity = glyphs.capacity();

            glyphs.clear();

            assertEquals(0, glyphs.size());
            assertTrue(glyphs.isEmpty());
            assertEquals(capacity, glyphs.capacity(), "the allocation is the point of reusing it");
            assertEquals(0L, glyphs.pointer().get(ValueLayout.JAVA_LONG, RUN_SIZE));
        }
    }

    @Test
    @DisplayName("a closed buffer refuses to stage anything more")
    void closedBufferIsUnusable() {
        var glyphs = BlendGlyphBuffer.create();
        glyphs.close();

        assertTrue(glyphs.isClosed());
        assertThrows(IllegalStateException.class, () -> glyphs.add(1, 0, 0, 0, 0));
        assertThrows(IllegalStateException.class, glyphs::clear);
        // Closing twice is a no-op, so try-with-resources around an explicit
        // close is not an error.
        glyphs.close();
    }

    @Test
    @DisplayName("a negative capacity is refused")
    void negativeCapacityIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> BlendGlyphBuffer.withCapacity(-1));
    }

    @Test
    @DisplayName("a zero capacity still yields a usable buffer")
    void zeroCapacityStillWorks() {
        // Asking for nothing is not the same as being unusable: the buffer grows
        // on the first add rather than dividing by zero on the way there.
        try (var glyphs = BlendGlyphBuffer.withCapacity(0)) {
            assertFalse(glyphs.isClosed());
            glyphs.add(1, 0, 0, 5, 0);
            assertEquals(1, glyphs.size());
        }
    }

    // --- helpers -------------------------------------------------------------

    /// The glyph id at `index`, read back through the descriptor's own pointer —
    /// so the test follows the same route Blend2D does.
    @SuppressWarnings("restricted")
    private static int glyphIdAt(BlendGlyphBuffer glyphs, int index) {
        var data = glyphs.pointer()
                .get(ValueLayout.ADDRESS, Layouts.BL_GLYPH_RUN.offsetOf("glyph_data"))
                .reinterpret(4L * (index + 1));
        return data.getAtIndex(ValueLayout.JAVA_INT, index);
    }

    /// Every placement, flattened to `int`s in memory order.
    @SuppressWarnings("restricted")
    private static int[] placementsOf(BlendGlyphBuffer glyphs) {
        var data = glyphs.pointer()
                .get(ValueLayout.ADDRESS, Layouts.BL_GLYPH_RUN.offsetOf("placement_data"))
                .reinterpret(PLACEMENT_STRIDE * glyphs.size());
        return data.toArray(ValueLayout.JAVA_INT);
    }
}
