package dev.goldberry.widgets.panel.list;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/// Which rows of a virtual list are built: the window over the viewport, and
/// the few rows outside it that must exist anyway.
///
/// A row outside the window is **pinned** when the keyboard is on it or on its
/// way to it. The focused row is pinned because scrolling it out of the window
/// would unmount it, and the router lets go of an element that has left the
/// tree, so the next arrow key would go nowhere. The row a reach is building is
/// pinned because a name resolves against the tree, and it has to be there to
/// be focused.
///
/// A pinned row is built **on its own**, between spacers, rather than by widening
/// the window to it: widening to row 9,000 from a window at the top builds the
/// nine thousand rows in between for a frame.
///
/// @param from the first index of the run
/// @param to   one past its last index
record BuiltRuns(int from, int to) {

    /// The runs to build, in order, with neither overlaps nor touching ends: the
    /// window `[first, last)` and each pin, merged wherever they meet.
    ///
    /// A pin outside `[0, count)` is dropped, which is what a row that has left
    /// the model while the keyboard was on it comes to.
    static List<BuiltRuns> of(int first, int last, int count, int... pins) {
        var runs = new ArrayList<BuiltRuns>(pins.length + 1);
        var from = Math.clamp(first, 0, count);
        var to = Math.clamp(last, from, count);
        var sorted = Arrays.stream(pins)
                .filter(pin -> pin >= 0 && pin < count)
                .sorted()
                .toArray();
        var window = from < to ? new BuiltRuns(from, to) : null;
        for (var pin : sorted) {
            if (window != null && window.from <= pin) {
                add(runs, window);
                window = null;
            }
            add(runs, new BuiltRuns(pin, pin + 1));
        }
        if (window != null) {
            add(runs, window);
        }
        return List.copyOf(runs);
    }

    /// Appends `run`, joining it to the last one when they overlap or touch.
    private static void add(List<BuiltRuns> runs, BuiltRuns run) {
        if (!runs.isEmpty()) {
            var previous = runs.getLast();
            if (run.from <= previous.to) {
                runs.set(runs.size() - 1, new BuiltRuns(previous.from, Math.max(previous.to, run.to)));
                return;
            }
        }
        runs.add(run);
    }
}
