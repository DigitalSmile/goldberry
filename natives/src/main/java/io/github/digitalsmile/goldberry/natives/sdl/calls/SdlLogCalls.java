package io.github.digitalsmile.goldberry.natives.sdl.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import io.github.digitalsmile.goldberry.natives.Downcalls;

/// Where SDL's own log messages go.
///
/// One function. SDL logs for itself — a driver it could not load, a hint it did
/// not understand, a surface format it fell back from — and writes it to stderr
/// or, on Windows, to the debugger. This is the call that redirects it into
/// SLF4J (ADR-0443).
public record SdlLogCalls(SetLogOutputFunction setLogOutputFunction) {

    /// Binds the function, which may be absent from a `libgoldberry` built
    /// before it was on the export list.
    public static SdlLogCalls bind(SymbolLookup lookup) {
        return new SdlLogCalls(new SetLogOutputFunction(lookup));
    }

    /// Replaces SDL's log destination for the whole process.
    ///
    /// `void SDL_SetLogOutputFunction(SDL_LogOutputFunction, void*)`
    ///
    /// Process-wide rather than per-window, like the cursor: there is one SDL in
    /// a process and it logs once. Safe to call before `SDL_Init`, which is when
    /// this toolkit calls it — a driver that will not start is exactly the
    /// message worth keeping, and it is written during initialisation.
    ///
    /// **Optional**, unlike almost every other SDL binding here. A library built
    /// before this symbol was listed gives back no address, and the toolkit
    /// carries on with SDL logging where it always did; refusing to open a
    /// window because a log line cannot be redirected would be the wrong trade
    /// by some distance. See
    /// [io.github.digitalsmile.goldberry.natives.sdl.calls.SdlThemeCalls]
    /// for the same shape.
    public static final class SetLogOutputFunction {

        private static final MethodHandle FD_SDL_SetLogOutputFunction =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));

        private final MemorySegment address;

        SetLogOutputFunction(SymbolLookup lookup) {
            this.address = Downcalls.optionalSymbol(lookup, "SDL_SetLogOutputFunction");
        }

        /// Whether this build of the library exports it.
        public boolean isPresent() {
            return address != null;
        }

        /// Calls `SDL_SetLogOutputFunction`.
        ///
        /// @param callback an upcall stub of `SDL_LogOutputFunction`'s shape
        /// @param userData passed back to the callback untouched; may be
        ///        [MemorySegment#NULL]
        public void call(MemorySegment callback, MemorySegment userData) {
            try {
                FD_SDL_SetLogOutputFunction.invokeExact(address, callback, userData);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_SetLogOutputFunction", t);
            }
        }
    }
}
