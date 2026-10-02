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

#include <string>
#include <unordered_map>

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

#if defined(__APPLE__)
// WKWebView, driven through the Objective-C runtime from C++ -- the same way
// webview.h drives it, so this stays one translation unit with one compiler and
// no .mm beside it. See the "COCOA EMBEDDING" section below for what it does.
#define GOLDBERRY_WEBVIEW_COCOA 1
#include <CoreGraphics/CoreGraphics.h>
#include <objc/message.h>
#include <objc/runtime.h>
#include <cstdint>
#include <unordered_map>
#endif

#if defined(_WIN32)
// WebView2, which webview.h has already included -- <windows.h> and WebView2.h
// both come in with it. See the "WIN32 EMBEDDING" section below.
#define GOLDBERRY_WEBVIEW_WIN32 1
#include <objbase.h>
#include <atomic>
#include <cstdint>
#include <unordered_map>
#endif

// The contract Webview.java binds. Bump on any change to the shape of what is
// exported below; Java refuses a library that disagrees rather than calling into
// it, because a mismatched shim is undefined behaviour and not a missing feature.
#define GOLDBERRY_WEBVIEW_ABI 8

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

#if defined(GOLDBERRY_WEBVIEW_COCOA)
// COCOA EMBEDDING
//
// On X11 a page is a GTK toplevel that is reparented into the application's
// window. macOS has no reparenting of windows, and does not need it: a view is
// the unit of composition there, and a WKWebView is a view. So an embedded page
// is the engine's WKWebView added as a SUBVIEW of the content view of SDL's
// NSWindow, placed with setFrame:.
//
// WHY NOT webview_create(debug, sdlWindow). webview.h accepts a parent window on
// Cocoa, and what it does with it is `[window setContentView:webview]` -- it
// REPLACES the content view. SDL draws through that content view (its Metal
// view is a subview of it, and its event handling is wired to it), so handing
// SDL's window over would take the whole Goldberry frame off the screen and
// leave a page in its place.
//
// So the engine is given a HOLDER instead: a borderless NSWindow that is never
// ordered front and exists only so that webview.h has somewhere to put the view
// it makes. The view is then taken out of the holder and added to SDL's content
// view. The holder is kept until the page is destroyed, because the engine keeps
// a pointer to it as `m_window` and reads it in its destructor.
//
// Everything here runs on the main thread, which is the UI thread on macOS
// (`-XstartOnFirstThread`, ADR-0039) and the only thread AppKit allows.
namespace goldberry_cocoa {

using webview::detail::objc::msg_send;

/// NSViewMinYMargin and NSViewMaxYMargin -- which margin stretches when the
/// superview is resized. Spelled out because <AppKit/AppKit.h> is Objective-C.
constexpr NSUInteger VIEW_MIN_Y_MARGIN = 8;
constexpr NSUInteger VIEW_MAX_Y_MARGIN = 32;

/// NSWindowAbove, for addSubview:positioned:relativeTo:.
constexpr NSInteger WINDOW_ABOVE = 1;

/// NSWindowStyleMaskBorderless and NSBackingStoreBuffered.
constexpr NSUInteger STYLE_BORDERLESS = 0;
constexpr NSUInteger BACKING_BUFFERED = 2;

inline SEL sel(const char *name) { return sel_registerName(name); }

inline id cls(const char *name) { return reinterpret_cast<id>(objc_getClass(name)); }

/// A message that returns a CGRect.
///
/// On arm64 a struct return comes back through plain objc_msgSend; on x86_64 a
/// 32-byte struct is returned through memory and needs objc_msgSend_stret. The
/// matrix ships macos-aarch64 only, and CMake can still be asked for x86_64, so
/// both are written down rather than the one that happens to be built today.
inline CGRect msg_send_rect(id receiver, SEL selector) {
#if defined(__x86_64__)
    return reinterpret_cast<CGRect (*)(id, SEL)>(objc_msgSend_stret)(receiver, selector);
#else
    return msg_send<CGRect>(receiver, selector);
#endif
}

/// The holder window of every embedded page, by the page's handle.
///
/// A plain map with no lock, because every caller is on the main thread; a page
/// that is not in here was opened as a window of its own and has nothing to
/// detach.
inline std::unordered_map<void *, id> &holders() {
    static std::unordered_map<void *, id> map;
    return map;
}

/// A borderless window nobody will see, for webview.h to put its view into.
///
/// `releasedWhenClosed` off, because this file owns the one reference and
/// releases it itself -- see goldberry_webview_destroy.
inline id make_holder() {
    id holder = msg_send<id>(cls("NSWindow"), sel("alloc"));
    holder = msg_send<id>(holder,
                          sel("initWithContentRect:styleMask:backing:defer:"),
                          CGRectMake(0, 0, 1, 1),
                          STYLE_BORDERLESS,
                          BACKING_BUFFERED,
                          static_cast<BOOL>(YES));
    if (holder != nullptr) {
        msg_send<void>(holder, sel("setReleasedWhenClosed:"), static_cast<BOOL>(NO));
    }
    return holder;
}

/// Puts `view` at `x,y` / `width x height`, given in the parent window's
/// PIXELS with the origin at the top left.
///
/// Three conversions, because AppKit disagrees with the caller about all three:
///
///   - Pixels to points. Java hands over device pixels -- the same numbers an
///     X11 window takes -- and a frame on macOS is in points, so the window's
///     backingScaleFactor divides them out.
///   - Top-left to bottom-left. A view that is not flipped has its origin at the
///     bottom, so the y is measured from the other edge.
///   - Kept to the TOP when the window is resized. The widget moves the page only
///     when its own logical box changes, and a window that grows taller moves an
///     unflipped view's top edge without changing that box at all. The
///     autoresizing mask makes the bottom margin the one that stretches, so the
///     page stays where the widget is until the next placement confirms it.
///
/// And one decision: a frame that lies entirely outside the content view is
/// HIDDEN as well as moved. That is how the widget parks a page (ADR-0444,
/// ADR-0445) -- far past the top-left corner -- and on X11 the parent clips a
/// child there. An NSView does not reliably clip its subviews (`clipsToBounds`
/// has defaulted to NO since macOS 14), and a frame above the content view is in
/// the title bar, so on macOS "out of sight" is said rather than implied.
///
/// Returns 0, or -1 when the view is not in a window.
inline int place(id view, int x, int y, int width, int height) {
    id superview = msg_send<id>(view, sel("superview"));
    id window = msg_send<id>(view, sel("window"));
    if (superview == nullptr || window == nullptr) {
        return -1;
    }
    CGFloat scale = msg_send<CGFloat>(window, sel("backingScaleFactor"));
    if (!(scale > 0)) {
        scale = 1;
    }
    CGRect bounds = msg_send_rect(superview, sel("bounds"));
    bool flipped = msg_send<BOOL>(superview, sel("isFlipped"));
    CGFloat left = x / scale;
    CGFloat top = y / scale;
    CGFloat w = width / scale;
    CGFloat h = height / scale;
    CGRect frame = CGRectMake(left, flipped ? top : bounds.size.height - top - h, w, h);

    msg_send<void>(view, sel("setFrame:"), frame);
    msg_send<void>(view, sel("setAutoresizingMask:"), flipped ? VIEW_MAX_Y_MARGIN : VIEW_MIN_Y_MARGIN);
    msg_send<void>(view, sel("setHidden:"), static_cast<BOOL>(!CGRectIntersectsRect(frame, bounds)));

    // On top of SDL's own subviews, every time. SDL keeps a Metal view inside
    // its content view and may make it again, and a view added after the page
    // would draw the Goldberry frame straight over it. Checking costs two
    // messages; re-adding a view that is already a subview only reorders it.
    id subviews = msg_send<id>(superview, sel("subviews"));
    if (msg_send<id>(subviews, sel("lastObject")) != view) {
        msg_send<void>(superview, sel("addSubview:positioned:relativeTo:"), view, WINDOW_ABOVE, static_cast<id>(nullptr));
    }
    return 0;
}

/// The WKWebView behind a page.
inline id web_view(void *w) {
    return static_cast<id>(webview_get_native_handle(w, WEBVIEW_NATIVE_HANDLE_KIND_UI_WIDGET));
}

/// Opens a page inside `parent`, an NSWindow*, at a rectangle in its pixels.
/// NULL when there is no content view to go into or the engine would not start.
inline void *create_embedded(int debug, id parent, int x, int y, int width, int height) {
    webview::detail::objc::autoreleasepool pool;
    id content = msg_send<id>(parent, sel("contentView"));
    if (content == nullptr) {
        return nullptr;
    }
    id holder = make_holder();
    if (holder == nullptr) {
        return nullptr;
    }
    void *w = webview_create(debug, holder);
    id view = w == nullptr ? nullptr : web_view(w);
    if (view == nullptr) {
        if (w != nullptr) {
            webview_destroy(w);
        }
        msg_send<void>(holder, sel("release"));
        return nullptr;
    }
    // Out of the holder and into the application's window. The engine's own
    // reference -- it allocated the view -- keeps it alive across the move, and
    // the content view's retain is the one that will be dropped on destroy.
    msg_send<void>(holder, sel("setContentView:"), static_cast<id>(nullptr));
    msg_send<void>(content, sel("addSubview:"), view);
    holders()[w] = holder;
    place(view, x, y, width, height);
    return w;
}

/// Whether the key window's first responder is the page or something inside
/// it -- WKWebView keeps a private content view that is the real responder.
///
/// This is the question behind keyboard routing. SDL3's NSApplication subclass
/// handles every key event in `Cocoa_DispatchEvent` AND passes it on to the
/// window, so a key typed into a focused page reaches both the page and SDL's
/// queue. Java asks this and drops the SDL copy (ADR-0459).
inline bool has_focus(id view) {
    id window = msg_send<id>(view, sel("window"));
    if (window == nullptr) {
        return false;
    }
    id responder = msg_send<id>(window, sel("firstResponder"));
    if (responder == nullptr
            || !msg_send<BOOL>(responder, sel("isKindOfClass:"), reinterpret_cast<id>(objc_getClass("NSView")))) {
        return false;
    }
    return responder == view || msg_send<BOOL>(responder, sel("isDescendantOf:"), view);
}

/// Hands the keyboard back from the page to the application.
///
/// A click on the Goldberry frame does not do this by itself: SDL's content view
/// does not accept first responder, so the page keeps it and every key after
/// the click would still go to the page -- and, with [has_focus] filtering, to
/// nobody else.
///
/// Back to SDL's text-input field editor when there is one. SDL makes it first
/// responder only when it ADDS it, which it does not do again while text input
/// is already on, so a Goldberry field that was focused before the page was
/// clicked would otherwise lose its IME composition for good. The editor is an
/// `SDL3TranslatorResponder` subview of the content view for exactly as long as
/// text input is on; with none, the window itself takes the keyboard.
inline void blur(id view) {
    if (!has_focus(view)) {
        return;
    }
    id window = msg_send<id>(view, sel("window"));
    id content = msg_send<id>(window, sel("contentView"));
    id editor = nullptr;
    if (Class translator = objc_getClass("SDL3TranslatorResponder"); translator != nullptr && content != nullptr) {
        id subviews = msg_send<id>(content, sel("subviews"));
        NSUInteger count = msg_send<NSUInteger>(subviews, sel("count"));
        for (NSUInteger i = 0; i < count && editor == nullptr; i++) {
            id subview = msg_send<id>(subviews, sel("objectAtIndex:"), i);
            if (msg_send<BOOL>(subview, sel("isKindOfClass:"), reinterpret_cast<id>(translator))) {
                editor = subview;
            }
        }
    }
    msg_send<BOOL>(window, sel("makeFirstResponder:"), editor);
}

/// Takes an embedded page's view out of the application's window, and hands
/// back its holder for releasing AFTER the engine is destroyed -- the engine
/// reads it in its destructor. Null for a page that was never embedded.
inline id detach(void *w) {
    auto found = holders().find(w);
    if (found == holders().end()) {
        return nullptr;
    }
    id holder = found->second;
    holders().erase(found);
    if (id view = web_view(w)) {
        msg_send<void>(view, sel("removeFromSuperview"));
    }
    return holder;
}

} // namespace goldberry_cocoa
#endif

