package io.github.digitalsmile.goldberry.widgets.core;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.render.event.EventLoop;

/// An overlay going away: `closing → removed`, with the ordering that makes it
/// look right.
///
/// §1.7 asks for an "overlay enter/exit lifecycle" — `opening → open → closing →
/// removed` — and for a long time that was a specification with no subject,
/// because the widgets it describes did not exist. They do now, and surveying the
/// five of them ([ADR-0234]) says the *arrival* half needs nothing shared:
/// [Phase] is already the beginning and the end of it, and a `collapse`, a
/// `carousel` and a `tab` arrive with a phase and no other machinery at all.
///
/// The **departure** is where they were the same code twice. `dialog` and
/// `message` each held two flags, a timer and a six-line dance, and got the same
/// four things right independently:
///
/// 1. **Idempotence.** A second press during the fade is not a second answer,
///    which matters most where it costs most: two handlers on a save dialog is
///    two saves.
/// 2. **Two flags, not one.** "Input is off" starts at the instant an answer is
///    given (§1.7's "no ghost clicks"); "there is nothing left to draw" starts
///    when the fade runs out. Conflating them is why a closing dialog used to
///    stop asking for frames on the frame it started closing, and therefore
///    never faded at all ([ADR-0176]).
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
/// ADR-0081 refused a per-element controller for `spinner` and indeterminate
/// progress, because a loop that never ends has nothing to remember and a
/// controller would put two spinners permanently out of phase. ADR-0178 refused
/// one for a toast's reflow, because the interruption turned out to be three
/// lines of arithmetic. This is what was left of that idea after both refusals,
/// and it is not a controller: it drives no value, interpolates nothing, and owns
/// no clock. It owns a **timer and an ordering**, which is exactly the part two
/// widgets had each written out.
///
/// Confined to the UI thread, like everything else a `State` holds.
public final class Departure {

    /// How long the exit takes, in milliseconds — §1.7's `fast` for a `message`
    /// and `base` for a `dialog`, which is the one thing the two disagree about.
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
