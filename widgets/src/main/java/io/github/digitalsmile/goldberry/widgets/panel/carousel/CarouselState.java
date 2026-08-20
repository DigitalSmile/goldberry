package io.github.digitalsmile.goldberry.widgets.panel.carousel;

import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.backend.EventLoop;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.core.Phase;

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

    /// Where the slide now showing is in its arrival, and which way it came.
    ///
    /// A **new phase per slide change**, which is what starts the animation: the
    /// clock is stamped on the first frame that draws it, because `render` is the
    /// only place a widget has one (`tabs`'s arrangement, [Phase]).
    ///
    /// There is no *departure*. §5 builds only the current slide, and keeping the
    /// old one alive for the length of a fade would be building a slide that has
    /// been moved away from — which is the one thing "only the current slide is
    /// built" says it does not do. So the outgoing slide is dropped and the
    /// incoming one fades up over the viewport's own surface, which is exactly
    /// what a `tab` panel does.
    private Phase arriving = new Phase(Phase.Kind.SETTLED);

    /// Which way the last move went: `+1` forwards, `-1` back. The arriving slide
    /// translates *from* that direction, so a carousel going forwards moves its
    /// content leftwards — the direction a reader's eye is already going.
    private int direction = 1;

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
                this::go, this::step, this::hover, this::focus, this::motion,
                this::visibility, direction);
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
        var current = resolved();
        if (next == current) {
            return;
        }
        // Before the change, so the arriving slide's first frame is its first
        // frame -- and so a controlled carousel animates too, where the index
        // comes back from the application rather than from here.
        //
        // Wrapping from the last slide to the first is a move *forwards*, not a
        // long way back: what the reader asked for was "next".
        var count = widget().count();
        var forwards = next > current;
        if (widget().loop() && count > 1) {
            if (current == count - 1 && next == 0) {
                forwards = true;
            } else if (current == 0 && next == count - 1) {
                forwards = false;
            }
        }
        direction = forwards ? 1 : -1;
        arriving = new Phase(Phase.Kind.ENTERING);
        if (widget().isControlled()) {
            widget().onChange().accept(next);
            return;
        }
        setState(() -> index = next);
    }

    /// How far into its arrival the current slide is at `now`, `0..1`.
    ///
    /// Read from `render`, which is what stamps the beginning: a `State` never
    /// sees the frame clock.
    private double visibility(double now) {
        return arriving.progressAt(now);
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
