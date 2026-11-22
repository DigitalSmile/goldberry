package dev.goldberry.camera.backend;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("a BGRA pool")
class BgraPoolTest {

    @Test
    @DisplayName("name the same slot until it is written, then the next, round three")
    void slots() {
        var pool = new BgraPool(2, 2);
        pool.next().put(0, (byte) 1);
        assertEquals(1, pool.next().get(0), "named, not taken");
        var first = pool.written(10);
        pool.next().put(0, (byte) 2);
        var second = pool.written(20);
        pool.next().put(0, (byte) 3);
        var third = pool.written(30, false);
        assertAll(
                () -> assertEquals(1, first.pixels().get(0)),
                () -> assertEquals(2, second.pixels().get(0)),
                () -> assertEquals(3, third.pixels().get(0)),
                () -> assertEquals(1, pool.next().get(0), "the fourth is the first slot again"),
                () -> assertEquals(8, first.stride()),
                () -> assertEquals(20, second.ptsNanos()),
                () -> assertTrue(second.opaque()),
                () -> assertFalse(third.opaque()));
    }

    @Test
    @DisplayName("say which size it holds, and refuse no size")
    void size() {
        var pool = new BgraPool(4, 3);
        assertAll(
                () -> assertTrue(pool.fits(4, 3)),
                () -> assertFalse(pool.fits(3, 4)),
                () -> assertEquals(16, pool.stride()),
                () -> assertEquals(48, pool.next().remaining()),
                () -> assertThrows(IllegalArgumentException.class, () -> new BgraPool(0, 3)));
    }
}
