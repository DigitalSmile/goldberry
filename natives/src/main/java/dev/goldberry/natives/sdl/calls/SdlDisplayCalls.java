package dev.goldberry.natives.sdl.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import dev.goldberry.natives.Downcalls;

/// SDL's display queries — which displays there are, their names, bounds and
/// scale, a window's work area, and refresh rate.
///
/// One holder per function: its handle, its address, and a `call` whose
/// parameters are the C prototype’s. See [Downcalls] for why the handle is a
/// `static final` constant and why these live in a package of their own.
public record SdlDisplayCalls(
        GetWindowDisplayScale getWindowDisplayScale,
        GetDisplayUsableBounds getDisplayUsableBounds,
        GetDisplayForWindow getDisplayForWindow,
        GetCurrentDisplayMode getCurrentDisplayMode,
        GetDisplays getDisplays,
        GetDisplayName getDisplayName,
        GetDisplayBounds getDisplayBounds,
        GetDisplayContentScale getDisplayContentScale) {

    /// Binds every function above.
    ///
    /// @param lookup the loaded `libgoldberry`
    public static SdlDisplayCalls bind(SymbolLookup lookup) {
        return new SdlDisplayCalls(
                new GetWindowDisplayScale(lookup),
                new GetDisplayUsableBounds(lookup),
                new GetDisplayForWindow(lookup),
                new GetCurrentDisplayMode(lookup),
                new GetDisplays(lookup),
                new GetDisplayName(lookup),
                new GetDisplayBounds(lookup),
                new GetDisplayContentScale(lookup));
    }

    /// The displays connected now, as a zero-terminated array SDL allocated.
    ///
    /// The caller releases it with `SDL_free`. The first entry is the primary
    /// display: `SDL_GetPrimaryDisplay` answers with the same one.
    ///
    /// `SDL_DisplayID* SDL_GetDisplays(int* count)`
    public static final class GetDisplays {

        private static final MethodHandle FD_SDL_GetDisplays = Downcalls.link(FunctionDescriptor.of(ADDRESS, ADDRESS));

        private final MemorySegment address;

        GetDisplays(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_GetDisplays");
        }

        /// Calls `SDL_GetDisplays`.
        ///
        /// @param outCount a caller-allocated `int` the count is written to
        /// @return the array, or NULL on failure
        public MemorySegment call(MemorySegment outCount) {
            try {
                return (MemorySegment) FD_SDL_GetDisplays.invokeExact(address, outCount);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_GetDisplays", t);
            }
        }
    }

    /// A display's human-readable name, which SDL owns.
    ///
    /// `const char* SDL_GetDisplayName(int)`
    public static final class GetDisplayName {

        private static final MethodHandle FD_SDL_GetDisplayName =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, JAVA_INT));

        private final MemorySegment address;

        GetDisplayName(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_GetDisplayName");
        }

        /// Calls `SDL_GetDisplayName`.
        ///
        /// @param displayId an `SDL_DisplayID`
        /// @return the name, or NULL on failure
        public MemorySegment call(int displayId) {
            try {
                return (MemorySegment) FD_SDL_GetDisplayName.invokeExact(address, displayId);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_GetDisplayName", t);
            }
        }
    }

    /// A display's full bounds, in the desktop's coordinates — what
    /// [GetDisplayUsableBounds] takes the panels away from.
    ///
    /// `_Bool SDL_GetDisplayBounds(int, void*)`
    public static final class GetDisplayBounds {

        private static final MethodHandle FD_SDL_GetDisplayBounds =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, JAVA_INT, ADDRESS));

        private final MemorySegment address;

        GetDisplayBounds(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_GetDisplayBounds");
        }

        /// Calls `SDL_GetDisplayBounds`.
        ///
        /// @param displayId an `SDL_DisplayID`
        /// @param outRect a caller-allocated `SDL_Rect`
        /// @return false if SDL refused
        public boolean call(int displayId, MemorySegment outRect) {
            try {
                return (boolean) FD_SDL_GetDisplayBounds.invokeExact(address, displayId, outRect);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_GetDisplayBounds", t);
            }
        }
    }

    /// The scale the desktop is set to on a display — 1.5 for 150%.
    ///
    /// Not the pixel density: a window's own scale is this times the density
    /// of the display's mode.
    ///
    /// `float SDL_GetDisplayContentScale(int)`
    public static final class GetDisplayContentScale {

        private static final MethodHandle FD_SDL_GetDisplayContentScale =
                Downcalls.link(FunctionDescriptor.of(JAVA_FLOAT, JAVA_INT));

        private final MemorySegment address;

        GetDisplayContentScale(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_GetDisplayContentScale");
        }

        /// Calls `SDL_GetDisplayContentScale`.
        ///
        /// @param displayId an `SDL_DisplayID`
        /// @return the scale, or 0 on failure
        public float call(int displayId) {
            try {
                return (float) FD_SDL_GetDisplayContentScale.invokeExact(address, displayId);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_GetDisplayContentScale", t);
            }
        }
    }

    /// The display scale in force for the window.
    ///
    /// Fractional, and it changes when the window is dragged between monitors.
    ///
    /// `float SDL_GetWindowDisplayScale(void*)`
    public static final class GetWindowDisplayScale {

        private static final MethodHandle FD_SDL_GetWindowDisplayScale =
                Downcalls.link(FunctionDescriptor.of(JAVA_FLOAT, ADDRESS));

        private final MemorySegment address;

        GetWindowDisplayScale(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_GetWindowDisplayScale");
        }

        /// Calls `SDL_GetWindowDisplayScale`.
        ///
        /// @param window the window to ask about
        /// @return logical-to-physical factor, or 0 on failure
        public float call(MemorySegment window) {
            try {
                return (float) FD_SDL_GetWindowDisplayScale.invokeExact(address, window);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_GetWindowDisplayScale", t);
            }
        }
    }

    /// The display’s **work area** — its bounds less panels and docks.
    ///
    /// What a popup is flipped and shifted against, rather than the full bounds:
    /// a menu that opens under a taskbar is a menu nobody can click.
    ///
    /// `_Bool SDL_GetDisplayUsableBounds(int, void*)`
    public static final class GetDisplayUsableBounds {

        private static final MethodHandle FD_SDL_GetDisplayUsableBounds =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, JAVA_INT, ADDRESS));

        private final MemorySegment address;

        GetDisplayUsableBounds(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_GetDisplayUsableBounds");
        }

        /// Calls `SDL_GetDisplayUsableBounds`.
        ///
        /// @param displayId an `SDL_DisplayID`
        /// @param outRect a caller-allocated `SDL_Rect`
        /// @return false if SDL refused
        public boolean call(int displayId, MemorySegment outRect) {
            try {
                return (boolean) FD_SDL_GetDisplayUsableBounds.invokeExact(address, displayId, outRect);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_GetDisplayUsableBounds", t);
            }
        }
    }

    /// Which display the window is mostly on.
    ///
    /// Optional: an older `libgoldberry` may not export it, and the frame pacer
    /// has a defined answer for "the platform will not say" — do not pace.
    ///
    /// `int SDL_GetDisplayForWindow(void*)`
    public static final class GetDisplayForWindow {

        private static final MethodHandle FD_SDL_GetDisplayForWindow =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        GetDisplayForWindow(SymbolLookup lookup) {
            this.address = Downcalls.optionalSymbol(lookup, "SDL_GetDisplayForWindow");
        }

        /// Whether this build of the library exports it.
        ///
        /// @return false when it does not, in which case [#call] must not be used
        public boolean isAvailable() {
            return address != null;
        }

        /// Makes the call.
        ///
        /// @param window the window to locate
        /// @return an `SDL_DisplayID`, or 0 on failure
        public int call(MemorySegment window) {
            try {
                return (int) FD_SDL_GetDisplayForWindow.invokeExact(address, window);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_GetDisplayForWindow", t);
            }
        }
    }

    /// The mode a display is running in, which is where its refresh rate is.
    ///
    /// Optional, for [SdlDisplayCalls.GetDisplayForWindow]’s reason.
    ///
    /// `void* SDL_GetCurrentDisplayMode(int)`
    public static final class GetCurrentDisplayMode {

        private static final MethodHandle FD_SDL_GetCurrentDisplayMode =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, JAVA_INT));

        private final MemorySegment address;

        GetCurrentDisplayMode(SymbolLookup lookup) {
            this.address = Downcalls.optionalSymbol(lookup, "SDL_GetCurrentDisplayMode");
        }

        /// Whether this build of the library exports it.
        ///
        /// @return false when it does not, in which case [#call] must not be used
        public boolean isAvailable() {
            return address != null;
        }

        /// Makes the call.
        ///
        /// @param displayId an `SDL_DisplayID`
        /// @return an `SDL_DisplayMode*` SDL owns, or NULL
        public MemorySegment call(int displayId) {
            try {
                return (MemorySegment) FD_SDL_GetCurrentDisplayMode.invokeExact(address, displayId);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_GetCurrentDisplayMode", t);
            }
        }
    }
}
