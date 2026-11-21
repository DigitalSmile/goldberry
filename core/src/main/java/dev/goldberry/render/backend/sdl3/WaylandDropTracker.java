package dev.goldberry.render.backend.sdl3;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.natives.desktop.dragout.DragAction;
import dev.goldberry.natives.desktop.dragout.WaylandDragListener;
import dev.goldberry.render.dragout.DragEnd;
import dev.goldberry.render.dragout.DragSession;
import dev.goldberry.render.model.LogicalPoint;
import dev.goldberry.render.window.BackendWindow;

/// Where a Wayland drag of the application's own is, and where it ended.
///
/// A Wayland source never hears where the pointer is. Over the application's
/// own windows the target is SDL's data device, which says so as SDL's own
/// drop events: a position while the drag is over a window, and a "complete"
/// both when it leaves and when it is let go there. The source, for its part,
/// hears `dnd_drop_performed` and then `dnd_finished` when **any** target took
/// a drop. This puts the two together.
///
/// The rule: a "complete" from SDL over a window marks that window as where
/// the drag may have been let go. A position anywhere clears the mark, and so
/// does a `target` the source hears before a drop, because a compositor says
/// `target` when the pointer moves to another surface (mutter says null on
/// every move, weston says what the new target took). If the drop finishes
/// with the mark still set, the drag was let go over that window; otherwise
/// another application took it.
///
/// The order the two halves arrive in is what makes this hold. SDL's drop
/// events are delivered on the pump after they are read, before the source's
/// queue is dispatched on that pump, and `dnd_finished` needs SDL's own
/// `finish` to have reached the compositor first. So the mark is always set
/// before the finish that it explains is heard.
///
/// **What it cannot see:** SDL takes only files and text. An offer of other
/// types alone (a PNG with no file, bytes of an application's own type) is
/// declined over the application's windows, which then hear nothing, and is
/// cancelled when let go there.
final class WaylandDropTracker implements WaylandDragListener {

    private record Over(BackendWindow window, LogicalPoint at) {}

    private final DragSession session;
    private @Nullable Over over;
    private @Nullable Over released;
    private boolean dropped;
    private boolean ended;

    WaylandDropTracker(DragSession session) {
        this.session = Objects.requireNonNull(session, "session");
    }

    /// SDL's drop position: the drag is over `window` at `at`.
    void over(BackendWindow window, LogicalPoint at) {
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(at, "at");
        if (ended) {
            return;
        }
        over = new Over(window, at);
        released = null;
        session.over(window, at);
    }

    /// SDL's drop complete: the drag left `window`, or was let go over it.
    void left(BackendWindow window) {
        Objects.requireNonNull(window, "window");
        if (ended) {
            return;
        }
        var was = over;
        over = null;
        if (was != null && was.window() == window) {
            released = was;
        }
        session.outside();
    }

    @Override
    public void target(@Nullable String mime) {
        if (!dropped) {
            released = null;
        }
    }

    @Override
    public void dropPerformed() {
        dropped = true;
    }

    @Override
    public void finished(@Nullable DragAction action) {
        if (ended) {
            return;
        }
        ended = true;
        var where = released;
        session.ended(
                where != null
                        ? new DragEnd.ReleasedOver(where.window(), where.at(), false)
                        : new DragEnd.Delivered(DragOutData.operation(action)));
    }

    @Override
    public void cancelled() {
        if (ended) {
            return;
        }
        ended = true;
        session.ended(new DragEnd.Cancelled());
    }
}