#if defined(GOLDBERRY_WEBVIEW_WIN32)
// WIN32 EMBEDDING
//
// UNVERIFIED: written on macOS and compiled by nobody yet. Every call here is
// documented Win32 or WebView2, and webview.h's own Win32 backend is the model.
//
// Windows is the easy one. Given a parent HWND, webview.h does exactly what
// embedding needs and nothing more: it makes a WS_CHILD "webview_widget" window
// inside the parent, puts the WebView2 controller in that, and never touches
// the parent's own content or window procedure. So there is no holder as on
// macOS and no reparenting as on X11 -- the parent is SDL's HWND, and the page
// is the child the engine made.
//
// What is left to this file is four things webview.h leaves to an owner:
//
//   - COM. webview.h initialises it only for a window it owns. SDL's
//     WIN_VideoInit has already initialised an STA on this thread, which is
//     what WebView2 needs; one more reference is taken here anyway so a page
//     does not depend on that, and it is released when the page goes.
//   - Clipping. SDL presents by BitBlt'ing its surface into the window's DC, and
//     without WS_CLIPCHILDREN on the parent that blit paints straight over the
//     child. The style is added once, and it changes nothing for a window with
//     no children.
//   - Placement. The child starts 0x0 and hidden; SetWindowPos sizes and shows
//     it, and the widget's own WM_SIZE handler passes the new client rect to
//     the controller.
//   - Load state. WebView2 has no synchronous "is loading", only events, so a
//     small handler records NavigationStarting and NavigationCompleted.
namespace goldberry_win32 {

/// The engine's child window, which holds the WebView2 controller.
inline HWND widget(void *w) {
    return static_cast<HWND>(webview_get_native_handle(w, WEBVIEW_NATIVE_HANDLE_KIND_UI_WIDGET));
}

/// The controller, which is what webview.h hands out as the browser controller.
inline ICoreWebView2Controller *controller(void *w) {
    return static_cast<ICoreWebView2Controller *>(
            webview_get_native_handle(w, WEBVIEW_NATIVE_HANDLE_KIND_BROWSER_CONTROLLER));
}

/// Records how far the current navigation has got, as the shim's three numbers.
///
/// Both handler interfaces on one object with one reference count, the way
/// webview.h's own `webview2_com_handler` is written. The engine calls it on the
/// UI thread -- WebView2 raises its events on the thread that created it -- so
/// the state needs no lock; the count is atomic only because COM's contract says
/// it may be touched from anywhere.
class load_tracker final : public ICoreWebView2NavigationStartingEventHandler,
                           public ICoreWebView2NavigationCompletedEventHandler {
public:
    ULONG STDMETHODCALLTYPE AddRef() override { return ++m_refs; }

    ULONG STDMETHODCALLTYPE Release() override {
        ULONG left = --m_refs;
        if (left == 0) {
            delete this;
        }
        return left;
    }

    HRESULT STDMETHODCALLTYPE QueryInterface(REFIID riid, void **out) override {
        if (out == nullptr) {
            return E_POINTER;
        }
        // __uuidof against WebView2.h's own MIDL_INTERFACE declarations, rather
        // than GUIDs typed in here: the Windows build is MSVC under both
        // generators (ADR-0454), and a typo in a hand-copied GUID is silent.
        if (riid == __uuidof(IUnknown) || riid == __uuidof(ICoreWebView2NavigationStartingEventHandler)) {
            *out = static_cast<ICoreWebView2NavigationStartingEventHandler *>(this);
        } else if (riid == __uuidof(ICoreWebView2NavigationCompletedEventHandler)) {
            *out = static_cast<ICoreWebView2NavigationCompletedEventHandler *>(this);
        } else {
            *out = nullptr;
            return E_NOINTERFACE;
        }
        AddRef();
        return S_OK;
    }

    HRESULT STDMETHODCALLTYPE Invoke(ICoreWebView2 *, ICoreWebView2NavigationStartingEventArgs *) override {
        m_state = 1;
        return S_OK;
    }

    HRESULT STDMETHODCALLTYPE Invoke(ICoreWebView2 *, ICoreWebView2NavigationCompletedEventArgs *) override {
        // Success or failure alike: a failed navigation ends on WebView2's own
        // error page, which is something to show rather than a spinner forever.
        m_state = 2;
        return S_OK;
    }

    /// 0 not started, 1 loading, 2 finished.
    int state() const { return m_state; }

private:
    std::atomic<ULONG> m_refs{1};
    int m_state{0};
};

/// What an embedded page holds beyond the engine: its COM reference and its
/// load tracker with the tokens to unregister it.
struct page {
    bool com{};
    load_tracker *tracker{};
    ICoreWebView2 *core{};
    EventRegistrationToken starting{};
    EventRegistrationToken completed{};
};

/// Every embedded page's extras, by handle. UI-thread confined, like the rest.
inline std::unordered_map<void *, page> &pages() {
    static std::unordered_map<void *, page> map;
    return map;
}

/// Puts the page at `x,y` / `width x height` in the parent's client pixels, and
/// shows it.
///
/// Win32's coordinates are already the caller's -- client pixels, origin top
/// left -- so unlike Cocoa there is nothing to convert, and a child parked off
/// the parent's top-left corner is clipped by the parent as it is on X11.
///
/// NotifyParentWindowPositionChanged because WebView2 positions its own popups
/// -- a <select>, a tooltip -- in screen coordinates it caches, and a move that
/// is not a resize reaches it no other way.
inline int place(void *w, int x, int y, int width, int height) {
    HWND child = widget(w);
    if (child == nullptr) {
        return -1;
    }
    if (!SetWindowPos(child, HWND_TOP, x, y, width, height, SWP_NOACTIVATE | SWP_SHOWWINDOW)) {
        return -1;
    }
    if (ICoreWebView2Controller *c = controller(w)) {
        c->NotifyParentWindowPositionChanged();
    }
    return 0;
}

/// The window with the keyboard focus on the foreground thread.
///
/// Not GetFocus(): the focused window inside a WebView2 belongs to the browser
/// process, and GetFocus answers only for this thread's queue.
inline HWND focused() {
    GUITHREADINFO info{};
    info.cbSize = sizeof(info);
    return GetGUIThreadInfo(0, &info) ? info.hwndFocus : nullptr;
}

/// Whether the keyboard is inside the page.
inline bool has_focus(void *w) {
    HWND child = widget(w);
    HWND focus = focused();
    return child != nullptr && focus != nullptr && (focus == child || IsChild(child, focus));
}

/// Hands the keyboard back to the parent -- SDL's window. A click on the
/// parent's client area does not do this: activation is already the
/// top-level's, and SDL calls no SetFocus of its own.
inline void blur(void *w) {
    if (!has_focus(w)) {
        return;
    }
    if (HWND parent = GetParent(widget(w))) {
        SetFocus(parent);
    }
}

/// Opens a page inside `host`, or NULL.
inline void *create_embedded(int debug, HWND host, int x, int y, int width, int height) {
    HRESULT com = CoInitializeEx(nullptr, COINIT_APARTMENTTHREADED);
    if (com == RPC_E_CHANGED_MODE) {
        // Somebody made this thread multi-threaded, and WebView2 requires a
        // single-threaded apartment. Refused here rather than inside the engine,
        // where it would surface as a controller that never arrives.
        return nullptr;
    }
    bool took_com = SUCCEEDED(com);

    LONG_PTR style = GetWindowLongPtrW(host, GWL_STYLE);
    if ((style & WS_CLIPCHILDREN) == 0) {
        SetWindowLongPtrW(host, GWL_STYLE, style | WS_CLIPCHILDREN);
        SetWindowPos(host, nullptr, 0, 0, 0, 0,
                     SWP_NOMOVE | SWP_NOSIZE | SWP_NOZORDER | SWP_NOACTIVATE | SWP_FRAMECHANGED);
    }

    void *w = webview_create(debug, host);
    HWND child = w == nullptr ? nullptr : widget(w);
    if (child == nullptr) {
        if (w != nullptr) {
            webview_destroy(w);
        }
        if (took_com) {
            CoUninitialize();
        }
        return nullptr;
    }
    SetWindowLongPtrW(child, GWL_STYLE, GetWindowLongPtrW(child, GWL_STYLE) | WS_CLIPSIBLINGS);

    page extras{};
    extras.com = took_com;
    if (ICoreWebView2Controller *c = controller(w); c != nullptr && SUCCEEDED(c->get_CoreWebView2(&extras.core))) {
        extras.tracker = new load_tracker();
        extras.core->add_NavigationStarting(extras.tracker, &extras.starting);
        extras.core->add_NavigationCompleted(extras.tracker, &extras.completed);
    }
    pages()[w] = extras;
    place(w, x, y, width, height);
    return w;
}

/// How far through loading the page is, or -1 for a page with no tracker.
inline int load_state(void *w) {
    auto found = pages().find(w);
    if (found == pages().end() || found->second.tracker == nullptr) {
        return -1;
    }
    return found->second.tracker->state();
}

/// Unregisters and releases what [create_embedded] added. Before the engine is
/// destroyed, because the handlers are registered on its ICoreWebView2. Answers
/// whether a COM reference is to be released afterwards.
inline bool detach(void *w) {
    auto found = pages().find(w);
    if (found == pages().end()) {
        return false;
    }
    page extras = found->second;
    pages().erase(found);
    if (extras.core != nullptr) {
        extras.core->remove_NavigationStarting(extras.starting);
        extras.core->remove_NavigationCompleted(extras.completed);
        extras.core->Release();
    }
    if (extras.tracker != nullptr) {
        extras.tracker->Release();
    }
    return extras.com;
}

} // namespace goldberry_win32
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

