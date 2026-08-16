package io.github.digitalsmile.goldberry.backend.sdl3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// The pacer's decisions, without a display.
///
/// Every one is a function of the nanosecond stamps handed in, which is why the
/// class takes them rather than reading the clock: the cases worth testing are
/// the boundaries, and a test that slept for them would be slow and flaky both.
class FramePacerTest {

    private static final long SIXTY_HZ = 16_666_666L;

    private static FramePacer atSixtyHz() {
        return new FramePacer(SIXTY_HZ);
    }

    @Nested
    @DisplayName("unpaced")
    class Unpaced {

        @Test
        @DisplayName("a zero interval paces nothing")
        void zeroIntervalIsUnpaced() {
            var pacer = new FramePacer(0L);

            assertFalse(pacer.isPacing());
            assertTrue(pacer.isDue(0L));
            pacer.frameEmitted(0L);
            // Still due one nanosecond later: this is the pre-existing behaviour,
            // and it has to stay reachable so a benchmark can have every frame.
            assertTrue(pacer.isDue(1L));
            assertEquals(0L, pacer.nanosUntilDue(1L));
        }

        @Test
        @DisplayName("a negative interval is treated as unpaced, not as a wait forever")
        void negativeIntervalIsUnpaced() {
            assertFalse(new FramePacer(-1L).isPacing());
        }

        @Test
        @DisplayName("an unpaced pacer never shortens the caller's wait")
        void unpacedDoesNotCapTheWait() {
            var pacer = new FramePacer(0L);
            var timeout = Duration.ofSeconds(1);

            assertEquals(timeout, pacer.capWait(timeout, true, 0L));
        }
    }

    @Nested
    @DisplayName("paced")
    class Paced {

        @Test
        @DisplayName("the first frame is never held back")
        void theFirstFrameIsImmediate() {
            var pacer = atSixtyHz();

            // Not "due because enough time passed" -- there is no previous frame
            // to measure from, and the first frame is the one the start-up
            // timeline reports.
            assertTrue(pacer.isDue(0L));
            assertEquals(0L, pacer.nanosUntilDue(0L));
        }

        @Test
        @DisplayName("a second frame inside the interval is held for the remainder")
        void aFrameInsideTheIntervalWaits() {
            var pacer = atSixtyHz();
            pacer.frameEmitted(1_000_000_000L);

            var quarterIn = 1_000_000_000L + SIXTY_HZ / 4;
            assertFalse(pacer.isDue(quarterIn));
            assertEquals(SIXTY_HZ - SIXTY_HZ / 4, pacer.nanosUntilDue(quarterIn));
        }

        @Test
        @DisplayName("a frame exactly on the interval is due")
        void theBoundaryIsDue() {
            var pacer = atSixtyHz();
            pacer.frameEmitted(1_000_000_000L);

            assertFalse(pacer.isDue(1_000_000_000L + SIXTY_HZ - 1));
            assertTrue(pacer.isDue(1_000_000_000L + SIXTY_HZ));
        }

        @Test
        @DisplayName("a frame long overdue reports zero, never a negative wait")
        void anOverdueFrameNeverGoesNegative() {
            var pacer = atSixtyHz();
            pacer.frameEmitted(0L);

            // A negative here would become a "wait forever" timeout one
            // conversion later, and the window would stop updating.
            assertEquals(0L, pacer.nanosUntilDue(Duration.ofSeconds(10).toNanos()));
            assertTrue(pacer.isDue(Duration.ofSeconds(10).toNanos()));
        }

        @Test
        @DisplayName("the wait is shortened to when the frame comes due")
        void theWaitIsCappedToTheFrame() {
            var pacer = atSixtyHz();
            pacer.frameEmitted(0L);

            var capped = pacer.capWait(Duration.ofSeconds(1), true, SIXTY_HZ / 2);

            // Without this the held frame would sit until the event loop's
            // one-second heartbeat rather than until it was due.
            assertEquals(SIXTY_HZ - SIXTY_HZ / 2, capped.toNanos());
        }

        @Test
        @DisplayName("the wait is left alone when no frame is waiting")
        void noPendingFrameLeavesTheWaitAlone() {
            var pacer = atSixtyHz();
            pacer.frameEmitted(0L);
            var timeout = Duration.ofSeconds(1);

            // An idle window must still block for a full heartbeat; shortening
            // it here would spin the loop at the frame rate forever.
            assertEquals(timeout, pacer.capWait(timeout, false, 0L));
        }

        @Test
        @DisplayName("a caller's shorter timeout still wins")
        void theCallersTimeoutStillWins() {
            var pacer = atSixtyHz();
            pacer.frameEmitted(0L);
            var shorter = Duration.ofNanos(1_000L);

            assertEquals(shorter, pacer.capWait(shorter, true, 0L));
        }

