package io.github.digitalsmile.goldberry.natives.webview;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;

import io.github.digitalsmile.goldberry.log.Logs;
import io.github.digitalsmile.goldberry.natives.sdl.window.NativeWindowHandle;
import io.github.digitalsmile.goldberry.natives.webview.calls.WebviewCalls;

/// One web page, in a window the engine owns.
///
/// The owning wrapper around `webview/webview`: it holds the handle, it traffics
/// in Java types, and no `MemorySegment` reaches a caller — §3.1's rule, the same
/// one every wrapper in this module keeps.
///
/// ## The window is not the toolkit's
///
/// What this opens is a top-level window belonging to WebKitGTK, WebView2 or
/// WKWebView. It is not a Goldberry window, it has no `BackendWindow`, and it is
/// not in any element tree. That is the whole of [ADR-0441]: a page cannot be a
/// box, because Wayland permits neither reparenting a foreign surface nor placing
/// a window where a widget is.
///
/// ## Threads
///
/// UI-thread confined, like every other platform handle here. The page is created
/// on that thread and `webview_run` is deliberately **never** called — see
/// [#pump()], which is what services it instead.
public final class Webview implements AutoCloseable {

    /// The contract this binding is written against.
    ///
    /// Bumped whenever the shim's exported functions change shape. A library
    /// found on a path the build did not choose — a distribution package, a `-D`
    /// override — is checked against this before anything is called through it.
    public static final int ABI = 4;

    private static final Logger LOG = Logs.of(Webview.class);

    private static final class Holder {
        private static final Optional<WebviewCalls> CALLS = bindCalls();
    }

    /// Binds the shim, and answers empty rather than throwing for **every** way
    /// that can fail.
    ///
    /// Nothing here may escape as a `LinkageError`. This runs inside a static
    /// initialiser reached from `Goldberry.capabilities()`, which promises an
    /// answer on a machine with no native library at all — and a throw would not
    /// merely fail, it would poison the class for the life of the JVM, so the
    /// *second* call gets a `NoClassDefFoundError` with none of the original
    /// cause on it. Both happened.
    ///
    /// The ABI is read **before** the rest is bound, because a library that
    /// disagrees is one whose other symbols may not exist — see
    /// [WebviewCalls#bindAbi].
    private static Optional<WebviewCalls> bindCalls() {
        var library = WebviewLibrary.get();
        if (library.isEmpty()) {
            return Optional.empty();
        }
        var lookup = library.get().lookup();
        try {
            var abi = WebviewCalls.bindAbi(lookup).call();
            if (abi != ABI) {
                // Loud, unlike a missing library: a library that is present and
                // disagrees is a stale installation rather than a build without
                // the feature, and calling into it would be undefined behaviour.
                LOG.warn(
                        "{} at {} reports ABI {} and this build binds ABI {}; no page will be opened."
                                + " Rebuild the natives, or delete the stale library",
                        WebviewLibrary.LIBRARY_STEM,
                        library.get().path(),
                        abi,
                        ABI);
                return Optional.empty();
            }
            return Optional.of(WebviewCalls.bind(lookup));
        } catch (LinkageError | RuntimeException e) {
            // A library that agreed about the ABI and is still missing a symbol,
            // or one too old to have the probe at all. Either way: no page, and
            // the toolkit carries on.
            LOG.warn(
                    "{} at {} could not be bound ({}); no page will be opened",
                    WebviewLibrary.LIBRARY_STEM,
                    library.get().path(),
                    e.toString());
            return Optional.empty();
        }
    }

    /// How many pages are open, and therefore whether [#pump()] has anything to
    /// drive.
    ///
    /// The guard matters more than it looks: without it, a frame loop calling
    /// `pump` would touch [Holder] on the first frame of **every** application,
    /// which is what loads `libgoldberry-webview` — and with it GTK and WebKit —
    /// into a process that never asked for a page. The whole point of the library
    /// being separate is that it stays unopened until something wants it.
    ///
    /// A plain `int` rather than an atomic because this class is UI-thread
    /// confined, like every other platform handle in this module.
    private static int open;