#if defined(GOLDBERRY_WEBVIEW_GLIB)
// TEARING A PAGE DOWN ON LINUX (ADR-0507).
//
// WebKitGTK renders a page in another process, WebKitWebProcess, which draws
// through EGL into surfaces that belong to this one. webview_destroy releases
// the view and returns; the message telling the renderer its page is gone is
// sent from GLib's main context, which nothing iterates once the page is closed,
// and the caller's next step is to destroy the X window the page was reparented
// into -- which the server takes the page's own window down with. A renderer
// still drawing then draws into a surface that has gone, and NVIDIA's EGL
// answers that with SIGSEGV rather than an error: Ubuntu's crash dialog,
// "WebKitWebProcess crashed in libnvidia-eglcore", after the showcase had shut
// down cleanly.
//
// So the renderer is stopped first, the page destroyed second, and the queue
// drained last, before the window can go. A renderer that dies on its own is
// said out loud: a GLib warning in the `goldberry-webview` domain, which
// ADR-0443 routes to `native.glib.goldberry-webview` beside the toolkit's lines.
namespace goldberry_glib {

/// The WebKitWebView webview/webview owns, or null.
static WebKitWebView *web_view(void *w) {
    void *controller = webview_get_native_handle(w, WEBVIEW_NATIVE_HANDLE_KIND_BROWSER_CONTROLLER);
    return controller != nullptr && WEBKIT_IS_WEB_VIEW(controller) ? WEBKIT_WEB_VIEW(controller) : nullptr;
}

static void on_renderer_terminated(WebKitWebView *, WebKitWebProcessTerminationReason reason, gpointer) {
    switch (reason) {
        case WEBKIT_WEB_PROCESS_CRASHED:
            g_log("goldberry-webview", G_LOG_LEVEL_WARNING,
                  "the page's web process crashed; the page is blank until it is navigated again");
            break;
        case WEBKIT_WEB_PROCESS_EXCEEDED_MEMORY_LIMIT:
            g_log("goldberry-webview", G_LOG_LEVEL_WARNING,
                  "the page's web process exceeded its memory limit and was stopped");
            break;
        default:
            // WEBKIT_WEB_PROCESS_TERMINATED_BY_API: stop_renderer below, on purpose.
            break;
    }
}

/// Keeps WebKit's default context alive past `exit()`, once per process.
///
/// WebKit holds its default WebKitWebContext in a C++ static, and drops it from
/// the exit handlers. Finalising the context releases its website data manager,
/// whose `~WebsiteDataStore` reaches `allDataStores()` -- a registry WebKit only
/// lets its **main** thread touch, the one that started GTK, which is Goldberry's
/// UI thread. The stock `java` launcher runs `main` on a thread of its own and
/// calls `exit()` from its first one, so the exit handlers run on the wrong
/// thread and WebKit crashes on purpose: `WTFCrashWithInfo` in
/// `WebsiteDataStore.cpp:124`, SIGABRT after a clean shutdown. Whether it did
/// depended on whether anything still held the context at exit, which is why it
/// came and went; with the renderer stopped and the page released (below) it
/// came every time.
///
/// One reference, taken here on the UI thread and never given back, means the
/// static's release only counts down and nothing is finalised off the main
/// thread. The cost is the context living until the process ends, which is the
/// moment it is being asked to end at. A native image runs `main` on the first
/// thread and never met this.
static void pin_default_context() {
    static bool pinned = false;
    if (!pinned) {
        pinned = true;
        g_object_ref(webkit_web_context_get_default());
    }
}

/// Says so when the page's renderer dies, rather than leaving a blank page, and
/// pins the context every page shares.
static void watch_renderer(void *w) {
    if (w == nullptr) {
        return;
    }
    pin_default_context();
    if (WebKitWebView *view = web_view(w)) {
        g_signal_connect(view, "web-process-terminated", G_CALLBACK(on_renderer_terminated), nullptr);
    }
}

/// Stops the page's renderer before anything it draws into can go.
///
/// Termination rather than a polite close: a close is a message the renderer
/// handles when it gets to it, and the window is destroyed in the next call.
/// An embedded page's unload handlers do not run, which is the price, and the
/// same thing closing the window under a browser tab costs.
static void stop_renderer(void *w) {
#if WEBKIT_CHECK_VERSION(2, 34, 0)
    if (WebKitWebView *view = web_view(w)) {
        webkit_web_view_terminate_web_process(view);
    }
#else
    // Older than the call: the drain after the destroy is the whole defence.
    (void) w;
#endif
}

/// Runs what the teardown queued -- GTK unrealising the page's window, WebKit
/// releasing its surfaces and noticing its renderer is gone -- before the caller
/// destroys the window it was in. Bounded, and never blocking, for
/// goldberry_webview_pump's reason: a context that keeps producing work must
/// not hold the caller here.
static void drain() {
    for (int i = 0; i < 256; i++) {
        if (!g_main_context_iteration(nullptr, FALSE)) {
            break;
        }
    }
}

} // namespace goldberry_glib
#endif

} // extern "C", for the C++ below: none of it is exported, and C linkage would
  // forbid two helpers of one name in two namespaces.

