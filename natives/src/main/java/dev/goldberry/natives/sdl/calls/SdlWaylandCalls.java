package dev.goldberry.natives.sdl.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import dev.goldberry.natives.Downcalls;

/// What libgoldberry reads from SDL's Wayland seat for a drag out.
///
/// @param dragOrigin `goldberry_wayland_drag_origin`
public record SdlWaylandCalls(DragOrigin dragOrigin) {

    /// Binds every function above.
    ///
    /// @param lookup the loaded `libgoldberry`
    public static SdlWaylandCalls bind(SymbolLookup lookup) {
        return new SdlWaylandCalls(new DragOrigin(lookup));
    }

    /// `bool goldberry_wayland_drag_origin(SDL_Window *window, void **data_device,
    /// Uint32 *serial)`: SDL's `wl_data_device` for the seat whose pointer is in
    /// the window, and that seat's last implicit-grab serial. False off Wayland.
    public static final class DragOrigin {

        private static final MethodHandle FD_goldberry_wayland_drag_origin =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS, ADDRESS, ADDRESS));

        private final MemorySegment address;

        DragOrigin(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "goldberry_wayland_drag_origin");
        }

        /// Calls `goldberry_wayland_drag_origin`.
        public boolean call(MemorySegment window, MemorySegment dataDevice, MemorySegment serial) {
            try {
                return (boolean) FD_goldberry_wayland_drag_origin.invokeExact(address, window, dataDevice, serial);
            } catch (Throwable t) {
                throw Downcalls.failure("goldberry_wayland_drag_origin", t);
            }
        }
    }
}
