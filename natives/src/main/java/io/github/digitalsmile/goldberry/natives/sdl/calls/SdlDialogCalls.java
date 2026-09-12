package io.github.digitalsmile.goldberry.natives.sdl.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import io.github.digitalsmile.goldberry.natives.Downcalls;

/// SDL's three file dialogs.
///
/// One holder per function: its handle, its address, and a `call` whose
/// parameters are the C prototype's. See [Downcalls] for why the handle is a
/// `static final` constant and why these live in a package of their own.
///
/// **All three return `void` and none of them reports failure here.** A dialog
/// is asynchronous: the call posts a request and returns, and the one callback
/// says which of "chose", "cancelled" and "failed" happened — a NULL file list
/// being the failure. That is why there is no `bool` to check and why
/// [io.github.digitalsmile.goldberry.natives.sdl.SdlFileDialogs] keeps the
/// memory it handed over alive until the callback arrives.
public record SdlDialogCalls(
        ShowOpenFileDialog showOpenFileDialog,
        ShowSaveFileDialog showSaveFileDialog,
        ShowOpenFolderDialog showOpenFolderDialog) {

    /// Binds every function above.
    ///
    /// @param lookup the loaded `libgoldberry`
    public static SdlDialogCalls bind(SymbolLookup lookup) {
        return new SdlDialogCalls(
                new ShowOpenFileDialog(lookup), new ShowSaveFileDialog(lookup), new ShowOpenFolderDialog(lookup));
    }

    /// Asks the platform to let the user pick one or more existing files.
    ///
    /// `void SDL_ShowOpenFileDialog(SDL_DialogFileCallback callback, void* userdata,`
    /// `SDL_Window* window, const SDL_DialogFileFilter* filters, int nfilters,`
    /// `const char* default_location, bool allow_many)`
    ///
    /// `filters` **must stay valid until the callback runs** — SDL's own header
    /// says so, and on Linux the request travels to an XDG portal over DBus and
    /// comes back much later.
    ///
    /// @param callback        an `SDL_DialogFileCallback` upcall stub
    /// @param userdata        handed back to it, unread by SDL
    /// @param window          the window to be modal for, or NULL
    /// @param filters         an array of `SDL_DialogFileFilter`, or NULL
    /// @param filterCount     how many of them; ignored when `filters` is NULL
    /// @param defaultLocation a NUL-terminated path to start at, or NULL
    /// @param allowMany       whether more than one file may be chosen
    public static final class ShowOpenFileDialog {

        private static final MethodHandle FD_SDL_ShowOpenFileDialog = Downcalls.link(
                FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, JAVA_BOOLEAN));

        private final MemorySegment address;

        ShowOpenFileDialog(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_ShowOpenFileDialog");
        }

        public void call(
                MemorySegment callback,
                MemorySegment userdata,
                MemorySegment window,
                MemorySegment filters,
                int filterCount,
                MemorySegment defaultLocation,
                boolean allowMany) {
            try {
                FD_SDL_ShowOpenFileDialog.invokeExact(
                        address, callback, userdata, window, filters, filterCount, defaultLocation, allowMany);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_ShowOpenFileDialog", t);
            }
        }
    }

    /// Asks the platform to let the user name a file to write.
    ///
    /// No `allow_many`: a save dialog produces one path, and the path may not
    /// exist yet — which is the whole point of it and the reason an export
    /// cannot be built out of [ShowOpenFileDialog].
    ///
    /// `void SDL_ShowSaveFileDialog(SDL_DialogFileCallback callback, void* userdata,`
    /// `SDL_Window* window, const SDL_DialogFileFilter* filters, int nfilters,`
    /// `const char* default_location)`
    public static final class ShowSaveFileDialog {

        private static final MethodHandle FD_SDL_ShowSaveFileDialog =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS, ADDRESS, JAVA_INT, ADDRESS));

        private final MemorySegment address;

        ShowSaveFileDialog(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_ShowSaveFileDialog");
        }

        public void call(
                MemorySegment callback,
                MemorySegment userdata,
                MemorySegment window,
                MemorySegment filters,
                int filterCount,
                MemorySegment defaultLocation) {
            try {
                FD_SDL_ShowSaveFileDialog.invokeExact(
                        address, callback, userdata, window, filters, filterCount, defaultLocation);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_ShowSaveFileDialog", t);
            }
        }
    }

    /// Asks the platform to let the user pick a directory.
    ///
    /// No filters: there is nothing to filter, and passing them would be a lie
    /// the signature tells.
    ///
    /// `void SDL_ShowOpenFolderDialog(SDL_DialogFileCallback callback, void* userdata,`
    /// `SDL_Window* window, const char* default_location, bool allow_many)`
    public static final class ShowOpenFolderDialog {

        private static final MethodHandle FD_SDL_ShowOpenFolderDialog =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS, ADDRESS, ADDRESS, JAVA_BOOLEAN));

        private final MemorySegment address;

        ShowOpenFolderDialog(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_ShowOpenFolderDialog");
        }

        public void call(
                MemorySegment callback,
                MemorySegment userdata,
                MemorySegment window,
                MemorySegment defaultLocation,
                boolean allowMany) {
            try {
                FD_SDL_ShowOpenFolderDialog.invokeExact(
                        address, callback, userdata, window, defaultLocation, allowMany);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_ShowOpenFolderDialog", t);
            }
        }
    }
}