// COOKIES AND NAVIGATION
//
// Two things about a page that an application sometimes has to know and that
// no script in the page can tell it. What cookies the engine holds for a site:
// an HttpOnly cookie is invisible to JavaScript by design, and a service that
// accepts nothing but its own session cookie can only be signed in to through
// a page and then read from the engine. And where the page is about to go: an
// OAuth redirect to a custom scheme -- `myapp://callback?code=...` -- is a URL
// no engine can load, so the only moment it exists is the decision to try.
//
// Both answer on the UI thread. GLib's main context is drained by
// goldberry_webview_pump, and AppKit's run loop and the Win32 message queue by
// SDL's own pump, so Java is called back from inside the frame loop and never
// from a thread of the engine's.
//
// What crosses for cookies is TEXT, one cookie per line, seven tab-separated
// fields:
//
//     name  value  domain  path  expires  secure  http-only
//
// `expires` is whole seconds since 1970, or -1 for a session cookie; the last
// two are 0 or 1. A backslash, tab, newline or carriage return inside a field
// is written \\ \t \n \r. One string rather than an array of structs, so that
// three engines with three cookie types cross in one shape, and a field added
// later is a column rather than a struct layout two languages must agree on.

/// `void (*)(long long request, const char *cookies)` -- the cookies as text,
/// or NULL when the engine could not read them.
typedef void (*goldberry_webview_cookies_fn)(long long request, const char *cookies);

/// `int (*)(long long page, const char *uri)` -- nonzero lets the navigation go
/// ahead, zero cancels it.
typedef int (*goldberry_webview_navigate_fn)(long long page, const char *uri);

