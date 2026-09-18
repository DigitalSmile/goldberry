package io.github.digitalsmile.goldberry.render.event;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import io.github.digitalsmile.goldberry.render.Backend;

/// A clock a test moves, and the event loop that reads it.
///
/// ## What this is for
///
/// Every delay a widget can ask for goes through [EventLoop#after] — a tooltip's
/// dwell, a hover-hold, a menu's safe triangle, a toast's stay, a carousel's
/// interval. A test that wants to know what happens *after* one of them had no
/// way to ask but to sleep, and about thirty of them did: `docs/testing.md` §0
/// calls determinism a feature under test, and a suite that proves absence by
/// waiting 1.5 seconds is neither deterministic nor quick (the 2026-09-18
/// review, §6).
///
/// So the loop's clock is a seam, and this is the other side of it:
///
/// ```java
/// var clock = new TestClock();
/// var loop = clock.loopOver(backend);
/// // ... open a tooltip's owner, start the loop on another turn ...
/// clock.advance(Duration.ofMillis(500));
/// backend.wakeup();   // the pump returns, and the dwell has elapsed
/// ```
///
/// **Both halves are needed.** Moving the clock alone changes what
/// `fireDueTimers` will conclude and does not get it asked: the loop is inside
/// `pumpEvents`, parked for whatever `nextTimeout` computed. `wakeup()` is what
/// ends that park — it is the one call a backend promises is safe from anywhere,
/// and the headless backend's park is written to be interrupted by it.
///
/// The clock does not advance by itself and never goes backwards, which is the
/// whole of what `nanoTime`'s contract asks of it.
public final class TestClock {

    /// Nanoseconds, on an origin chosen to be far from zero so that a test which
    /// accidentally compares against a raw `nanoTime` reading fails loudly rather
    /// than passing by coincidence.
    private final AtomicLong nanos = new AtomicLong(Duration.ofHours(3).toNanos());

    /// The reading an [EventLoop] gets.
    public long nanoTime() {
        return nanos.get();
    }

    /// Moves it forward. Refuses to go backwards, because `nanoTime` does not.
    public void advance(Duration by) {
        Objects.requireNonNull(by, "by");
        if (by.isNegative()) {
            throw new IllegalArgumentException("a clock does not go backwards: " + by);
        }
        nanos.addAndGet(by.toNanos());
    }

    /// Moves it forward and ends the pump the loop is parked in, which is the
    /// pair a test almost always wants.
    ///
    /// Separate from [#advance(Duration)] so that a test driving several loops,
    /// or moving the clock while the loop is *not* running, can still say exactly
    /// what it means.
    public void advance(Duration by, Backend backend) {
        advance(by);
        Objects.requireNonNull(backend, "backend").wakeup();
    }

    /// An event loop over `backend` that reads this clock.
    ///
    /// Here rather than on `EventLoop` because the constructor it calls is
    /// package-private: a scheduling internal does not belong in the toolkit's
    /// API for the sake of a test helper, which is the same arrangement
    /// [TestTimers] has and the same one `TestFrames` has over `Frame`.
    public EventLoop loopOver(Backend backend) {
        return new EventLoop(Objects.requireNonNull(backend, "backend"), this::nanoTime);
    }
}
