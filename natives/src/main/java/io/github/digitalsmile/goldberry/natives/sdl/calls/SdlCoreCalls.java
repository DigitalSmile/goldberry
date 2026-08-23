package io.github.digitalsmile.goldberry.natives.sdl.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

import io.github.digitalsmile.goldberry.natives.Downcalls;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

/// SDL's process-wide functions: init, quit, errors, hints and version.
///
/// See [io.github.digitalsmile.goldberry.natives.calls] for why the holders
/// live in a package of their own.
public record SdlCoreCalls(
        Init init,
        InitSubSystem initSubSystem,
        QuitSubSystem quitSubSystem,
        WasInit wasInit,
        Quit quit,
        GetError getError,
        ClearError clearError,
        GetVersion getVersion,
        GetRevision getRevision,
        GetCurrentVideoDriver getCurrentVideoDriver,
        SetHint setHint,
        GetModState getModState) {

    /// Binds every function above.
    public static SdlCoreCalls bind(SymbolLookup lookup) {
        return new SdlCoreCalls(
                new Init(lookup),
                new InitSubSystem(lookup),
                new QuitSubSystem(lookup),
                new WasInit(lookup),
                new Quit(lookup),
                new GetError(lookup),
                new ClearError(lookup),
                new GetVersion(lookup),
                new GetRevision(lookup),
                new GetCurrentVideoDriver(lookup),
                new SetHint(lookup),
                new GetModState(lookup));
    }

    /// `_Bool SDL_Init(int)`
    public static final class Init {

        private static final MethodHandle FD_SDL_Init =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, JAVA_INT));

        private final MemorySegment address;

        Init(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_Init");
        }

        public boolean call(int a1) {
            try {
                return (boolean) FD_SDL_Init.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_Init", t);
            }
        }
    }

    /// `_Bool SDL_InitSubSystem(int)`
    public static final class InitSubSystem {

        private static final MethodHandle FD_SDL_InitSubSystem =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, JAVA_INT));

        private final MemorySegment address;

        InitSubSystem(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_InitSubSystem");
        }

        public boolean call(int a1) {
            try {
                return (boolean) FD_SDL_InitSubSystem.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_InitSubSystem", t);
            }
        }
    }

    /// `void SDL_QuitSubSystem(int)`
    public static final class QuitSubSystem {

        private static final MethodHandle FD_SDL_QuitSubSystem =
                Downcalls.link(FunctionDescriptor.ofVoid(JAVA_INT));

        private final MemorySegment address;

        QuitSubSystem(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_QuitSubSystem");
        }

        public void call(int a1) {
            try {
                FD_SDL_QuitSubSystem.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_QuitSubSystem", t);
            }
        }
    }

    /// `int SDL_WasInit(int)`
    public static final class WasInit {

        private static final MethodHandle FD_SDL_WasInit =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, JAVA_INT));

        private final MemorySegment address;

        WasInit(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_WasInit");
        }

        public int call(int a1) {
            try {
                return (int) FD_SDL_WasInit.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_WasInit", t);
            }
        }
    }

    /// `void SDL_Quit(void)`
    public static final class Quit {

        private static final MethodHandle FD_SDL_Quit = Downcalls.link(FunctionDescriptor.ofVoid());

        private final MemorySegment address;

        Quit(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_Quit");
        }

        public void call() {
            try {
                FD_SDL_Quit.invokeExact(address);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_Quit", t);
            }
        }
    }

    /// `void* SDL_GetError(void)`
    public static final class GetError {

        private static final MethodHandle FD_SDL_GetError =
                Downcalls.link(FunctionDescriptor.of(ADDRESS));

        private final MemorySegment address;

        GetError(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_GetError");
        }

        public MemorySegment call() {
            try {
                return (MemorySegment) FD_SDL_GetError.invokeExact(address);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_GetError", t);
            }
        }
    }

    /// `_Bool SDL_ClearError(void)`
    public static final class ClearError {

        private static final MethodHandle FD_SDL_ClearError =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN));

        private final MemorySegment address;

        ClearError(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_ClearError");
        }

        public boolean call() {
            try {
                return (boolean) FD_SDL_ClearError.invokeExact(address);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_ClearError", t);
            }
        }
    }

    /// `int SDL_GetVersion(void)`
    public static final class GetVersion {

        private static final MethodHandle FD_SDL_GetVersion =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT));

        private final MemorySegment address;

        GetVersion(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_GetVersion");
        }

        public int call() {
            try {
                return (int) FD_SDL_GetVersion.invokeExact(address);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_GetVersion", t);
            }
        }
    }

    /// `void* SDL_GetRevision(void)`
    public static final class GetRevision {

        private static final MethodHandle FD_SDL_GetRevision =
                Downcalls.link(FunctionDescriptor.of(ADDRESS));

        private final MemorySegment address;

        GetRevision(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_GetRevision");
        }

        public MemorySegment call() {
            try {
                return (MemorySegment) FD_SDL_GetRevision.invokeExact(address);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_GetRevision", t);
            }
        }
    }

    /// `void* SDL_GetCurrentVideoDriver(void)`
    public static final class GetCurrentVideoDriver {

        private static final MethodHandle FD_SDL_GetCurrentVideoDriver =
                Downcalls.link(FunctionDescriptor.of(ADDRESS));

        private final MemorySegment address;

        GetCurrentVideoDriver(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_GetCurrentVideoDriver");
        }

        public MemorySegment call() {
            try {
                return (MemorySegment) FD_SDL_GetCurrentVideoDriver.invokeExact(address);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_GetCurrentVideoDriver", t);
            }
        }
    }

    /// `_Bool SDL_SetHint(void*, void*)`
    public static final class SetHint {

        private static final MethodHandle FD_SDL_SetHint =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS, ADDRESS));

        private final MemorySegment address;

        SetHint(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_SetHint");
        }

        public boolean call(MemorySegment a1, MemorySegment a2) {
            try {
                return (boolean) FD_SDL_SetHint.invokeExact(address, a1, a2);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_SetHint", t);
            }
        }
    }

    /// `short SDL_GetModState(void)`
    public static final class GetModState {

        private static final MethodHandle FD_SDL_GetModState =
                Downcalls.link(FunctionDescriptor.of(JAVA_SHORT));

        private final MemorySegment address;

        GetModState(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_GetModState");
        }

        public short call() {
            try {
                return (short) FD_SDL_GetModState.invokeExact(address);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_GetModState", t);
            }
        }
    }
}
