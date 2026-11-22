package dev.goldberry.frame.pacing;

import java.time.Duration;

import dev.goldberry.render.desktop.PowerState;

/// When an animating window's next frame should come: now, after a wait, or
/// not until the window can be seen again.
///
/// | the window is | animation frames |
/// |---|---|
/// | occluded, hidden or minimized | none, until it can be seen |
/// | not focused, with an inactive cap | at most the cap a second |
/// | on a desktop saving power | at most [PowerState#SAVING_FRAME_RATE] a second |
/// | anything else | as fast as the window presents |
///
/// The caps combine by taking the lower. **The inactive cap is off unless an
/// application sets one**: a window beside the one being typed in is still
/// being looked at, a video in it is still being watched, and a browser does not
/// slow a visible window down either. An application whose motion is
/// decoration sets one, or pauses the motion itself through `:window-inactive`.
///
/// Pure arithmetic on the activity and the time since the last frame, so every
/// case is tested without a window.
public final class AnimationPacing {

    /// What to do about the next animation frame.
    public sealed interface Next {

        /// Ask for it now.
        record Now() implements Next {}

        /// Ask for it after `delay`.
        record After(Duration delay) implements Next {}

        /// Do not ask: the window cannot be seen. Ask again when it can.
        record Paused() implements Next {}
    }

    private static final Next NOW = new Next.Now();

    private static final Next PAUSED = new Next.Paused();

    /// The cap for a window without the focus, in frames a second; zero for none.
    private double inactiveRate;

    /// A pacing with no inactive cap: what every window starts with.
    public AnimationPacing() {}

    /// The cap for a window without the focus, in frames a second; zero for none.
    public double inactiveRate() {
        return inactiveRate;
    }

    /// Caps a window without the focus at `hertz` animation frames a second,
    /// or lifts the cap for zero.
    ///
    /// @throws IllegalArgumentException when `hertz` is negative or not finite
    public void inactiveRate(double hertz) {
        if (!(hertz >= 0) || Double.isInfinite(hertz)) {
            throw new IllegalArgumentException("an inactive frame cap of " + hertz + " frames a second");
        }
        inactiveRate = hertz;
    }

    /// The cap that applies to `activity`, in frames a second; zero for none.
    public double rate(WindowActivity activity) {
        var rate = 0.0;
        if (!activity.active() && inactiveRate > 0) {
            rate = inactiveRate;
        }
        if (activity.saving()) {
            rate = rate > 0 ? Math.min(rate, PowerState.SAVING_FRAME_RATE) : PowerState.SAVING_FRAME_RATE;
        }
        return rate;
    }

    /// What to do about the next animation frame of a window in `activity`
    /// whose last frame began `sinceLastFrame` ago.
    public Next next(WindowActivity activity, Duration sinceLastFrame) {
        if (activity.occluded()) {
            return PAUSED;
        }
        var rate = rate(activity);
        if (rate <= 0) {
            return NOW;
        }
        var interval = (long) (1_000_000_000L / rate);
        var waited = Math.max(0L, sinceLastFrame.toNanos());
        return waited >= interval ? NOW : new Next.After(Duration.ofNanos(interval - waited));
    }
}
