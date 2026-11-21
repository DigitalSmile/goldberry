package dev.goldberry.render.dragout;

import dev.goldberry.render.model.LogicalPoint;
import dev.goldberry.render.window.BackendWindow;

/// What a backend tells the toolkit while a drag it started is with the
/// platform.
///
/// **On the UI thread, always**, and from inside the backend's event pump or
/// from inside the call that started the drag (Windows' `DoDragDrop` does not
/// return until the drag is over). After [#ended] nothing more is said, and a
/// second [#ended] is ignored.
public interface DragSession {

    /// The pointer is over one of this application's windows, at `at` in its
    /// logical pixels.
    void over(BackendWindow window, LogicalPoint at);

    /// The pointer is over no window of this application.
    void outside();

    /// The platform is holding the UI thread for the drag, and offers the
    /// toolkit a turn of its loop: the work posted to the UI thread and the
    /// timers that are due run here, as they would between two pumps.
    ///
    /// Only from inside the call that started the drag, and only where that
    /// call does not return until the drag is over (Windows' `DoDragDrop`).
    /// The backend paints the frames that are due itself, after this returns.
    /// Nothing by default.
    default void whileHeld() {}

    /// The drag is over.
    void ended(DragEnd end);
}
