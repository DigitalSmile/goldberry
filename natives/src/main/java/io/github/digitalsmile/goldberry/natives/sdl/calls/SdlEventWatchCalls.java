package io.github.digitalsmile.goldberry.natives.sdl.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;

import io.github.digitalsmile.goldberry.natives.Downcalls;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

/// The two functions an SDL event watch is added and removed with.
///
/// See [io.github.digitalsmile.goldberry.natives.calls] for why the holders
/// live in a package of their own.
public record SdlEventWatchCalls(
        AddEventWatch addEventWatch,
        RemoveEventWatch removeEventWatch) {

    /// Binds every function above.
    public static SdlEventWatchCalls bind(SymbolLookup lookup) {
        return new SdlEventWatchCalls(
                new AddEventWatch(lookup),
                new RemoveEventWatch(lookup));
    }

    /// `_Bool SDL_AddEventWatch(void*, void*)`
    public static final class AddEventWatch {

        private static final MethodHandle FD_SDL_AddEventWatch =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS, ADDRESS));

        private final MemorySegment address;

        AddEventWatch(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_AddEventWatch");
        }

        public boolean call(MemorySegment a1, MemorySegment a2) {
            try {
                return (boolean) FD_SDL_AddEventWatch.invokeExact(address, a1, a2);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_AddEventWatch", t);
            }
        }
    }

    /// `void SDL_RemoveEventWatch(void*, void*)`
    public static final class RemoveEventWatch {

        private static final MethodHandle FD_SDL_RemoveEventWatch =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));

        private final MemorySegment address;

        RemoveEventWatch(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_RemoveEventWatch");
        }

        public void call(MemorySegment a1, MemorySegment a2) {
            try {
                FD_SDL_RemoveEventWatch.invokeExact(address, a1, a2);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_RemoveEventWatch", t);
            }
        }
    }
}