namespace goldberry_hooks {

/// Appends `text` with the four characters the format reserves escaped.
inline void append_field(std::string &out, const char *text) {
    if (text == nullptr) {
        return;
    }
    for (const char *c = text; *c != '\0'; c++) {
        switch (*c) {
            case '\\': out += "\\\\"; break;
            case '\t': out += "\\t"; break;
            case '\n': out += "\\n"; break;
            case '\r': out += "\\r"; break;
            default: out += *c; break;
        }
    }
}

/// One line of the format above.
inline void append_cookie(std::string &out,
                          const char *name,
                          const char *value,
                          const char *domain,
                          const char *path,
                          long long expires,
                          bool secure,
                          bool http_only) {
    append_field(out, name);
    out += '\t';
    append_field(out, value);
    out += '\t';
    append_field(out, domain);
    out += '\t';
    append_field(out, path);
    out += '\t';
    out += std::to_string(expires);
    out += '\t';
    out += secure ? '1' : '0';
    out += '\t';
    out += http_only ? '1' : '0';
    out += '\n';
}

/// Whom to ask about a page's navigations: the function, and the number Java
/// registered the page under.
struct navigation_hook {
    goldberry_webview_navigate_fn fn{};
    long long page{};
};

/// Whether `hook` lets the page go to `uri`. A URI the engine could not spell
/// is let through rather than asked about: there is nothing to decide on.
inline bool allowed(const navigation_hook &hook, const char *uri) {
    return hook.fn == nullptr || uri == nullptr || hook.fn(hook.page, uri) != 0;
}

#if defined(GOLDBERRY_WEBVIEW_GLIB)
// WebKitGTK: the website data manager's cookie manager, which reads cookies
// for a URI itself -- domain, path and Secure already applied -- and the
// view's `decide-policy` signal, which every navigation passes through,
// server redirects included.

/// One outstanding cookie read. Owned by the GAsyncReadyCallback, which runs
/// exactly once.
struct cookie_request {
    goldberry_webview_cookies_fn fn;
    long long request;
};

static void on_cookies(GObject *source, GAsyncResult *result, gpointer data) {
    auto *request = static_cast<cookie_request *>(data);
    GError *error = nullptr;
    GList *cookies = webkit_cookie_manager_get_cookies_finish(WEBKIT_COOKIE_MANAGER(source), result, &error);
    if (error != nullptr) {
        g_log("goldberry-webview", G_LOG_LEVEL_DEBUG, "the engine could not read cookies: %s", error->message);
        g_error_free(error);
        request->fn(request->request, nullptr);
        delete request;
        return;
    }
    std::string out;
    for (GList *item = cookies; item != nullptr; item = item->next) {
        auto *cookie = static_cast<SoupCookie *>(item->data);
        long long expires = -1;
#if SOUP_CHECK_VERSION(2, 99, 0)
        if (GDateTime *date = soup_cookie_get_expires(cookie)) {
            expires = g_date_time_to_unix(date);
        }
#else
        if (SoupDate *date = soup_cookie_get_expires(cookie)) {
            expires = soup_date_to_time_t(date);
        }
#endif
        append_cookie(out,
                      soup_cookie_get_name(cookie),
                      soup_cookie_get_value(cookie),
                      soup_cookie_get_domain(cookie),
                      soup_cookie_get_path(cookie),
                      expires,
                      soup_cookie_get_secure(cookie),
                      soup_cookie_get_http_only(cookie));
    }
    g_list_free_full(cookies, reinterpret_cast<GDestroyNotify>(soup_cookie_free));
    request->fn(request->request, out.c_str());
    delete request;
}

/// The cookie jar behind `view`. Every page webview/webview makes is on the
/// default context, so this is one jar for the process.
static WebKitCookieManager *cookie_manager(WebKitWebView *view) {
#if GTK_MAJOR_VERSION == 4
    WebKitNetworkSession *session = webkit_web_view_get_network_session(view);
    return session == nullptr ? nullptr : webkit_network_session_get_cookie_manager(session);
#else
    WebKitWebsiteDataManager *data = webkit_web_view_get_website_data_manager(view);
    return data == nullptr ? nullptr : webkit_website_data_manager_get_cookie_manager(data);
#endif
}

static int read_cookies(void *w, const char *url, goldberry_webview_cookies_fn fn, long long request) {
    WebKitWebView *view = goldberry_glib::web_view(w);
    WebKitCookieManager *manager = view == nullptr ? nullptr : cookie_manager(view);
    if (manager == nullptr) {
        return -1;
    }
    webkit_cookie_manager_get_cookies(manager, url, nullptr, on_cookies, new cookie_request{fn, request});
    return 0;
}

/// NAVIGATION_ACTION only. A NEW_WINDOW_ACTION goes nowhere, because
/// webview/webview connects no `create` handler, and a RESPONSE is a document
/// arriving rather than a page going somewhere. WebKitGTK does not say which
/// frame a navigation is in, so a frame's own navigations are asked about too.
static gboolean on_decide_policy(WebKitWebView *, WebKitPolicyDecision *decision, WebKitPolicyDecisionType type,
                                 gpointer data) {
    if (type != WEBKIT_POLICY_DECISION_TYPE_NAVIGATION_ACTION) {
        return FALSE;
    }
    auto *hook = static_cast<navigation_hook *>(data);
    WebKitNavigationAction *action =
            webkit_navigation_policy_decision_get_navigation_action(WEBKIT_NAVIGATION_POLICY_DECISION(decision));
    WebKitURIRequest *request = action == nullptr ? nullptr : webkit_navigation_action_get_request(action);
    const char *uri = request == nullptr ? nullptr : webkit_uri_request_get_uri(request);
    if (allowed(*hook, uri)) {
        // FALSE is "not handled here": WebKit's own default, which is to load.
        return FALSE;
    }
    webkit_policy_decision_ignore(decision);
    return TRUE;
}

/// The signal handler each page's hook is connected as, so a second hook
/// replaces the first rather than both being asked.
static std::unordered_map<void *, gulong> &navigation_handlers() {
    static std::unordered_map<void *, gulong> map;
    return map;
}

static void free_hook(gpointer data, GClosure *) {
    delete static_cast<navigation_hook *>(data);
}

static int watch_navigation(void *w, goldberry_webview_navigate_fn fn, long long page) {
    WebKitWebView *view = goldberry_glib::web_view(w);
    if (view == nullptr) {
        return -1;
    }
    auto &handlers = navigation_handlers();
    if (auto found = handlers.find(w); found != handlers.end()) {
        g_signal_handler_disconnect(view, found->second);
        handlers.erase(found);
    }
    if (fn != nullptr) {
        handlers[w] = g_signal_connect_data(view,
                                            "decide-policy",
                                            G_CALLBACK(on_decide_policy),
                                            new navigation_hook{fn, page},
                                            free_hook,
                                            static_cast<GConnectFlags>(0));
    }
    return 0;
}

/// Before the engine is destroyed. The handler itself goes with the view, and
/// its hook with it through free_hook; what is left is this file's record.
static void forget(void *w) {
    navigation_handlers().erase(w);
}

#elif defined(GOLDBERRY_WEBVIEW_COCOA)
// WKWebView: the data store's WKHTTPCookieStore, which hands back EVERY cookie
// and leaves matching them to a URL to the caller, and a WKNavigationDelegate,
// which webview.h does not set and this file therefore can. UNVERIFIED: written
// against Apple's documentation and webview.h's own Objective-C idioms, and not
// yet run on a Mac.
using goldberry_cocoa::cls;
using goldberry_cocoa::msg_send;
using goldberry_cocoa::sel;

/// The head of every block: Clang's block ABI, which is what the runtime
/// itself calls through. Only `invoke` is read.
struct block_layout {
    void *isa;
    int flags;
    int reserved;
    void (*invoke)(void *, ...);
};

/// WKNavigationActionPolicyCancel and WKNavigationActionPolicyAllow.
constexpr NSInteger POLICY_CANCEL = 0;
constexpr NSInteger POLICY_ALLOW = 1;

/// Every hooked page's hook, by its WKWebView -- the one thing the delegate
/// is told.
inline std::unordered_map<id, navigation_hook> &navigation_hooks() {
    static std::unordered_map<id, navigation_hook> map;
    return map;
}

inline std::string utf8(id text) {
    const char *bytes = text == nullptr ? nullptr : msg_send<const char *>(text, sel("UTF8String"));
    return bytes == nullptr ? std::string() : std::string(bytes);
}

inline std::string lower(std::string text) {
    for (char &c : text) {
        if (c >= 'A' && c <= 'Z') {
            c = static_cast<char>(c - 'A' + 'a');
        }
    }
    return text;
}

/// `webView:decidePolicyForNavigationAction:decisionHandler:`.
///
/// The main frame only. A nil target frame is a request for a new window,
/// which a WKWebView opens only through a UI delegate's createWebView, and
/// webview.h's has none -- it goes nowhere whatever is answered here.
inline void decide_policy(id, SEL, id view, id action, id handler) {
    NSInteger policy = POLICY_ALLOW;
    auto found = navigation_hooks().find(view);
    if (found != navigation_hooks().end()) {
        id frame = msg_send<id>(action, sel("targetFrame"));
        if (frame != nullptr && msg_send<BOOL>(frame, sel("isMainFrame"))) {
            id url = msg_send<id>(msg_send<id>(action, sel("request")), sel("URL"));
            std::string uri = url == nullptr ? std::string() : utf8(msg_send<id>(url, sel("absoluteString")));
            if (!allowed(found->second, uri.empty() ? nullptr : uri.c_str())) {
                policy = POLICY_CANCEL;
            }
        }
    }
    auto *block = reinterpret_cast<block_layout *>(handler);
    reinterpret_cast<void (*)(id, NSInteger)>(block->invoke)(handler, policy);
}

/// The one delegate every hooked page shares. Never released: a WKWebView
/// holds its navigation delegate weakly, so somebody has to own it, and the
/// class is registered once per process anyway.
inline id navigation_delegate() {
    static id delegate = [] {
        constexpr auto name = "GoldberryNavigationDelegate";
        Class c = objc_lookUpClass(name);
        if (c == nullptr) {
            c = objc_allocateClassPair(objc_getClass("NSObject"), name, 0);
            class_addProtocol(c, objc_getProtocol("WKNavigationDelegate"));
            class_addMethod(c,
                            sel("webView:decidePolicyForNavigationAction:decisionHandler:"),
                            reinterpret_cast<IMP>(decide_policy),
                            "v@:@@@?");
            objc_registerClassPair(c);
        }
        return msg_send<id>(reinterpret_cast<id>(c), sel("new"));
    }();
    return delegate;
}

inline int watch_navigation(void *w, goldberry_webview_navigate_fn fn, long long page) {
    webview::detail::objc::autoreleasepool pool;
    id view = goldberry_cocoa::web_view(w);
    if (view == nullptr) {
        return -1;
    }
    if (fn == nullptr) {
        navigation_hooks().erase(view);
        return 0;
    }
    navigation_hooks()[view] = navigation_hook{fn, page};
    msg_send<void>(view, sel("setNavigationDelegate:"), navigation_delegate());
    return 0;
}

inline void forget(void *w) {
    if (id view = goldberry_cocoa::web_view(w)) {
        navigation_hooks().erase(view);
    }
}

/// RFC 6265's domain-match, with NSHTTPCookie's spelling of it: a domain with
/// a leading dot covers its subdomains, and one without is that host alone.
inline bool domain_matches(const std::string &host, std::string domain) {
    domain = lower(domain);
    if (domain.empty()) {
        return false;
    }
    if (domain[0] != '.') {
        return host == domain;
    }
    domain.erase(0, 1);
    if (host == domain) {
        return true;
    }
    return host.size() > domain.size() + 1
            && host.compare(host.size() - domain.size(), domain.size(), domain) == 0
            && host[host.size() - domain.size() - 1] == '.';
}

/// RFC 6265's path-match.
inline bool path_matches(std::string request, const std::string &cookie) {
    if (request.empty()) {
        request = "/";
    }
    if (cookie.empty() || request == cookie) {
        return true;
    }
    if (request.compare(0, cookie.size(), cookie) != 0) {
        return false;
    }
    return cookie.back() == '/' || request[cookie.size()] == '/';
}

inline int read_cookies(void *w, const char *url, goldberry_webview_cookies_fn fn, long long request) {
    webview::detail::objc::autoreleasepool pool;
    id view = goldberry_cocoa::web_view(w);
    if (view == nullptr) {
        return -1;
    }
    id store = msg_send<id>(
            msg_send<id>(msg_send<id>(view, sel("configuration")), sel("websiteDataStore")), sel("httpCookieStore"));
    id target = msg_send<id>(
            cls("NSURL"), sel("URLWithString:"), msg_send<id>(cls("NSString"), sel("stringWithUTF8String:"), url));
    if (store == nullptr || target == nullptr) {
        return -1;
    }
    std::string host = lower(utf8(msg_send<id>(target, sel("host"))));
    std::string path = utf8(msg_send<id>(target, sel("path")));
    std::string scheme = lower(utf8(msg_send<id>(target, sel("scheme"))));
    bool secure_channel = scheme == "https" || scheme == "wss";
    // A block literal: Clang compiles blocks in C++ on Apple platforms (and
    // CMake passes -fblocks there to say so). WebKit copies it, because the
    // answer comes later, on the main thread.
    void (^completion)(id) = ^(id cookies) {
        std::string out;
        NSUInteger count = cookies == nullptr ? 0 : msg_send<NSUInteger>(cookies, sel("count"));
        for (NSUInteger i = 0; i < count; i++) {
            id cookie = msg_send<id>(cookies, sel("objectAtIndex:"), i);
            std::string domain = utf8(msg_send<id>(cookie, sel("domain")));
            std::string cookie_path = utf8(msg_send<id>(cookie, sel("path")));
            bool secure = msg_send<BOOL>(cookie, sel("isSecure"));
            if (!domain_matches(host, domain) || !path_matches(path, cookie_path) || (secure && !secure_channel)) {
                continue;
            }
            id date = msg_send<id>(cookie, sel("expiresDate"));
            long long expires = date == nullptr
                    ? -1
                    : static_cast<long long>(msg_send<double>(date, sel("timeIntervalSince1970")));
            append_cookie(out,
                          utf8(msg_send<id>(cookie, sel("name"))).c_str(),
                          utf8(msg_send<id>(cookie, sel("value"))).c_str(),
                          domain.c_str(),
                          cookie_path.c_str(),
                          expires,
                          secure,
                          msg_send<BOOL>(cookie, sel("isHTTPOnly")));
        }
        fn(request, out.c_str());
    };
    msg_send<void>(store, sel("getAllCookies:"), completion);
    return 0;
}

#elif defined(GOLDBERRY_WEBVIEW_WIN32)
// WebView2: ICoreWebView2_2's cookie manager, which reads cookies for a URI
// itself, and the NavigationStarting event, which can cancel. Both reached
// through the controller webview.h hands out, so they work for a page in a
// window of its own as well as an embedded one. UNVERIFIED: written against
// the WebView2 SDK headers and not yet compiled.

/// The page's ICoreWebView2, with a reference the caller releases. Null
/// before the controller has arrived.
inline ICoreWebView2 *core_of(void *w) {
    ICoreWebView2Controller *controller = goldberry_win32::controller(w);
    ICoreWebView2 *core = nullptr;
    if (controller == nullptr || FAILED(controller->get_CoreWebView2(&core))) {
        return nullptr;
    }
    return core;
}

/// A COM string as UTF-8, freed.
inline std::string take(LPWSTR text) {
    if (text == nullptr) {
        return std::string();
    }
    std::string narrow = webview::detail::narrow_string(std::wstring(text));
    CoTaskMemFree(text);
    return narrow;
}

class cookies_handler final : public ICoreWebView2GetCookiesCompletedHandler {
public:
    cookies_handler(goldberry_webview_cookies_fn fn, long long request) : m_fn(fn), m_request(request) {}

