package io.github.digitalsmile.goldberry.widgets.panel.carousel;

import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.backend.EventLoop;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;

/// Which slide a [Carousel] is showing, and whether the rotation is running.
///
/// The rotation is the whole of why this class is interesting. Everything else in
/// §5 is a description; this holds a **timer**, which is a thing that keeps
/// happening, and §1.7 rule 4 makes the conditions for stopping it as important
/// as the rotation itself ([ADR-0165]).
final class CarouselState extends State<Carousel> {

    /// Only meaningful while the widget is uncontrolled.
    private int index;

    /// The pending advance, or null when nothing is scheduled.
    ///
    /// One timer, rescheduled after each slide rather than a repeating one,
    /// because "pause" then means "do not schedule the next" and needs no second
    /// mechanism to suspend.
    private EventLoop.Timer pending;

    /// The window this is being built into, captured for the timer.
    private Host host;

    /// Whether the pointer is over the carousel.
    ///
    /// **Not `setState`.** Nothing drawn depends on it — `:hover` is the
    /// stylesheet's and arrives through the cascade — and rebuilding on a hover
    /// would put a build on every pointer move across the widget.
    private boolean hovered;

    /// Whether the strip or one of the carousel's own controls has focus.
    ///
    /// Not focus *anywhere inside*, which is what §5 asks for: the cascade has no
    /// `:focus-within` and nothing tells a widget that focus landed in its
    /// subtree, so focus on a button inside a slide does not stop the rotation.
    /// Written down in `TODO.md` rather than papered over.
    private boolean focused;

    /// What the last frame said about the motion preference.
    ///
    /// Reported from `render`, where a `Paints.Context` exists — a `State` has no
    /// way to ask. Not `setState` for the reason above, and safe because the
    /// timer that reads it fires between frames rather than during one.
    private boolean reducedMotion;

    @Override
    protected void initState() {
        index = widget().index();
    }

    @Override
    protected void dispose() {
        cancel();
        super.dispose();
    }

    /// A controlled carousel's answer comes from its widget; an uncontrolled
    /// one's from here. Clamped either way, because the list can shrink under a
    /// held index between frames.
    private int resolved() {
        var count = widget().count();
        if (count == 0) {
            return 0;
        }
        return Math.clamp(widget().isControlled() ? widget().index() : index, 0, count - 1);
    }

    @Override
    public Widget build(BuildContext context) {
        host = context.host().orElse(null);
        var carousel = widget();
        var current = resolved();
        // Rescheduled on every build, which is also every slide change: the timer
        // is one-shot, so this is what keeps it going, and a carousel that has
        // stopped rotating for any reason simply does not get a new one.
        schedule();
        return new CarouselView(
                current, carousel.count(), carousel.loop(), carousel.rotates(),
                current > 0 || carousel.loop(),
                current < carousel.count() - 1 || carousel.loop(),
                slide(current), carousel.attributes(),
                this::go, this::step, this::hover, this::focus, this::motion);
    }

    /// **Only the current slide is built** — `tabs`'s bargain, for `tabs`'s
    /// reason: a slide nobody can see should not hold subscriptions or images.
    private Widget slide(int current) {
        var carousel = widget();
        return carousel.count() == 0 ? null : carousel.children().get(current);
    }

    /// Go to a slide by number — what a dot asks for.
    private void go(int to) {
        var count = widget().count();
        if (count == 0) {
            return;
        }
        set(Math.clamp(to, 0, count - 1));
    }

    /// Move by one — what `Previous`, `Next` and the arrows ask for, and what the
    /// rotation does.
    ///
    /// `loop` decides what happens at an end: wrap, or stay. Staying is the
    /// default because at the last slide a disabled `Next` says "that is all of
    /// them" where a silent wrap says nothing.
    private void step(int by) {
        var count = widget().count();
        if (count == 0) {
            return;
        }
        var next = resolved() + by;
        if (next < 0 || next >= count) {
            if (!widget().loop()) {
                return;
            }
            next = Math.floorMod(next, count);
        }
        set(next);
    }

    private void set(int next) {
        if (next == resolved()) {
            return;
        }
        if (widget().isControlled()) {
            widget().onChange().accept(next);
            return;
        }
        setState(() -> index = next);
    }

    /// The pointer arrived or left. Cancels immediately rather than waiting for
    /// the next build, because the next build may be a long time coming — a
    /// carousel nobody is rebuilding is exactly the case where the rotation
    /// running under the pointer would be most annoying.
    private void hover(boolean over) {
        hovered = over;
        if (over) {
            cancel();
        } else {
            schedule();
        }
    }

    private void focus(boolean has) {
        focused = has;
        if (has) {
            cancel();
        } else {
            schedule();
        }
    }

    /// What the frame said about the motion preference.
    private void motion(boolean reduced) {
        if (reducedMotion == reduced) {
            return;
        }
        reducedMotion = reduced;
        if (reduced) {
            cancel();
        } else {
            schedule();
        }
    }

    /// §5's three reasons to stop, plus the ones that are simply "there is
    /// nothing to rotate".
    ///
    /// **The last of them is the one a build found**: a carousel that does not
    /// loop has nowhere to go from its final slide, and rescheduling there wakes
    /// the loop up for a slide that cannot change. Putting it here rather than at
    /// the reschedule is what makes `build` safe to call [#schedule] from
    /// unconditionally — the alternative was the same test written twice, and the
    /// copy in `build` was the one that was missing.
    private boolean shouldRotate() {
        return widget().rotates() && !hovered && !focused && !reducedMotion && host != null
                && (widget().loop() || resolved() < widget().count() - 1);
    }

    private void schedule() {
        cancel();
        if (!shouldRotate()) {
            return;
        }
        pending = host.after(widget().interval(), () -> {
            pending = null;
            // Checked again on firing, not only on scheduling: the pointer may
            // have arrived, or the preference changed, in the interval — and a
            // timer that had already been scheduled would otherwise advance one
            // slide past the moment it was supposed to stop.
            if (!shouldRotate()) {
                return;
            }
            step(1);
            // `schedule` re-asks `shouldRotate`, which is what stops a carousel
            // at its last slide when it does not loop.
            schedule();
        });
    }

    private void cancel() {
        if (pending != null) {
            pending.cancel();
            pending = null;
        }
    }
}
