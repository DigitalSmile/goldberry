package io.github.digitalsmile.goldberry.natives.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// What can be said about the layout table without a compiled library.
///
/// The offsets, sizes and alignments of everything in [Layouts#registry()] are
/// compared field by field with the library the C compiler actually produced, by
/// `LayoutVerificationTest`; restating them here would be restating them. What is
/// left is the shape of the registry itself, and the two entries that sit outside
/// it.
class LayoutsTest {

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

    /// Layouts modelled by extent alone, with no fields to compare.
    ///
    /// Unions Goldberry allocates and hands over without ever reading a field.
    ///
    /// `SDL_Event` is one: Goldberry allocates it, hands SDL the pointer, and
    /// reads the arms it understands through their own layouts. `BLObjectDetail`
    /// is the other, and more so — it is Blend2D's entire object model, a static
    /// payload and an `Impl` pointer overlapped in sixteen bytes, and its
    /// contents are Blend2D's business alone.
    ///
    /// For both, the size and alignment are the whole contract. Naming members
    /// of a union would assert a structure it does not have.
    ///
    /// `BLPathCore` is the same union again under its own name, registered
    /// separately because `BlendPath` allocates by it (ADR-0043), and
    /// `BLGradientCore` is a third for the same reason (ADR-0207).
    private static final List<String> OPAQUE = List.of("SDL_Event", "BLObjectDetail", "BLPathCore", "BLGradientCore");

    @Test
    @DisplayName("every registered layout is named and non-empty")
    void registryIsWellFormed() {
        var registry = Layouts.registry();

        assertFalse(registry.isEmpty(), "registry must not be empty");
        for (var struct : registry) {
            assertFalse(struct.name().isBlank(), "struct name must not be blank");
            assertTrue(struct.byteSize() > 0, () -> struct.name() + " has zero size");
            if (!OPAQUE.contains(struct.name())) {
                assertFalse(struct.fieldNames().isEmpty(), () -> struct.name() + " has no named fields");
            }
        }
    }

    @Test
    @DisplayName("the event union is 8-aligned, not byte-aligned")
    void eventUnionIsAligned() {
        // Modelling 128 bytes as a byte array would declare alignment 1. SDL
        // fills it with types up to 8 bytes wide, so an under-aligned allocation
        // is a fault on targets less forgiving than x86.
        assertEquals(128, Layouts.SDL_EVENT.byteSize());
        assertEquals(8, Layouts.SDL_EVENT.byteAlignment());
    }
}
