package io.github.digitalsmile.goldberry.natives.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LayoutsTest {

    @Test
    @DisplayName("the canary struct is laid out as the C compiler will lay it out")
    void probeSelfLayout() {
        var probe = Layouts.PROBE_SELF;

        // uint8_t a; -- 3 bytes of padding follow, because uint32_t b must be
        // 4-aligned. void *c then lands on 8 without further padding, since b
        // ends exactly there.
        assertEquals(0, probe.offsetOf("a"));
        assertEquals(4, probe.offsetOf("b"));
        assertEquals(8, probe.offsetOf("c"));
        assertEquals(16, probe.offsetOf("d"));
        assertEquals(24, probe.byteSize());
        assertEquals(8, probe.byteAlignment());
    }

    @Test
    @DisplayName("field sizes are what the C types are")
    void probeSelfFieldSizes() {
        var probe = Layouts.PROBE_SELF;

        assertEquals(1, probe.sizeOf("a"));
        assertEquals(4, probe.sizeOf("b"));
        assertEquals(8, probe.sizeOf("c"));
        assertEquals(8, probe.sizeOf("d"));
    }

    @Test
    @DisplayName("padding is unnamed, so only real fields are compared against C")
    void fieldNamesExcludePadding() {
        assertEquals(List.of("a", "b", "c", "d"), List.copyOf(Layouts.PROBE_SELF.fieldNames()));
    }

    @Test
    @DisplayName("the layout-entry struct matches goldberry_layout_entry_t")
    void layoutEntryLayout() {
        // Read before anything else in the table can be trusted, so it is
        // asserted explicitly rather than left to the round trip.
        assertEquals(32, Layouts.LAYOUT_ENTRY.byteSize());
        assertEquals(8, Layouts.LAYOUT_ENTRY.byteAlignment());
    }

    @Test
    @DisplayName("every registered layout is named and non-empty")
    void registryIsWellFormed() {
        var registry = Layouts.registry();

        assertFalse(registry.isEmpty(), "registry must not be empty");
        for (var struct : registry) {
            assertFalse(struct.name().isBlank(), "struct name must not be blank");
            assertTrue(struct.byteSize() > 0, () -> struct.name() + " has zero size");
            assertFalse(struct.fieldNames().isEmpty(), () -> struct.name() + " has no named fields");
        }
    }
}
