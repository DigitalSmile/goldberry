package io.github.digitalsmile.goldberry.natives.sdl.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import io.github.digitalsmile.goldberry.natives.Downcalls;

/// SDL's window calls — creating one, moving it, and taking it down.
///
/// One holder per function: its handle, its address, and a `call` whose
/// parameters are the C prototype’s. See [Downcalls] for why the handle is a
/// `static final` constant and why these live in a package of their own.
public record SdlWindowCalls(
        CreateWindow createWindow,
        CreatePopupWindow createPopupWindow,
        DestroyWindow destroyWindow,
        ShowWindow showWindow,
        SetWindowTitle setWindowTitle,
        MaximizeWindow maximizeWindow,
        RestoreWindow restoreWindow,
        SetWindowPosition setWindowPosition,
        GetWindowPosition getWindowPosition,
        SetWindowSize setWindowSize,
        GetWindowSize getWindowSize,
        GetWindowSizeInPixels getWindowSizeInPixels,
        GetWindowId getWindowId,
        StartTextInput startTextInput,
        StopTextInput stopTextInput,
        TextInputActive textInputActive,
        SetTextInputArea setTextInputArea) {

    /// Binds every function above.
    ///
    /// @param lookup the loaded `libgoldberry`
    public static SdlWindowCalls bind(SymbolLookup lookup) {
        return new SdlWindowCalls(
                new CreateWindow(lookup),
                new CreatePopupWindow(lookup),
                new DestroyWindow(lookup),
                new ShowWindow(lookup),
                new SetWindowTitle(lookup),
                new MaximizeWindow(lookup),
                new RestoreWindow(lookup),
                new SetWindowPosition(lookup),
                new GetWindowPosition(lookup),
                new SetWindowSize(lookup),
                new GetWindowSize(lookup),
                new GetWindowSizeInPixels(lookup),
                new GetWindowId(lookup),
                new StartTextInput(lookup),
                new StopTextInput(lookup),
                new TextInputActive(lookup),
                new SetTextInputArea(lookup));
    }

    /// Tells the platform where the text being typed is, so a candidate window
    /// can be put beside it rather than wherever the compositor guesses.
    ///
    /// `bool SDL_SetTextInputArea(SDL_Window* window, const SDL_Rect* rect, int cursor)`
    ///
    /// The rectangle is in the window's own **logical** coordinates, and `cursor`
    /// is the caret's offset from its left edge — an input method uses the first
    /// to keep its list clear of the text and the second to align it under the
    /// insertion point.
    ///
    /// @param window the window being typed into
    /// @param rect   an `SDL_Rect`, or NULL to clear the area
    /// @param cursor the caret's x offset within `rect`
    public static final class SetTextInputArea {

        private static final MethodHandle FD_SDL_SetTextInputArea =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        SetTextInputArea(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_SetTextInputArea");
        }

        public boolean call(MemorySegment window, MemorySegment rect, int cursor) {
            try {
                return (boolean) FD_SDL_SetTextInputArea.invokeExact(address, window, rect, cursor);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_SetTextInputArea", t);
            }
        }
    }

    /// Creates a top-level window.
    ///
    /// `void* SDL_CreateWindow(void*, int, int, int64_t)`
    ///
    /// @param title the title, NUL-terminated
    /// @param width in logical pixels
    /// @param height in logical pixels
    /// @param flags a mask of `SDL_WINDOW_*` flags
    /// @return an `SDL_Window*`, or NULL on failure
    public static final class CreateWindow {

        private static final MethodHandle FD_SDL_CreateWindow =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, JAVA_LONG));

        private final MemorySegment address;

        CreateWindow(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_CreateWindow");
        }

        public MemorySegment call(MemorySegment title, int width, int height, long flags) {
            try {
                return (MemorySegment) FD_SDL_CreateWindow.invokeExact(address, title, width, height, flags);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_CreateWindow", t);
            }
        }
    }

    /// Creates a window owned by another, positioned in its coordinates.
    ///
    /// A driver with no popup support refuses this, which is the answer a menu
    /// has to be able to hear (ADR-0102).
    ///
    /// `void* SDL_CreatePopupWindow(void*, int, int, int, int, int64_t)`
    ///
    /// @param parent the owning `SDL_Window*`
    /// @param offsetX relative to the parent’s client area
    /// @param offsetY relative to the parent’s client area
    /// @param width in logical pixels
    /// @param height in logical pixels
    /// @param flags a mask of `SDL_WINDOW_*` flags; one of TOOLTIP or POPUP_MENU is required
    /// @return an `SDL_Window*`, or NULL on failure
    public static final class CreatePopupWindow {

        private static final MethodHandle FD_SDL_CreatePopupWindow = Downcalls.link(
                FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_LONG));

        private final MemorySegment address;

        CreatePopupWindow(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_CreatePopupWindow");
        }

        public MemorySegment call(MemorySegment parent, int offsetX, int offsetY, int width, int height, long flags) {
            try {
                return (MemorySegment)
                        FD_SDL_CreatePopupWindow.invokeExact(address, parent, offsetX, offsetY, width, height, flags);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_CreatePopupWindow", t);
            }
        }
    }

    /// Destroys a window and everything hanging off it.
    ///
    /// `void SDL_DestroyWindow(void*)`
    ///
    /// @param window the window to destroy
    public static final class DestroyWindow {

        private static final MethodHandle FD_SDL_DestroyWindow = Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        DestroyWindow(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_DestroyWindow");
        }

        public void call(MemorySegment window) {
            try {
                FD_SDL_DestroyWindow.invokeExact(address, window);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_DestroyWindow", t);
            }
        }
    }

    /// Maps the window. Nothing is on screen before this.
    ///
    /// `_Bool SDL_ShowWindow(void*)`
    ///
    /// @param window the window to map
    /// @return false if SDL refused
    public static final class ShowWindow {

        private static final MethodHandle FD_SDL_ShowWindow =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS));

        private final MemorySegment address;

        ShowWindow(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_ShowWindow");
        }

        public boolean call(MemorySegment window) {
            try {
                return (boolean) FD_SDL_ShowWindow.invokeExact(address, window);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_ShowWindow", t);
            }
        }
    }

    /// Sets the window’s title.
    ///
    /// `_Bool SDL_SetWindowTitle(void*, void*)`
    ///
    /// @param title the title, NUL-terminated
    /// @return false if SDL refused
    public static final class SetWindowTitle {

        private static final MethodHandle FD_SDL_SetWindowTitle =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS, ADDRESS));

        private final MemorySegment address;

        SetWindowTitle(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_SetWindowTitle");
        }

        public boolean call(MemorySegment window, MemorySegment title) {
            try {
                return (boolean) FD_SDL_SetWindowTitle.invokeExact(address, window, title);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_SetWindowTitle", t);
            }
        }
    }

    /// Asks the window manager to maximize the window.
    ///
    /// `_Bool SDL_MaximizeWindow(void*)`
    ///
    /// **A request rather than a setter.** On every platform this is a message to
    /// the window manager, which may refuse it — a tiling compositor has its own
    /// idea — so what it returns is whether SDL accepted the *ask*, and the state
    /// is `SDL_EVENT_WINDOW_MAXIMIZED` arriving afterwards (ADR-0252).
    ///
    /// @return false if SDL refused
    public static final class MaximizeWindow {

        private static final MethodHandle FD_SDL_MaximizeWindow =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS));

        private final MemorySegment address;

        MaximizeWindow(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_MaximizeWindow");
        }

        public boolean call(MemorySegment window) {
            try {
                return (boolean) FD_SDL_MaximizeWindow.invokeExact(address, window);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_MaximizeWindow", t);
            }
        }
    }

    /// Asks for the window's ordinary size back — [MaximizeWindow]'s undo, and
    /// also un-minimizes.
    ///
    /// `_Bool SDL_RestoreWindow(void*)`
    ///
    /// @return false if SDL refused
    public static final class RestoreWindow {

        private static final MethodHandle FD_SDL_RestoreWindow =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS));

        private final MemorySegment address;

        RestoreWindow(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_RestoreWindow");
        }

        public boolean call(MemorySegment window) {
            try {
                return (boolean) FD_SDL_RestoreWindow.invokeExact(address, window);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_RestoreWindow", t);
            }
        }
    }

    /// Moves the window.
    ///
    /// `_Bool SDL_SetWindowPosition(void*, int, int)`
    ///
    /// @param x in the parent’s coordinates for a popup, the display’s for a top-level window
    /// @param y the same
    /// @return false if SDL refused
    public static final class SetWindowPosition {

        private static final MethodHandle FD_SDL_SetWindowPosition =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS, JAVA_INT, JAVA_INT));

        private final MemorySegment address;

        SetWindowPosition(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_SetWindowPosition");
        }

        public boolean call(MemorySegment window, int x, int y) {
            try {
                return (boolean) FD_SDL_SetWindowPosition.invokeExact(address, window, x, y);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_SetWindowPosition", t);
            }
        }
    }

    /// Reads where the window is.
    ///
    /// `_Bool SDL_GetWindowPosition(void*, void*, void*)`
    ///
    /// @param outX a caller-allocated `int*`
    /// @param outY a caller-allocated `int*`
    /// @return false if SDL refused
    public static final class GetWindowPosition {

        private static final MethodHandle FD_SDL_GetWindowPosition =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS, ADDRESS, ADDRESS));

        private final MemorySegment address;

        GetWindowPosition(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_GetWindowPosition");
        }

        public boolean call(MemorySegment window, MemorySegment outX, MemorySegment outY) {
            try {
                return (boolean) FD_SDL_GetWindowPosition.invokeExact(address, window, outX, outY);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_GetWindowPosition", t);
            }
        }
    }

    /// Resizes the window.
    ///
    /// `_Bool SDL_SetWindowSize(void*, int, int)`
    ///
    /// @param width in logical pixels
    /// @param height in logical pixels
    /// @return false if SDL refused
    public static final class SetWindowSize {

        private static final MethodHandle FD_SDL_SetWindowSize =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS, JAVA_INT, JAVA_INT));

        private final MemorySegment address;

        SetWindowSize(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_SetWindowSize");
        }

        public boolean call(MemorySegment window, int width, int height) {
            try {
                return (boolean) FD_SDL_SetWindowSize.invokeExact(address, window, width, height);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_SetWindowSize", t);
            }
        }
    }

    /// Reads the window’s size in **logical** pixels.
    ///
    /// `_Bool SDL_GetWindowSize(void*, void*, void*)`
    ///
    /// @param outWidth a caller-allocated `int*`
    /// @param outHeight a caller-allocated `int*`
    /// @return false if SDL refused
    public static final class GetWindowSize {

        private static final MethodHandle FD_SDL_GetWindowSize =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS, ADDRESS, ADDRESS));

        private final MemorySegment address;

        GetWindowSize(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_GetWindowSize");
        }

        public boolean call(MemorySegment window, MemorySegment outWidth, MemorySegment outHeight) {
            try {
                return (boolean) FD_SDL_GetWindowSize.invokeExact(address, window, outWidth, outHeight);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_GetWindowSize", t);
            }
        }
    }

    /// Reads the window’s size in **physical** pixels.
    ///
    /// The pair with [SdlWindowCalls.GetWindowSize] is what a fractional display
    /// scale is: the two disagree by exactly that factor.
    ///
    /// `_Bool SDL_GetWindowSizeInPixels(void*, void*, void*)`
    ///
    /// @param outWidth a caller-allocated `int*`
    /// @param outHeight a caller-allocated `int*`
    /// @return false if SDL refused
    public static final class GetWindowSizeInPixels {

        private static final MethodHandle FD_SDL_GetWindowSizeInPixels =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS, ADDRESS, ADDRESS));

        private final MemorySegment address;

        GetWindowSizeInPixels(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_GetWindowSizeInPixels");
        }

        public boolean call(MemorySegment window, MemorySegment outWidth, MemorySegment outHeight) {
            try {
                return (boolean) FD_SDL_GetWindowSizeInPixels.invokeExact(address, window, outWidth, outHeight);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_GetWindowSizeInPixels", t);
            }
        }
    }

    /// The window’s id, which is what an event carries instead of a pointer.
    ///
    /// `int SDL_GetWindowID(void*)`
    ///
    /// @return an `SDL_WindowID`, or 0 on failure
    public static final class GetWindowId {

        private static final MethodHandle FD_SDL_GetWindowID = Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        GetWindowId(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_GetWindowID");
        }

        public int call(MemorySegment window) {
            try {
                return (int) FD_SDL_GetWindowID.invokeExact(address, window);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_GetWindowID", t);
            }
        }
    }

    /// Asks the platform to start delivering composed text.
    ///
    /// Nothing produces `SDL_EVENT_TEXT_INPUT` until this is called, which is why
    /// a field that never asked never sees a keystroke.
    ///
    /// `_Bool SDL_StartTextInput(void*)`
    ///
    /// @param window the window that should receive text
    /// @return false if SDL refused
    public static final class StartTextInput {

        private static final MethodHandle FD_SDL_StartTextInput =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS));

        private final MemorySegment address;

        StartTextInput(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_StartTextInput");
        }

        public boolean call(MemorySegment window) {
            try {
                return (boolean) FD_SDL_StartTextInput.invokeExact(address, window);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_StartTextInput", t);
            }
        }
    }

    /// Stops text delivery for the window.
    ///
    /// `_Bool SDL_StopTextInput(void*)`
    ///
    /// @return false if SDL refused
    public static final class StopTextInput {

        private static final MethodHandle FD_SDL_StopTextInput =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS));

        private final MemorySegment address;

        StopTextInput(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_StopTextInput");
        }

        public boolean call(MemorySegment window) {
            try {
                return (boolean) FD_SDL_StopTextInput.invokeExact(address, window);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_StopTextInput", t);
            }
        }
    }

    /// Whether text delivery is on for the window.
    ///
    /// `_Bool SDL_TextInputActive(void*)`
    ///
    /// @return true if active
    public static final class TextInputActive {

        private static final MethodHandle FD_SDL_TextInputActive =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS));

        private final MemorySegment address;

        TextInputActive(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_TextInputActive");
        }

        public boolean call(MemorySegment window) {
            try {
                return (boolean) FD_SDL_TextInputActive.invokeExact(address, window);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_TextInputActive", t);
            }
        }
    }
}
