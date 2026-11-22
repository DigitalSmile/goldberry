package dev.goldberry.natives.sdl.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import dev.goldberry.natives.Downcalls;

/// SDL's one question about the machine's power: where it comes from, and how
/// much battery is left.
///
/// One holder per function, as [SdlThemeCalls] is. See [Downcalls] for why the
/// handle is a `static final` constant.
public record SdlPowerCalls(GetPowerInfo getPowerInfo) {

    /// Binds every function above.
    ///
    /// @param lookup the loaded `libgoldberry`
    public static SdlPowerCalls bind(SymbolLookup lookup) {
        return new SdlPowerCalls(new GetPowerInfo(lookup));
    }

    /// The power source, the seconds of battery left and the percentage left.
    ///
    /// `SDL_PowerState SDL_GetPowerInfo(int *seconds, int *percent)`
    public static final class GetPowerInfo {

        private static final MethodHandle FD_SDL_GetPowerInfo =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

        private final MemorySegment address;

        GetPowerInfo(SymbolLookup lookup) {
            // Optional, like the theme: a `libgoldberry` built before this export
            // existed must keep opening windows, and "the platform does not say"
            // is an answer the question already has.
            this.address = Downcalls.optionalSymbol(lookup, "SDL_GetPowerInfo");
        }

        /// Whether this build of the library exports it.
        ///
        /// @return false when it does not, in which case [#call] must not be used
        public boolean isAvailable() {
            return address != null;
        }

        /// Makes the call.
        ///
        /// @param seconds an `int` the seconds of battery left are written to,
        ///                -1 where unknown; or `NULL`
        /// @param percent an `int` the percentage left is written to, -1 where
        ///                unknown; or `NULL`
        /// @return an `SDL_PowerState`
        public int call(MemorySegment seconds, MemorySegment percent) {
            try {
                return (int) FD_SDL_GetPowerInfo.invokeExact(address, seconds, percent);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_GetPowerInfo", t);
            }
        }
    }
}
