package dev.goldberry.natives.sdl.window;

/// Where a drag out of a window starts on Wayland, as SDL knows it.
///
/// Addresses rather than foreign memory, for [NativeWindowHandle]'s reason:
/// nothing of `java.lang.foreign` crosses out of `:natives`. They are SDL's
/// own objects and live as long as the window and SDL's seat do. A drag source
/// in this module turns them back into pointers to speak on SDL's connection.
///
/// @param display    SDL's `wl_display`
/// @param surface    the window's `wl_surface`, the drag's origin
/// @param dataDevice SDL's `wl_data_device` for the seat whose pointer is in
///                   the window
/// @param serial     that seat's last implicit-grab serial, which the
///                   compositor checks `start_drag` against
public record WaylandDragOrigin(long display, long surface, long dataDevice, int serial) {

    public WaylandDragOrigin {
        if (display == 0 || surface == 0 || dataDevice == 0) {
            throw new IllegalArgumentException("a drag origin needs a display, a surface and a data device");
        }
    }
}