    ULONG STDMETHODCALLTYPE AddRef() override { return ++m_refs; }

    ULONG STDMETHODCALLTYPE Release() override {
        ULONG left = --m_refs;
        if (left == 0) {
            delete this;
        }
        return left;
    }

    HRESULT STDMETHODCALLTYPE QueryInterface(REFIID riid, void **out) override {
        if (out == nullptr) {
            return E_POINTER;
        }
        if (riid == __uuidof(IUnknown) || riid == __uuidof(ICoreWebView2GetCookiesCompletedHandler)) {
            *out = static_cast<ICoreWebView2GetCookiesCompletedHandler *>(this);
            AddRef();
            return S_OK;
        }
        *out = nullptr;
        return E_NOINTERFACE;
    }

    HRESULT STDMETHODCALLTYPE Invoke(HRESULT result, ICoreWebView2CookieList *list) override {
        if (FAILED(result) || list == nullptr) {
            m_fn(m_request, nullptr);
            return S_OK;
        }
        UINT count = 0;
        list->get_Count(&count);
        std::string out;
        for (UINT i = 0; i < count; i++) {
            ICoreWebView2Cookie *cookie = nullptr;
            if (FAILED(list->GetValueAtIndex(i, &cookie)) || cookie == nullptr) {
                continue;
            }
            LPWSTR name = nullptr;
            LPWSTR value = nullptr;
            LPWSTR domain = nullptr;
            LPWSTR path = nullptr;
            double expires = -1;
            BOOL session = TRUE;
            BOOL secure = FALSE;
            BOOL http_only = FALSE;
            cookie->get_Name(&name);
            cookie->get_Value(&value);
            cookie->get_Domain(&domain);
            cookie->get_Path(&path);
            cookie->get_Expires(&expires);
            cookie->get_IsSession(&session);
            cookie->get_IsSecure(&secure);
            cookie->get_IsHttpOnly(&http_only);
            append_cookie(out,
                          take(name).c_str(),
                          take(value).c_str(),
                          take(domain).c_str(),
                          take(path).c_str(),
                          session ? -1 : static_cast<long long>(expires),
                          secure != FALSE,
                          http_only != FALSE);
            cookie->Release();
        }
        m_fn(m_request, out.c_str());
        return S_OK;
    }

private:
    std::atomic<ULONG> m_refs{1};
    goldberry_webview_cookies_fn m_fn;
    long long m_request;
};

inline int read_cookies(void *w, const char *url, goldberry_webview_cookies_fn fn, long long request) {
    ICoreWebView2 *core = core_of(w);
    if (core == nullptr) {
        return -1;
    }
    ICoreWebView2_2 *core2 = nullptr;
    HRESULT found = core->QueryInterface(__uuidof(ICoreWebView2_2), reinterpret_cast<void **>(&core2));
    core->Release();
    if (FAILED(found) || core2 == nullptr) {
        // A WebView2 Runtime older than the cookie manager.
        return -1;
    }
    ICoreWebView2CookieManager *manager = nullptr;
    HRESULT got = core2->get_CookieManager(&manager);
    core2->Release();
    if (FAILED(got) || manager == nullptr) {
        return -1;
    }
    auto *handler = new cookies_handler(fn, request);
    HRESULT started = manager->GetCookies(webview::detail::widen_string(url).c_str(), handler);
    handler->Release();
    manager->Release();
    return SUCCEEDED(started) ? 0 : -1;
}

class navigation_handler final : public ICoreWebView2NavigationStartingEventHandler {
public:
    explicit navigation_handler(navigation_hook hook) : m_hook(hook) {}

