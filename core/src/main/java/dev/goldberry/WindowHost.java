package dev.goldberry;

import dev.goldberry.bind.Subscription;

/// The [Host] of a window [Host#openWindow] opened: everything a host is, for
/// that window, and the handle that closes it.
///
/// ```java
/// var about = host.openWindow(WindowSpec.of("About", LogicalSize.of(360, 240)), new AboutPage())
///         .orElseThrow();
/// about.onClose(() -> LOG.info("about closed"));
/// ```
///
/// Confined to the UI thread, like every host.
///
/// Read more: [More than one window](https://goldberry.dev/docs/guide/windows.html#more-than-one-window).
public interface WindowHost extends Host {

    /// Closes the window and takes its tree down. Closing it twice is harmless.
    ///
    /// The close hook of [Window#onCloseRequest] is not asked: that is for
    /// the user's close, and this is the application's.
    void close();

    /// Whether the window is still open.
    boolean isOpen();

    /// Runs `action` once the window has closed and its tree is gone, however it
    /// closed: the user, [#close()], or the application ending.
    ///
    /// @return what stops the listening; closing it twice is harmless
    Subscription onClose(Runnable action);
}
