package io.github.digitalsmile.goldberry.widgets.overlay.message;

import org.jspecify.annotations.Nullable;

import io.github.digitalsmile.goldberry.Host;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.core.Departure;
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
/// this describes [Widget#nothing()] once the phase has run out — because a ×
/// that faded a banner and then sprang it back would read as a click that
/// failed. It used to leave a hole: an empty box takes no room of its own and is
/// still a child, so a `column` with a `gap` kept the gap. A node that describes
/// nothing has no box for a gap to hang off ([ADR-0227]).
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

    /// §1.7's `closing → removed`, shared with `dialog` — see [Departure], which
    /// is where the timer, the two flags and the ordering live now ([ADR-0234]).
    private final Departure leaving = new Departure(EXIT_MILLIS, this::setState);

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

    @Override
    protected void dispose() {
        leaving.cancel();
        super.dispose();
    }

    @Override
    public Widget build(BuildContext context) {
        host = context.host().orElse(null);
        var message = widget();
        var words = message.resolved();
        // §9's `bind=`, and the whole of what it took to build: a banner whose
        // value is empty is **not there**, where before this had to be described
        // away from outside because a widget could not say it ([ADR-0227]).
        //
        // The departed case joins it. `MessageBox` used to answer a box with no
        // style for it, which takes no room of its own and is still a child — so
        // a `column` with a `gap` kept the gap round the banner that had gone,
        // and the widget's own documentation recorded the hole as somebody
        // else's number. It is nobody's number now.
        if (leaving.isOver() || (message.binding() != null && words.isBlank())) {
            return Widget.nothing();
        }
        return new MessageBox(
                message.kind(),
                words,
                message.actions(),
                message.onDismiss() == null ? null : this::asked,
                leaving.phaseOr(arriving),
                this::motion,
                message.attributes());
    }

    /// The × was pressed, or `Space` was.
    ///
    /// Idempotent: a second press during the fade is not a second dismissal, and
    /// two timers would call the application twice.
    private void asked() {
        // Every rule this used to spell out is [Departure]'s now: idempotent, two
        // flags, stop drawing before telling the application, and gone at once
        // when there is no window or the reader asked for no motion. `dialog` had
        // written the same six lines independently ([ADR-0234]).
        leaving.begin(host, reducedMotion, () -> {
            var onDismiss = widget().onDismiss();
            if (onDismiss != null) {
                onDismiss.run();
            }
        });
    }

    /// What the frame said about the motion preference — see [#reducedMotion].
    private void motion(boolean reduced) {
        reducedMotion = reduced;
    }
}