    ULONG STDMETHODCALLTYPE AddRef() override { return ++m_refs; }

    ULONG STDMETHODCALLTYPE Release() override {
        ULONG left = --m_refs;
        if (left == 0) {
            delete this;
        }
        return left;
    }

    HRESULT STDMETHODCALLTYPE QueryInterface(REFIID riid, void **out) override {
        if (out == nullptr) {
            return E_POINTER;
        }
        if (riid == __uuidof(IUnknown) || riid == __uuidof(ICoreWebView2NavigationStartingEventHandler)) {
            *out = static_cast<ICoreWebView2NavigationStartingEventHandler *>(this);
            AddRef();
            return S_OK;
        }
        *out = nullptr;
        return E_NOINTERFACE;
    }

    HRESULT STDMETHODCALLTYPE Invoke(ICoreWebView2 *, ICoreWebView2NavigationStartingEventArgs *args) override {
        LPWSTR uri = nullptr;
        if (args != nullptr && SUCCEEDED(args->get_Uri(&uri)) && uri != nullptr) {
            std::string text = take(uri);
            if (!allowed(m_hook, text.c_str())) {
                args->put_Cancel(TRUE);
            }
        }
        return S_OK;
    }

private:
    std::atomic<ULONG> m_refs{1};
    navigation_hook m_hook;
};

struct navigation_registration {
    navigation_handler *handler{};
    EventRegistrationToken token{};
};

inline std::unordered_map<void *, navigation_registration> &navigation_registrations() {
    static std::unordered_map<void *, navigation_registration> map;
    return map;
}

/// Takes `w`'s hook off its engine, while the engine is still there.
inline void forget(void *w) {
    auto found = navigation_registrations().find(w);
    if (found == navigation_registrations().end()) {
        return;
    }
    navigation_registration registration = found->second;
    navigation_registrations().erase(found);
    if (ICoreWebView2 *core = core_of(w)) {
        core->remove_NavigationStarting(registration.token);
        core->Release();
    }
    registration.handler->Release();
}

inline int watch_navigation(void *w, goldberry_webview_navigate_fn fn, long long page) {
    forget(w);
    if (fn == nullptr) {
        return 0;
    }
    ICoreWebView2 *core = core_of(w);
    if (core == nullptr) {
        return -1;
    }
    navigation_registration registration{};
    registration.handler = new navigation_handler(navigation_hook{fn, page});
    HRESULT added = core->add_NavigationStarting(registration.handler, &registration.token);
    core->Release();
    if (FAILED(added)) {
        registration.handler->Release();
        return -1;
    }
    navigation_registrations()[w] = registration;
    return 0;
}

#else
inline int read_cookies(void *, const char *, goldberry_webview_cookies_fn, long long) { return -1; }
inline int watch_navigation(void *, goldberry_webview_navigate_fn, long long) { return -1; }
inline void forget(void *) {}
#endif

} // namespace goldberry_hooks

extern "C" {

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
    void *w = webview_create(debug, nullptr);
    goldberry_glib::watch_renderer(w);
    return w;
#else
    return webview_create(debug, nullptr);
#endif
}

