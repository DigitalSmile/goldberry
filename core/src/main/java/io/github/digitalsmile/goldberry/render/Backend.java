package io.github.digitalsmile.goldberry.render;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import io.github.digitalsmile.goldberry.render.dialog.FileChoice;
import io.github.digitalsmile.goldberry.render.dialog.FileDialogs;
import io.github.digitalsmile.goldberry.render.event.EventSink;
import io.github.digitalsmile.goldberry.render.popup.BackendPopup;
import io.github.digitalsmile.goldberry.render.popup.PopupSpec;
import io.github.digitalsmile.goldberry.render.tray.BackendTray;
import io.github.digitalsmile.goldberry.render.tray.TraySpec;
import io.github.digitalsmile.goldberry.render.window.BackendWindow;
import io.github.digitalsmile.goldberry.render.window.WindowSpec;

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
/// The §4 sketch also lists a GPU surface, which is absent from this cut and not
/// dropped: it needs a consumer before its shape can be decided, and an interface
/// designed against nothing is an interface that gets designed twice (ADR-0019).
/// Popups were on that list until §7's menus, tooltips and `select` gave them
/// one, **the clipboard was until `text-input` did**, and the **tray** was until
/// §9's `tray-icon` did. `canvas3d` is the one left.
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

    /// Puts an icon in the desktop's notification area.
    ///
    /// **Empty is a normal answer**, and it covers more than a missing feature
    /// usually does: a Linux session with no AppIndicator library, a desktop that
    /// removed its notification area, a container with no shell at all. Unlike
    /// [#createPopup], no error is read to tell absence from refusal — the Linux
    /// path reports a missing library, which is an absence wearing the words of a
    /// failure, and `core-widgets.md` §9 asks for absence to be reported either
    /// way. A caller that gets empty has nowhere else to put a tray icon and is
    /// expected to carry on without one.
    ///
    /// Process-global rather than per window, like the clipboard: an application
    /// has a tray presence, a window does not.
    ///
    /// The menu these rows describe is drawn by the **platform**, not by
    /// Goldberry — see [TrayItem].
    ///
    /// @param spec the icon, the tooltip and the menu
    /// @return the tray, or empty if this desktop has none
    default Optional<BackendTray> createTray(TraySpec spec) {
        return Optional.empty();
    }

    /// The session's clipboard.
    ///
    /// Never null and never [Optional]: a platform without one reports
    /// [Clipboard#none()], because every caller of a missing clipboard would
    /// otherwise write that class itself and a copy that quietly did nothing is
    /// the honest behaviour of a session with nowhere to put it.
    ///
    /// Process-global rather than per window — the clipboard belongs to the
    /// session, which is why this is on the backend and not on [BackendWindow].
    default Clipboard clipboard() {
        return Clipboard.none();
    }

    /// The platform's own open, save and folder dialogs.
    ///
    /// Never null and never [Optional], for [#clipboard()]'s reason rather than
    /// [#createPopup]'s: a caller that got an empty Optional would write
    /// [FileDialogs#none()] itself. Absence is asked about with
    /// [FileDialogs#supported()] and answered with a [FileChoice.Failed], so an
    /// export on a backend with no dialogs says so instead of quietly doing
    /// nothing.
    ///
    /// Process-global rather than per window, like the clipboard and the tray —
    /// the owner window is a parameter of the request, because modality is the
    /// only thing a window contributes to it.
    default FileDialogs fileDialogs() {
        return FileDialogs.none();
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
