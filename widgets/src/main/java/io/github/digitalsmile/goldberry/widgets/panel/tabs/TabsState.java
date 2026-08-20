package io.github.digitalsmile.goldberry.widgets.panel.tabs;

import io.github.digitalsmile.goldberry.widget.BuildContext;
import io.github.digitalsmile.goldberry.widgets.core.scroll.ScrollController;
import io.github.digitalsmile.goldberry.widget.State;
import io.github.digitalsmile.goldberry.widgets.core.Phase;
import io.github.digitalsmile.goldberry.widget.Widget;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// What a [Tabs] remembers between builds: which tabs are arriving, and which
/// have been closed but are still on their way out.
///
/// ## Why a strip needs any state at all
///
/// The list of tabs is the application's — a strip reports `close` and the
/// application shortens its own list
/// ([ADR-0107](../../../../../../../../book/src/adr/0107-a-tab-strip-is-a-model-a-header-and-a-panel.md)).
/// Which means that by the time a tab should be *animating out*, it is already
/// gone from everything the strip is given. Something has to hold on to it for
/// the length of its departure, and this is that something
/// ([ADR-0109](../../../../../../../../book/src/adr/0109-a-tab-arrives-and-departs-on-the-frame-clock.md)).
///
/// Arrivals need it for the mirror-image reason: a tab that has just appeared
/// must be told it is new, and only something that saw the *previous* build knows
/// which of these tabs was not there before.
///
/// ## What it does not do
///
/// It keeps no clock. Times are read in `render`, which is the only place a
/// widget is handed one, and a departing tab tells this state when it has
/// finished — which marks a rebuild, and the next build is where it is dropped.
final class TabsState extends State<Tabs> {

    /// The viewport the headers live in, so a selected tab that is scrolled out
    /// of the strip can be brought back ([ADR-0120]).
    ///
    /// Created here and handed *down* into the `tab-list`, which is the one
    /// direction a controller can travel: the scroll view is a descendant, so
    /// `findAncestorState` looks the wrong way, and a controller built by the
    /// viewport would have a new identity on every rebuild.
    private final ScrollController headerScroll = new ScrollController();

    /// The tab that has just been selected and has not yet been shown, or null.
    ///
    /// A **request**, cleared as soon as it is acted on. A strip that pulled the
    /// selected tab into view on every frame would take the strip's scrollbar
    /// away from the user for as long as anything was selected, which is always.
    private String pendingReveal;

    /// The tabs on screen, in the order they are drawn: the application's, plus
    /// any that are still leaving.
    private final Map<String, Phase> phases = new LinkedHashMap<>();

    /// A leaving tab's last known description, because the application no longer
    /// has one to give.
    private final Map<String, Tab> departing = new LinkedHashMap<>();

    /// What each tab was last built as — kept so that when one disappears there is
    /// still something to draw on its way out. This is the only copy: by the time
    /// a tab is leaving, the application's list no longer mentions it.
    private final Map<String, Tab> lastBuilt = new LinkedHashMap<>();

    /// Whether a build has happened. The first one animates nothing: a window
    /// opening should show its tabs, not play six arrivals at once.
    private boolean opened;

    /// Acts on a pending reveal, then forgets it.
    ///
    /// Handed the selected header's rectangle and the one that clips it, which is
    /// the strip's viewport — the controller turns those two into a distance and
    /// the viewport clamps it ([ADR-0119], [ADR-0120]).
    private void revealed(io.github.digitalsmile.goldberry.backend.LogicalRect self,
            io.github.digitalsmile.goldberry.backend.LogicalRect clip) {
        if (pendingReveal == null) {
            return;
        }
        headerScroll.reveal(self, clip);
        setState(() -> pendingReveal = null);
    }