/// Closes the window and frees the page.
///
/// An embedded page on macOS is a view in somebody else's window, so it is taken
/// out of that window first -- the application's content view holds a reference
/// to it that the engine's destructor knows nothing about -- and its holder
/// window is released last, because the engine reads it on the way out.
GOLDBERRY_WEBVIEW_EXPORT void goldberry_webview_destroy(void *w) {
    if (w == nullptr) {
        return;
    }
    // The navigation hook first, on every platform: it is registered on the
    // engine, and the engine is about to go.
    goldberry_hooks::forget(w);
#if defined(GOLDBERRY_WEBVIEW_COCOA)
    webview::detail::objc::autoreleasepool pool;
    id holder = goldberry_cocoa::detach(w);
    webview_destroy(w);
    if (holder != nullptr) {
        goldberry_cocoa::msg_send<void>(holder, goldberry_cocoa::sel("release"));
    }
#elif defined(GOLDBERRY_WEBVIEW_WIN32)
    // The load handlers come off the engine while it is still there, and the
    // COM reference goes last, after the engine has released its own objects.
    bool com = goldberry_win32::detach(w);
    webview_destroy(w);
    if (com) {
        CoUninitialize();
    }
#elif defined(GOLDBERRY_WEBVIEW_GLIB)
    // The renderer first, the page second, and GLib's queue last -- all before
    // this returns, because what the caller does next is destroy the window the
    // page lives in (ADR-0507).
    goldberry_glib::stop_renderer(w);
    webview_destroy(w);
    goldberry_glib::drain();
#else
    webview_destroy(w);
#endif
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
#elif defined(GOLDBERRY_WEBVIEW_COCOA)
    // addSubview: is always available -- a view can go into any window.
    return 1;
#elif defined(GOLDBERRY_WEBVIEW_WIN32)
    // A child window can go into any window. UNVERIFIED, like the rest of the
    // Win32 embedding -- see "WIN32 EMBEDDING" above.
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
#elif defined(GOLDBERRY_WEBVIEW_COCOA)
    // No GDK_BACKEND and no reparenting: the page is a view, added to the
    // content view of the NSWindow SDL made -- see "COCOA EMBEDDING" above.
    if (kind != GOLDBERRY_WEBVIEW_PARENT_COCOA) {
        return nullptr;
    }
    return goldberry_cocoa::create_embedded(
            debug, reinterpret_cast<id>(static_cast<std::uintptr_t>(parent)), x, y, width, height);
#elif defined(GOLDBERRY_WEBVIEW_WIN32)
    // The engine makes the child itself when given a parent -- see "WIN32
    // EMBEDDING" above. UNVERIFIED.
    if (kind != GOLDBERRY_WEBVIEW_PARENT_WIN32) {
        return nullptr;
    }
    HWND host = reinterpret_cast<HWND>(static_cast<std::uintptr_t>(parent));
    if (!IsWindow(host)) {
        return nullptr;
    }
    return goldberry_win32::create_embedded(debug, host, x, y, width, height);
#else
    (void) debug;
    (void) kind;
    (void) x;
    (void) y;
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
#elif defined(GOLDBERRY_WEBVIEW_COCOA)
    // The same two facts, under WKWebView's names: `loading` and
    // `estimatedProgress`. A document that loaded with no network at all --
    // `loadHTMLString:` -- still ends on a URL (about:blank), which is the
    // tie-breaker for a page whose progress has already been reset.
    webview::detail::objc::autoreleasepool pool;
    id view = goldberry_cocoa::web_view(w);
    if (view == nullptr) {
        return -1;
    }
    if (goldberry_cocoa::msg_send<BOOL>(view, goldberry_cocoa::sel("isLoading"))) {
        return 1;
    }
    double progress = goldberry_cocoa::msg_send<double>(view, goldberry_cocoa::sel("estimatedProgress"));
    id url = goldberry_cocoa::msg_send<id>(view, goldberry_cocoa::sel("URL"));
    return progress > 0.0 || url != nullptr ? 2 : 0;
#elif defined(GOLDBERRY_WEBVIEW_WIN32)
    // From NavigationStarting and NavigationCompleted, recorded as they arrive:
    // WebView2 has nothing synchronous to ask. -1 for a page opened as a window
    // of its own, which has no tracker. UNVERIFIED.
    return goldberry_win32::load_state(w);
#else
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
#elif defined(GOLDBERRY_WEBVIEW_COCOA)
    // A frame rather than a window move -- see goldberry_cocoa::place for the
    // three conversions and why a parked page is also hidden.
    webview::detail::objc::autoreleasepool pool;
    id view = goldberry_cocoa::web_view(w);
    return view == nullptr ? -1 : goldberry_cocoa::place(view, x, y, width, height);
#elif defined(GOLDBERRY_WEBVIEW_WIN32)
    return goldberry_win32::place(w, x, y, width, height);
#else
    (void) x;
    (void) y;
    return -1;
#endif
}

/// Whether the keyboard focus is inside this page: 1 yes, 0 no.
///
/// Asked by the SDL backend for every key and text event of the page's parent
/// window, to drop the ones that were typed into the page (ADR-0459). Where the
/// window system delivers a focused page's keys to the page alone, SDL never
/// sees them and this answers 0 without being wrong:
///
///   - X11: the page's own X window has the focus, and the server sends key
///     events to it and nowhere else.
///   - Windows: the focused window is WebView2's, in the browser process; its
///     keys never enter SDL's queue. Answered anyway, because it costs one call
///     and a runtime that routes differently should not become a double-typing
///     bug.
///   - macOS: the one where it matters -- SDL3 handles key events in its
///     NSApplication subclass BEFORE the window delivers them to the page.
GOLDBERRY_WEBVIEW_EXPORT int goldberry_webview_has_focus(void *w) {
    if (w == nullptr) {
        return 0;
    }
#if defined(GOLDBERRY_WEBVIEW_COCOA)
    webview::detail::objc::autoreleasepool pool;
    id view = goldberry_cocoa::web_view(w);
    return view != nullptr && goldberry_cocoa::has_focus(view) ? 1 : 0;
#elif defined(GOLDBERRY_WEBVIEW_WIN32)
    return goldberry_win32::has_focus(w) ? 1 : 0;
#else
    return 0;
#endif
}

/// Takes the keyboard focus out of this page and gives it back to the
/// application's window, if the page has it. Nothing otherwise.
///
/// Called by the SDL backend on every button press SDL reports in the parent
/// window. Those are exactly the presses that landed OUTSIDE the page, because
/// a press on the page is the page's and never reaches SDL -- so this is "the
/// user clicked back into the application", said without hit-testing anything.
///
/// A no-op on X11, as it was before this function existed: focus between a
/// reparented child and its parent is GTK's and the window manager's business
/// there, and nothing here has measured how they settle it.
GOLDBERRY_WEBVIEW_EXPORT void goldberry_webview_blur(void *w) {
    if (w == nullptr) {
        return;
    }
#if defined(GOLDBERRY_WEBVIEW_COCOA)
    webview::detail::objc::autoreleasepool pool;
    if (id view = goldberry_cocoa::web_view(w)) {
        goldberry_cocoa::blur(view);
    }
#elif defined(GOLDBERRY_WEBVIEW_WIN32)
    goldberry_win32::blur(w);
#endif
}

/// Reads the cookies the engine would send to `url`, HttpOnly ones included,
/// and hands them to `fn` as text -- see "COOKIES AND NAVIGATION" above for
/// the format -- or NULL when the engine could not read them.
///
/// Asynchronous on every engine: 0 means `fn` WILL be called, exactly once,
/// later and on the UI thread, with `request` beside the answer; -1 means it
/// will not be called at all, because this page or engine cannot read its jar.
/// Java matches the answer to its question by `request`, which is why one
/// upcall stub serves every read in the process.
///
/// The read is of the engine's jar rather than of the page, and on Linux and
/// macOS every page shares one, so a page that signed in leaves its session
/// readable from any other page.
GOLDBERRY_WEBVIEW_EXPORT int goldberry_webview_cookies(
        void *w, const char *url, goldberry_webview_cookies_fn fn, long long request) {
    if (w == nullptr || url == nullptr || fn == nullptr) {
        return -1;
    }
    return goldberry_hooks::read_cookies(w, url, fn, request);
}

/// Asks `fn` before the page goes anywhere, with the URI it is about to load.
/// Nonzero lets it go; zero cancels the navigation, and the page stays on the
/// document it had.
///
/// Every navigation of the page's main frame -- a link, a form, a script
/// setting `location`, a server's redirect -- and on Linux a frame's own as
/// well, because WebKitGTK does not say which frame a decision is for.
/// Synchronous: `fn` runs on the UI thread inside the engine's decision, so it
/// must answer at once rather than wait for anything.
///
/// `fn` NULL takes the hook away. A second call replaces the first. Returns 0,
/// or -1 where the engine cannot be asked.
GOLDBERRY_WEBVIEW_EXPORT int goldberry_webview_on_navigate(void *w, goldberry_webview_navigate_fn fn, long long page) {
    if (w == nullptr) {
        return -1;
    }
    return goldberry_hooks::watch_navigation(w, fn, page);
}
}
