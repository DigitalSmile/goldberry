package io.github.digitalsmile.goldberry.natives.sdl.calls;

import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import io.github.digitalsmile.goldberry.natives.Downcalls;

/// SDL's one question about the desktop's appearance — `docs/gaps.md` G26.
///
/// A record of its own rather than a method on [SdlDisplayCalls], because it is
/// not a display query: the answer is the same on every monitor and changes when
/// the user changes a setting — which is what `SdlEventType.SYSTEM_THEME_CHANGED`
/// reports.
///
/// One holder per function: its handle, its address, and a `call` whose
/// parameters are the C prototype's. See [Downcalls] for why the handle is a
/// `static final` constant and why these live in a package of their own.
public record SdlThemeCalls(GetSystemTheme getSystemTheme) {

    /// Binds every function above.
    ///
    /// @param lookup the loaded `libgoldberry`
    public static SdlThemeCalls bind(SymbolLookup lookup) {
        return new SdlThemeCalls(new GetSystemTheme(lookup));
    }

    /// What the desktop is set to.
    ///
    /// `SDL_SystemTheme SDL_GetSystemTheme(void)`
    public static final class GetSystemTheme {

        private static final MethodHandle FD_SDL_GetSystemTheme = Downcalls.link(FunctionDescriptor.of(JAVA_INT));

        private final MemorySegment address;

        GetSystemTheme(SymbolLookup lookup) {
            // Optional, like the display-mode pair: a `libgoldberry` built before
            // this export existed must keep opening windows, and "the desktop does
            // not say" is an answer this question already has (ADR-0322).
            this.address = Downcalls.optionalSymbol(lookup, "SDL_GetSystemTheme");
        }

        /// Whether this build of the library exports it.
        ///
        /// @return false when it does not, in which case [#call] must not be used
        public boolean isAvailable() {
            return address != null;
        }

        /// Makes the call.
        ///
        /// @return an `SDL_SystemTheme`, which is `SDL_SYSTEM_THEME_UNKNOWN` on a
        ///         desktop that has no such setting and on a driver that cannot ask
        public int call() {
            try {
                return (int) FD_SDL_GetSystemTheme.invokeExact(address);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_GetSystemTheme", t);
            }
        }
    }
}
