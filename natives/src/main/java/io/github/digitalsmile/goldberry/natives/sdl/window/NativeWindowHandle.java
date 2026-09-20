package io.github.digitalsmile.goldberry.natives.sdl.window;

/// The platform's own handle for a window, and which platform's it is.
///
/// **The escape hatch `docs/ARCHITECTURE.md` §12 has promised since day one** —
/// *"backends expose raw native window handles for apps embedding external
/// renderers"* — and which nothing built until §9's `web-view` needed it
/// ([ADR-0442]). A page is a platform window, and putting one *inside* a
/// Goldberry window means naming that window in the platform's own terms.
///
/// ## Why this is a value and not a `MemorySegment`
///
/// §3.1's rule: raw foreign memory does not leave `:natives`. What crosses here
/// is a `long` and an enum constant — the same shape
/// [io.github.digitalsmile.goldberry.natives.sdl.SdlWindowHandle] has, one level
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
