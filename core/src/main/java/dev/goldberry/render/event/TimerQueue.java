package dev.goldberry.render.event;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.function.LongSupplier;

import dev.goldberry.motion.Clock;

/// The timers a loop has been asked for, and the rule for firing them.
///
/// ```java
/// var clock = Clock.virtual();
/// var timers = TimerQueue.over(clock);
/// timers.after(Duration.ofMillis(150), () -> closed = true);
/// clock.advance(150);
/// timers.fireDue(); // closed is now true
/// ```
///
/// What [EventLoop#after] schedules into, taken out of the loop so that
/// something with no loop can keep the same promises: an offscreen session
/// drives a virtual clock, and a dialog asking for its closing animation to
/// end in 150 ms has to be told at 150 ms on that clock, in the order a
/// window would have told it.
///
/// Holds no thread of its own and fires nothing by itself. Whoever owns it
/// calls [#fireDue()] when it is ready to run what is due, which for a window
/// is after each pump of the platform's events.
///
/// Confined to the thread that uses it, like the loop it came out of.
///
/// Read more: [Windows, popups and the host](https://goldberry.dev/docs/guide/windows.html#threads).
public final class TimerQueue {

    private static final long NANOS_PER_MILLI = 1_000_000L;

    /// What has been scheduled, in the order it was made. A list rather than a
    /// heap: there are never many, and it is usually empty.
    private final List<EventLoop.Timer> timers = new ArrayList<>();

    /// Where "now" comes from, in nanoseconds on an arbitrary origin.
    private final LongSupplier nanos;

    private TimerQueue(LongSupplier nanos) {
        this.nanos = nanos;
    }

    /// A queue timed against `nanos`, which has [System#nanoTime()]'s contract.
    static TimerQueue nanos(LongSupplier nanos) {
        return new TimerQueue(Objects.requireNonNull(nanos, "nanos"));
    }

    /// A queue timed against `clock`, so that moving a virtual clock is what
    /// makes a timer due.
    public static TimerQueue over(Clock clock) {
        Objects.requireNonNull(clock, "clock");
        return new TimerQueue(() -> (long) (clock.nowMillis() * NANOS_PER_MILLI));
    }

    /// Schedules `action` for `delay` from now.
    ///
    /// Zero or negative is due at once, but still waits for the next
    /// [#fireDue()]: "later" is the only thing a caller asking a loop can mean.
    ///
    /// @return a handle that cancels it
    public EventLoop.Timer after(Duration delay, Runnable action) {
        Objects.requireNonNull(delay, "delay");
        Objects.requireNonNull(action, "action");
        var timer = new EventLoop.Timer(nanos.getAsLong() + Math.max(0L, delay.toNanos()), action);
        timers.add(timer);
        return timer;
    }

    /// When the earliest timer still pending is due, in this queue's
    /// nanoseconds, or empty when nothing is.
    OptionalLong nextDueNanos() {
        var earliest = Long.MAX_VALUE;
        for (var timer : timers) {
            if (timer.isPending()) {
                earliest = Math.min(earliest, timer.dueNanos());
            }
        }
        return earliest == Long.MAX_VALUE ? OptionalLong.empty() : OptionalLong.of(earliest);
    }

    /// When the earliest pending timer is due, in milliseconds on the clock
    /// this queue was made [over][#over(Clock)], or empty when nothing is
    /// pending.
    ///
    /// What lets an owner step a virtual clock **to** each timer in turn,
    /// rather than past all of them at once — a timer's action may schedule
    /// the next one, and a jump would fire it late.
    public OptionalLong nextDueMillis() {
        var next = nextDueNanos();
        return next.isPresent() ? OptionalLong.of(Math.ceilDiv(next.getAsLong(), NANOS_PER_MILLI)) : next;
    }

    /// When the earliest pending timer due **after** `millis` is due, in
    /// milliseconds on the clock this queue was made [over][#over(Clock)], or
    /// empty when none is.
    ///
    /// What an owner stepping a virtual clock steps to once it has fired what
    /// is already due. A timer due at or before `millis` is not an answer: it
    /// is either about to fire at the current time or a chain re-arming itself
    /// at it, and stepping to it would be standing still.
    public OptionalLong nextDueMillisAfter(double millis) {
        var after = (long) (millis * NANOS_PER_MILLI);
        var earliest = Long.MAX_VALUE;
        for (var timer : timers) {
            if (timer.isPending() && timer.dueNanos() > after) {
                earliest = Math.min(earliest, timer.dueNanos());
            }
        }
        return earliest == Long.MAX_VALUE
                ? OptionalLong.empty()
                : OptionalLong.of(Math.ceilDiv(earliest, NANOS_PER_MILLI));
    }

    /// The current time on this queue's clock, in nanoseconds.
    long nowNanos() {
        return nanos.getAsLong();
    }

    /// Runs whatever is due, and drops it.
    ///
    /// Collected before running: a timer's action may schedule another, and a
    /// tooltip's does — an action that added itself to the list being walked would
    /// fire in the same call for ever.
    ///
    /// And sorted before running. The list is in the order timers were made, and
    /// an owner that overslept — a loaded macOS runner did, by more than the gap
    /// between a 5 ms and a 30 ms timer — finds both due at once. Firing them in
    /// list order then fires the later one first, which is the one ordering a
    /// caller can never have meant.
    ///
    /// @return whether anything ran
    public boolean fireDue() {
        if (timers.isEmpty()) {
            return false;
        }
        var now = nanos.getAsLong();
        var due = new ArrayList<EventLoop.Timer>();
        for (var iterator = timers.iterator(); iterator.hasNext(); ) {
            var timer = iterator.next();
            if (!timer.isPending()) {
                iterator.remove();
            } else if (timer.dueNanos() <= now) {
                iterator.remove();
                due.add(timer);
            }
        }
        due.sort(Comparator.comparingLong(EventLoop.Timer::dueNanos));
        var ran = false;
        for (var timer : due) {
            // Cancelled *by an earlier timer in this same batch* -- the reason
            // this is re-read rather than assumed from the loop above.
            ran |= timer.fire();
        }
        return ran;
    }

    /// Whether any timer is still waiting to fire.
    public boolean hasPending() {
        for (var timer : timers) {
            if (timer.isPending()) {
                return true;
            }
        }
        return false;
    }
}
