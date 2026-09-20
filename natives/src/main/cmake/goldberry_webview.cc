// libgoldberry-webview -- §9's `web-view`, and the only native library of this
// project that an installation is allowed not to have (ADR-0441).
//
// WHY THIS IS NOT IN libgoldberry
//
// The engine behind webview/webview is the desktop's own: WebKitGTK here,
// WebView2 on Windows, WKWebView on macOS. Linking it into libgoldberry would
// make GTK and WebKit load-time dependencies of every Goldberry application on
// Linux -- including the overwhelming majority that never open a page -- and an
// application on a machine without them would then fail to load the toolkit at
// all rather than fail to open a web view. So this is a second shared library,
// linked into nothing, opened by Java on demand and absent from most builds.
//
// WHY THERE IS A SHIM AT ALL RATHER THAN DIRECT DOWNCALLS
//
// §3.1's rule is that Goldberry calls upstream functions directly through FFM,
// and two functions here have no upstream to call: `pump`, which is a GLib
// iteration on Linux and nothing anywhere else, and `abi`, which is how a
// library found somewhere the build did not put it is caught before it is
// trusted. The rest are thin by design -- they exist so that the whole surface
// is one export list with one naming convention, and so that
// `webview_error_t` crosses as a plain int.

#include <webview/webview.h>

#if defined(__linux__) || defined(__FreeBSD__)
#define GOLDBERRY_WEBVIEW_GLIB 1
#include <dlfcn.h>
#include <glib.h>
#include <gtk/gtk.h>
#include <gdk/gdkx.h>
#include <X11/Xlib.h>

// For webkit_web_view_is_loading and the estimated progress beside it, which is
// how `goldberry_webview_load_state` below answers. The engine's own header,
// reached through `webview_get_native_handle` -- webview/webview exposes the
// WebKitWebView it owns and promises nothing else about it, which is all this
// needs.
#if GTK_MAJOR_VERSION == 4
#include <webkit/webkit.h>
#else
#include <webkit2/webkit2.h>
#endif

// The GTK this shim was compiled against, and therefore the one it must NOT
// meet a different major of in the same process. See goldberry_webview_create.
#if GTK_MAJOR_VERSION == 4
#define GOLDBERRY_WEBVIEW_RIVAL_GTK "libgtk-3.so.0"
#else
#define GOLDBERRY_WEBVIEW_RIVAL_GTK "libgtk-4.so.1"
#endif
#endif

// The contract Webview.java binds. Bump on any change to the shape of what is
// exported below; Java refuses a library that disagrees rather than calling into
// it, because a mismatched shim is undefined behaviour and not a missing feature.
#define GOLDBERRY_WEBVIEW_ABI 6

// SizeHint.java carries these four numbers. Checked here rather than trusted
// there, which is the same move the layout table makes for every other binding:
// a constant that drifts is caught by the build that compiled both sides.
static_assert(WEBVIEW_HINT_NONE == 0, "SizeHint.NONE must be WEBVIEW_HINT_NONE");
static_assert(WEBVIEW_HINT_MIN == 1, "SizeHint.MIN must be WEBVIEW_HINT_MIN");
static_assert(WEBVIEW_HINT_MAX == 2, "SizeHint.MAX must be WEBVIEW_HINT_MAX");
static_assert(WEBVIEW_HINT_FIXED == 3, "SizeHint.FIXED must be WEBVIEW_HINT_FIXED");

#if defined(_WIN32)
#define GOLDBERRY_WEBVIEW_EXPORT __declspec(dllexport)
#else
#define GOLDBERRY_WEBVIEW_EXPORT __attribute__((visibility("default")))
#endif

