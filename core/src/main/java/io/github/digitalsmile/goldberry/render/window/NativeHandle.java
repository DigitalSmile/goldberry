package io.github.digitalsmile.goldberry.render.window;

import java.util.Objects;

/// The platform's own handle for a window, in the toolkit's vocabulary.
///
/// **`docs/ARCHITECTURE.md` §12's escape hatch** — *"backends expose raw native
/// window handles for apps embedding external renderers"* — promised since day
/// one and built when §9's `web-view` needed it
/// ([ADR-0442](../../../../../../../book/src/adr/0442-a-page-is-a-child-window-where-the-window-system-allows-one.md)).
///
/// `:core`'s own type rather than `:natives`' `NativeWindowHandle`, which is the
/// arrangement [io.github.digitalsmile.goldberry.platform.Capability] has with
/// `NativeCapability` and for its reason: `:core` does not require `:natives`
/// transitively (ADR-0290), so a `:natives` type in a signature here would be one
/// an application could not name. The two are kept in step by an exhaustive
/// switch at the seam, which is the compiler noticing rather than a convention.
///
/// @param kind  which window system the handle belongs to
/// @param value the handle: an X11 `Window` id, an `HWND`, or an `NSWindow*`
public record NativeHandle(Kind kind, long value) {

    /// Which window system a handle belongs to.
    ///
    /// **Wayland is deliberately not here.** A Wayland window has a `wl_surface`
    /// and it is never reported, because nothing may be done with it: there is no
    /// cross-client surface embedding, `xdg-foreign` is toplevel *parenting* and
    /// errors on anything else, and fourteen years of requests have produced no
    /// protocol. A handle nothing can embed into is an invitation to try.
    public enum Kind {

        /// An X11 `Window` — an integer id rather than a pointer. Reported under
        /// XWayland too, which is what lets an application on a Wayland desktop
        /// embed a page by asking SDL for the x11 driver.
        X11,

        /// A Win32 `HWND`.
        WIN32,

        /// A Cocoa `NSWindow*`.
        COCOA
    }

    public NativeHandle {
        Objects.requireNonNull(kind, "kind");
        if (value == 0L) {
            throw new IllegalArgumentException("a native window handle of 0 is no handle; report empty instead");
        }
    }
}
