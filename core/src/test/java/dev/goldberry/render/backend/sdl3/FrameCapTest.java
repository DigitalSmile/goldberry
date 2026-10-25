package dev.goldberry.render.backend.sdl3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// A window's frame cap, and how it meets the display's pacer, without a
/// display: every decision is a function of the stamps handed in.
@DisplayName("a window's frame cap")
class FrameCapTest {

    private static final long SIXTY_HZ = 16_666_666L;
    private static final long THIRTY_HZ = 33_333_333L;

    private static FrameCap at(double hertz) {
        var cap = new FrameCap();
        cap.rate(hertz);
        return cap;
    }

    @Nested
    @DisplayName("alone")
    class Alone {

        @Test
        @DisplayName("holds nothing back when there is none, or before its first frame")
        void uncappedAndFirst() {
            var none = new FrameCap();
            assertFalse(none.isCapped());
            none.frameEmitted(0L);
            assertTrue(none.isDue(1L));
            assertEquals(0L, none.dueAt());
            var capped = at(30);
            assertTrue(capped.isDue(0L), "a first frame is never held");
        }

        @Test
        @DisplayName("holds a frame until its interval has passed, and says when that is")
        void holdsToItsInterval() {
            var cap = at(30);
            cap.frameEmitted(0L);
            assertFalse(cap.isDue(THIRTY_HZ - 1));
            assertEquals(1L, cap.nanosUntilDue(THIRTY_HZ - 1));
            assertTrue(cap.isDue(THIRTY_HZ));
            assertEquals(THIRTY_HZ, cap.dueAt());
            assertEquals(30, cap.rate());
        }

        @Test
        @DisplayName("is lifted by zero, and refuses a negative or endless rate")
        void liftedAndRefused() {
            var cap = at(30);
            cap.frameEmitted(0L);
            cap.rate(0);
            assertTrue(cap.isDue(1L));
            assertThrows(IllegalArgumentException.class, () -> cap.rate(-1));
            assertThrows(IllegalArgumentException.class, () -> cap.rate(Double.NaN));
            assertThrows(IllegalArgumentException.class, () -> cap.rate(Double.POSITIVE_INFINITY));
        }
    }

    @Nested
    @DisplayName("with the display's pacer")
    class WithThePacer {

        @Test
        @DisplayName("a window that waits for the display waits for both, and one that does not waits for its cap")
        void waitsForWhatHoldsIt() {
            var pacer = new FramePacer(SIXTY_HZ);
            pacer.frameEmitted(0L);
            var uncapped = new FrameCap();
            assertFalse(pacer.isDue(SIXTY_HZ / 2, uncapped, true), "vsync: the display has not refreshed");
            assertTrue(pacer.isDue(SIXTY_HZ / 2, uncapped, false), "mailbox, no cap: nothing holds it");
            var cap = at(30);
            cap.frameEmitted(0L);
            assertFalse(pacer.isDue(SIXTY_HZ, cap, true), "the display refreshed, and the cap still holds it");
            assertTrue(pacer.isDue(THIRTY_HZ, cap, true));
            assertFalse(pacer.isDue(SIXTY_HZ, cap, false), "mailbox at 30: the cap alone");
        }

        @Test
        @DisplayName("shortens the loop's wait to the later of the two, and leaves it for a frame nothing holds")
        void shortensTheWait() {
            var pacer = new FramePacer(SIXTY_HZ);
            pacer.frameEmitted(0L);
            var cap = at(30);
            cap.frameEmitted(0L);
            var second = Duration.ofSeconds(1);
            assertEquals(Duration.ofNanos(THIRTY_HZ), pacer.capWait(second, cap, true, 0L), "the cap is later");
            assertEquals(Duration.ofNanos(THIRTY_HZ), pacer.capWait(second, cap, false, 0L));
            assertEquals(second, pacer.capWait(second, new FrameCap(), false, 0L), "its wakeup ends the wait");
            var unpaced = new FramePacer(0L);
            assertEquals(Duration.ofNanos(THIRTY_HZ), unpaced.capWait(second, cap, true, 0L), "no display rate");
        }

        @Test
        @DisplayName("a cap above the display's rate changes nothing for a window that waits for the display")
        void aFastCapIsInert() {
            var pacer = new FramePacer(SIXTY_HZ);
            pacer.frameEmitted(0L);
            var cap = at(240);
            cap.frameEmitted(0L);
            assertEquals(pacer.isDue(SIXTY_HZ - 1), pacer.isDue(SIXTY_HZ - 1, cap, true));
            assertEquals(pacer.isDue(SIXTY_HZ), pacer.isDue(SIXTY_HZ, cap, true));
        }
    }
}