    @Override
    public Widget build(BuildContext context) {
        var strip = widget();
        var current = new LinkedHashMap<String, Tab>();
        var others = new ArrayList<Widget>();
        for (var child : strip.children()) {
            if (child instanceof Tab tab) {
                current.put(tab.value(), tab);
            } else {
                // Not a tab: drawn in the strip and left alone, so a `spacer` or
                // a button can live in a tab bar.
                others.add(child);
            }
        }

        arrivals(current);
        departures(current);
        lastBuilt.putAll(current);
        lastBuilt.keySet().removeIf(value -> !phases.containsKey(value));

        var selected = strip.selected();
        var headers = new ArrayList<Widget>(phases.size() + others.size() + 1);
        List<Widget> content = List.of();
        for (var value : List.copyOf(phases.keySet())) {
            var tab = current.get(value);
            var leaving = tab == null;
            if (leaving) {
                tab = departing.get(value);
                if (tab == null) {
                    phases.remove(value);
                    continue;
                }
            }
            var isSelected = !leaving && value.equals(selected);
            var phase = phases.get(value);
            // A tab on its way out answers nothing: it is not in the
            // application's list any more, so picking it would report a value
            // that does not exist and closing it twice is not a thing.
            headers.add(tab.wired(
                    isSelected,
                    leaving ? null : () -> strip.select(value),
                    leaving ? null : () -> strip.close(value),
                    phase::isRunning,
                    now -> visibility(value, phase, now),
                    // Non-null only for the tab that has just been selected, so
                    // exactly one header per build is asked where it is —
                    // and only until it has been brought into view (ADR-0120).
                    value.equals(pendingReveal) ? this::revealed : null));
            if (isSelected) {
                content = tab.content();
            }
        }
        headers.addAll(others);
        if (strip.onNew() != null) {
            headers.add(new TabNew(strip.onNew()));
        }
        opened = true;
        return new TabStrip(headers, content, headerScroll, strip.attributes());
    }

    /// Everything in `current` that was not here before is arriving — except on
    /// the first build, where everything is simply already there.
    private void arrivals(Map<String, Tab> current) {
        for (var value : current.keySet()) {
            phases.computeIfAbsent(value, ignored -> new Phase(
                    opened ? Phase.Kind.ENTERING : Phase.Kind.SETTLED));
        }
    }

    /// Everything here that is no longer in `current` is leaving, and its last
    /// description is kept so there is something to draw on the way out.
    private void departures(Map<String, Tab> current) {
        for (var entry : phases.entrySet()) {
            if (current.containsKey(entry.getKey())) {
                departing.remove(entry.getKey());
                continue;
            }
            if (entry.getValue().kind() != Phase.Kind.LEAVING) {
                entry.getValue().leave();
                // The last moment its description is available: the application
                // has already dropped it, and `lastBuilt` is the only copy left.
                var remembered = lastBuilt.get(entry.getKey());
                if (remembered != null) {
                    departing.put(entry.getKey(), remembered);
                }
            }
        }
        departing.keySet().removeIf(value -> !phases.containsKey(value));
    }

    /// How visible a tab is at `now`, `0..1` — and the one place a phase is
    /// advanced.
    ///
    /// Called from the tab's `render`, which is the only place the frame clock
    /// reaches a widget: the first call stamps the phase's start, and the call
    /// that finds a departure over is what asks for the tab to be dropped
    /// (ADR-0109).
    private double visibility(String value, Phase phase, double now) {
        var progress = phase.progressAt(now);
        if (phase.hasDeparted(now)) {
            departed(value);
            return 0;
        }
        return phase.kind() == Phase.Kind.LEAVING ? 1 - progress : progress;
    }

    /// A departure has finished — drop the tab on the next build.
    ///
    /// Called from `render`, so it only *marks*: the rebuild happens on the next
    /// frame, which is what a deferred rebuild is for (ADR-0052).
    private void departed(String value) {
        if (phases.containsKey(value)) {
            setState(() -> {
                phases.remove(value);
                departing.remove(value);
            });
        }
    }
}
