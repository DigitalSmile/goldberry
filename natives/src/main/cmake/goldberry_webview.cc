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
#define GOLDBERRY_WEBVIEW_ABI 7

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
///
/// An embedded page on macOS is a view in somebody else's window, so it is taken
/// out of that window first -- the application's content view holds a reference
/// to it that the engine's destructor knows nothing about -- and its holder
/// window is released last, because the engine reads it on the way out.
GOLDBERRY_WEBVIEW_EXPORT void goldberry_webview_destroy(void *w) {
    if (w == nullptr) {
        return;
    }
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
}
