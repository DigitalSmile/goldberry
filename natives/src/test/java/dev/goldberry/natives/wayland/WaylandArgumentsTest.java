package dev.goldberry.natives.wayland;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The `union wl_argument` array a request is marshalled from, and the same
/// array read back as an event.
@DisplayName("Wayland's argument array")
class WaylandArgumentsTest {

    @Test
    @DisplayName("puts each argument eight bytes after the last")
    @SuppressWarnings("restricted")
    void layout() {
        try (var arena = Arena.ofConfined()) {
            var object = arena.allocate(16);
            var arguments = new WaylandArguments(arena, 4)
                    .integer(-7)
                    .string("image/png")
                    .object(object)
                    .newId();
            var segment = arguments.segment();

            assertEquals(32, segment.byteSize());
            assertEquals(-7, segment.get(JAVA_INT, 0));
            assertEquals("image/png", segment.get(ADDRESS, 8).reinterpret(64).getString(0));
            assertEquals(object.address(), segment.get(ADDRESS, 16).address());
            assertEquals(0, segment.get(ADDRESS, 24).address());
        }
    }

    @Test
    @DisplayName("refuses more arguments than it made room for")
    void tooMany() {
        try (var arena = Arena.ofConfined()) {
            var arguments = new WaylandArguments(arena, 1).unsigned(1);

            assertThrows(IllegalStateException.class, () -> arguments.unsigned(2));
            assertThrows(IllegalStateException.class, () -> new WaylandArguments(arena, 0).newId());
        }
    }

    @Test
    @DisplayName("reads back as an event: integers, strings, a null string and objects")
    void asEvent() {
        try (var arena = Arena.ofConfined()) {
            var object = arena.allocate(8);
            var segment = new WaylandArguments(arena, 4)
                    .unsigned(0xffff_fffe)
                    .string("text/uri-list")
                    .object(MemorySegment.NULL)
                    .object(object)
                    .segment();
            var event = new WaylandEvent(1, segment);

            assertEquals(0xffff_fffe, event.integer(0));
            assertEquals("text/uri-list", event.string(1));
            assertEquals(null, event.string(2));
            assertEquals(object.address(), event.object(3).address());
        }
    }
}
