package dev.goldberry.natives.desktop.dragout;

import org.jspecify.annotations.Nullable;

/// What a macOS drag source tells its caller, on the main thread, from inside
/// AppKit's event handling: a caller that runs application code on it hands
/// it to its own queue first.
///
/// Windows are `NSWindow` pointers, as addresses. Points are AppKit's:
/// points, not pixels, from the window's bottom-left corner, which is the
/// content's for a window whose title bar sits above it. Exactly one of the
/// three endings is heard, once.
public interface CocoaDragListener {

    /// The pointer is over one of the application's own windows.
    void over(long window, double x, double yFromBottom);

    /// The pointer left the application's own windows.
    void outside();

    /// Another application's target took the drag, doing `action`, or null
    /// where it did not say.
    void delivered(@Nullable DragAction action);

    /// The button came up over one of the application's own windows.
    void releasedOver(long window, double x, double yFromBottom);

    /// The drag was cancelled, or let go where nothing took it.
    void cancelled();
}
