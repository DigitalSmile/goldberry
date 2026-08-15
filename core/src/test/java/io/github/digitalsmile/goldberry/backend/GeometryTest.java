package io.github.digitalsmile.goldberry.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class GeometryTest {

    @Nested
    class Logical {

        @ParameterizedTest
        @ValueSource(floats = {Float.NaN, Float.POSITIVE_INFINITY, -1f})
        @DisplayName("an unusable dimension is rejected")
        void rejectsUnusable(float value) {
            assertThrows(IllegalArgumentException.class, () -> new LogicalSize(value, 10f));
            assertThrows(IllegalArgumentException.class, () -> new LogicalSize(10f, value));
        }

        @Test
        @DisplayName("zero is allowed, and is empty")
        void zeroIsEmpty() {
            assertTrue(new LogicalSize(0f, 100f).isEmpty());
            assertTrue(new LogicalSize(100f, 0f).isEmpty());
            assertFalse(new LogicalSize(1f, 1f).isEmpty());
        }
    }

    @Nested
    class Physical {

        @Test
        @DisplayName("a negative size is rejected")
        void rejectsNegative() {
            assertThrows(IllegalArgumentException.class, () -> new PhysicalSize(-1, 10));
            assertThrows(IllegalArgumentException.class, () -> new PhysicalSize(10, -1));
        }

        @Test
        @DisplayName("pixel count does not overflow an int")
        void pixelCountIsWide() {
            // 65536x65536 is 4.29 billion pixels -- an int would report it as
            // zero, and a buffer allocated from that is a very confusing crash.
            assertEquals(4_294_967_296L, new PhysicalSize(65536, 65536).pixelCount());
        }
    }

    @Nested
    class Damage {

        @ParameterizedTest
        @CsvSource({
            "0,  0,  100, 100, true",
            "0,  0,  200, 200, true",
            "100, 100, 100, 100, true",
            "0,  0,  201, 200, false",
            "1,  0,  200, 200, false",
            "-1, 0,  10,  10,  false",
            "0,  -1, 10,  10,  false",
        })
        @DisplayName("bounds are checked against the frame, not assumed")
        void checksBounds(int x, int y, int width, int height, boolean fits) {
            var frame = new PhysicalSize(200, 200);

            assertEquals(fits, new DamageRect(x, y, width, height).fitsWithin(frame));
        }

        @Test
        @DisplayName("a rectangle that would overflow when added is still rejected")
        void handlesOverflow() {
            // x + width overflows int; the check is done in long arithmetic
            // precisely so this reads as "outside the frame" rather than as a
            // negative number that passes.
            var rect = new DamageRect(Integer.MAX_VALUE, 0, 10, 10);

            assertFalse(rect.fitsWithin(new PhysicalSize(200, 200)));
        }

        @Test
        @DisplayName("whole-frame damage covers the frame exactly")
        void allCoversFrame() {
            var frame = new PhysicalSize(1920, 1080);
            var all = DamageRect.all(frame);

            assertTrue(all.fitsWithin(frame));
            assertEquals(1920, all.width());
            assertEquals(1080, all.height());
        }

        @Test
        @DisplayName("a negative size is rejected")
        void rejectsNegativeSize() {
            assertThrows(IllegalArgumentException.class, () -> new DamageRect(0, 0, -1, 10));
        }
    }

    @Nested
    class Window {

        @Test
        @DisplayName("a window needs a real size")
        void rejectsEmptySize() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> WindowSpec.of("Goldberry", new LogicalSize(0f, 720f)));
        }

        @Test
        @DisplayName("the default is resizable and server-side decorated")
        void defaultsMatchTheArchitecture() {
            var spec = WindowSpec.of("Goldberry", LogicalSize.of(1280f, 720f));

            assertTrue(spec.resizable());
            assertTrue(spec.decorated(), "server-side decorations are the default on every platform");
        }
    }
}
