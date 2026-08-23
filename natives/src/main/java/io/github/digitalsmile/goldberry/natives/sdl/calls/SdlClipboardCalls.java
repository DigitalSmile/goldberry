package io.github.digitalsmile.goldberry.natives.sdl.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;

import io.github.digitalsmile.goldberry.natives.Downcalls;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

/// SDL's clipboard, and the allocator its strings come from.
///
/// One holder per function: its handle, its address, and a `call` whose
/// parameters are the C prototype’s. See [Downcalls] for why the handle is a
/// `static final` constant and why these live in a package of their own.
public record SdlClipboardCalls(
        GetClipboardText getClipboardText,
        SetClipboardText setClipboardText,
        HasClipboardText hasClipboardText,
        Free free) {

    /// Binds every function above.
    ///
    /// @param lookup the loaded `libgoldberry`
    public static SdlClipboardCalls bind(SymbolLookup lookup) {
        return new SdlClipboardCalls(
                new GetClipboardText(lookup),
                new SetClipboardText(lookup),
                new HasClipboardText(lookup),
                new Free(lookup));
    }

    /// The clipboard’s text.
    ///
    /// On X11 and Wayland this is a **round trip to the owning client**, which is
    /// why [SdlClipboardCalls.HasClipboardText] is worth asking first. The result
    /// is the caller’s to free with [SdlClipboardCalls.Free].
    ///
    /// `void* SDL_GetClipboardText(void)`
    ///
    /// @return a NUL-terminated string the caller owns; empty rather than NULL on failure
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

    /// Puts text on the clipboard, replacing whatever was there.
    ///
    /// `_Bool SDL_SetClipboardText(void*)`
    ///
    /// @param text the text to publish, NUL-terminated
    /// @return false if the compositor declined
    public static final class SetClipboardText {

        private static final MethodHandle FD_SDL_SetClipboardText =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS));

        private final MemorySegment address;

        SetClipboardText(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_SetClipboardText");
        }

        public boolean call(MemorySegment text) {
            try {
                return (boolean) FD_SDL_SetClipboardText.invokeExact(address, text);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_SetClipboardText", t);
            }
        }
    }

    /// Whether the clipboard holds any non-empty text.
    ///
    /// Answered from what the compositor already told us, so it costs no round
    /// trip.
    ///
    /// `_Bool SDL_HasClipboardText(void)`
    ///
    /// @return true if there is text to paste
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

    /// SDL’s allocator, for a string SDL just handed over.
    ///
    /// It has to be SDL’s `free` and not the platform’s: on Windows SDL may be
    /// linked against a different C runtime than the process.
    ///
    /// `void SDL_free(void*)`
    ///
    /// @param pointer memory SDL allocated
    public static final class Free {

        private static final MethodHandle FD_SDL_free =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        Free(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_free");
        }

        public void call(MemorySegment pointer) {
            try {
                FD_SDL_free.invokeExact(address, pointer);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_free", t);
            }
        }
    }
}
