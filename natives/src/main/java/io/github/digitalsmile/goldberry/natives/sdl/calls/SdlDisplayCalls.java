package io.github.digitalsmile.goldberry.natives.sdl.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import io.github.digitalsmile.goldberry.natives.Downcalls;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

/// SDL's display queries — scale, work area, and refresh rate.
///
/// One holder per function: its handle, its address, and a `call` whose
/// parameters are the C prototype’s. See
/// [io.github.digitalsmile.goldberry.natives.calls] for why the handle is a
/// `static final` constant and why these live in a package of their own.
public record SdlDisplayCalls(
        GetWindowDisplayScale getWindowDisplayScale,
        GetDisplayUsableBounds getDisplayUsableBounds,
        GetDisplayForWindow getDisplayForWindow,
        GetCurrentDisplayMode getCurrentDisplayMode) {

    /// Binds every function above.
    ///
    /// @param lookup the loaded `libgoldberry`
    public static SdlDisplayCalls bind(SymbolLookup lookup) {
        return new SdlDisplayCalls(
                new GetWindowDisplayScale(lookup),
                new GetDisplayUsableBounds(lookup),
                new GetDisplayForWindow(lookup),
                new GetCurrentDisplayMode(lookup));
    }

    /// The display scale in force for the window.
    ///
    /// Fractional, and it changes when the window is dragged between monitors.
    ///
    /// `float SDL_GetWindowDisplayScale(void*)`
    ///
    /// @param window the window to ask about
    /// @return logical-to-physical factor, or 0 on failure
    public static final class GetWindowDisplayScale {

        private static final MethodHandle FD_SDL_GetWindowDisplayScale =
                Downcalls.link(FunctionDescriptor.of(JAVA_FLOAT, ADDRESS));

        private final MemorySegment address;

        GetWindowDisplayScale(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_GetWindowDisplayScale");
        }

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
    /// a menu that opens under a taskbar is a menu nobody can click (ADR-0104).
    ///
    /// `_Bool SDL_GetDisplayUsableBounds(int, void*)`
    ///
    /// @param displayId an `SDL_DisplayID`
    /// @param outRect a caller-allocated `SDL_Rect`
    /// @return false if SDL refused
    public static final class GetDisplayUsableBounds {

        private static final MethodHandle FD_SDL_GetDisplayUsableBounds =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, JAVA_INT, ADDRESS));

        private final MemorySegment address;

        GetDisplayUsableBounds(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_GetDisplayUsableBounds");
        }

        public boolean call(int displayId, MemorySegment outRect) {
            try {
                return (boolean) FD_SDL_GetDisplayUsableBounds.invokeExact(
                        address, displayId, outRect);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_GetDisplayUsableBounds", t);
            }
        }
    }

    /// Which display the window is mostly on.
    ///
    /// Optional: an older `libgoldberry` may not export it, and the frame pacer
    /// has a defined answer for "the platform will not say" — do not pace
    /// (ADR-0047).
    ///
    /// `int SDL_GetDisplayForWindow(void*)`
    ///
    /// @param window the window to locate
    /// @return an `SDL_DisplayID`, or 0 on failure
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
    ///
    /// @param displayId an `SDL_DisplayID`
    /// @return an `SDL_DisplayMode*` SDL owns, or NULL
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

        public MemorySegment call(int displayId) {
            try {
                return (MemorySegment) FD_SDL_GetCurrentDisplayMode.invokeExact(address, displayId);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_GetCurrentDisplayMode", t);
            }
        }
    }
}
