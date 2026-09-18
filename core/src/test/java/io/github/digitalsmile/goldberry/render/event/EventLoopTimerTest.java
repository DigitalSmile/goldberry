package io.github.digitalsmile.goldberry.render.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import io.github.digitalsmile.goldberry.render.backend.headless.HeadlessBackend;
import io.github.digitalsmile.goldberry.render.model.LogicalSize;
import io.github.digitalsmile.goldberry.render.window.WindowSpec;

/// [EventLoop#after], which is what a tooltip's delay and a submenu's hover
/// intent are made of.
///
/// The delays here are milliseconds rather than the 400 a tooltip uses: what is
/// being tested is the ordering and the cancellation, and both are the same at
/// any duration.
///
/// **On a clock this test owns.** The loop reads its `now` from a seam, and
/// [TestClock] is the other side of it, so "ten milliseconds later" is a value
/// this class assigns rather than a wait it hopes for. `bothOverdueAtOnce` is
/// why: it slept 25 ms to make two timers overdue at once, and failed under a
/// loaded machine — a wall-clock test of scheduling has the same problem the
/// scheduling has (the 2026-09-18 review, §6). Moving the clock and calling
/// `wakeup()` is the pair: the first changes what the loop will conclude, the
/// second ends the pump it is parked in so it is asked.
class EventLoopTimerTest {

    private HeadlessBackend backend;
    private TestClock clock;
    private EventLoop loop;

    @BeforeEach
    void setUp() {
        backend = new HeadlessBackend();
        clock = new TestClock();
        loop = clock.loopOver(backend);
    }

    @AfterEach
    void tearDown() {
        loop.close();
        backend.close();
    }

    /// Opened so the loop has a window and does not stop for want of one.
    private void withAWindow() {
        backend.createWindow(WindowSpec.of("timers", LogicalSize.of(100, 100)));
    }

    @Test
    @Timeout(10)
    @DisplayName("a timer fires on the UI thread, after its delay")
    void fires() {
        withAWindow();
        var fired = new ArrayList<String>();
        var uiThread = Thread.currentThread();

        loop.after(Duration.ofMillis(5), () -> {
            fired.add(Thread.currentThread() == uiThread ? "ui" : "elsewhere");
            loop.stop();
        });
        clock.advance(Duration.ofMillis(5));
        loop.run(event -> {});

        assertEquals(List.of("ui"), fired);
    }

    /// The one thing a caller ever does with the handle: a hover that ends before
    /// the delay is up cancels one on every pointer move.
    @Test
    @Timeout(10)
    @DisplayName("a cancelled timer does not fire")
    void cancelled() {
        withAWindow();
        var fired = new ArrayList<String>();

        var cancelled = loop.after(Duration.ofMillis(5), () -> fired.add("cancelled"));
        loop.after(Duration.ofMillis(20), () -> {
            fired.add("kept");
            loop.stop();
        });
        cancelled.cancel();
        clock.advance(Duration.ofMillis(20));
        assertFalse(cancelled.isPending());

        loop.run(event -> {});

        assertEquals(
                List.of("kept"), fired, "the cancelled one was still in the list when the loop woke for the other");
    }

    /// The 2026-09-18 review's C14.
    ///
    /// `isPending()` was `!cancelled`, and `fireDueTimers` removes a timer from
    /// the list without telling it anything — so a timer that had *fired*
    /// answered "still going to fire", for ever. A caller holding one to decide
    /// whether to schedule another was told to wait for something that had
    /// already happened.
    @Test
    @Timeout(10)
    @DisplayName("a timer that has fired is no longer pending")
    void firedIsNotPending() {
        withAWindow();
        var pendingInside = new ArrayList<Boolean>();
        var handle = new EventLoop.Timer[1];

        handle[0] = loop.after(Duration.ofMillis(5), () -> {
            // Asked from inside its own action: it is firing, not waiting.
            pendingInside.add(handle[0].isPending());
            loop.stop();
        });
        assertTrue(handle[0].isPending(), "a timer that has not fired is pending");
        clock.advance(Duration.ofMillis(5));

        loop.run(event -> {});

        assertEquals(List.of(false), pendingInside);
        assertFalse(handle[0].isPending(), "a timer that has fired is not going to fire again");
    }

