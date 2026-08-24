package io.github.digitalsmile.goldberry.widgets.data;

import io.github.digitalsmile.goldberry.bind.Subscription;
import java.util.ArrayList;
import java.util.List;

/// One crosshair across several charts — `charts.md` §3.1's "shared crosshair
/// across charts, linked by a shared `CrosshairGroup`; cheap because it is one
/// value two widgets read".
///
/// ```java
/// var linked = new CrosshairGroup();
/// var latency = new LineChart(…).crosshair(linked);
/// var errors  = new LineChart(…).crosshair(linked);
/// ```
///
/// Pointing at Tuesday on one chart puts the crosshair on Tuesday on all of them,
/// which is how a reader answers "and what was happening to the other one at the
/// same moment" — the question a dashboard exists for and the one it is worst at
/// without this.
///
/// ## It is an index, so the charts must be aligned
///
/// What travels is the **point index**, not a value and not a time. Charts in a
/// group are assumed to be sampled together — the *n*th point of one is the *n*th
/// point of the others — which is what a dashboard's panels over one time range
/// actually are. A group of charts with different point counts is not refused,
/// because there is no moment at which it could be: each chart simply clamps to
/// its own last point, which is the least surprising of the wrong answers.
///
/// A group over charts that are *not* aligned wants a shared **time**, and that
/// is a different type from this one — worth building when something needs it,
/// and not worth guessing at now.
///
/// ## The readout stays where the pointer is
///
/// Every chart in the group draws the crosshair; only the one under the pointer
/// draws the readout. A dashboard with six floating boxes on it, five of them
/// about a chart nobody is pointing at, is worse than no linking at all.
///
/// ## An application holds it, like a `ToastController`
///
/// It is mutable and it outlives any one build, which is what a widget cannot be
/// ([ADR-0177](../../../../../../../book/src/adr/0177-a-toast-is-raised-through-a-controller.md)'s
/// shape). Confined to the UI thread, like everything in the widget layer.
public final class CrosshairGroup {

    private final List<Runnable> listeners = new ArrayList<>(4);
    private int hovered = -1;

    /// A group with nothing in it yet — an application makes one and hands it to
    /// every chart that should share a crosshair.
    public CrosshairGroup() {
    }

    /// The point every chart in this group is showing, or -1 for none.
    public int hovered() {
        return hovered;
    }

    /// Moves the crosshair, telling every chart in the group.
    ///
    /// **Only on a change**, which is the rule every callback in this area
    /// follows: a pointer crossing one point sends an event per pixel, and a
    /// notification per event would rebuild every chart on the dashboard sixty
    /// times a second to draw the same line (§1.7).
    public void hover(int index) {
        if (index == hovered) {
            return;
        }
        hovered = index;
        // Over a copy: a listener that disposes its chart mid-notification would
        // otherwise be removing from the list being walked, and a dashboard that
        // rebuilds while a pointer is moving does exactly that.
        for (var listener : List.copyOf(listeners)) {
            listener.run();
        }
    }

    /// Lets go — what a chart calls when the pointer leaves it.
    public void clear() {
        hover(-1);
    }

    /// Registers `onChange`, to be run whenever the crosshair moves.
    ///
    /// The returned [Subscription] must be closed when the chart goes, or the
    /// group holds the last window's charts alive and rebuilds them.
    public Subscription subscribe(Runnable onChange) {
        java.util.Objects.requireNonNull(onChange, "onChange");
        listeners.add(onChange);
        return () -> listeners.remove(onChange);
    }

    /// How many charts are listening. For a test, and for a leak that would
    /// otherwise be invisible.
    public int listenerCount() {
        return listeners.size();
    }
}