extern "C" {

/// The contract this library implements.
GOLDBERRY_WEBVIEW_EXPORT int goldberry_webview_abi(void) { return GOLDBERRY_WEBVIEW_ABI; }

/// Whether a GTK of the other major is already in this process.
///
/// Exists so that Java can say WHICH of the two reasons a page did not open.
/// Both come back from `create` as NULL, and they want completely different
/// things from whoever reads the log: "no display, or a WebKit too old" sends a
/// reader to their session, while this one sends them to the tray they enabled
/// three screens earlier. Without it the message for the commonest failure on
/// Linux would be the least useful one.
///
/// Answers 0 where there is no GTK at all -- macOS and Windows, and a Linux
/// process that has opened neither a tray nor a page.
GOLDBERRY_WEBVIEW_EXPORT int goldberry_webview_gtk_conflict(void) {
#if defined(GOLDBERRY_WEBVIEW_GLIB)
    void *rival = dlopen(GOLDBERRY_WEBVIEW_RIVAL_GTK, RTLD_NOLOAD | RTLD_LAZY);
    if (rival != nullptr) {
        dlclose(rival);
        return 1;
    }
#endif
    return 0;
}

/// Opens a page in a window of the engine's own.
///
/// The second argument of `webview_create` is the native window to embed into,
/// and it is deliberately NULL here: embedding is impossible on Wayland, which
/// is the default Linux session, and a widget that is a box on three platforms
/// and a window on the fourth is two behaviours wearing one name (ADR-0441).
///
/// Returns NULL when the engine will not start -- no display, or a WebKit too
/// old -- which Java reports as a page that could not be opened rather than as a
/// crash.
GOLDBERRY_WEBVIEW_EXPORT void *goldberry_webview_create(int debug) {
#if defined(GOLDBERRY_WEBVIEW_GLIB)
    // TWO GTK MAJORS IN ONE PROCESS IS A SEGFAULT, NOT AN ERROR.
    //
    // GObject's type registry is process-global, and gdk-3 and gdk-4 both
    // register a type called `GdkDisplayManager`. Whichever initialises second
    // loses: g_type_register_static returns 0, g_once_init_leave_pointer
    // asserts, and gdk_display_manager_get_default_display dereferences NULL
    // inside gtk_init_check. The process dies in C, with a JVM crash log.
    //
    // This is not hypothetical and it is not rare. SDL's tray on Linux is
    // libayatana-appindicator, which links libgtk-3 -- so ANY Goldberry
    // application that shows a `tray-icon` (§9, ADR-0191) is already a GTK 3
    // process by the time anybody asks for a page. That is how this was found:
    // the showcase puts an icon in the tray at start-up, and the first press of
    // its "Open the Goldberry page" button took the whole window down.
    //
    // RTLD_NOLOAD asks "is this already mapped" WITHOUT loading it, so the check
    // costs nothing and cannot itself cause the collision it is looking for.
    if (goldberry_webview_gtk_conflict()) {
        // NULL, and Java asks goldberry_webview_gtk_conflict() which of the two
        // reasons it was. Refusing to open a page is a feature politely
        // declining; calling gtk_init here is a crash in somebody else's
        // application.
        return nullptr;
    }
#endif
    return webview_create(debug, nullptr);
}

/// Closes the window and frees the page.
GOLDBERRY_WEBVIEW_EXPORT void goldberry_webview_destroy(void *w) {
    if (w != nullptr) {
        webview_destroy(w);
    }
}

GOLDBERRY_WEBVIEW_EXPORT int goldberry_webview_navigate(void *w, const char *url) {
    return static_cast<int>(webview_navigate(w, url));
}

GOLDBERRY_WEBVIEW_EXPORT int goldberry_webview_set_html(void *w, const char *html) {
    return static_cast<int>(webview_set_html(w, html));
}

GOLDBERRY_WEBVIEW_EXPORT int goldberry_webview_set_title(void *w, const char *title) {
    return static_cast<int>(webview_set_title(w, title));
}

/// Sizes the window, working around an upstream defect on the GTK backend.
///
/// webview 0.12.0's `gtk_webview::set_size_impl` applies the size through a
/// complete if/else chain over the four hints and then falls off the end into
///
///     return error_info{WEBVIEW_ERROR_INVALID_ARGUMENT, "Invalid hint"};
///
/// unconditionally -- the final `else` is missing. The GTK calls are
/// straight-line code above it, so the size is applied and the error is
/// reported anyway: every set_size on this backend answers
/// WEBVIEW_ERROR_INVALID_ARGUMENT (-2), for every one of the four valid hints.
///
/// The error that return was meant to carry is "the hint is not one of the
/// four", and that is a question this shim can answer for itself. So the hint is
/// checked here, and a -2 that comes back for a hint already known to be valid
/// is the upstream bug rather than a real failure and is reported as success.
/// Any other error is passed through untouched.
///
/// Remove when the fix is released upstream and the pin moves past it -- the
/// check below is harmless either way, because a correct implementation returns
/// 0 and never reaches the translation.
GOLDBERRY_WEBVIEW_EXPORT int goldberry_webview_set_size(void *w, int width, int height, int hint) {
    if (hint < WEBVIEW_HINT_NONE || hint > WEBVIEW_HINT_FIXED) {
        return WEBVIEW_ERROR_INVALID_ARGUMENT;
    }
    int result = static_cast<int>(webview_set_size(w, width, height, static_cast<webview_hint_t>(hint)));
    return result == WEBVIEW_ERROR_INVALID_ARGUMENT ? WEBVIEW_ERROR_OK : result;
}

GOLDBERRY_WEBVIEW_EXPORT int goldberry_webview_eval(void *w, const char *js) {
    return static_cast<int>(webview_eval(w, js));
}

/// Gives the engine's event loop a turn, without ever taking the thread.
///
/// `webview_run()` is never called by this project. It owns a thread with its
/// own loop, and a page created on a private thread cannot call back into
/// anything that touches a widget (ADR-0020). So a page is created on the UI
/// thread and serviced from the frame loop instead, and what that costs is
/// different on each platform:
///
///   - Linux: SDL drives its own event queue and nothing drives GLib's, so this
///     drains the main context. Non-blocking -- `may_block` false -- because the
///     caller is a frame loop with a budget and this must return whether or not
///     there was anything to do.
///   - macOS and Windows: nothing. SDL's pump already drains the run loop and
///     the thread's message queue, which is exactly what services a WKWebView
///     and a WebView2 HWND created on that thread. The function still exists so
///     the caller has one shape rather than a platform test.
///
/// Process-wide rather than per page, because the main context is.
GOLDBERRY_WEBVIEW_EXPORT void goldberry_webview_pump(void) {
#if defined(GOLDBERRY_WEBVIEW_GLIB)
    // Bounded rather than `while (...)`: a page that keeps its context busy --
    // an animation, a spinning script -- would otherwise hold the frame loop
    // here for as long as it kept producing work, and a frame budget is the one
    // thing this must not spend. Sixteen is enough to drain an idle context in
    // one call and cheap enough to be wrong about.
    for (int i = 0; i < 16; i++) {
        if (!g_main_context_iteration(nullptr, FALSE)) {
            break;
        }
    }
#endif
}

/// Which window system a parent handle belongs to. Mirrors
/// `NativeWindowHandle.Kind` on the Java side, ordinal for ordinal.
#define GOLDBERRY_WEBVIEW_PARENT_X11 0
#define GOLDBERRY_WEBVIEW_PARENT_WIN32 1
#define GOLDBERRY_WEBVIEW_PARENT_COCOA 2

/// Whether a page can be put *inside* another window in this process.
///
/// **Only meaningful once GTK has been initialised**, which is what
/// `goldberry_webview_create*` does -- before that there is no default display
/// and this answers 0. That is why it is not the gate: Java decides from
/// `BackendWindow.nativeHandle()`, which is SDL's answer and needs no GTK, and
/// this is the confirmation afterwards.
///
/// Wayland is a permanent no rather than a gap: a surface belongs to the client
/// that made it, `xdg-foreign` is toplevel *parenting* and errors on anything
/// else, and fourteen years of requests have produced no embedding protocol
/// (ADR-0442).
GOLDBERRY_WEBVIEW_EXPORT int goldberry_webview_can_embed(void) {
#if defined(GOLDBERRY_WEBVIEW_GLIB)
    GdkDisplay *display = gdk_display_get_default();
    return display != nullptr && GDK_IS_X11_DISPLAY(display) ? 1 : 0;
#elif defined(_WIN32) || defined(__APPLE__)
    // SetParent and addSubview: both always available. UNVERIFIED -- neither has
    // been built or run, which is the tray's situation exactly.
    return 1;
#else
    return 0;
#endif
}

/// Reparents an open page's window into `parent`. Internal to
/// `goldberry_webview_create_embedded`, which is the only correct order.
static int goldberry_webview_embed_into(void *w, long long parent, int x, int y, int width, int height) {
#if defined(GOLDBERRY_WEBVIEW_GLIB)
    GtkWidget *window = static_cast<GtkWidget *>(webview_get_window(w));
    if (window == nullptr) {
        return -1;
    }
    // Undecorated, because the frame belongs to the Goldberry window now.
    gtk_window_set_decorated(GTK_WINDOW(window), FALSE);

    // AND NEVER MANAGED BY THE WINDOW MANAGER AT ALL.
    //
    // This window is a toplevel for exactly as long as it takes to map it and
    // reparent it, and for that instant the WM adopts it: it goes in the
    // taskbar, in the dock, in the alt-tab list. The reparent then takes it off
    // the root window, and what the shell is left holding is an entry for a
    // window that is now somebody's child -- so it cannot be raised, cannot be
    // focused and cannot be closed. A ghost, one per page opened, for the life
    // of the session.
    //
    // The hints are the EWMH way to say it and the override-redirect flag is
    // the X way, and both are set because they fail differently: a WM that
    // ignores _NET_WM_STATE_SKIP_TASKBAR still honours override-redirect, since
    // an override-redirect window is one it is told not to manage at all -- and
    // this one genuinely is not for it to manage, because it is about to stop
    // being a toplevel.
    //
    // Nothing is lost by it. An override-redirect window gets no decorations
    // (already off), no WM focus (the page's focus comes from being a child of a
    // window that has it) and no WM placement (it is positioned by
    // XReparentWindow on the line below).
    gtk_window_set_skip_taskbar_hint(GTK_WINDOW(window), TRUE);
    gtk_window_set_skip_pager_hint(GTK_WINDOW(window), TRUE);
    gtk_window_resize(GTK_WINDOW(window), width, height);

    // Realize BEFORE show, which is the one reordering in this function and is
    // required by the flag above: override-redirect is an attribute of the X
    // window, so the X window has to exist, and it must be set before the map
    // or the WM has already seen it. `gtk_widget_realize` creates the window
    // without mapping it, which is exactly the gap that is wanted here.
    gtk_widget_realize(window);
    GdkWindow *gdk = gtk_widget_get_window(window);
    if (gdk == nullptr || !GDK_IS_X11_WINDOW(gdk)) {
        return -1;
    }
    gdk_window_set_override_redirect(gdk, TRUE);

    // And NOW shown. `gtk_widget_realize` alone creates the shell's window
    // without mapping the WebKit widget inside it, and the result is a correctly
    // positioned rectangle with nothing in it -- which is exactly what the first
    // attempt at this function produced. That is why the show is still here and
    // still before the reparent.
    gtk_widget_show_all(window);
    Display *display = gdk_x11_get_default_xdisplay();
    Window child = gdk_x11_window_get_xid(gdk);
    XReparentWindow(display, child, static_cast<Window>(parent), x, y);
    XMoveResizeWindow(display, child, x, y, static_cast<unsigned int>(width), static_cast<unsigned int>(height));
    XMapWindow(display, child);
    XSync(display, False);
    return 0;
#else
    (void) w; (void) parent; (void) x; (void) y; (void) width; (void) height;
    return -1;
#endif
}

/// Opens a page already inside `parent`, at `x,y` and `width x height` in that
/// window's coordinates.
///
/// **Not `create` followed by `embed`, and the order is the whole point.** GDK
/// chooses its backend during `gtk_init`, and on a machine running XWayland both
/// Wayland and X11 are available -- GTK prefers Wayland. So a page created
/// without saying otherwise gets a `wl_surface`, which cannot be reparented into
/// anything, even though SDL is happily on X11 and handed us an X11 `Window`.
/// The two halves of one process disagreeing about the window system is not a
/// theoretical hazard; it is the default on this desktop.
///
/// `GDK_BACKEND=x11` is therefore set BEFORE the engine starts, and with
/// overwrite=0 so an application that has deliberately chosen otherwise keeps
/// its choice.
///
/// Returns NULL when the page could not be opened embedded -- a Wayland GDK, a
/// GTK already initialised on the wrong backend, a rival GTK major, or an engine
/// that would not start. The caller falls back to saying so rather than to
/// opening a loose window (ADR-0442).
GOLDBERRY_WEBVIEW_EXPORT void *goldberry_webview_create_embedded(
        int debug, long long parent, int kind, int x, int y, int width, int height) {
    if (parent == 0 || width <= 0 || height <= 0) {
        return nullptr;
    }
#if defined(GOLDBERRY_WEBVIEW_GLIB)
    if (kind != GOLDBERRY_WEBVIEW_PARENT_X11) {
        return nullptr;
    }
    // Before gtk_init, and only if nothing has said otherwise.
    setenv("GDK_BACKEND", "x11", 0);
    void *w = goldberry_webview_create(debug);
    if (w == nullptr) {
        return nullptr;
    }
    // Asked AFTER create, because only then is there a display to ask. A GTK
    // that was already up on Wayland -- a tray shown first, say -- lands here.
    if (!goldberry_webview_can_embed() || goldberry_webview_embed_into(w, parent, x, y, width, height) != 0) {
        webview_destroy(w);
        return nullptr;
    }
    return w;
#else
    (void) debug;
    (void) kind;
    (void) x;
    (void) y;
    // UNVERIFIED on Windows and macOS: SetParent(hwnd, parent) and
    // [[parent contentView] addSubview:view] are the calls, and neither has been
    // written because neither can be run here (ADR-0442).
    return nullptr;
#endif
}

/// How far through loading a page is: -1 unknown, 0 not started, 1 loading,
/// 2 finished.
///
/// **Why this exists.** A page is a platform window above the frame, so nothing
/// Goldberry paints can cover it -- including a "loading" indicator. From the
/// moment the window is mapped until the document paints, what the user sees is
/// WebKit's default white, for as long as the network takes. The only way to
/// show a spinner instead is to keep the page out of sight until it has
/// something to show, and that needs someone to ask whether it does.
///
/// **Polled rather than signalled**, which is the whole reason it is one int.
/// `load-changed` is a GObject signal and binding it would mean an upcall stub,
/// a callback whose lifetime outlives the Java object that owns it, and a
/// crossing from GLib's thread. The widget is already asking this library
/// something once a frame -- it calls `set_bounds` from its painter -- so a
/// question it can ask on the same pass costs nothing it was not already
/// paying.
///
/// The two facts are combined **here** rather than in Java because the
/// interesting state is the one neither of them reports on its own: a page that
/// has been created but never navigated is not loading and has made no progress,
/// and it is exactly as unready as one that is still fetching. Java would have
/// to know that `estimated-load-progress` is 0 before the first load and stays
/// at 1 after the last, which is WebKit's business and not the toolkit's.
GOLDBERRY_WEBVIEW_EXPORT int goldberry_webview_load_state(void *w) {
    if (w == nullptr) {
        return -1;
    }
#if defined(GOLDBERRY_WEBVIEW_GLIB)
    void *controller = webview_get_native_handle(w, WEBVIEW_NATIVE_HANDLE_KIND_BROWSER_CONTROLLER);
    if (controller == nullptr || !WEBKIT_IS_WEB_VIEW(controller)) {
        return -1;
    }
    WebKitWebView *view = WEBKIT_WEB_VIEW(controller);
    if (webkit_web_view_is_loading(view)) {
        return 1;
    }
    // Not loading, and the progress says which kind of "not": 0 is a page that
    // has never been asked for anything, anything above it is one whose last
    // load ended -- successfully, or on the error document WebKit substitutes,
    // which is still something worth showing rather than a spinner forever.
    return webkit_web_view_get_estimated_load_progress(view) > 0.0 ? 2 : 0;
#else
    // UNVERIFIED on Windows and macOS, like the embedding itself: the calls are
    // ICoreWebView2's NavigationCompleted and WKWebView's `loading`, and neither
    // has been written because neither can be run here (ADR-0442).
    return -1;
#endif
}

/// Makes `name` a global JavaScript function the page can call.
///
/// `webview_bind` injects the glue itself: calling `window.<name>(...)` in the
/// page returns a **promise**, and `fn` is handed a request id, the arguments as
/// a JSON array, and the `arg` this was bound with. Answering is
/// `goldberry_webview_return` below, and until something answers, the promise is
/// pending.
///
/// Thin, like `navigate` and `eval` beside it: what this adds is the one naming
/// convention and a `webview_error_t` that crosses as a plain int (sec. 3.1).
///
/// **Bind before navigating.** The glue runs at document start, so a binding
/// made after a page has loaded is not there for the script that already ran.
GOLDBERRY_WEBVIEW_EXPORT int goldberry_webview_bind(
        void *w, const char *name, void (*fn)(const char *id, const char *req, void *arg), void *arg) {
    if (w == nullptr || name == nullptr || fn == nullptr) {
        return -1;
    }
    return webview_bind(w, name, fn, arg);
}

/// Answers one call to a bound function, resolving or rejecting its promise.
///
/// `status` of zero resolves with `result`, which must be a valid JSON value or
/// an empty string for `undefined`; anything else rejects with it. The id is the
/// one the binding handler was given and is not valid after this call.
GOLDBERRY_WEBVIEW_EXPORT int goldberry_webview_return(void *w, const char *id, int status, const char *result) {
    if (w == nullptr || id == nullptr) {
        return -1;
    }
    return webview_return(w, id, status, result == nullptr ? "" : result);
}

/// Moves and resizes an embedded page within its parent.
///
/// Called whenever the widget's box changes, which is every layout that moves
/// it: a window resize, a `scroll`, a tab change. Cheap enough for that -- it is
/// one X request and no round trip.
GOLDBERRY_WEBVIEW_EXPORT int goldberry_webview_set_bounds(void *w, int x, int y, int width, int height) {
    if (w == nullptr || width <= 0 || height <= 0) {
        return -1;
    }
#if defined(GOLDBERRY_WEBVIEW_GLIB)
    GtkWidget *window = static_cast<GtkWidget *>(webview_get_window(w));
    if (window == nullptr) {
        return -1;
    }
    GdkWindow *gdk = gtk_widget_get_window(window);
    if (gdk == nullptr || !GDK_IS_X11_WINDOW(gdk)) {
        return -1;
    }
    // GTK is told as well as X, so the WebKit widget inside lays out to the new
    // size rather than staying the size it was mapped at.
    gtk_window_resize(GTK_WINDOW(window), width, height);
    XMoveResizeWindow(
            gdk_x11_get_default_xdisplay(),
            gdk_x11_window_get_xid(gdk),
            x,
            y,
            static_cast<unsigned int>(width),
            static_cast<unsigned int>(height));
    return 0;
#else
    (void) x;
    (void) y;
    return -1;
#endif
}
}
