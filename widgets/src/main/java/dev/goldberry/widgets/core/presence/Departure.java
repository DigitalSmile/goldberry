package dev.goldberry.widgets.core.presence;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import dev.goldberry.Host;
import dev.goldberry.render.event.EventLoop;

/// An overlay going away: `closing → removed`, with the ordering that makes it
/// look right.
///
/// An overlay's lifecycle is `opening → open → closing → removed`. The *arrival*
/// half needs nothing shared: [Phase] is the beginning and the end of it, and a
/// `collapse`, a `carousel` and a `tab` arrive with a phase and no other
/// machinery at all.
///
/// The **departure** is where `dialog` and `message` would each hold two flags,
/// a timer and a six-line dance. This is that dance, written once, and it gets
/// four things right:
///
/// 1. **Idempotence.** A second press during the fade is not a second answer,
///    which matters most where it costs most: two handlers on a save dialog is
///    two saves.
/// 2. **Two flags, not one.** "Input is off" starts at the instant an answer is
///    given, so there are no ghost clicks; "there is nothing left to draw"
///    starts when the fade runs out. Conflating them makes a closing dialog stop
///    asking for frames on the frame it started closing, so it never fades at
///    all.
/// 3. **Stop drawing, then tell the application** — in that order, and it matters
///    for one frame: the handler usually rebuilds the tree without this overlay
///    in it, and a state still mid-fade would hand a half-faded panel to whatever
///    element the reconciler reused.
/// 4. **No host, or reduced motion, means gone now.** There is nothing to animate
///    against, or a reader has asked not to be animated at; either way a hundred
///    milliseconds of nothing happening is not a courtesy.
///
/// ## Why this is not an `AnimationController`
///
/// A per-element controller is the wrong shape for a `spinner` or an
/// indeterminate progress bar, because a loop that never ends has nothing to
/// remember and a controller would put two spinners permanently out of phase;
/// and it is the wrong shape for a toast's reflow, which is three lines of
/// arithmetic. This is not a controller: it drives no value, interpolates
/// nothing, and owns no clock. It owns a **timer and an ordering**, which is
/// exactly the part two widgets would each have written out.
///
/// Confined to the UI thread, like everything else a `State` holds.
///
/// Read more: [Overlays](https://goldberry.dev/docs/components/overlays.html).
public final class Departure {

    /// How long the exit takes, in milliseconds — the design system's `fast` for a
    /// `message` and `base` for a `dialog`, which is the one thing the two disagree about.
    private final double millis;

    /// The owner's `setState`, so a change here asks for the rebuild that draws
    /// it. Passed in rather than inherited: this is a field a state *has*, not a
    /// base class it extends, and a departure is not the only thing an overlay's
    /// state holds.
    private final Consumer<Runnable> setState;

    /// Null until it begins, and never null again: an overlay departs once.
    private @Nullable Phase phase;

    /// True once the fade has run out — see the class note's second point.
    private boolean over;

    /// The timer that ends it. Cancelled on unmount, or an overlay removed while
    /// it was fading would call a handler for a tree that is gone.
    private EventLoop.@Nullable Timer pending;

    /// @param millis   how long the exit takes
    /// @param setState the owner's `setState`, usually `this::setState`
    public Departure(double millis, Consumer<Runnable> setState) {
        this.millis = millis;
        this.setState = Objects.requireNonNull(setState, "setState");
    }

    /// Whether the exit has started — **input is off** from this moment.
    public boolean hasBegun() {
        return phase != null;
    }

    /// Whether the exit has finished — **there is nothing left to draw**.
    public boolean isOver() {
        return over;
    }

    /// The phase to hand the part being drawn: the departure's if it has begun,
    /// and `arriving` otherwise.
    ///
    /// One call rather than two fields at every build site, because the choice is
    /// always this one and writing it out is how the two get swapped.
    public Phase phaseOr(Phase arriving) {
        return phase == null ? arriving : phase;
    }

    /// Starts the exit, running `then` when it is over.
    ///
    /// Idempotent: a second call while one is running is not a second departure.
    ///
    /// @param host          the window, or null in a test with none — see the
    ///                      class note's fourth point
    /// @param reducedMotion what the last frame said the reader asked for
    /// @param then          told when there is nothing left to draw, or null
    /// @return whether this call is the one that started it
    public boolean begin(@Nullable Host host, boolean reducedMotion, @Nullable Runnable then) {
        if (phase != null) {
            return false;
        }
        if (host == null || reducedMotion) {
            phase = new Phase(Phase.Kind.LEAVING, millis);
            over = true;
            if (then != null) {
                then.run();
            }
            return true;
        }
        setState.accept(() -> phase = new Phase(Phase.Kind.LEAVING, millis));
        pending = host.after(Duration.ofMillis((long) millis), () -> {
            pending = null;
            setState.accept(() -> over = true);
            if (then != null) {
                then.run();
            }
        });
        return true;
    }

    /// Gives back the timer. Called from the owner's `dispose`.
    public void cancel() {
        if (pending != null) {
            pending.cancel();
            pending = null;
        }
    }
}
