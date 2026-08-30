package io.github.digitalsmile.goldberry.natives.sdl.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import io.github.digitalsmile.goldberry.natives.Downcalls;

/// SDL's event queue — polling it, waiting on it, and pushing to it.
///
/// One holder per function: its handle, its address, and a `call` whose
/// parameters are the C prototype’s. See [Downcalls] for why the handle is a
/// `static final` constant and why these live in a package of their own.
public record SdlEventCalls(PollEvent pollEvent, WaitEventTimeout waitEventTimeout, PushEvent pushEvent) {

    /// Binds every function above.
    ///
    /// @param lookup the loaded `libgoldberry`
    public static SdlEventCalls bind(SymbolLookup lookup) {
        return new SdlEventCalls(new PollEvent(lookup), new WaitEventTimeout(lookup), new PushEvent(lookup));
    }

    /// Takes the next event if there is one, without waiting.
    ///
    /// `_Bool SDL_PollEvent(void*)`
    ///
    /// @param outEvent a caller-allocated `SDL_Event`, or NULL to discard one
    /// @return false if the queue was empty
    public static final class PollEvent {

        private static final MethodHandle FD_SDL_PollEvent =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS));

        private final MemorySegment address;

        PollEvent(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_PollEvent");
        }

        public boolean call(MemorySegment outEvent) {
            try {
                return (boolean) FD_SDL_PollEvent.invokeExact(address, outEvent);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_PollEvent", t);
            }
        }
    }

    /// Waits for an event, giving up after `timeoutMillis`.
    ///
    /// What lets the UI thread sleep between frames instead of spinning.
    ///
    /// `_Bool SDL_WaitEventTimeout(void*, int)`
    ///
    /// @param outEvent a caller-allocated `SDL_Event`
    /// @param timeoutMillis how long to block; 0 returns immediately
    /// @return false if the timeout expired first
    public static final class WaitEventTimeout {

        private static final MethodHandle FD_SDL_WaitEventTimeout =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        WaitEventTimeout(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_WaitEventTimeout");
        }

        public boolean call(MemorySegment outEvent, int timeoutMillis) {
            try {
                return (boolean) FD_SDL_WaitEventTimeout.invokeExact(address, outEvent, timeoutMillis);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_WaitEventTimeout", t);
            }
        }
    }

    /// Puts an event on the queue.
    ///
    /// How another thread wakes the UI thread up.
    ///
    /// `_Bool SDL_PushEvent(void*)`
    ///
    /// @param event the `SDL_Event` to enqueue; SDL copies it
    /// @return false if a watch filtered it out
    public static final class PushEvent {

        private static final MethodHandle FD_SDL_PushEvent =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS));

        private final MemorySegment address;

        PushEvent(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_PushEvent");
        }

        public boolean call(MemorySegment event) {
            try {
                return (boolean) FD_SDL_PushEvent.invokeExact(address, event);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_PushEvent", t);
            }
        }
    }
}
