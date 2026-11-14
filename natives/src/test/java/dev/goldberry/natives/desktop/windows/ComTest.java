package dev.goldberry.natives.desktop.windows;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.HexFormat;
import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The parts of [Com] that do not need Windows: a GUID's in-memory bytes and a
/// vtable read.
@DisplayName("COM")
class ComTest {

    @Test
    @DisplayName("lays a GUID out with its first three groups little endian")
    void guid() {
        try (var arena = Arena.ofConfined()) {
            var guid = Com.guid(arena, "{56FDF344-FD6D-11D0-958A-006097C9A090}");

            assertArrayEquals(HexFormat.of().parseHex("44f3fd566dfdd011958a006097c9a090"), guid.toArray(JAVA_BYTE));
            assertArrayEquals(
                    guid.toArray(JAVA_BYTE),
                    Com.guid(arena, "56fdf344-fd6d-11d0-958a-006097c9a090").toArray(JAVA_BYTE),
                    "braces and case are optional");
            assertThrows(IllegalArgumentException.class, () -> Com.guid(arena, "{56FDF344-FD6D}"));
        }
    }

    @Test
    @DisplayName("reads slot n of an object's vtable")
    void vtable() {
        try (var arena = Arena.ofConfined()) {
            var table = arena.allocate(ADDRESS, 4);
            for (var slot = 0; slot < 4; slot++) {
                table.setAtIndex(ADDRESS, slot, MemorySegment.ofAddress(0x1000L + slot));
            }
            var object = arena.allocate(ADDRESS);
            object.set(ADDRESS, 0, table);

            assertEquals(0x1003L, Com.method(object, 3).address());
            assertEquals(0x1000L + Com.RELEASE, Com.method(object, Com.RELEASE).address());
        }
    }

    @Test
    @DisplayName("counts S_FALSE as success and an error as failure")
    void results() {
        assertTrue(Com.succeeded(0));
        assertTrue(Com.succeeded(1));
        assertFalse(Com.succeeded(Com.CHANGED_MODE));
    }

    @Test
    @DisplayName("is not offered off Windows")
    void onlyOnWindows() {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            assertTrue(Com.open().isEmpty());
        }
    }
}
