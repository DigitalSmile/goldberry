package io.github.digitalsmile.goldberry.widgets.overlay.message;

import java.time.Duration;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.render.event.EventLoop;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.core.Phase;

/// A [Message]'s arrival and its departure — the whole of its state, and it
/// holds no value at all.
///
/// ## Why a banner is stateful when it decides nothing
///
/// `docs/design-system.md` §3 gives `message` an entrance: "in: `opacity` + 2px
/// rise, base". A newly mounted element deliberately starts no transition
/// ([ADR-0065]) — it has no previous style to move from — so an arrival is a
/// function of the frame clock and needs a beginning, which is what a [Phase]
/// is. A beginning has to survive the next build, and the only thing that does
/// is a `State`.
///
/// So this exists to hold two timestamps. `collapse`'s body arrives exactly this
/// way and `carousel`'s slides do too ([ADR-0166]) — with one difference: those
/// hand their part a *function* of the clock and decide at build time whether
/// there is an animation at all, and this hands over the [Phase] itself, so that
/// a banner nobody rebuilds still stops asking for frames when it settles. See
/// [MessageBox#isAnimating()].
///
/// ## The departure runs **before** the application is told
///
/// §3 also asks for "out: `opacity` fast", and the first cut of this widget did
/// not have one, on an argument that turned out to be a false choice: a banner
/// goes away because the application stopped describing it, and by then there is
/// nothing left to fade.
///
/// The way out is to reverse the order. The × does not tell the application and
/// hope; it starts a `LEAVING` phase **here**, keeps drawing the banner for the
/// hundred milliseconds §1.7 calls `fast`, and calls `onDismiss` when the fade is
/// over. The description is still in the tree for the whole of the animation
/// because nothing has asked for it to go yet — so the widget needs no owner
/// holding it, which is exactly what a lone banner does not have.
///
/// That leaves one case worth being explicit about: an application that wires a
/// `dismiss` handler and then **does not remove the banner**. It stays gone —
/// [MessageBox] draws nothing once the phase has run out — because a × that
/// faded a banner and then sprang it back would read as a click that failed.
/// What it does not do is close the gap its container left round it, which is
/// the container's number and not this widget's.
final class MessageState extends State<Message> {

    /// Stamped on the first frame that draws it — [Phase] reads the clock in
    /// `render`, because that is the only place a widget has one.
    ///
    /// Every message arrives, including one that was in the document when the
    /// window opened: mounting *is* arriving, and there is no "was already here"
    /// for a banner the way `collapse` has one for a section that started open.
    private final Phase arriving = new Phase(Phase.Kind.ENTERING);

    /// §1.7's `fast`, which §3 asks for by name for this widget's exit. The
    /// arrival is `base`; a dismissal that took as long as an arrival feels like
    /// the control is arguing.
    private static final double EXIT_MILLIS = 100;

    /// Null until the × is pressed, and never null again: a banner departs once.
    private Phase leaving;

    /// True once the departure has run out — see the class note's last paragraph.
    private boolean departed;

    /// Captured in `build` for the handler that runs later, which is the only
    /// thing [BuildContext#host()] may be used for. Null in a test or a golden
    /// with no window, and the departure degrades to an instant one.
    private @Nullable Host host;

    /// What the last frame said about the motion preference.
    ///
    /// `carousel` reads it the same way and for the same reason: the preference
    /// is a property of a *frame*, and the code that has to act on it here runs
    /// between frames, in a pointer handler.
    private boolean reducedMotion;

    /// The timer that ends the departure. Cancelled on unmount, or a banner
    /// removed while it was fading would call a handler for a tree that is gone.
    private EventLoop.@Nullable Timer pending;

    @Override
    protected void dispose() {
        if (pending != null) {
            pending.cancel();
            pending = null;
        }
        super.dispose();
    }

    @Override
    public Widget build(BuildContext context) {
        host = context.host().orElse(null);
        var message = widget();
        return new MessageBox(
                message.kind(),
                message.text(),
                message.actions(),
                message.onDismiss() == null ? null : this::asked,
                leaving != null ? leaving : arriving,
                departed,
                this::motion,
                message.attributes());
    }

    /// The × was pressed, or `Space` was.
    ///
    /// Idempotent: a second press during the fade is not a second dismissal, and
    /// two timers would call the application twice.
    private void asked() {
        if (leaving != null) {
            return;
        }
        var onDismiss = widget().onDismiss();
        if (host == null || reducedMotion) {
            // Nothing to animate against, or a reader who has asked not to be
            // animated at. Either way the banner goes now rather than in a
            // hundred milliseconds of nothing happening.
            departed = true;
            onDismiss.run();
            return;
        }
        setState(() -> leaving = new Phase(Phase.Kind.LEAVING, EXIT_MILLIS));
        pending = host.after(Duration.ofMillis((long) EXIT_MILLIS), this::departed);
    }

    /// The fade is over: stop drawing, then tell the application.
    ///
    /// In that order, and it matters for one frame — the application's handler
    /// usually rebuilds the tree without this banner in it, and a state that
    /// still thought it was mid-fade would draw a half-faded banner in whatever
    /// element the reconciler handed it next.
    private void departed() {
        pending = null;
        var onDismiss = widget().onDismiss();
        setState(() -> departed = true);
        if (onDismiss != null) {
            onDismiss.run();
        }
    }

    /// What the frame said about the motion preference — see [#reducedMotion].
    private void motion(boolean reduced) {
        reducedMotion = reduced;
    }
}
