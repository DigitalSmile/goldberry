package io.github.digitalsmile.goldberry.natives.sdl.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import io.github.digitalsmile.goldberry.natives.Downcalls;

/// SDL's process-wide lifecycle, error and version calls.
///
/// One holder per function: its handle, its address, and a `call` whose
/// parameters are the C prototype’s. See [Downcalls] for why the handle is a
/// `static final` constant and why these live in a package of their own.
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
        GetModState getModState,
        GetGlobalMouseState getGlobalMouseState) {

    /// Binds every function above.
    ///
    /// @param lookup the loaded `libgoldberry`
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
                new GetModState(lookup),
                new GetGlobalMouseState(lookup));
    }

    /// Initialises SDL and the subsystems named.
    ///
    /// `_Bool SDL_Init(int)`
    public static final class Init {

        private static final MethodHandle FD_SDL_Init = Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, JAVA_INT));

        private final MemorySegment address;

        Init(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_Init");
        }

        /// Calls `SDL_Init`.
        ///
        /// @param subsystems a mask of `SDL_INIT_*` flags
        /// @return false if SDL refused
        public boolean call(int subsystems) {
            try {
                return (boolean) FD_SDL_Init.invokeExact(address, subsystems);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_Init", t);
            }
        }
    }

    /// Initialises further subsystems on top of an existing [SdlCoreCalls.Init].
    ///
    /// `_Bool SDL_InitSubSystem(int)`
    public static final class InitSubSystem {

        private static final MethodHandle FD_SDL_InitSubSystem =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, JAVA_INT));

        private final MemorySegment address;

        InitSubSystem(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_InitSubSystem");
        }

        /// Calls `SDL_InitSubSystem`.
        ///
        /// @param subsystems a mask of `SDL_INIT_*` flags
        /// @return false if SDL refused
        public boolean call(int subsystems) {
            try {
                return (boolean) FD_SDL_InitSubSystem.invokeExact(address, subsystems);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_InitSubSystem", t);
            }
        }
    }

    /// Shuts specific subsystems down. Cannot fail, by SDL’s design.
    ///
    /// `void SDL_QuitSubSystem(int)`
    public static final class QuitSubSystem {

        private static final MethodHandle FD_SDL_QuitSubSystem = Downcalls.link(FunctionDescriptor.ofVoid(JAVA_INT));

        private final MemorySegment address;

        QuitSubSystem(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_QuitSubSystem");
        }

        /// Calls `SDL_QuitSubSystem`.
        ///
        /// @param subsystems a mask of `SDL_INIT_*` flags
        public void call(int subsystems) {
            try {
                FD_SDL_QuitSubSystem.invokeExact(address, subsystems);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_QuitSubSystem", t);
            }
        }
    }

    /// Which subsystems are initialised.
    ///
    /// Usually a superset of what was asked for, because SDL initialises implied
    /// subsystems too — video brings events with it.
    ///
    /// `int SDL_WasInit(int)`
    public static final class WasInit {

        private static final MethodHandle FD_SDL_WasInit = Downcalls.link(FunctionDescriptor.of(JAVA_INT, JAVA_INT));

        private final MemorySegment address;

        WasInit(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_WasInit");
        }

        /// Calls `SDL_WasInit`.
        ///
        /// @param subsystems a mask to test, or 0 for "tell me everything"
        /// @return a mask of `SDL_INIT_*` flags
        public int call(int subsystems) {
            try {
                return (int) FD_SDL_WasInit.invokeExact(address, subsystems);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_WasInit", t);
            }
        }
    }

    /// Shuts SDL down entirely.
    ///
    /// Process-global: this undoes every initialisation, not only the caller’s.
    ///
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

    /// The calling thread’s last SDL error.
    ///
    /// `void* SDL_GetError(void)`
    public static final class GetError {

        private static final MethodHandle FD_SDL_GetError = Downcalls.link(FunctionDescriptor.of(ADDRESS));

        private final MemorySegment address;

        GetError(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_GetError");
        }

        /// Calls `SDL_GetError`.
        ///
        /// @return a NUL-terminated string SDL owns, empty when there is none
        public MemorySegment call() {
            try {
                return (MemorySegment) FD_SDL_GetError.invokeExact(address);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_GetError", t);
            }
        }
    }

    /// Clears the calling thread’s error.
    ///
    /// `_Bool SDL_ClearError(void)`
    public static final class ClearError {

        private static final MethodHandle FD_SDL_ClearError = Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN));

        private final MemorySegment address;

        ClearError(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_ClearError");
        }

        /// Calls `SDL_ClearError`.
        ///
        /// @return always true
        public boolean call() {
            try {
                return (boolean) FD_SDL_ClearError.invokeExact(address);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_ClearError", t);
            }
        }
    }

    /// The version of SDL linked into `libgoldberry`.
    ///
    /// `int SDL_GetVersion(void)`
    public static final class GetVersion {

        private static final MethodHandle FD_SDL_GetVersion = Downcalls.link(FunctionDescriptor.of(JAVA_INT));

        private final MemorySegment address;

        GetVersion(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_GetVersion");
        }

        /// Calls `SDL_GetVersion`.
        ///
        /// @return a packed version, `major * 1000000 + minor * 1000 + patch`
        public int call() {
            try {
                return (int) FD_SDL_GetVersion.invokeExact(address);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_GetVersion", t);
            }
        }
    }

    /// The source revision SDL was built from.
    ///
    /// `void* SDL_GetRevision(void)`
    public static final class GetRevision {

        private static final MethodHandle FD_SDL_GetRevision = Downcalls.link(FunctionDescriptor.of(ADDRESS));

        private final MemorySegment address;

        GetRevision(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_GetRevision");
        }

        /// Calls `SDL_GetRevision`.
        ///
        /// @return a NUL-terminated string SDL owns
        public MemorySegment call() {
            try {
                return (MemorySegment) FD_SDL_GetRevision.invokeExact(address);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_GetRevision", t);
            }
        }
    }

    /// The video driver SDL chose — `wayland`, `x11`, `windows`, `cocoa`.
    ///
    /// Worth checking before believing anything about windowing behaviour: a
    /// Wayland session running through XWayland behaves like X11, and nothing
    /// else in the process gives that away. Empty until video is initialised.
    ///
    /// `void* SDL_GetCurrentVideoDriver(void)`
    public static final class GetCurrentVideoDriver {

        private static final MethodHandle FD_SDL_GetCurrentVideoDriver = Downcalls.link(FunctionDescriptor.of(ADDRESS));

        private final MemorySegment address;

        GetCurrentVideoDriver(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_GetCurrentVideoDriver");
        }

        /// Calls `SDL_GetCurrentVideoDriver`.
        ///
        /// @return a NUL-terminated string SDL owns
        public MemorySegment call() {
            try {
                return (MemorySegment) FD_SDL_GetCurrentVideoDriver.invokeExact(address);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_GetCurrentVideoDriver", t);
            }
        }
    }

    /// Sets an SDL hint.
    ///
    /// Hints are SDL’s configuration channel; most must be set before the
    /// subsystem they affect is initialised.
    ///
    /// `_Bool SDL_SetHint(void*, void*)`
    public static final class SetHint {

        private static final MethodHandle FD_SDL_SetHint =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS, ADDRESS));

        private final MemorySegment address;

        SetHint(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_SetHint");
        }

        /// Calls `SDL_SetHint`.
        ///
        /// @param name the hint’s name, NUL-terminated
        /// @param value the value, NUL-terminated
        /// @return false if SDL refused it
        public boolean call(MemorySegment name, MemorySegment value) {
            try {
                return (boolean) FD_SDL_SetHint.invokeExact(address, name, value);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_SetHint", t);
            }
        }
    }

    /// The modifier keys held **right now**.
    ///
    /// Polled rather than carried on the event, because SDL’s mouse events have
    /// no `mod` field where its keyboard events do. Read at the moment an event
    /// is translated, which is inside the same pump that produced it.
    ///
    /// Returns `SDL_Keymod`, a `Uint16` — the layout table’s "Uint16" scalar row
    /// is what says so, and binding it as `JAVA_INT` would read two bytes of
    /// whatever follows it in the return register.
    ///
    /// `short SDL_GetModState(void)`
    public static final class GetModState {

        private static final MethodHandle FD_SDL_GetModState = Downcalls.link(FunctionDescriptor.of(JAVA_SHORT));

        private final MemorySegment address;

        GetModState(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_GetModState");
        }

        /// Calls `SDL_GetModState`.
        ///
        /// @return an `SDL_Keymod` bitmask
        public short call() {
            try {
                return (short) FD_SDL_GetModState.invokeExact(address);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_GetModState", t);
            }
        }
    }

    /// Where the pointer is on the **desktop**, right now.
    ///
    /// Polled at translation time for [GetModState]'s reason and one sharper
    /// one: it is the only reading of the pointer that does not depend on which
    /// window the platform decided an event belongs to. A popup's events on
    /// macOS arrive attributed to the popup with coordinates in the *owner's*
    /// space, so the per-event numbers cannot be trusted for one
    /// (ADR-0211).
    ///
    /// `unsigned int SDL_GetGlobalMouseState(float *x, float *y)`
    public static final class GetGlobalMouseState {

        private static final MethodHandle FD_SDL_GetGlobalMouseState =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

        private final MemorySegment address;

        GetGlobalMouseState(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_GetGlobalMouseState");
        }

        /// Calls `SDL_GetGlobalMouseState`.
        ///
        /// @param x out-parameter for the x coordinate, in desktop logical points
        /// @param y out-parameter for the y coordinate
        /// @return an `SDL_MouseButtonFlags` bitmask, which nothing reads yet
        public int call(MemorySegment x, MemorySegment y) {
            try {
                return (int) FD_SDL_GetGlobalMouseState.invokeExact(address, x, y);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_GetGlobalMouseState", t);
            }
        }
    }
}
