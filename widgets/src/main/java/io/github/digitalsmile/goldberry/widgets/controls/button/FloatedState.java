package io.github.digitalsmile.goldberry.widgets.controls.button;

import java.time.Duration;
import java.util.HashSet;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.Overlay;
import io.github.digitalsmile.goldberry.bind.Property;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;

/// A [Floated]'s one piece of state: the overlay it put its button in.
final class FloatedState extends State<Floated> {

    private @Nullable Overlay overlay;

    /// The slot in [#overlay], whose switch sends the button out.
    private @Nullable FloatSlot slot;

    /// The host the overlay was put on, which is also what removes it once its
    /// exit has played.
    private @Nullable Host attachedTo;

    /// How long the way out takes: `--gb-motion-fast`, §3.1's "out: reverse,
    /// fast", read at each build so a theme's value is the one used.
    private double exitMillis = EXIT_FALLBACK_MILLIS;

    /// What the exit takes when no stylesheet says: `fast`'s specified value.
    static final double EXIT_FALLBACK_MILLIS = 100;

    /// What is floating now, so a rebuild with the same button does not take
    /// it down and put it back — which would restart its hover and its focus.
    private @Nullable Floated floating;

    @Override
    public Widget build(BuildContext context) {
        var host = context.host().orElse(null);
        exitMillis = context.duration("--gb-motion-fast", EXIT_FALLBACK_MILLIS);
        var wanted = widget();
        current = wanted;
        if (host != null && (floating == null || !presentsAs(wanted, floating))) {
            attach(host, wanted);
        }
        // Nothing in the tree: the button is in the overlay layer, and a
        // placeholder here would take the space the floating button is meant
        // not to.
        return Widget.nothing();
    }

    /// What was last handed to the tree, so the floating button's press runs
    /// the handler of *this* build rather than the one it was attached with.
    private @Nullable Floated current;

    /// Whether two descriptions float the same button: the same word, icon,
    /// disablement, attributes and corner. **Not the handler** — a lambda is a
    /// new object on every build, so an equality that included it would take
    /// the button down and put it back on every frame, losing its hover and
    /// its focus each time. The handler is read at the press instead.
    static boolean presentsAs(Floated a, Floated b) {
        var x = a.button();
        var y = b.button();
        return a.corner() == b.corner()
                && x.label().equals(y.label())
                && x.icon() == y.icon()
                && x.disabled() == y.disabled()
                && x.attributes().equals(y.attributes());
    }

    private void attach(Host host, Floated wanted) {
        detach();
        var button = wanted.button();
        var classes = new HashSet<>(button.attributes().classes());
        classes.add(Button.FLOAT);
        // The switch before the button, so the button's press can ask its own
        // switch rather than whichever slot this state holds by then.
        Property<Boolean> leaving = Property.of(false);
        var floated = new Button(
                        button.label(), button.icon(), () -> press(leaving), button.disabled(), button.attributes())
                .withAttributes(button.attributes().classes(classes.toArray(String[]::new)));
        slot = new FloatSlot(floated, leaving);
        overlay = host.overlay(slot, wanted.corner());
        attachedTo = host;
        floating = wanted;
    }

    /// The press, forwarded to the latest description's handler — and ignored
    /// once the button is leaving, which is §1.7's "input is disabled the instant
    /// closing starts": a press on a button that is fading out is a ghost click.
    private void press(Property<Boolean> leaving) {
        if (Boolean.TRUE.equals(leaving.get())) {
            return;
        }
        var latest = current;
        if (latest != null && latest.button().onPress() != null) {
            latest.button().onPress().run();
        }
    }

    /// Sends the floating button out and lets go of it.
    ///
    /// The overlay stays up for the length of the exit, with `leaving` on the
    /// button so the stylesheet's rule plays it, and is removed by a timer on the
    /// host after that (ADR-0355). This state forgets it at once: a rebuild that
    /// attaches a new button while the old one leaves shows both for a moment,
    /// crossing, which is what a toast queue does too. Without a host to time the
    /// exit on, the overlay goes at once.
    private void detach() {
        var leavingOverlay = overlay;
        var leavingSlot = slot;
        var host = attachedTo;
        overlay = null;
        slot = null;
        attachedTo = null;
        floating = null;
        if (leavingOverlay == null) {
            return;
        }
        if (host == null || leavingSlot == null || exitMillis <= 0) {
            leavingOverlay.remove();
            return;
        }
        leavingSlot.leaving().set(true);
        host.after(Duration.ofMillis(Math.max(1, Math.round(exitMillis))), leavingOverlay::remove);
    }

    @Override
    protected void dispose() {
        detach();
        super.dispose();
    }

    /// The overlay this state holds, for a test.
    @Nullable
    Overlay overlay() {
        return overlay;
    }
}
