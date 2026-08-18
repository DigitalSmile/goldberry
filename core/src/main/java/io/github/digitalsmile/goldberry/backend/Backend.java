package io.github.digitalsmile.goldberry.backend;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/// The only platform-facing interface. Everything above it is platform-agnostic
/// (`docs/ARCHITECTURE.md` §4).
///
/// Two implementations are planned and that is the complete list: `sdl3` for
/// desktop — Linux, Windows and macOS alike — and `headless` for tests. The SPI
/// exists to serve `headless` and to keep the platform boundary in one place; it
/// is not an invitation to grow hand-written Win32, Cocoa or Wayland backends
/// (ADR-0003, ADR-0041).
///
/// ## Threading
///
/// Every method here and on [BackendWindow] must be called on the **UI thread**:
/// the thread that created the backend. This is not a preference — AppKit
/// requires window and event calls on the process's first thread, so Goldberry
/// takes over the calling thread rather than spawning one, and a toolkit that
/// allowed otherwise would work everywhere except macOS.
///
/// [#wakeup()] is the single exception, and exists precisely so other threads
/// have one legal way to reach the UI thread: it is safe to call from anywhere.
///
/// ## What is not here yet
///
/// The §4 sketch also lists tray icons, a clipboard and a GPU surface. They are
/// absent from this cut, not dropped — each needs a consumer before its shape can
/// be decided, and an interface designed against nothing is an interface that
/// gets designed twice (ADR-0019). Popups were on that list until §7's menus,
/// tooltips and `select` gave them one.
public interface Backend extends AutoCloseable {

    /// A name for logs and diagnostics: `sdl3`, `headless`.
    String name();

    /// Creates a window.
    ///
    /// @throws BackendException if the platform refuses
    BackendWindow createWindow(WindowSpec spec);

    /// The windows this backend currently has open, in creation order.
    ///
    /// Popups included: a popup is a window, and a caller enumerating windows to
    /// shut them down must not miss one.
    List<BackendWindow> windows();

    /// Opens a popup window parented to `owner` — a menu, a dropdown, a tooltip.
    ///
    /// The one thing the in-window overlay layer cannot do is leave the window
    /// (ADR-0100), and it is exactly what a dropdown taller than the space below
    /// its button has to do. A popup is placed in the **owner's** logical
    /// coordinates and may extend past its edges.
    ///
    /// **Empty is a normal answer**, and the reason this returns an `Optional`
    /// rather than throwing: popup support is a property of the platform's video
    /// driver, not of the request. SDL's `dummy` driver has none. A caller that
    /// gets empty has to have somewhere else to put its menu, which is the
    /// in-window overlay layer, at the cost of being clipped to the window.
    ///
    /// The popup is created hidden, like every window here, and appears when its
    /// first frame is presented.
    ///
    /// @param owner the window the popup belongs to and is positioned against
    /// @param spec  where, how big, and what kind
    /// @return the popup, or empty if this backend or its driver has no popups
    /// @throws BackendException if the platform refuses a popup it should support
    default Optional<BackendPopup> createPopup(BackendWindow owner, PopupSpec spec) {
        return Optional.empty();
    }

    /// Waits for platform events and delivers them, then returns.
    ///
    /// Blocks until at least one event is available, `timeout` elapses, or
    /// [#wakeup()] is called. A zero timeout polls: it delivers whatever is
    /// already queued and returns immediately.
    ///
    /// Returning without delivering anything is normal — a timeout, or a wakeup
    /// with nothing behind it — so callers must not treat the frame loop as
    /// event-driven only.
    ///
    /// @return the number of events delivered to `sink`
    /// @throws BackendException if the platform's event queue fails
    int pumpEvents(EventSink sink, Duration timeout);

    /// Unblocks a [#pumpEvents] in progress, or makes the next one return
    /// immediately.
    ///
    /// **The only method safe to call from another thread.** It is how background
    /// work says "I have something for you" to the UI thread without touching
    /// anything else. Calls coalesce: ten wakeups while nothing is waiting release
    /// one pump, not ten.
    void wakeup();

    /// Closes every window and releases the platform's resources. Idempotent.
    @Override
    void close();
}