    private final WebviewCalls calls;

    private MemorySegment handle;

    private Webview(WebviewCalls calls, MemorySegment handle) {
        this.calls = calls;
        this.handle = handle;
    }

    /// Opens a page, if this build can.
    ///
    /// @param debug whether to enable the engine's own inspector — WebKit's Web
    ///        Inspector, or Edge's DevTools. Off in a release build of an
    ///        application, and the reason this is a parameter rather than a
    ///        property is that an application may legitimately ship it on
    /// @return the page, or empty where there is no web view library, where it
    ///         is too old, or where the engine would not start
    public static Optional<Webview> open(boolean debug) {
        var calls = Holder.CALLS;
        if (calls.isEmpty()) {
            return Optional.empty();
        }
        // The ABI was checked when the calls were bound, before any other symbol
        // was looked up — see bindCalls().
        var bound = calls.get();
        var handle = bound.create().call(debug ? 1 : 0);
        if (MemorySegment.NULL.equals(handle)) {
            // Two reasons, and they send a reader to completely different places.
            // The second is the common one on Linux and the least guessable, so
            // it is spelled out rather than left as "would not start".
            if (bound.gtkConflict().call() != 0) {
                LOG.warn("no page was opened: this process already holds a different major version of GTK,"
                        + " and two in one process crash inside gtk_init_check. On Linux that is"
                        + " usually the system tray — SDL's is libayatana-appindicator, which links"
                        + " GTK 3 — against a web view library built for GTK 4. Build the natives"
                        + " against webkit2gtk-4.1 (libwebkit2gtk-4.1-dev), which is the GTK 3"
                        + " pairing, or do not show a tray icon. See ADR-0441");
            } else {
                LOG.warn("the web view engine would not start; no page was opened");
            }
            return Optional.empty();
        }
        open++;
        return Optional.of(new Webview(bound, handle));
    }

