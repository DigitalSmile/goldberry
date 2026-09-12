package io.github.digitalsmile.goldberry.natives.sdl.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import io.github.digitalsmile.goldberry.natives.Downcalls;

/// SDL's clipboard, and the allocator its strings come from.
///
/// One holder per function: its handle, its address, and a `call` whose
/// parameters are the C prototype’s. See [Downcalls] for why the handle is a
/// `static final` constant and why these live in a package of their own.
public record SdlClipboardCalls(
        GetClipboardText getClipboardText,
        SetClipboardText setClipboardText,
        HasClipboardText hasClipboardText,
        SetClipboardData setClipboardData,
        ClearClipboardData clearClipboardData,
        GetClipboardData getClipboardData,
        HasClipboardData hasClipboardData,
        GetClipboardMimeTypes getClipboardMimeTypes,
        Free free) {

    /// Binds every function above.
    ///
    /// @param lookup the loaded `libgoldberry`
    public static SdlClipboardCalls bind(SymbolLookup lookup) {
        return new SdlClipboardCalls(
                new GetClipboardText(lookup),
                new SetClipboardText(lookup),
                new HasClipboardText(lookup),
                new SetClipboardData(lookup),
                new ClearClipboardData(lookup),
                new GetClipboardData(lookup),
                new HasClipboardData(lookup),
                new GetClipboardMimeTypes(lookup),
                new Free(lookup));
    }

    /// Offers the clipboard a list of MIME types and two callbacks to produce
    /// them with.
    ///
    /// **Lazy, and that is the point.** The bytes are not copied here: SDL keeps
    /// the callbacks and calls the first one when another application actually
    /// pastes, which is what the platform protocols do underneath — an X11
    /// selection owner is asked to serialise on demand. The data the callback
    /// returns must stay valid until the cleanup callback says otherwise.
    ///
    /// `bool SDL_SetClipboardData(SDL_ClipboardDataCallback, SDL_ClipboardCleanupCallback,`
    /// `void* userdata, const char* const* mime_types, size_t num_mime_types)`
    ///
    /// @param callback  produces the bytes for one MIME type
    /// @param cleanup   called when this offer is replaced or cleared
    /// @param userdata  handed back to both, unread by SDL
    /// @param mimeTypes an array of NUL-terminated strings
    /// @param count     how many of them
    public static final class SetClipboardData {

        private static final MethodHandle FD_SDL_SetClipboardData =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS, ADDRESS, ADDRESS, ADDRESS, JAVA_LONG));

        private final MemorySegment address;

        SetClipboardData(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_SetClipboardData");
        }

        public boolean call(
                MemorySegment callback,
                MemorySegment cleanup,
                MemorySegment userdata,
                MemorySegment mimeTypes,
                long count) {
            try {
                return (boolean)
                        FD_SDL_SetClipboardData.invokeExact(address, callback, cleanup, userdata, mimeTypes, count);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_SetClipboardData", t);
            }
        }
    }

    /// Drops whatever this application was offering, which calls its cleanup.
    ///
    /// `bool SDL_ClearClipboardData(void)`
    public static final class ClearClipboardData {

        private static final MethodHandle FD_SDL_ClearClipboardData =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN));

        private final MemorySegment address;

        ClearClipboardData(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_ClearClipboardData");
        }

        public boolean call() {
            try {
                return (boolean) FD_SDL_ClearClipboardData.invokeExact(address);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_ClearClipboardData", t);
            }
        }
    }

    /// Reads one MIME type off the clipboard, whoever owns it.
    ///
    /// A **round trip to the owning application** on X11 and Wayland, and the
    /// bytes come back in SDL's allocator — so [SdlClipboardCalls.Free] closes
    /// this loop exactly as it does for text.
    ///
    /// `void* SDL_GetClipboardData(const char* mime_type, size_t* size)`
    ///
    /// @param mime a NUL-terminated MIME type
    /// @param size filled in with how many bytes came back
    /// @return the bytes, which the caller owns, or NULL
    public static final class GetClipboardData {

        private static final MethodHandle FD_SDL_GetClipboardData =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));

        private final MemorySegment address;

        GetClipboardData(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_GetClipboardData");
        }

        public MemorySegment call(MemorySegment mime, MemorySegment size) {
            try {
                return (MemorySegment) FD_SDL_GetClipboardData.invokeExact(address, mime, size);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_GetClipboardData", t);
            }
        }
    }

    /// Whether the clipboard can produce this MIME type.
    ///
    /// `bool SDL_HasClipboardData(const char* mime_type)`
    public static final class HasClipboardData {

        private static final MethodHandle FD_SDL_HasClipboardData =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS));

        private final MemorySegment address;

        HasClipboardData(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_HasClipboardData");
        }

        public boolean call(MemorySegment mime) {
            try {
                return (boolean) FD_SDL_HasClipboardData.invokeExact(address, mime);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_HasClipboardData", t);
            }
        }
    }

    /// Every MIME type the clipboard is currently offering.
    ///
    /// `char** SDL_GetClipboardMimeTypes(size_t* num_mime_types)`
    ///
    /// A NUL-terminated array of NUL-terminated strings, allocated as one block:
    /// a single [SdlClipboardCalls.Free] releases the array and the strings with
    /// it.
    ///
    /// @param count filled in with how many types there are, or NULL
    /// @return the types, which the caller owns, or NULL
    public static final class GetClipboardMimeTypes {

        private static final MethodHandle FD_SDL_GetClipboardMimeTypes =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, ADDRESS));

        private final MemorySegment address;

        GetClipboardMimeTypes(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_GetClipboardMimeTypes");
        }

        public MemorySegment call(MemorySegment count) {
            try {
                return (MemorySegment) FD_SDL_GetClipboardMimeTypes.invokeExact(address, count);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_GetClipboardMimeTypes", t);
            }
        }
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

        private static final MethodHandle FD_SDL_GetClipboardText = Downcalls.link(FunctionDescriptor.of(ADDRESS));

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

        private static final MethodHandle FD_SDL_HasClipboardText = Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN));

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

        private static final MethodHandle FD_SDL_free = Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS));

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
