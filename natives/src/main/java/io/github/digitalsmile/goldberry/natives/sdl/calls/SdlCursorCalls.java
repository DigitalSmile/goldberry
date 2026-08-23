package io.github.digitalsmile.goldberry.natives.sdl.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import io.github.digitalsmile.goldberry.natives.Downcalls;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

/// SDL's cursor functions, one holder each.
///
/// See [io.github.digitalsmile.goldberry.natives.calls] for why the holders
/// live in a package of their own.
public record SdlCursorCalls(
        CreateSystemCursor createSystemCursor,
        SetCursor setCursor,
        DestroyCursor destroyCursor,
        ShowCursor showCursor,
        HideCursor hideCursor) {

    /// Binds every function above.
    public static SdlCursorCalls bind(SymbolLookup lookup) {
        return new SdlCursorCalls(
                new CreateSystemCursor(lookup),
                new SetCursor(lookup),
                new DestroyCursor(lookup),
                new ShowCursor(lookup),
                new HideCursor(lookup));
    }

    /// `void* SDL_CreateSystemCursor(int)`
    public static final class CreateSystemCursor {

        private static final MethodHandle FD_SDL_CreateSystemCursor =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, JAVA_INT));

        private final MemorySegment address;

        CreateSystemCursor(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_CreateSystemCursor");
        }

        public MemorySegment call(int a1) {
            try {
                return (MemorySegment) FD_SDL_CreateSystemCursor.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_CreateSystemCursor", t);
            }
        }
    }

    /// `_Bool SDL_SetCursor(void*)`
    public static final class SetCursor {

        private static final MethodHandle FD_SDL_SetCursor =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS));

        private final MemorySegment address;

        SetCursor(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_SetCursor");
        }

        public boolean call(MemorySegment a1) {
            try {
                return (boolean) FD_SDL_SetCursor.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_SetCursor", t);
            }
        }
    }

    /// `void SDL_DestroyCursor(void*)`
    public static final class DestroyCursor {

        private static final MethodHandle FD_SDL_DestroyCursor =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        DestroyCursor(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_DestroyCursor");
        }

        public void call(MemorySegment a1) {
            try {
                FD_SDL_DestroyCursor.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_DestroyCursor", t);
            }
        }
    }

    /// `_Bool SDL_ShowCursor(void)`
    public static final class ShowCursor {

        private static final MethodHandle FD_SDL_ShowCursor =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN));

        private final MemorySegment address;

        ShowCursor(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_ShowCursor");
        }

        public boolean call() {
            try {
                return (boolean) FD_SDL_ShowCursor.invokeExact(address);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_ShowCursor", t);
            }
        }
    }

    /// `_Bool SDL_HideCursor(void)`
    public static final class HideCursor {

        private static final MethodHandle FD_SDL_HideCursor =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN));

        private final MemorySegment address;

        HideCursor(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_HideCursor");
        }

        public boolean call() {
            try {
                return (boolean) FD_SDL_HideCursor.invokeExact(address);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_HideCursor", t);
            }
        }
    }
}