    /// Opens a page **inside** `parent`, at `x,y` and `width x height` in that
    /// window's own pixels.
    ///
    /// The embedded half of §9's `web-view`: the page keeps its own platform
    /// window and that window becomes a child of the application's, so it takes
    /// part in the layout instead of floating beside it.
    ///
    /// **X11 only, and that is permanent.** Wayland allows no cross-client
    /// surface embedding — `xdg-foreign` is toplevel *parenting* and errors on
    /// anything else — so a caller on Wayland gets empty and is expected to say
    /// so rather than to open a loose window ([ADR-0442]).
    ///
    /// @param parent the platform's handle for the window to go inside
    /// @param kind   which window system that handle belongs to
    /// @return the page, or empty where it could not be opened embedded
    public static Optional<Webview> openEmbedded(
            boolean debug, long parent, NativeWindowHandle.Kind kind, int x, int y, int width, int height) {
        Objects.requireNonNull(kind, "kind");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException(
                    "an embedded page is " + width + "x" + height + ", and both must be > 0");
        }
        var calls = Holder.CALLS;
        if (calls.isEmpty()) {
            return Optional.empty();
        }
        var bound = calls.get();
        var handle = bound.createEmbedded().call(debug ? 1 : 0, parent, kind.ordinal(), x, y, width, height);
        if (MemorySegment.NULL.equals(handle)) {
            if (bound.gtkConflict().call() != 0) {
                LOG.warn("no page was opened: this process already holds a different major version of GTK"
                        + " — see ADR-0441 and ADR-0442");
            } else {
                LOG.info("no page could be opened inside the window: this session does not permit embedding."
                        + " Wayland has no cross-client surface embedding; running on X11 or XWayland does");
            }
            return Optional.empty();
        }
        open++;
        return Optional.of(new Webview(bound, handle));
    }

    /// Moves and resizes an embedded page within its parent.
    ///
    /// Called whenever the widget's box changes. One X request and no round trip,
    /// which is what makes it affordable every frame.
    public void bounds(int x, int y, int width, int height) {
        requireOpen();
        if (width <= 0 || height <= 0) {
            return;
        }
        calls.setBounds().call(handle, x, y, width, height);
    }

    /// Whether a page can be opened in this process at all.
    public static boolean isAvailable() {
        return Holder.CALLS.isPresent();
    }

    /// Whether any page is open, and therefore whether the frame loop has to keep
    /// giving [#pump()] turns.
    ///
    /// Reads the counter and nothing else, so an application with no page never
    /// loads the library by asking.
    public static boolean hasOpenPages() {
        return open > 0;
    }

    /// Gives every open page's event loop a turn.
    ///
    /// Static, because what it drives is: GLib has one main context, and draining
    /// it services every page at once. A no-op on macOS and Windows, where SDL's
    /// own pump already drains the run loop and the thread's message queue.
    ///
    /// Cheap enough to call every frame and doing nothing when no page is open,
    /// which is what the frame loop relies on — see [#open], which is why the
    /// count is read *before* anything that could load the library.
    public static void pump() {
        if (open == 0) {
            return;
        }
        Holder.CALLS.ifPresent(calls -> calls.pump().call());
    }

    /// Points the page at `url`.
    ///
    /// @param url an absolute URL. `file://` and `data:` work as they do in a
    ///        browser; a relative one has nothing to be relative to, because a
    ///        page opened here has no document to start from
    public void navigate(String url) {
        Objects.requireNonNull(url, "url");
        withText(url, text -> calls.navigate().call(handle, text), "navigate to " + url);
    }

    /// Replaces the page's contents with `html`.
    public void html(String html) {
        Objects.requireNonNull(html, "html");
        withText(html, text -> calls.setHtml().call(handle, text), "set " + html.length() + " characters of HTML");
    }

    /// Sets the window's title.
    public void title(String title) {
        Objects.requireNonNull(title, "title");
        withText(title, text -> calls.setTitle().call(handle, text), "set the title to \"" + title + '"');
    }

    /// Sizes the window, in the desktop's own pixels.
    ///
    /// **Not logical pixels.** A page's window is not a Goldberry window and does
    /// not inherit a scale from one; the engine applies the desktop's scaling to
    /// the page itself, exactly as a browser does.
    ///
    /// @param width  the width, positive
    /// @param height the height, positive
    /// @param hint   what the size means
    public void size(int width, int height, SizeHint hint) {
        Objects.requireNonNull(hint, "hint");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("a page's window is " + width + "x" + height + ", and both must be > 0");
        }
        requireOpen();
        report(calls.setSize().call(handle, width, height, hint.value()), "size the window " + width + "x" + height);
    }

    /// Runs `script` in the page.
    ///
    /// Nothing comes back. The upstream call is asynchronous and its result
    /// reaches the host through a binding rather than a return value, and no
    /// binding is exposed here yet — an application that needs one has asked for
    /// the half of `webview/webview` this project has not had a consumer for.
    public void eval(String script) {
        Objects.requireNonNull(script, "script");
        withText(script, text -> calls.eval().call(handle, text), "evaluate " + script.length() + " characters");
    }

    /// Whether this page has been closed.
    public boolean isClosed() {
        return handle == null;
    }

    /// Closes the window and frees the page. Idempotent.
    @Override
    public void close() {
        if (handle != null) {
            var closing = handle;
            // Cleared first, so a destroy that throws does not leave a handle
            // that a second close would free twice.
            handle = null;
            open--;
            calls.destroy().call(closing);
        }
    }

    private void withText(String value, java.util.function.ToIntFunction<MemorySegment> call, String what) {
        requireOpen();
        try (var arena = Arena.ofConfined()) {
            report(call.applyAsInt(arena.allocateFrom(value)), what);
        }
    }

    /// A failed call is logged and not thrown.
    ///
    /// The engine refuses things for reasons an application cannot predict or fix
    /// — a URL scheme WebKit will not load, a script the page's CSP forbids — and
    /// none of them is a programming error on the caller's side. A page that
    /// refused a navigation is still a page.
    private void report(int result, String what) {
        if (result != 0) {
            LOG.warn("the page would not {} (the engine returned {})", what, result);
        }
    }

    private void requireOpen() {
        if (handle == null) {
            throw new IllegalStateException("this page is closed");
        }
    }
}
