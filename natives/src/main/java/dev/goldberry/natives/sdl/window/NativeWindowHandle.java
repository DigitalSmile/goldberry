package dev.goldberry.natives.sdl.window;

/// The platform's own handle for a window, and which platform's it is.
///
/// **The escape hatch for embedding foreign content**: a backend exposes the
/// platform's own window handle so an application can embed an external
/// renderer, and the first thing that needed it is the `web-view` widget. A page
/// is a platform window, and putting one *inside* a Goldberry window means
/// naming that window in the platform's own terms.
///
/// ## Why this is a value and not a `MemorySegment`
///
/// Raw foreign memory does not leave `:natives`. What crosses here
/// is a `long` and an enum constant — the same shape
/// [dev.goldberry.natives.sdl.SdlWindowHandle] has, one level
/// further out. An X11 `Window` really is an integer id rather than a pointer,
/// so a `long` is not a pointer smuggled through a primitive; it is the natural
/// type for three of the four cases and a lossless carrier for the fourth.
///
/// ## Wayland is deliberately absent
///
/// There is a `wl_surface` behind a Wayland window and it is **not** reported,
/// because nothing may usefully be done with it. Wayland has no cross-client
/// surface embedding — `xdg-foreign` is toplevel *parenting*, not embedding, and
/// fourteen years of requests have produced no protocol. Reporting a handle that
/// cannot be embedded into would be an invitation to try.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
///
/// @param kind  which platform's handle this is
/// @param value the handle: an X11 `Window` id, an `HWND`, or an `NSWindow*`
public record NativeWindowHandle(Kind kind, long value) {

    /// Which window system the handle belongs to.
    public enum Kind {

        /// An X11 `Window` — an integer id. Reported under XWayland too, which is
        /// what makes embedding work on a Wayland desktop when the application
        /// asks SDL for the X11 driver.
        X11,

        /// A Win32 `HWND`.
        WIN32,

        /// A Cocoa `NSWindow*`.
        COCOA
    }

    public NativeWindowHandle {
        java.util.Objects.requireNonNull(kind, "kind");
        if (value == 0L) {
            throw new IllegalArgumentException("a native window handle of 0 is no handle; report empty instead");
        }
    }
}
