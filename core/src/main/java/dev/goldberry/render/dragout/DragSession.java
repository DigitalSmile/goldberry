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

    /// The drag is over.
    void ended(DragEnd end);
}
