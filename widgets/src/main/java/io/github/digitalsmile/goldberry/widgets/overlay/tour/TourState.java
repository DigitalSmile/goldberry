package io.github.digitalsmile.goldberry.widgets.overlay.tour;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.digitalsmile.goldberry.render.model.LogicalRect;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import io.github.digitalsmile.goldberry.widgets.core.Phase;

/// Which stop a [Tour] is showing, and how it moves between them.
final class TourState extends State<Tour> {

    private static final Logger LOG = LoggerFactory.getLogger(TourState.class);

    /// Which stop is showing. Past the end means the tour is over and waiting for
    /// the frame that removes it.
    private int index;

    /// Whether the current stop has already asked its viewport to reveal the
    /// target.
    ///
    /// A request, cleared on every move, for
    /// [io.github.digitalsmile.goldberry.widgets.core.scroll.ScrollController]'s
    /// reason: a stop that scrolled on every frame would hold the viewport
    /// against a user trying to look at something else (ADR-0120).
    private boolean revealed;

    /// How big the window is, as the last frame measured the tour's own node.
    ///
    /// Zero until the first frame has been laid out, which draws nothing — a
    /// veil of no size — and is corrected on the frame after. One frame of
    /// nothing at the start of a tour is invisible; guessing would not be
    /// ([ADR-0121]).
    private LogicalRect window = LogicalRect.of(0, 0, 0, 0);

    @Override
    public Widget build(BuildContext context) {
        var tour = widget();
        var stop = advanceToAFindableStop();
        if (stop == null) {
            // Every remaining stop names something that is not on screen. Ending
            // is the only honest thing left, and it is deferred out of the build
            // because removing an overlay mid-build would mutate the tree that is
            // being described.
            end();
            return new TourVeil(null, window);
        }
        var anchor = anchorOf(stop);
        if (!revealed && stop.scroll() != null) {
            // §5: the target is scrolled into view *before* the popover is
            // positioned. The reveal marks the tree dirty, so the next frame
            // re-reads the anchor and this one draws against where it was --
            // which is why the popover lands correctly on the frame after.
            revealed = true;
            stop.scroll().reveal(anchor, clipOf(stop));
        }
        return new TourStop(
                stop,
                anchor,
                cameFrom,
                travel,
                arrival,
                window,
                index,
                tour.stops().size(),
                index > 0 ? this::back : null,
                this::next,
                this::skip,
                this::measured,
                cardHeight,
                this::cardMeasured);
    }

    /// The first stop from here whose target is on screen, skipping any that are
    /// not — §5's "a target that is not in the tree is skipped with a warning".
    private @Nullable Stop advanceToAFindableStop() {
        var tour = widget();
        while (index < tour.stops().size()) {
            var stop = tour.stops().get(index);
            if (widget().host().anchor(stop.targetId()).isPresent()) {
                return stop;
            }
            LOG.warn("tour stop \"{}\" names #{}, which is not on screen; skipping it", stop.title(), stop.targetId());
            index++;
            revealed = false;
        }
        return null;
    }

    /// Where the target is on screen.
    ///
    /// `painted()` and not `bounds()`. A region's `bounds` is the **layout**
    /// rectangle, and a veil is about what the user can see: a row inside a
    /// scrolled list is laid out where it always was and drawn a long way from
    /// there ([ADR-0123]). A popup anchors to the same rectangle this does, and
    /// for the same reason ([ADR-0270]).
    private LogicalRect anchorOf(Stop stop) {
        return widget().host()
                .anchor(stop.targetId())
                .map(region -> region.painted())
                .orElse(LogicalRect.of(0, 0, 0, 0));
    }

    /// What clips the target, which is the viewport a reveal has to move.
    private LogicalRect clipOf(Stop stop) {
        return widget().host()
                .anchor(stop.targetId())
                .map(region -> {
                    var clip = region.clip();
                    return clip == null || clip.isNone()
                            ? region.bounds()
                            : LogicalRect.of((float) clip.left(), (float) clip.top(), (float) clip.width(), (float)
                                    clip.height());
                })
                .orElse(LogicalRect.of(0, 0, 0, 0));
    }

    /// §1.7's overlay curve, for the tour's own arrival: the card fades and
    /// rises in as a `popover` does, because §3.1 says a tour's card animates
    /// "as `popover`" and that row is `opacity` 0→1 with `translateY` −4→0
    /// ([ADR-0269]).
    ///
    /// One phase for the whole tour rather than one per stop: a tour arrives
    /// once, and a card that faded in again at every stop would be a sequence
    /// that restarts rather than advances.
    private final Phase arrival = new Phase(Phase.Kind.ENTERING);

    /// Where the previous stop's target was, so the cut-out can travel from it —
    /// or null on the first stop, which has nowhere to have come from.
    private @Nullable LogicalRect cameFrom;

    /// §3.1's tour row: "stop change: veil cut-out `translate`+size **base**".
    ///
    /// Restarted on every stop change, and null while there is nothing to
    /// travel. It carries the *geometry* rather than a style, which is why it is
    /// a `Phase` and not a `transition`: a cut-out's rectangle is computed from
    /// an anchor the cascade has never seen ([ADR-0269]).
    private @Nullable Phase travel;

    /// How tall the card came out, as the last frame laid it out, or 0 before
    /// there has been one.
    ///
    /// Banked here rather than read in `render` for the reason `window` is: the
    /// decision it feeds — above the target or below it — is made while
    /// describing the tree, and a measurement arrives after one has been drawn.
    ///
    /// It settles in one frame and cannot oscillate, which is `Measured`'s third
    /// rule and holds **by construction** here: the card's width is fixed and its
    /// content is the stop's own text, so its height does not depend on whether
    /// it was placed above or below ([ADR-0268]).
    private double cardHeight;

    /// Told how tall the card is by the card.
    private void cardMeasured(double height) {
        if (Math.abs(height - cardHeight) < 0.5) {
            return;
        }
        setState(() -> cardHeight = height);
    }

    /// Told how big the window is by the node that fills it.
    private void measured(LogicalRect bounds) {
        if (bounds.equals(window)) {
            return;
        }
        setState(() -> window = bounds);
    }

    /// Starts the cut-out travelling from where it is now to wherever the next
    /// stop's target turns out to be.
    ///
    /// Called **before** the index moves, so `anchorOf` still answers the stop
    /// being left — which is the rectangle the travel has to start from. Doing it
    /// after would bank the destination as the origin and animate nothing.
    private void beginTravel() {
        var leaving = advanceToAFindableStop();
        cameFrom = leaving == null ? null : anchorOf(leaving);
        travel = cameFrom == null ? null : new Phase(Phase.Kind.ENTERING);
    }

    private void back() {
        beginTravel();
        setState(() -> {
            index = Math.max(0, index - 1);
            revealed = false;
        });
    }

    private void next() {
        if (index + 1 >= widget().stops().size()) {
            end();
            return;
        }
        beginTravel();
        setState(() -> {
            index++;
            revealed = false;
        });
    }

    /// §5: "`Esc` skips the whole tour, not one stop."
    private void skip() {
        end();
    }

    private void end() {
        widget().onEnd().run();
    }
}
