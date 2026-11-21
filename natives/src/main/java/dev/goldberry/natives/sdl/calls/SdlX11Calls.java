package dev.goldberry.natives.sdl.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import dev.goldberry.natives.Downcalls;

/// SDL's X11 event hook.
///
/// @param setX11EventHook `SDL_SetX11EventHook`
public record SdlX11Calls(SetX11EventHook setX11EventHook) {

    /// Binds every function above.
    ///
    /// @param lookup the loaded `libgoldberry`
    public static SdlX11Calls bind(SymbolLookup lookup) {
        return new SdlX11Calls(new SetX11EventHook(lookup));
    }

    /// `void SDL_SetX11EventHook(SDL_X11EventHook callback, void *userdata)`:
    /// one hook for the process, replaced by the next call, removed with NULL.
    public static final class SetX11EventHook {

        private static final MethodHandle FD_SDL_SetX11EventHook =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));

        private final MemorySegment address;

        SetX11EventHook(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_SetX11EventHook");
        }

        /// Calls `SDL_SetX11EventHook`.
        public void call(MemorySegment callback, MemorySegment userData) {
            try {
                FD_SDL_SetX11EventHook.invokeExact(address, callback, userData);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_SetX11EventHook", t);
            }
        }
    }
}
