package io.github.digitalsmile.goldberry.widgets.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.digitalsmile.goldberry.RendererRequirement;
import io.github.digitalsmile.goldberry.widgets.TestHost;

/// §1.7's `closing → removed`, as the one object two widgets had each written out
/// ([ADR-0234]).
///
/// `dialog` and `message` held two flags, a timer and the same six lines, and got
/// the same four rules right independently. Each of those rules is a case here,
/// and each was a bug in one of them at some point:
///
/// - **idempotence**, because two handlers on a save dialog is two saves;
/// - **two flags**, because using "input is off" for "stop drawing" is why a
///   closing dialog once stopped asking for frames on the frame it started
///   closing, and therefore never faded at all ([ADR-0176]);
/// - **stop drawing, then tell the application**, which matters for one frame;
/// - **gone at once with no host or under reduced motion**, because a hundred
///   milliseconds of nothing happening is not a courtesy.
class DepartureTest {

    private static final double EXIT_MILLIS = 100;

    private TestHost host;
    private List<String> log;
    private int rebuilds;

    @BeforeEach
    void setUp() {
        RendererRequirement.enforce();
        host = new TestHost();
        log = new ArrayList<>();
        rebuilds = 0;
    }

    /// A departure whose `setState` runs the mutation and counts the rebuild —
    /// which is what `State.setState` does, minus the element.
    private Departure departure() {
        return new Departure(EXIT_MILLIS, mutation -> {
            mutation.run();
            rebuilds++;
        });
    }

    @Nested
    @DisplayName("the ordinary path")
    class Animated {

        @Test
        @DisplayName("input goes off at once and drawing stops when the fade runs out")
        void twoFlags() {
            var leaving = departure();

            assertFalse(leaving.hasBegun());
            assertFalse(leaving.isOver());

            assertTrue(leaving.begin(host, false, () -> log.add("told")));

            assertTrue(leaving.hasBegun(), "input is still live on a thing that is going away");
            assertFalse(leaving.isOver(), "it stopped drawing before it had faded");
            assertEquals(List.of(), log, "the application was told before the fade finished");

            host.tick();

            assertTrue(leaving.isOver());
            assertEquals(List.of("told"), log);
        }

        /// The ordering that matters for exactly one frame: the handler usually
        /// rebuilds the tree without this overlay in it, and a state still
        /// mid-fade would hand a half-faded panel to whatever element the
        /// reconciler reused.
        @Test
        @DisplayName("drawing stops before the application is told, not after")
        void orderAtTheEnd() {
            var leaving = departure();
            leaving.begin(host, false, () -> log.add(leaving.isOver() ? "over" : "still drawing"));

            host.tick();

            assertEquals(List.of("over"), log, "the handler ran while the overlay was still being drawn");
        }

        @Test
        @DisplayName("it is scheduled for its own duration and nobody else's")
        void duration() {
            departure().begin(host, false, null);

            assertEquals(List.of(Duration.ofMillis((long) EXIT_MILLIS)), host.scheduledDelays());
        }

        /// The rule that matters most where it costs most: two handlers on a save
        /// dialog is two saves.
        @Test
        @DisplayName("a second press during the fade is not a second departure")
        void idempotent() {
            var leaving = departure();

            assertTrue(leaving.begin(host, false, () -> log.add("told")));
            assertFalse(leaving.begin(host, false, () -> log.add("told again")));

            host.tick();

            assertEquals(List.of("told"), log);
            assertEquals(1, host.scheduledDelays().size(), "two timers, so the application is told twice");
        }

        @Test
        @DisplayName("a rebuild is asked for at each end of it")
        void asksForFrames() {
            var leaving = departure();
            leaving.begin(host, false, null);
            assertEquals(1, rebuilds, "nothing asked for the frame that starts the fade");

            host.tick();

            assertEquals(2, rebuilds, "nothing asked for the frame that stops drawing it");
        }
    }

    @Nested
    @DisplayName("when there is nothing to animate against")
    class Instant {

        /// A widget built and driven outside a window — a test, a golden.
        @Test
        @DisplayName("no host means gone now, in one call")
        void noHost() {
            var leaving = departure();

            assertTrue(leaving.begin(null, false, () -> log.add("told")));

            assertTrue(leaving.hasBegun());
            assertTrue(leaving.isOver());
            assertEquals(List.of("told"), log);
        }

        /// §1.7 asks for movement to be **removed** rather than shortened, and a
        /// hundred milliseconds of nothing happening is not a courtesy.
        @Test
        @DisplayName("reduced motion means gone now, with no timer at all")
        void reducedMotion() {
            var leaving = departure();

            leaving.begin(host, true, () -> log.add("told"));

            assertTrue(leaving.isOver());
            assertEquals(List.of("told"), log);
            assertEquals(List.of(), host.scheduledDelays(), "a timer was set for an animation that is not running");
        }
    }

    @Nested
    @DisplayName("the phase it hands over")
    class Phases {

        @Test
        @DisplayName("the arrival's, until it begins")
        void arrivingUntilItBegins() {
            var arriving = new Phase(Phase.Kind.ENTERING);
            var leaving = departure();

            assertSame(arriving, leaving.phaseOr(arriving));

            leaving.begin(host, false, null);

            assertEquals(Phase.Kind.LEAVING, leaving.phaseOr(arriving).kind());
        }
    }

    @Nested
    @DisplayName("giving the timer back")
    class Cancelling {

        /// An overlay removed while it was fading would otherwise call a handler
        /// for a tree that is gone.
        ///
        /// Asserted on the **timer** and not on the handler, because
        /// [TestHost#tick()] fires whatever was scheduled whether or not it was
        /// cancelled — a fixture that drives the action itself rather than a loop
        /// that consults a flag. What a real `EventLoop` does with a cancelled
        /// timer is `EventLoop`'s to test; what this owes is that it gave the
        /// timer back.
        @Test
        @DisplayName("cancelling gives the timer back")
        void cancel() {
            var leaving = departure();
            leaving.begin(host, false, () -> log.add("told"));
            assertFalse(host.allTimersCancelled(), "the fixture handed out no timer to cancel");

            leaving.cancel();

            assertTrue(host.allTimersCancelled(), "a fading overlay that is removed leaves a timer behind");
        }

        @Test
        @DisplayName("cancelling one that never began is harmless")
        void cancelBeforeBeginning() {
            departure().cancel();

            assertEquals(List.of(), log);
        }
    }
}
