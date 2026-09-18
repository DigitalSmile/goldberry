package io.github.digitalsmile.goldberry.render.backend.sdl3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.time.Duration;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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
    @DisplayName("lateness")
    class Lateness {

        @Test
        @DisplayName("an unpaced pacer is never late, because it has no interval to be late against")
        void unpacedIsNeverLate() {
            var pacer = new FramePacer(0L);
            pacer.frameEmitted(0L);

            assertEquals(0, pacer.missedRefreshes(0L, Duration.ofSeconds(1).toNanos()));
        }

        @Test
        @DisplayName("the first frame is never late")
        void theFirstFrameIsNeverLate() {
            var pacer = atSixtyHz();

            // Nothing has been emitted, so there is no previous frame for this
            // one to be a refresh behind.
            assertEquals(0, pacer.missedRefreshes(0L, Duration.ofSeconds(1).toNanos()));
        }

        @Test
        @DisplayName("a frame delivered on its interval is not late")
        void anOnTimeFrameIsNotLate() {
            var pacer = atSixtyHz();
            pacer.frameEmitted(0L);

            // Asked for immediately, delivered one interval later, which is as
            // fast as a paced loop is allowed to go.
            assertEquals(0, pacer.missedRefreshes(1_000L, SIXTY_HZ));
        }

        @Test
        @DisplayName("a frame two intervals late costs one refresh, and three costs two")
        void anOverrunCostsARefreshPerInterval() {
            var pacer = atSixtyHz();
            pacer.frameEmitted(0L);

            assertEquals(1, pacer.missedRefreshes(1_000L, 2 * SIXTY_HZ));
            assertEquals(2, pacer.missedRefreshes(1_000L, 3 * SIXTY_HZ));
        }

        /// **The case that makes this number honest.** §1.7 makes the loop idle
        /// when nothing asks for a frame, so a window nobody touched for a second
        /// has sixty refreshes' worth of gap since its last frame and has dropped
        /// nothing at all: it drew every frame it was asked for.
        @Test
        @DisplayName("an idle second is not sixty dropped frames")
        void anIdleLoopDropsNothing() {
            var pacer = atSixtyHz();
            pacer.frameEmitted(0L);
            var aSecond = Duration.ofSeconds(1).toNanos();

            // Nothing was asked for until the very end of that second, and it was
            // emitted the moment it was asked for.
            assertEquals(0, pacer.missedRefreshes(aSecond, aSecond));
        }

        @Test
        @DisplayName("lateness is counted from the request, not from the last frame")
        void latenessIsCountedFromTheRequest() {
            var pacer = atSixtyHz();
            pacer.frameEmitted(0L);

            // Asked for ten intervals after the last frame -- nine of which
            // nobody wanted a frame in -- and delivered two intervals after that.
            // Two refreshes went by with somebody waiting, not eleven: the nine
            // idle ones are not this loop's failure to keep up.
            assertEquals(2, pacer.missedRefreshes(10 * SIXTY_HZ, 12 * SIXTY_HZ));
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

        /// What the property is set to, and the interval it becomes — `null` in
        /// the second column for the property unset, and in the third for a
        /// value that leaves the loop unpaced rather than stalled. A malformed
        /// tuning flag must not stop a window opening, so nonsense is in the
        /// same column as zero.
        static Stream<Arguments> rates() {
            return Stream.of(
                    arguments("no property means unpaced", null, null),
                    arguments("a rate becomes an interval", "60", SIXTY_HZ),
                    arguments("a fractional rate is honoured", "59.96", (long) (1_000_000_000L / 59.96)),
                    arguments("zero is unpaced rather than a stalled loop", "0", null),
                    arguments("and so is a negative rate", "-30", null),
                    arguments("a word is ignored rather than fatal", "sixty", null),
                    arguments("and so is whitespace", "  ", null));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("rates")
        @DisplayName("a readable positive rate is an interval, and everything else is unpaced")
        void fromProperties(String what, String rate, Long interval) {
            withRate(rate, () -> {
                var pacer = FramePacer.fromProperties();

                if (interval == null) {
                    assertFalse(pacer.isPacing(), what);
                } else {
                    assertTrue(pacer.isPacing(), what);
                    assertEquals(interval, pacer.interval().toNanos(), what);
                }
            });
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
