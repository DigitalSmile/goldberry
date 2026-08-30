package io.github.digitalsmile.goldberry.natives.sdl.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import io.github.digitalsmile.goldberry.natives.Downcalls;

/// SDL's cursors — the shapes, and showing or hiding them.
///
/// One holder per function: its handle, its address, and a `call` whose
/// parameters are the C prototype’s. See [Downcalls] for why the handle is a
/// `static final` constant and why these live in a package of their own.
public record SdlCursorCalls(
        CreateSystemCursor createSystemCursor,
        SetCursor setCursor,
        DestroyCursor destroyCursor,
        ShowCursor showCursor,
        HideCursor hideCursor) {

    /// Binds every function above.
    ///
    /// @param lookup the loaded `libgoldberry`
    public static SdlCursorCalls bind(SymbolLookup lookup) {
        return new SdlCursorCalls(
                new CreateSystemCursor(lookup),
                new SetCursor(lookup),
                new DestroyCursor(lookup),
                new ShowCursor(lookup),
                new HideCursor(lookup));
    }

    /// Makes one of the platform’s own cursor shapes.
    ///
    /// NULL when the platform has no such shape, which is not an error — it is a
    /// cursor to fall back from.
    ///
    /// `void* SDL_CreateSystemCursor(int)`
    ///
    /// @param shape an `SDL_SystemCursor`
    /// @return an `SDL_Cursor*`, or NULL
    public static final class CreateSystemCursor {

        private static final MethodHandle FD_SDL_CreateSystemCursor =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, JAVA_INT));

        private final MemorySegment address;

        CreateSystemCursor(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_CreateSystemCursor");
        }

        public MemorySegment call(int shape) {
            try {
                return (MemorySegment) FD_SDL_CreateSystemCursor.invokeExact(address, shape);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_CreateSystemCursor", t);
            }
        }
    }

    /// Sets the active cursor.
    ///
    /// `_Bool SDL_SetCursor(void*)`
    ///
    /// @param cursor the cursor to show
    /// @return false if SDL refused it
    public static final class SetCursor {

        private static final MethodHandle FD_SDL_SetCursor =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS));

        private final MemorySegment address;

        SetCursor(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_SetCursor");
        }

        public boolean call(MemorySegment cursor) {
            try {
                return (boolean) FD_SDL_SetCursor.invokeExact(address, cursor);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_SetCursor", t);
            }
        }
    }

    /// Releases a cursor.
    ///
    /// `void SDL_DestroyCursor(void*)`
    ///
    /// @param cursor the cursor to release
    public static final class DestroyCursor {

        private static final MethodHandle FD_SDL_DestroyCursor = Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        DestroyCursor(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_DestroyCursor");
        }

        public void call(MemorySegment cursor) {
            try {
                FD_SDL_DestroyCursor.invokeExact(address, cursor);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_DestroyCursor", t);
            }
        }
    }

    /// Makes the cursor visible.
    ///
    /// `_Bool SDL_ShowCursor(void)`
    ///
    /// @return false only when there is no video subsystem
    public static final class ShowCursor {

        private static final MethodHandle FD_SDL_ShowCursor = Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN));

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

    /// Hides the cursor.
    ///
    /// `_Bool SDL_HideCursor(void)`
    ///
    /// @return false only when there is no video subsystem
    public static final class HideCursor {

        private static final MethodHandle FD_SDL_HideCursor = Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN));

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
