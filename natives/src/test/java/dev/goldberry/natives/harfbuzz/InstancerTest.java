package dev.goldberry.natives.harfbuzz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.goldberry.natives.NativeLibraryRequirement;

/// The instancer's boundary, checked without a font file: what it refuses
/// before the call, and what it answers for bytes that are not a font. Pinning
/// a real variable face is tested above this module, against the bundled one.
class InstancerTest {

    @BeforeAll
    static void requireNativeLibrary() {
        NativeLibraryRequirement.enforce();
    }

    @Test
    @DisplayName("bytes that are not a font cannot be instanced, and say so with an empty answer")
    void garbage() {
        var bytes = "not a font, just some words".getBytes(StandardCharsets.US_ASCII);
        assertTrue(Instancer.instance(bytes, List.of(new Instancer.Axis("wght", 700)))
                .isEmpty());
    }

    @Test
    @DisplayName("a file with no bytes is refused before the call")
    void empty() {
        assertThrows(IllegalArgumentException.class, () -> Instancer.instance(new byte[0], List.of()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"wgh", "wghtt", "wgét", ""})
    @DisplayName("an axis tag is four printable ASCII characters")
    void tags(String tag) {
        assertThrows(IllegalArgumentException.class, () -> new Instancer.Axis(tag, 1));
    }

    @Test
    @DisplayName("a tag packs as the uint32 OpenType stores")
    void packed() {
        assertEquals(0x77676874, new Instancer.Axis("wght", 400).packed());
    }

    @Test
    @DisplayName("an axis position is finite")
    void finite() {
        assertThrows(IllegalArgumentException.class, () -> new Instancer.Axis("wght", Float.NaN));
    }
}
