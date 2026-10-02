package dev.goldberry.render;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import dev.goldberry.render.clipboard.Clipboard;
import dev.goldberry.render.clipboard.PrimarySelection;
import dev.goldberry.render.desktop.SystemTheme;
import dev.goldberry.render.dialog.FileChoice;
import dev.goldberry.render.dialog.FileDialogs;
import dev.goldberry.render.display.Display;
import dev.goldberry.render.event.EventSink;
import dev.goldberry.render.popup.BackendPopup;
import dev.goldberry.render.popup.PopupSpec;
import dev.goldberry.render.tray.BackendTray;
import dev.goldberry.render.tray.TraySpec;
import dev.goldberry.render.web.BackendWebView;
import dev.goldberry.render.web.WebViewSpec;
import dev.goldberry.render.window.BackendWindow;
import dev.goldberry.render.window.WindowSpec;

/// The only platform-facing interface: windows, popups, the clipboard, the tray,
/// file dialogs, web pages and the event pump. Everything above it is
/// platform-agnostic.
///
/// Two implementations exist and that is the complete list: `sdl3` for the
/// desktop — Linux, Windows and macOS alike — and `headless`, which renders to
/// memory for tests and servers. The SPI exists to serve `headless` and to keep
/// the platform boundary in one place; it is not an invitation to grow
/// hand-written Win32, Cocoa or Wayland backends. An application never names a
/// backend: the launcher picks one, and a test asks for the headless one through
/// the test-scope API.
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
/// ## What is optional
///
/// A popup, a tray icon, a web page, the desktop's theme and the primary
/// selection are `Optional`, because a platform may have none; the clipboard and
/// the file dialogs are never absent, and a platform without them answers
/// honestly through [Clipboard#none()] and [FileDialogs#none()].
///
/// Read more: [Architecture](https://goldberry.dev/docs/overview/architecture.html#the-backend-spi).
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
    /// The one thing the in-window overlay layer cannot do is leave the window,
    /// and it is exactly what a dropdown taller than the space below
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
    /// failure, and absence is reported either way. A caller that gets empty has
    /// nowhere else to put a tray icon and is expected to carry on without one.
    ///
    /// Process-global rather than per window, like the clipboard: an application
    /// has a tray presence, a window does not.
    ///
    /// The menu these rows describe is drawn by the **platform**, not by
    /// Goldberry — see [dev.goldberry.render.tray.TrayItem].
    ///
    /// @param spec the icon, the tooltip and the menu
    /// @return the tray, or empty if this desktop has none
    default Optional<BackendTray> createTray(TraySpec spec) {
        return Optional.empty();
    }

    /// Opens a web page in a window the engine owns.
    ///
    /// **Empty is a normal answer, and here it is the *usual* one.** Unlike every
    /// other call on this interface, the thing behind it lives in a library the
    /// build is allowed not to produce: the engine is the desktop's own, so
    /// `libgoldberry-webview` is separate and absent from any build made without
    /// WebKit's development headers. A caller that gets empty offers the user
    /// their own browser.
    ///
    /// Process-global rather than per window, like the tray and the clipboard: what
    /// this opens is not parented to anything of the toolkit's, and is not a
    /// [dev.goldberry.render.window.BackendWindow] at all.
    ///
    /// The default is empty, which is right for the headless backend — a golden
    /// image cannot contain a page, and a test must not open a real window.
    ///
    /// @param spec where the page starts, its title and its window size
    /// @return the page, or empty where no page can be opened here
    default Optional<BackendWebView> createWebView(WebViewSpec spec) {
        return Optional.empty();
    }

    /// Opens a page **inside** a window of this backend's — the `web-view` widget
    /// rather than a window of the page's own.
    ///
    /// **Empty is the usual answer, and on Wayland it always is.** Embedding
    /// means reparenting the engine's window into the application's, which X11,
    /// Win32 and Cocoa allow and Wayland does not — there is no cross-client
    /// surface embedding and no protocol proposing one. A caller that gets empty
    /// says so rather than opening a loose window.
    ///
    /// @param spec   where the page starts; its width and height are ignored,
    ///               because an embedded page is the size of the box it is in
    /// @param window the window to put it inside
    /// @param x      the left edge, in that window's own pixels
    /// @param y      the top edge, in the same pixels
    /// @param width  the width, positive
    /// @param height the height, positive
    /// @return the page, or empty where none can be embedded here
    default Optional<BackendWebView> createEmbeddedWebView(
            WebViewSpec spec, dev.goldberry.render.window.BackendWindow window, int x, int y, int width, int height) {
        return Optional.empty();
    }

    /// What the desktop's appearance is set to, or empty where it does not say.
    ///
    /// Process-global rather than per window, like the clipboard and the tray: a
    /// session has an appearance and a window does not.
    ///
    /// **Empty is a real answer and not a failure.** A desktop with no such
    /// setting, a driver that cannot ask and a `libgoldberry` built before the
    /// export all give it, and what a caller needs from the three is the same
    /// thing: use your own default rather than the desktop's, because the desktop
    /// has not got one.
    ///
    /// A change arrives as [dev.goldberry.render.event.BackendEvent.SystemThemeChanged],
    /// through the pump like everything else.
    ///
    /// @return `LIGHT`, `DARK`, or empty
    default Optional<SystemTheme> systemTheme() {
        return Optional.empty();
    }

    /// Whether the desktop asks for less movement — its reduce-motion switch.
    ///
    /// Process-global like the theme, and **empty is a real answer** for the same
    /// three reasons: a desktop with no such setting, a platform this cannot ask,
    /// and a machine with nothing to ask through. What a caller does about all
    /// three is the same — animate normally, because a default is not an
    /// instruction.
    ///
    /// Unlike the theme, **no event follows**: nothing listens for a change, so an
    /// answer is what the desktop said when the application started. Listening
    /// means a D-Bus main loop on Linux and a notification observer on the other
    /// two, which is a much larger thing than one read.
    ///
    /// @return `true` for reduce, `false` for animate, or empty for "the desktop
    ///         does not say"
    default Optional<Boolean> reducedMotion() {
        return Optional.empty();
    }

    /// Hands a URL to the desktop's own handler for its scheme — the browser
    /// for `https:`, the mail client for `mailto:`.
    ///
    /// Process-global, like the theme: the desktop opens it, not a window. It
    /// is a request, made and not awaited, and **false is an answer rather
    /// than a failure**: a headless backend, a library built before the export
    /// and a desktop with no handler for the scheme all give it, and a `link`
    /// that hears it has nothing more to do than say so.
    ///
    /// @param url what to open
    /// @return whether the request was made
    default boolean openUrl(String url) {
        return false;
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

    /// The session's primary selection — X11's middle-click buffer — or empty
    /// where the platform has none.
    ///
    /// **An [Optional], where [#clipboard()] is not**, because absence changes
    /// what a widget does and not only what it reads: with no primary selection
    /// a selection is published nowhere and a middle click is not a paste. See
    /// [PrimarySelection] for why a clipboard that reads empty is honest and a
    /// middle button that pastes nothing is not.
    ///
    /// Process-global, for the clipboard's reason.
    default Optional<PrimarySelection> primarySelection() {
        return Optional.empty();
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

    /// The displays connected now, the primary one first — asked fresh each
    /// time, because a display can be plugged in or taken away at any moment.
    ///
    /// Empty by default, which is right for a backend with no desktop under it,
    /// and which turns every clamp in
    /// [dev.goldberry.render.display.DisplayLayout] into a no-op.
    default List<Display> displays() {
        return List.of();
    }

    /// Whether this platform lets an application put a top-level window where
    /// it likes, and says where one is.
    ///
    /// False on Wayland, which places every window itself and tells nobody
    /// where. False by default.
    default boolean placesWindows() {
        return false;
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
