package io.github.digitalsmile.goldberry.natives.sdl.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;

import io.github.digitalsmile.goldberry.natives.Downcalls;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

/// SDL's clipboard functions, one holder each.
///
/// See [io.github.digitalsmile.goldberry.natives.calls] for why the holders
/// live in a package of their own.
public record SdlClipboardCalls(
        GetClipboardText getClipboardText,
        SetClipboardText setClipboardText,
        HasClipboardText hasClipboardText,
        Free free) {

    /// Binds every function above.
    public static SdlClipboardCalls bind(SymbolLookup lookup) {
        return new SdlClipboardCalls(
                new GetClipboardText(lookup),
                new SetClipboardText(lookup),
                new HasClipboardText(lookup),
                new Free(lookup));
    }

    /// `void* SDL_GetClipboardText(void)`
    public static final class GetClipboardText {

        private static final MethodHandle FD_SDL_GetClipboardText =
                Downcalls.link(FunctionDescriptor.of(ADDRESS));

        private final MemorySegment address;

        GetClipboardText(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_GetClipboardText");
        }

        public MemorySegment call() {
            try {
                return (MemorySegment) FD_SDL_GetClipboardText.invokeExact(address);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_GetClipboardText", t);
            }
        }
    }

    /// `_Bool SDL_SetClipboardText(void*)`
    public static final class SetClipboardText {

        private static final MethodHandle FD_SDL_SetClipboardText =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS));

        private final MemorySegment address;

        SetClipboardText(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_SetClipboardText");
        }

        public boolean call(MemorySegment a1) {
            try {
                return (boolean) FD_SDL_SetClipboardText.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_SetClipboardText", t);
            }
        }
    }

    /// `_Bool SDL_HasClipboardText(void)`
    public static final class HasClipboardText {

        private static final MethodHandle FD_SDL_HasClipboardText =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN));

        private final MemorySegment address;

        HasClipboardText(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_HasClipboardText");
        }

        public boolean call() {
            try {
                return (boolean) FD_SDL_HasClipboardText.invokeExact(address);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_HasClipboardText", t);
            }
        }
    }

    /// `void SDL_free(void*)`
    public static final class Free {

        private static final MethodHandle FD_SDL_free =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        Free(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_free");
        }

        public void call(MemorySegment a1) {
            try {
                FD_SDL_free.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_free", t);
            }
        }
    }
}
