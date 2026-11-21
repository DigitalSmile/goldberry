package dev.goldberry.natives.desktop.dragout;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.natives.wayland.WaylandEvent;

/// A `wl_data_source`'s events, read into reports while its queue dispatches
/// and acted on after: nothing of the application's runs inside libwayland's
/// dispatch, and a target's file descriptor is taken out of the event while it
/// is still valid.
final class WaylandSourceEvents {

    static final int TARGET = 0;
    static final int SEND = 1;
    static final int CANCELLED = 2;
    static final int DROP_PERFORMED = 3;
    static final int FINISHED = 4;
    static final int ACTION = 5;

    /// `wl_data_device_manager.dnd_action`.
    static final int DND_NONE = 0;

    static final int DND_COPY = 1;
    static final int DND_MOVE = 2;

    /// What one event says.
    sealed interface Report {

        /// `target`.
        record Target(@Nullable String mime) implements Report {}

        /// `send`: write `mime`'s bytes to `fd`, then close it.
        record Send(String mime, int fd) implements Report {
            public Send {
                Objects.requireNonNull(mime, "mime");
            }
        }

        /// `dnd_drop_performed`.
        record DropPerformed() implements Report {}

        /// `dnd_finished`, with the last action the compositor named.
        record Finished(@Nullable DragAction action) implements Report {}

        /// `cancelled`.
        record Cancelled() implements Report {}
    }

    private final List<Report> reports = new ArrayList<>();
    private @Nullable DragAction action;

    /// Reads `event`. A `send` with no type still carries a descriptor, which
    /// is closed by whoever drains it, as an empty answer.
    void onEvent(WaylandEvent event) {
        switch (event.opcode()) {
            case TARGET -> reports.add(new Report.Target(event.string(0)));
            case SEND -> {
                var mime = event.string(0);
                reports.add(new Report.Send(mime == null ? "" : mime, event.integer(1)));
            }
            case CANCELLED -> reports.add(new Report.Cancelled());
            case DROP_PERFORMED -> reports.add(new Report.DropPerformed());
            case FINISHED -> reports.add(new Report.Finished(action));
            case ACTION -> action = actionOf(event.integer(0));
            default -> {
                // A later version's event, which this binding did not ask for.
            }
        }
    }

    /// What was read since the last call, in order.
    List<Report> drain() {
        var drained = List.copyOf(reports);
        reports.clear();
        return drained;
    }

    /// The last action the compositor chose, or null for none yet.
    @Nullable
    DragAction action() {
        return action;
    }

    /// `dnd_action` as the toolkit's word, or null for none. `ask` is offered
    /// by no Goldberry source, so it is read as not said.
    static @Nullable DragAction actionOf(int dndAction) {
        return switch (dndAction) {
            case DND_COPY -> DragAction.COPY;
            case DND_MOVE -> DragAction.MOVE;
            default -> null;
        };
    }

    /// The `dnd_action` bits for `actions`. Wayland has copy, move and ask,
    /// and no link, so a link is not offered; an offer of link alone is
    /// offered as a copy, since a drag that allows nothing is dropped nowhere.
    static int maskOf(Iterable<DragAction> actions) {
        var mask = DND_NONE;
        for (var action : actions) {
            mask |= switch (action) {
                case COPY -> DND_COPY;
                case MOVE -> DND_MOVE;
                case LINK -> 0;
            };
        }
        return mask == DND_NONE ? DND_COPY : mask;
    }
}
