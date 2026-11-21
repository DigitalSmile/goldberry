package dev.goldberry.natives.desktop.dragout;

import org.jspecify.annotations.Nullable;

/// What a drag source tells its caller, on the UI thread.
///
/// Windows are the platform's own ids (an X11 `Window`), and points are
/// physical pixels from the window's top-left corner. Exactly one of the three
/// endings is heard, once.
public interface DragSourceListener {

    /// The pointer is over one of the application's own windows.
    void over(long window, int x, int y);

    /// The pointer left the application's own windows.
    void outside();

    /// Another application's target took the drag, doing `action`, or null
    /// where it did not say.
    void delivered(@Nullable DragAction action);

    /// The button came up over one of the application's own windows.
    void releasedOver(long window, int x, int y);

    /// The drag was cancelled, or let go where nothing took it.
    void cancelled();
}
