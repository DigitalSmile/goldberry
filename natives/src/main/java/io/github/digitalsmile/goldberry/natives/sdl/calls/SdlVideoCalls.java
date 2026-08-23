package io.github.digitalsmile.goldberry.natives.sdl.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import io.github.digitalsmile.goldberry.natives.Downcalls;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

/// SDL's window, display and event functions, one holder each.
///
/// Two of them are [#getDisplayForWindow()] and [#getCurrentDisplayMode()],
/// which older SDL3 builds do not export -- they answer `isAvailable()` with
/// false rather than refusing to bind, because an unpaced frame loop is worse
/// than a dead window only if the window is dead.
///
/// See [io.github.digitalsmile.goldberry.natives.calls] for why the holders
/// live in a package of their own.
public record SdlVideoCalls(
        CreateWindow createWindow,
        CreatePopupWindow createPopupWindow,
        SetWindowPosition setWindowPosition,
        SetWindowSize setWindowSize,
        GetWindowPosition getWindowPosition,
        GetDisplayUsableBounds getDisplayUsableBounds,
        DestroyWindow destroyWindow,
        ShowWindow showWindow,
        SetWindowTitle setWindowTitle,
        GetWindowSize getWindowSize,
        GetWindowSizeInPixels getWindowSizeInPixels,
        GetWindowDisplayScale getWindowDisplayScale,
        GetDisplayForWindow getDisplayForWindow,
        GetCurrentDisplayMode getCurrentDisplayMode,
        GetWindowId getWindowId,
        GetWindowSurface getWindowSurface,
        UpdateWindowSurfaceRects updateWindowSurfaceRects,
        DestroyWindowSurface destroyWindowSurface,
        StartTextInput startTextInput,
        StopTextInput stopTextInput,
        TextInputActive textInputActive,
        PollEvent pollEvent,
        WaitEventTimeout waitEventTimeout,
        PushEvent pushEvent) {

    /// Binds every function above.
    public static SdlVideoCalls bind(SymbolLookup lookup) {
        return new SdlVideoCalls(
                new CreateWindow(lookup),
                new CreatePopupWindow(lookup),
                new SetWindowPosition(lookup),
                new SetWindowSize(lookup),
                new GetWindowPosition(lookup),
                new GetDisplayUsableBounds(lookup),
                new DestroyWindow(lookup),
                new ShowWindow(lookup),
                new SetWindowTitle(lookup),
                new GetWindowSize(lookup),
                new GetWindowSizeInPixels(lookup),
                new GetWindowDisplayScale(lookup),
                new GetDisplayForWindow(lookup),
                new GetCurrentDisplayMode(lookup),
                new GetWindowId(lookup),
                new GetWindowSurface(lookup),
                new UpdateWindowSurfaceRects(lookup),
                new DestroyWindowSurface(lookup),
                new StartTextInput(lookup),
                new StopTextInput(lookup),
                new TextInputActive(lookup),
                new PollEvent(lookup),
                new WaitEventTimeout(lookup),
                new PushEvent(lookup));
    }

    /// `void* SDL_CreateWindow(void*, int, int, int64_t)`
    public static final class CreateWindow {

        private static final MethodHandle FD_SDL_CreateWindow =
                Downcalls.link(FunctionDescriptor.of(
                        ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, JAVA_LONG));

        private final MemorySegment address;

        CreateWindow(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_CreateWindow");
        }

        public MemorySegment call(MemorySegment a1, int a2, int a3, long a4) {
            try {
                return (MemorySegment) FD_SDL_CreateWindow.invokeExact(address, a1, a2, a3, a4);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_CreateWindow", t);
            }
        }
    }

    /// `void* SDL_CreatePopupWindow(void*, int, int, int, int, int64_t)`
    public static final class CreatePopupWindow {

        private static final MethodHandle FD_SDL_CreatePopupWindow =
                Downcalls.link(FunctionDescriptor.of(
                        ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_LONG));

        private final MemorySegment address;

        CreatePopupWindow(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_CreatePopupWindow");
        }

        public MemorySegment call(MemorySegment a1, int a2, int a3, int a4, int a5, long a6) {
            try {
                return (MemorySegment) FD_SDL_CreatePopupWindow.invokeExact(
                        address, a1, a2, a3, a4, a5, a6);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_CreatePopupWindow", t);
            }
        }
    }

    /// `_Bool SDL_SetWindowPosition(void*, int, int)`
    public static final class SetWindowPosition {

        private static final MethodHandle FD_SDL_SetWindowPosition =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS, JAVA_INT, JAVA_INT));

        private final MemorySegment address;

        SetWindowPosition(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_SetWindowPosition");
        }

        public boolean call(MemorySegment a1, int a2, int a3) {
            try {
                return (boolean) FD_SDL_SetWindowPosition.invokeExact(address, a1, a2, a3);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_SetWindowPosition", t);
            }
        }
    }

    /// `_Bool SDL_SetWindowSize(void*, int, int)`
    public static final class SetWindowSize {

        private static final MethodHandle FD_SDL_SetWindowSize =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS, JAVA_INT, JAVA_INT));

        private final MemorySegment address;

        SetWindowSize(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_SetWindowSize");
        }

        public boolean call(MemorySegment a1, int a2, int a3) {
            try {
                return (boolean) FD_SDL_SetWindowSize.invokeExact(address, a1, a2, a3);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_SetWindowSize", t);
            }
        }
    }

    /// `_Bool SDL_GetWindowPosition(void*, void*, void*)`
    public static final class GetWindowPosition {

        private static final MethodHandle FD_SDL_GetWindowPosition =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS, ADDRESS, ADDRESS));

        private final MemorySegment address;

        GetWindowPosition(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_GetWindowPosition");
        }

        public boolean call(MemorySegment a1, MemorySegment a2, MemorySegment a3) {
            try {
                return (boolean) FD_SDL_GetWindowPosition.invokeExact(address, a1, a2, a3);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_GetWindowPosition", t);
            }
        }
    }

    /// `_Bool SDL_GetDisplayUsableBounds(int, void*)`
    public static final class GetDisplayUsableBounds {

        private static final MethodHandle FD_SDL_GetDisplayUsableBounds =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, JAVA_INT, ADDRESS));

        private final MemorySegment address;

        GetDisplayUsableBounds(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_GetDisplayUsableBounds");
        }

        public boolean call(int a1, MemorySegment a2) {
            try {
                return (boolean) FD_SDL_GetDisplayUsableBounds.invokeExact(address, a1, a2);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_GetDisplayUsableBounds", t);
            }
        }
    }

    /// `void SDL_DestroyWindow(void*)`
    public static final class DestroyWindow {

        private static final MethodHandle FD_SDL_DestroyWindow =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        DestroyWindow(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_DestroyWindow");
        }

        public void call(MemorySegment a1) {
            try {
                FD_SDL_DestroyWindow.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_DestroyWindow", t);
            }
        }
    }

    /// `_Bool SDL_ShowWindow(void*)`
    public static final class ShowWindow {

        private static final MethodHandle FD_SDL_ShowWindow =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS));

        private final MemorySegment address;

        ShowWindow(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_ShowWindow");
        }

        public boolean call(MemorySegment a1) {
            try {
                return (boolean) FD_SDL_ShowWindow.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_ShowWindow", t);
            }
        }
    }

    /// `_Bool SDL_SetWindowTitle(void*, void*)`
    public static final class SetWindowTitle {

        private static final MethodHandle FD_SDL_SetWindowTitle =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS, ADDRESS));

        private final MemorySegment address;

        SetWindowTitle(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_SetWindowTitle");
        }

        public boolean call(MemorySegment a1, MemorySegment a2) {
            try {
                return (boolean) FD_SDL_SetWindowTitle.invokeExact(address, a1, a2);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_SetWindowTitle", t);
            }
        }
    }

    /// `_Bool SDL_GetWindowSize(void*, void*, void*)`
    public static final class GetWindowSize {

        private static final MethodHandle FD_SDL_GetWindowSize =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS, ADDRESS, ADDRESS));

        private final MemorySegment address;

        GetWindowSize(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_GetWindowSize");
        }

        public boolean call(MemorySegment a1, MemorySegment a2, MemorySegment a3) {
            try {
                return (boolean) FD_SDL_GetWindowSize.invokeExact(address, a1, a2, a3);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_GetWindowSize", t);
            }
        }
    }

    /// `_Bool SDL_GetWindowSizeInPixels(void*, void*, void*)`
    public static final class GetWindowSizeInPixels {

        private static final MethodHandle FD_SDL_GetWindowSizeInPixels =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS, ADDRESS, ADDRESS));

        private final MemorySegment address;

        GetWindowSizeInPixels(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_GetWindowSizeInPixels");
        }

        public boolean call(MemorySegment a1, MemorySegment a2, MemorySegment a3) {
            try {
                return (boolean) FD_SDL_GetWindowSizeInPixels.invokeExact(address, a1, a2, a3);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_GetWindowSizeInPixels", t);
            }
        }
    }

    /// `float SDL_GetWindowDisplayScale(void*)`
    public static final class GetWindowDisplayScale {

        private static final MethodHandle FD_SDL_GetWindowDisplayScale =
                Downcalls.link(FunctionDescriptor.of(JAVA_FLOAT, ADDRESS));

        private final MemorySegment address;

        GetWindowDisplayScale(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_GetWindowDisplayScale");
        }

        public float call(MemorySegment a1) {
            try {
                return (float) FD_SDL_GetWindowDisplayScale.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_GetWindowDisplayScale", t);
            }
        }
    }

    /// `int SDL_GetDisplayForWindow(void*)`
    public static final class GetDisplayForWindow {

        private static final MethodHandle FD_SDL_GetDisplayForWindow =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        GetDisplayForWindow(SymbolLookup lookup) {
            this.address = Downcalls.optionalSymbol(lookup, "SDL_GetDisplayForWindow");
        }

        /// Whether this build of the library exports it.
        public boolean isAvailable() {
            return address != null;
        }

        public int call(MemorySegment a1) {
            try {
                return (int) FD_SDL_GetDisplayForWindow.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_GetDisplayForWindow", t);
            }
        }
    }

    /// `void* SDL_GetCurrentDisplayMode(int)`
    public static final class GetCurrentDisplayMode {

        private static final MethodHandle FD_SDL_GetCurrentDisplayMode =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, JAVA_INT));

        private final MemorySegment address;

        GetCurrentDisplayMode(SymbolLookup lookup) {
            this.address = Downcalls.optionalSymbol(lookup, "SDL_GetCurrentDisplayMode");
        }

        /// Whether this build of the library exports it.
        public boolean isAvailable() {
            return address != null;
        }

        public MemorySegment call(int a1) {
            try {
                return (MemorySegment) FD_SDL_GetCurrentDisplayMode.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_GetCurrentDisplayMode", t);
            }
        }
    }

    /// `int SDL_GetWindowID(void*)`
    public static final class GetWindowId {

        private static final MethodHandle FD_SDL_GetWindowID =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        GetWindowId(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_GetWindowID");
        }

        public int call(MemorySegment a1) {
            try {
                return (int) FD_SDL_GetWindowID.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_GetWindowID", t);
            }
        }
    }

    /// `void* SDL_GetWindowSurface(void*)`
    public static final class GetWindowSurface {

        private static final MethodHandle FD_SDL_GetWindowSurface =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, ADDRESS));

        private final MemorySegment address;

        GetWindowSurface(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_GetWindowSurface");
        }

        public MemorySegment call(MemorySegment a1) {
            try {
                return (MemorySegment) FD_SDL_GetWindowSurface.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_GetWindowSurface", t);
            }
        }
    }

    /// `_Bool SDL_UpdateWindowSurfaceRects(void*, void*, int)`
    public static final class UpdateWindowSurfaceRects {

        private static final MethodHandle FD_SDL_UpdateWindowSurfaceRects =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        UpdateWindowSurfaceRects(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_UpdateWindowSurfaceRects");
        }

        public boolean call(MemorySegment a1, MemorySegment a2, int a3) {
            try {
                return (boolean) FD_SDL_UpdateWindowSurfaceRects.invokeExact(address, a1, a2, a3);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_UpdateWindowSurfaceRects", t);
            }
        }
    }

    /// `_Bool SDL_DestroyWindowSurface(void*)`
    public static final class DestroyWindowSurface {

        private static final MethodHandle FD_SDL_DestroyWindowSurface =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS));

        private final MemorySegment address;

        DestroyWindowSurface(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_DestroyWindowSurface");
        }

        public boolean call(MemorySegment a1) {
            try {
                return (boolean) FD_SDL_DestroyWindowSurface.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_DestroyWindowSurface", t);
            }
        }
    }

    /// `_Bool SDL_StartTextInput(void*)`
    public static final class StartTextInput {

        private static final MethodHandle FD_SDL_StartTextInput =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS));

        private final MemorySegment address;

        StartTextInput(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_StartTextInput");
        }

        public boolean call(MemorySegment a1) {
            try {
                return (boolean) FD_SDL_StartTextInput.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_StartTextInput", t);
            }
        }
    }

    /// `_Bool SDL_StopTextInput(void*)`
    public static final class StopTextInput {

        private static final MethodHandle FD_SDL_StopTextInput =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS));

        private final MemorySegment address;

        StopTextInput(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_StopTextInput");
        }

        public boolean call(MemorySegment a1) {
            try {
                return (boolean) FD_SDL_StopTextInput.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_StopTextInput", t);
            }
        }
    }

    /// `_Bool SDL_TextInputActive(void*)`
    public static final class TextInputActive {

        private static final MethodHandle FD_SDL_TextInputActive =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS));

        private final MemorySegment address;

        TextInputActive(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_TextInputActive");
        }

        public boolean call(MemorySegment a1) {
            try {
                return (boolean) FD_SDL_TextInputActive.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_TextInputActive", t);
            }
        }
    }

    /// `_Bool SDL_PollEvent(void*)`
    public static final class PollEvent {

        private static final MethodHandle FD_SDL_PollEvent =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS));

        private final MemorySegment address;

        PollEvent(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_PollEvent");
        }

        public boolean call(MemorySegment a1) {
            try {
                return (boolean) FD_SDL_PollEvent.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_PollEvent", t);
            }
        }
    }

    /// `_Bool SDL_WaitEventTimeout(void*, int)`
    public static final class WaitEventTimeout {

        private static final MethodHandle FD_SDL_WaitEventTimeout =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        WaitEventTimeout(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_WaitEventTimeout");
        }

        public boolean call(MemorySegment a1, int a2) {
            try {
                return (boolean) FD_SDL_WaitEventTimeout.invokeExact(address, a1, a2);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_WaitEventTimeout", t);
            }
        }
    }

    /// `_Bool SDL_PushEvent(void*)`
    public static final class PushEvent {

        private static final MethodHandle FD_SDL_PushEvent =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS));

        private final MemorySegment address;

        PushEvent(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_PushEvent");
        }

        public boolean call(MemorySegment a1) {
            try {
                return (boolean) FD_SDL_PushEvent.invokeExact(address, a1);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_PushEvent", t);
            }
        }
    }
}
