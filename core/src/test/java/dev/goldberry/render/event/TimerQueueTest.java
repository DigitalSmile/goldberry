package dev.goldberry.render.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.motion.Clock;

/// The timers an event loop keeps, kept without a loop: what an offscreen
/// session schedules a dialog's closing animation on.
///
/// `EventLoopTimerTest` runs the same rules through a real loop and a
/// headless backend. These are the rules alone, against a virtual clock.
///
/// Read more:
/// [The virtual clock](https://goldberry.dev/docs/guide/testing.html#the-virtual-clock).
@DisplayName("a timer queue")
class TimerQueueTest {

    private final Clock.Virtual clock = Clock.virtual();

    private final TimerQueue timers = TimerQueue.over(clock);

    private final List<String> fired = new ArrayList<>();

    @Test
    @DisplayName("fires a timer once its clock has reached it, and not before")
    void firesWhenDue() {
        timers.after(Duration.ofMillis(100), () -> fired.add("a"));
        clock.advance(99);
        assertFalse(timers.fireDue());
        clock.advance(1);
        assertTrue(timers.fireDue());
        assertEquals(List.of("a"), fired);
        assertFalse(timers.hasPending(), "and it fires once");
    }

    @Test
    @DisplayName("fires a zero delay on the next call, never inside after")
    void zeroIsLater() {
        timers.after(Duration.ZERO, () -> fired.add("later"));
        assertEquals(List.of(), fired);
        timers.fireDue();
        assertEquals(List.of("later"), fired);
    }

    @Test
    @DisplayName("fires two that came due together in the order they were due")
    void inOrderOfDue() {
        timers.after(Duration.ofMillis(30), () -> fired.add("thirty"));
        timers.after(Duration.ofMillis(5), () -> fired.add("five"));
        clock.advance(50);
        timers.fireDue();
        assertEquals(List.of("five", "thirty"), fired);
    }

    @Test
    @DisplayName("does not fire one an earlier timer in the same batch cancelled")
    void cancelledInTheBatch() {
        var later = new EventLoop.Timer[1];
        timers.after(Duration.ofMillis(1), () -> later[0].cancel());
        later[0] = timers.after(Duration.ofMillis(2), () -> fired.add("cancelled"));
        clock.advance(10);
        timers.fireDue();
        assertEquals(List.of(), fired);
        assertFalse(later[0].isPending());
    }

    @Test
    @DisplayName("does not fire one scheduled by a timer it is firing until the next call")
    void scheduledWhileFiring() {
        timers.after(Duration.ZERO, () -> timers.after(Duration.ZERO, () -> fired.add("second")));
        timers.fireDue();
        assertEquals(List.of(), fired);
        timers.fireDue();
        assertEquals(List.of("second"), fired);
    }

    @Test
    @DisplayName("says when the next pending timer is due, on its clock")
    void nextDue() {
        assertEquals(OptionalLong.empty(), timers.nextDueMillis());
        clock.advance(10);
        var first = timers.after(Duration.ofMillis(40), () -> {});
        timers.after(Duration.ofMillis(90), () -> {});
        assertEquals(OptionalLong.of(50), timers.nextDueMillis());
        first.cancel();
        assertEquals(OptionalLong.of(100), timers.nextDueMillis(), "a cancelled timer is not next");
    }

    @Test
    @DisplayName("says when the next timer after a time is due, passing over what is due by then")
    void nextDueAfter() {
        assertEquals(OptionalLong.empty(), timers.nextDueMillisAfter(0));
        timers.after(Duration.ZERO, () -> fired.add("now"));
        timers.after(Duration.ofMillis(1), () -> fired.add("one"));
        timers.after(Duration.ofMillis(40), () -> fired.add("forty"));
        assertEquals(OptionalLong.of(0), timers.nextDueMillis(), "the zero delay is the earliest");
        assertEquals(OptionalLong.of(1), timers.nextDueMillisAfter(0), "and is passed over after zero");
        assertEquals(OptionalLong.of(40), timers.nextDueMillisAfter(1));
        assertEquals(OptionalLong.empty(), timers.nextDueMillisAfter(40));
    }
}
