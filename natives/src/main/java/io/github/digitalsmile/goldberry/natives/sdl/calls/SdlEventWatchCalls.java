package io.github.digitalsmile.goldberry.natives.sdl.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import io.github.digitalsmile.goldberry.natives.Downcalls;

/// SDL's event watches — a callback SDL runs as events arrive.
///
/// One holder per function: its handle, its address, and a `call` whose
/// parameters are the C prototype’s. See [Downcalls] for why the handle is a
/// `static final` constant and why these live in a package of their own.
public record SdlEventWatchCalls(AddEventWatch addEventWatch, RemoveEventWatch removeEventWatch) {

    /// Binds every function above.
    ///
    /// @param lookup the loaded `libgoldberry`
    public static SdlEventWatchCalls bind(SymbolLookup lookup) {
        return new SdlEventWatchCalls(new AddEventWatch(lookup), new RemoveEventWatch(lookup));
    }

    /// Registers a callback SDL runs for every event, as it is queued.
    ///
    /// The only way to paint during a modal resize loop: the platform does not
    /// return to the event pump while the user drags an edge.
    ///
    /// `_Bool SDL_AddEventWatch(void*, void*)`
    ///
    /// @param filter an `SDL_EventFilter` upcall stub
    /// @param userData passed to the filter; NULL here, because the stub already knows
    /// @return false if SDL could not grow its watch list
    public static final class AddEventWatch {

        private static final MethodHandle FD_SDL_AddEventWatch =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS, ADDRESS));

        private final MemorySegment address;

        AddEventWatch(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_AddEventWatch");
        }

        public boolean call(MemorySegment filter, MemorySegment userData) {
            try {
                return (boolean) FD_SDL_AddEventWatch.invokeExact(address, filter, userData);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_AddEventWatch", t);
            }
        }
    }

    /// Unregisters a watch. Both arguments must match the registration.
    ///
    /// `void SDL_RemoveEventWatch(void*, void*)`
    ///
    /// @param filter the stub that was registered
    /// @param userData the same value it was registered with
    public static final class RemoveEventWatch {

        private static final MethodHandle FD_SDL_RemoveEventWatch =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));

        private final MemorySegment address;

        RemoveEventWatch(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_RemoveEventWatch");
        }

        public void call(MemorySegment filter, MemorySegment userData) {
            try {
                FD_SDL_RemoveEventWatch.invokeExact(address, filter, userData);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_RemoveEventWatch", t);
            }
        }
    }
}
