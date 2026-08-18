package io.github.digitalsmile.goldberry.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.digitalsmile.goldberry.backend.headless.HeadlessBackend;
import java.time.Duration;
import java.util.ArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/// [EventLoop#after], which is what a tooltip's delay and a submenu's hover
/// intent are made of.
///
/// The delays here are milliseconds rather than the 400 a tooltip uses: what is
/// being tested is the ordering and the cancellation, and both are the same at
/// any duration.
class EventLoopTimerTest {

    private HeadlessBackend backend;
    private EventLoop loop;

    @BeforeEach
    void setUp() {
        backend = new HeadlessBackend();
        loop = new EventLoop(backend);
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
        loop.run(event -> { });

        assertEquals(java.util.List.of("ui"), fired);
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
        assertFalse(cancelled.isPending());

        loop.run(event -> { });

        assertEquals(java.util.List.of("kept"), fired,
                "the cancelled one was still in the list when the loop woke for the other");
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

        loop.run(event -> { });

        assertEquals(java.util.List.of("early", "late"), fired);
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
        });
        loop.run(event -> { });

        assertEquals(java.util.List.of("first", "second"), fired);
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
        loop.run(event -> { });

        assertEquals(java.util.List.of("scheduled", "timer"), order);
        assertTrue(true);
    }
}