    @Test
    @Timeout(10)
    @DisplayName("cancelling a timer that already fired changes nothing and does not throw")
    void cancelAfterFiring() {
        withAWindow();
        var fired = new ArrayList<String>();
        var handle = new EventLoop.Timer[1];

        handle[0] = loop.after(Duration.ofMillis(5), () -> {
            fired.add("once");
            loop.stop();
        });
        clock.advance(Duration.ofMillis(5));
        loop.run(event -> {});

        handle[0].cancel();

        assertEquals(List.of("once"), fired);
        assertFalse(handle[0].isPending());
    }

    @Test
    @Timeout(10)
    @DisplayName("timers fire in the order they come due, not the order they were made")
    void ordering() {
        withAWindow();
        var fired = new ArrayList<String>();

        loop.after(Duration.ofMillis(30), () -> {
            fired.add("late");
            loop.stop();
        });
        loop.after(Duration.ofMillis(5), () -> fired.add("early"));
        clock.advance(Duration.ofMillis(30));

        loop.run(event -> {});

        assertEquals(List.of("early", "late"), fired);
    }

    /// The same rule when the loop was asleep past both: a pump that overslept
    /// hands `fireDueTimers` both at once, and list order is creation order. This
    /// is the case the wall clock only produces on a loaded machine, so it is
    /// produced on purpose here.
    @Test
    @Timeout(10)
    @DisplayName("two timers both overdue at one wake-up still fire in due order")
    void bothOverdueAtOnce() {
        withAWindow();
        var fired = new ArrayList<String>();

        loop.after(Duration.ofMillis(10), () -> {
            fired.add("late");
            loop.stop();
        });
        loop.after(Duration.ofMillis(1), () -> fired.add("early"));
        // Past both, exactly — where a `Thread.sleep(25)` was only *probably*
        // past both, and on a loaded machine sometimes past neither in time.
        clock.advance(Duration.ofMillis(25));

        loop.run(event -> {});

        assertEquals(List.of("early", "late"), fired);
    }

    /// The seam itself: a delay the loop has not reached is a delay nothing has
    /// fired, however long the test has been running.
    @Test
    @Timeout(10)
    @DisplayName("a timer whose delay has not elapsed on this clock does not fire")
    void theClockIsTheOnlyTime() {
        withAWindow();
        var fired = new ArrayList<String>();

        loop.after(Duration.ofSeconds(30), () -> fired.add("thirty seconds"));
        loop.after(Duration.ofMillis(1), () -> {
            fired.add("one millisecond");
            loop.stop();
        });
        clock.advance(Duration.ofMillis(2));

        loop.run(event -> {});

        // Proving an absence without waiting for it, which is the whole point:
        // the half-minute timer is not late, it is *not due*.
        assertEquals(List.of("one millisecond"), fired);
    }

    /// A timer's action scheduling another is the ordinary case — a tooltip
    /// closing schedules the next one's delay — and it must not fire in the same
    /// iteration, or a self-rescheduling timer is an infinite loop.
    @Test
    @Timeout(10)
    @DisplayName("a timer may schedule another")
    void reschedules() {
        withAWindow();
        var fired = new ArrayList<String>();

        loop.after(Duration.ZERO, () -> {
            fired.add("first");
            loop.after(Duration.ofMillis(5), () -> {
                fired.add("second");
                loop.stop();
            });
            // From inside the action, because that is when the second timer
            // comes into existence. The loop is between pumps here, so moving
            // the clock needs no wakeup to be noticed.
            clock.advance(Duration.ofMillis(5));
        });
        loop.run(event -> {});

        assertEquals(List.of("first", "second"), fired);
    }

    @Test
    @Timeout(10)
    @DisplayName("a zero delay is the next iteration, not this one")
    void zeroIsNextIteration() {
        withAWindow();
        var order = new ArrayList<String>();

        loop.after(Duration.ZERO, () -> {
            order.add("timer");
            loop.stop();
        });
        order.add("scheduled");
        loop.run(event -> {});

        assertEquals(List.of("scheduled", "timer"), order);
    }
}
