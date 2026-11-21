package dev.goldberry;

import java.util.Objects;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import dev.goldberry.input.dragout.DragHandOff;
import dev.goldberry.input.dragout.DragOffer;
import dev.goldberry.input.dragout.DragOutcome;
import dev.goldberry.log.Logs;
import dev.goldberry.render.dragout.DragEnd;
import dev.goldberry.render.dragout.DragSession;
import dev.goldberry.render.model.LogicalPoint;
import dev.goldberry.render.window.BackendWindow;

/// One drag out, from the moment a window hands it to the platform to the
/// outcome its source hears.
///
/// The backend's reports arrive here, and this is what turns them into an
/// in-app drag over whichever of the application's windows the pointer is
/// over: that window's router is told where the drag is, lights its targets
/// and, on a release, lands the original payload. The source's own window may
/// be one of them, or none.
///
/// UI thread only, like every report a [DragSession] receives.
final class DragFlight implements DragSession {

    private static final Logger LOG = Logs.of(DragFlight.class);

    private final GoldberryRuntime runtime;
    private final Window origin;
    private final DragHandOff handOff;
    private final DragOffer offer;

    /// The window the drag is over, or null over no window of this
    /// application.
    private @Nullable Window over;

    private boolean ended;

    DragFlight(GoldberryRuntime runtime, Window origin, DragHandOff handOff, DragOffer offer) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.origin = Objects.requireNonNull(origin, "origin");
        this.handOff = Objects.requireNonNull(handOff, "handOff");
        this.offer = Objects.requireNonNull(offer, "offer");
    }

    /// Whether the platform has said the drag is over.
    boolean isEnded() {
        return ended;
    }

    @Override
    public void over(BackendWindow window, LogicalPoint at) {
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(at, "at");
        if (ended) {
            return;
        }
        var target = runtime.windowFor(window);
        if (target != over) {
            leave();
        }
        over = target;
        if (target == null) {
            return;
        }
        var router = target.routerOrNull();
        if (router != null) {
            router.platformDragOver(
                    handOff.source(), handOff.payload(), handOff.from(), handOff.grab(), at.x(), at.y());
            target.afterDragReport();
        }
    }

    @Override
    public void outside() {
        if (!ended) {
            leave();
        }
    }

    /// A turn of the loop while the platform holds the thread for the drag, so
    /// posted work and timers run during it as they would around it.
    @Override
    public void whileHeld() {
        if (!ended) {
            runtime.loop().turnWhileHeld();
        }
    }

    @Override
    public void ended(DragEnd end) {
        Objects.requireNonNull(end, "end");
        if (ended) {
            return;
        }
        ended = true;
        var outcome = switch (end) {
            case DragEnd.Delivered(var operation) -> {
                leave();
                yield new DragOutcome.Delivered(operation);
            }
            case DragEnd.ReleasedOver(var window, var at, var echoed) -> landOn(window, at, echoed);
            case DragEnd.Cancelled _ -> {
                leave();
                yield new DragOutcome.Cancelled();
            }
        };
        runtime.flightEnded(this);
        var source = origin.routerOrNull();
        if (source != null) {
            source.dragOutFinished();
            origin.afterDragReport();
        }
        var listener = handOff.out().onEnd();
        if (listener != null) {
            try {
                listener.accept(outcome);
            } catch (RuntimeException e) {
                LOG.warn("a drag's onDragOutEnd failed on {}", outcome, e);
            }
        }
    }

    /// Lands the drag on `window` at `at`, which is one of this application's.
    private DragOutcome landOn(BackendWindow window, LogicalPoint at, boolean echoed) {
        var target = runtime.windowFor(window);
        if (target != over) {
            leave();
        }
        over = null;
        if (target == null) {
            return new DragOutcome.Landed(false);
        }
        if (echoed) {
            target.expectOwnDrop(offer);
        }
        var router = target.routerOrNull();
        if (router == null) {
            return new DragOutcome.Landed(false);
        }
        // Aimed first at the release point, which the last report may not have
        // been: a platform that reports moves sparsely still lands where the
        // button came up.
        router.platformDragOver(handOff.source(), handOff.payload(), handOff.from(), handOff.grab(), at.x(), at.y());
        var accepted = router.platformDragDropped(at.x(), at.y());
        target.afterDragReport();
        return new DragOutcome.Landed(accepted);
    }

    /// Takes the drag off the window it was over.
    private void leave() {
        var current = over;
        over = null;
        if (current == null) {
            return;
        }
        var router = current.routerOrNull();
        if (router != null) {
            router.platformDragLeft();
            current.afterDragReport();
        }
    }

    @Override
    public String toString() {
        return "DragFlight[" + handOff.payload() + (ended ? ", ended" : "") + "]";
    }
}