        @Test
        @DisplayName("pacing holds the long-run rate to the interval")
        void theRateHoldsOverManyFrames() {
            var pacer = atSixtyHz();
            var now = 0L;
            var emitted = 0;

            // A loop that always asks immediately -- which is what a painter
            // calling repaint() from inside paint() does. Sampled every
            // microsecond: at millisecond granularity each frame rounds up to the
            // next whole millisecond, which is exactly why the live loop measures
            // 58.8 fps rather than 60 and would make this assertion about the
            // sampling rate instead of about the pacer.
            for (var step = 0L; step < Duration.ofSeconds(1).toNanos(); step += 1_000L) {
                now = step;
                if (pacer.isDue(now)) {
                    pacer.frameEmitted(now);
                    emitted++;
                }
            }

            // 60, not the million iterations that asked.
            assertEquals(60, emitted);
        }
    }

    @Nested
    @DisplayName("adopting the display's rate")
    class DisplayRate {

        private static FramePacer unpaced() {
            var pacer = FramePacer.fromProperties();
            assertFalse(pacer.isPacing(), "fixture assumes no goldberry.frame.rate is set");
            return pacer;
        }

        @Test
        @DisplayName("a reported rate starts pacing a loop that was not")
        void aReportedRateStartsPacing() {
            var pacer = unpaced();

            assertTrue(pacer.useDisplayRate(59.96));
            assertTrue(pacer.isPacing());
            assertEquals((long) (1_000_000_000L / 59.96), pacer.interval().toNanos());
        }

        @Test
        @DisplayName("a display that will not say its rate leaves the loop unpaced")
        void anUnknownRateLeavesItUnpaced() {
            var pacer = unpaced();

            // SDL documents refresh_rate as 0.0f for "unspecified", and some
            // drivers never fill it in. Dividing by it would stall the loop.
            assertFalse(pacer.useDisplayRate(0));
            assertFalse(pacer.isPacing());
            assertTrue(pacer.isDue(Long.MAX_VALUE / 2));
        }

        @Test
        @DisplayName("the same rate reported again is not a change")
        void repeatingTheRateIsNotAChange() {
            var pacer = unpaced();
            assertTrue(pacer.useDisplayRate(60));

            // Read once per pump, so this is the common case -- and it must not
            // log a line per frame.
            assertFalse(pacer.useDisplayRate(60));
        }

        @Test
        @DisplayName("moving to a faster display re-paces the loop")
        void movingToAFasterDisplayRepaces() {
            var pacer = unpaced();
            pacer.useDisplayRate(60);

            assertTrue(pacer.useDisplayRate(144));
            assertEquals(FramePacer.intervalForRate(144), pacer.interval().toNanos());
        }

        @Test
        @DisplayName("an explicit rate is never overwritten by the display")
        void anExplicitRateWins() {
            var explicit = new FramePacer(FramePacer.intervalForRate(30));

            // The whole point of setting the property is to override a rate the
            // driver reports wrongly.
            assertTrue(explicit.isExplicit());
            assertFalse(explicit.useDisplayRate(144));
            assertEquals(FramePacer.intervalForRate(30), explicit.interval().toNanos());
        }
    }

    @Nested
    @DisplayName("configuration")
    class Configuration {

        @Test
        @DisplayName("no property means unpaced")
        void absentPropertyIsUnpaced() {
            withRate(null, () -> assertFalse(FramePacer.fromProperties().isPacing()));
        }

        @Test
        @DisplayName("a rate becomes an interval")
        void aRateBecomesAnInterval() {
            withRate("60", () -> {
                var pacer = FramePacer.fromProperties();
                assertTrue(pacer.isPacing());
                assertEquals(SIXTY_HZ, pacer.interval().toNanos());
            });
        }

        @Test
        @DisplayName("a fractional rate is honoured")
        void aFractionalRateIsHonoured() {
            withRate("59.96", () -> {
                var pacer = FramePacer.fromProperties();
                assertTrue(pacer.isPacing());
                assertEquals((long) (1_000_000_000L / 59.96), pacer.interval().toNanos());
            });
        }

        @Test
        @DisplayName("zero and negative rates mean unpaced rather than a stalled loop")
        void zeroAndNegativeRatesAreUnpaced() {
            withRate("0", () -> assertFalse(FramePacer.fromProperties().isPacing()));
            withRate("-30", () -> assertFalse(FramePacer.fromProperties().isPacing()));
        }

        @Test
        @DisplayName("nonsense is ignored rather than fatal")
        void nonsenseIsIgnored() {
            // A malformed tuning flag must not stop a window opening.
            withRate("sixty", () -> assertFalse(FramePacer.fromProperties().isPacing()));
            withRate("  ", () -> assertFalse(FramePacer.fromProperties().isPacing()));
        }

        private void withRate(String value, Runnable body) {
            var previous = System.getProperty(FramePacer.RATE_PROPERTY);
            if (value == null) {
                System.clearProperty(FramePacer.RATE_PROPERTY);
            } else {
                System.setProperty(FramePacer.RATE_PROPERTY, value);
            }
            try {
                body.run();
            } finally {
                if (previous == null) {
                    System.clearProperty(FramePacer.RATE_PROPERTY);
                } else {
                    System.setProperty(FramePacer.RATE_PROPERTY, previous);
                }
            }
        }
    }
}
