package io.github.digitalsmile.goldberry.widgets.overlay.tour;

import io.github.digitalsmile.goldberry.backend.LogicalRect;
import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widget.Widget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// Which stop a [Tour] is showing, and how it moves between them.
final class TourState extends State<Tour> {

    private static final Logger LOG = LoggerFactory.getLogger(TourState.class);

    /// Which stop is showing. Past the end means the tour is over and waiting for
    /// the frame that removes it.
    private int index;

    /// Whether the current stop has already asked its viewport to reveal the
    /// target. A request, cleared on every move, for [
    /// io.github.digitalsmile.goldberry.widgets.core.scroll.ScrollController]'s
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
                stop, anchor, window, index, tour.stops().size(),
                index > 0 ? this::back : null,
                this::next,
                this::skip,
                this::measured);
    }

    /// The first stop from here whose target is on screen, skipping any that are
    /// not — §5's "a target that is not in the tree is skipped with a warning".
    private Stop advanceToAFindableStop() {
        var tour = widget();
        while (index < tour.stops().size()) {
            var stop = tour.stops().get(index);
            if (widget().host().anchor(stop.targetId()).isPresent()) {
                return stop;
            }
            LOG.warn("tour stop \"{}\" names #{}, which is not on screen; skipping it",
                    stop.title(), stop.targetId());
            index++;
            revealed = false;
        }
        return null;
    }

    /// Where the target is on screen.
    ///
    /// `painted()` and not `bounds()`. A region's `bounds` is the **layout**
    /// rectangle, which is what a popup anchors to — a menu belongs under where
    /// its button sits in the flow. A veil is about what the user can see, and a
    /// row inside a scrolled list is laid out where it always was and drawn a
    /// long way from there ([ADR-0123]).
    private LogicalRect anchorOf(Stop stop) {
        return widget().host().anchor(stop.targetId())
                .map(region -> region.painted())
                .orElse(LogicalRect.of(0, 0, 0, 0));
    }

    /// What clips the target, which is the viewport a reveal has to move.
    private LogicalRect clipOf(Stop stop) {
        return widget().host().anchor(stop.targetId())
                .map(region -> {
                    var clip = region.clip();
                    return clip == null || clip.isNone()
                            ? region.bounds()
                            : LogicalRect.of((float) clip.left(), (float) clip.top(),
                                    (float) clip.width(), (float) clip.height());
                })
                .orElse(LogicalRect.of(0, 0, 0, 0));
    }

    /// Told how big the window is by the node that fills it.
    private void measured(LogicalRect bounds) {
        if (bounds.equals(window)) {
            return;
        }
        setState(() -> window = bounds);
    }

    private void back() {
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
