package dev.goldberry.golden;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// A golden says which instruction set it was rasterized with.
class SimdPathTest {

    @Test
    @DisplayName("HotSpot's UseAVX levels are named, and anything else is unknown rather than guessed")
    void levels() {
        assertEquals("SSE", SimdPath.x86Level(0));
        assertEquals("AVX", SimdPath.x86Level(1));
        assertEquals("AVX2", SimdPath.x86Level(2));
        assertEquals("AVX-512", SimdPath.x86Level(3));
        assertEquals("vector level unknown", SimdPath.x86Level(-1));
    }

    @Test
    @DisplayName("this machine is described by its architecture and, on x86, a level the JVM could read")
    void thisMachine() {
        var described = SimdPath.describe();
        var arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);

        switch (arch) {
            case "amd64", "x86_64" -> {
                assertTrue(described.startsWith("x86-64, "), described);
                assertFalse(described.endsWith("unknown"), "a HotSpot JVM on x86 reports UseAVX: " + described);
            }
            case "aarch64", "arm64" -> assertEquals("aarch64, NEON", described);
            default -> assertEquals(arch, described);
        }
    }
}
