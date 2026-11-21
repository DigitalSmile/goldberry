package dev.goldberry.render.backend.headless;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

import dev.goldberry.input.dragout.DragOperation;
import dev.goldberry.render.dragout.DragEnd;
import dev.goldberry.render.dragout.DragRequest;
import dev.goldberry.render.dragout.DragSession;
import dev.goldberry.render.model.LogicalPoint;
import dev.goldberry.render.window.BackendWindow;

/// A drag a headless window was asked to hand to the platform, and the session
/// a test plays the platform's part on.
///
/// ```java
/// var drag = window.drags().getFirst();
/// drag.over(other, 40, 30);
/// drag.releaseOver(other, 40, 30, false);
/// ```
///
/// Every report goes straight to the session, on the calling thread, which is
/// the UI thread's in a test as it is the platform's.
///
/// @param window  the window that started it
/// @param request what it was asked to carry
/// @param session what it reports to
public record HeadlessDrag(HeadlessWindow window, DragRequest request, DragSession session) {

    public HeadlessDrag {
        Objects.requireNonNull(window, "window");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(session, "session");
    }

    /// The pointer is over `target` at `x`, `y`.
    public void over(BackendWindow target, float x, float y) {
        session.over(target, new LogicalPoint(x, y));
    }

    /// The platform holds the UI thread and offers a turn of the loop, as
    /// Windows' modal drag does on every move.
    public void whileHeld() {
        session.whileHeld();
    }

    /// The pointer left every window of the application.
    public void outside() {
        session.outside();
    }

    /// Another application took it, doing `operation` (null where the platform
    /// would not say).
    public void deliver(@Nullable DragOperation operation) {
        session.ended(new DragEnd.Delivered(operation));
    }

    /// The button came up over `target` at `x`, `y`; `echoed` when SDL's own
    /// drop target took it too.
    public void releaseOver(BackendWindow target, float x, float y, boolean echoed) {
        session.ended(new DragEnd.ReleasedOver(target, new LogicalPoint(x, y), echoed));
    }

    /// The drag was cancelled.
    public void cancel() {
        session.ended(new DragEnd.Cancelled());
    }
}
