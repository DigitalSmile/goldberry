package io.github.digitalsmile.goldberry.natives.sdl.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import io.github.digitalsmile.goldberry.natives.Downcalls;

/// SDL's surfaces — the window's own, which a frame is painted into, and one
/// wrapped around pixels of Goldberry's, which is what a tray icon is.
///
/// One holder per function: its handle, its address, and a `call` whose
/// parameters are the C prototype’s. See [Downcalls] for why the handle is a
/// `static final` constant and why these live in a package of their own.
public record SdlSurfaceCalls(
        GetWindowSurface getWindowSurface,
        UpdateWindowSurfaceRects updateWindowSurfaceRects,
        DestroyWindowSurface destroyWindowSurface,
        CreateSurfaceFrom createSurfaceFrom,
        DestroySurface destroySurface) {

    /// Binds every function above.
    ///
    /// @param lookup the loaded `libgoldberry`
    public static SdlSurfaceCalls bind(SymbolLookup lookup) {
        return new SdlSurfaceCalls(
                new GetWindowSurface(lookup),
                new UpdateWindowSurfaceRects(lookup),
                new DestroyWindowSurface(lookup),
                new CreateSurfaceFrom(lookup),
                new DestroySurface(lookup));
    }

    /// Borrows the window’s own drawing surface.
    ///
    /// SDL’s memory, not a copy: it stops being valid when the window is resized
    /// or presented. On a driver with no surface support — Wayland is one — SDL
    /// builds a hidden renderer behind this, which is what makes
    /// `SDL_RENDER_VSYNC` matter to a toolkit that creates no renderer
    /// (ADR-0046).
    ///
    /// `void* SDL_GetWindowSurface(void*)`
    ///
    /// @param window the window to borrow from
    /// @return an `SDL_Surface*`, or NULL on failure
    public static final class GetWindowSurface {

        private static final MethodHandle FD_SDL_GetWindowSurface =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, ADDRESS));

        private final MemorySegment address;

        GetWindowSurface(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_GetWindowSurface");
        }

        public MemorySegment call(MemorySegment window) {
            try {
                return (MemorySegment) FD_SDL_GetWindowSurface.invokeExact(address, window);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_GetWindowSurface", t);
            }
        }
    }

    /// Presents the parts of the surface that changed.
    ///
    /// The damage path: a frame painted only inside its damage is presented only
    /// inside it too (ADR-0072).
    ///
    /// `_Bool SDL_UpdateWindowSurfaceRects(void*, void*, int)`
    ///
    /// @param rects an `SDL_Rect*` array of the regions that changed
    /// @param count how many rectangles
    /// @return false if SDL refused
    public static final class UpdateWindowSurfaceRects {

        private static final MethodHandle FD_SDL_UpdateWindowSurfaceRects =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        UpdateWindowSurfaceRects(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_UpdateWindowSurfaceRects");
        }

        public boolean call(MemorySegment window, MemorySegment rects, int count) {
            try {
                return (boolean) FD_SDL_UpdateWindowSurfaceRects.invokeExact(address, window, rects, count);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_UpdateWindowSurfaceRects", t);
            }
        }
    }

    /// Releases the borrowed surface, so the next request builds a fresh one.
    ///
    /// What a resize needs: the old surface is the old size.
    ///
    /// `_Bool SDL_DestroyWindowSurface(void*)`
    ///
    /// @return false if SDL refused
    public static final class DestroyWindowSurface {

        private static final MethodHandle FD_SDL_DestroyWindowSurface =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS));

        private final MemorySegment address;

        DestroyWindowSurface(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_DestroyWindowSurface");
        }

        public boolean call(MemorySegment window) {
            try {
                return (boolean) FD_SDL_DestroyWindowSurface.invokeExact(address, window);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_DestroyWindowSurface", t);
            }
        }
    }

    /// Wraps pixels the caller owns as a surface, copying nothing.
    ///
    /// The mirror of [io.github.digitalsmile.goldberry.natives.blend2d.BlendImage]
    /// on SDL's side, and it carries the same obligation: **the buffer must
    /// outlive the surface.** A tray icon is the one caller — SDL wants an
    /// `SDL_Surface*` and Goldberry has a painted BGRA buffer, and the platform
    /// consumes the pixels inside `SDL_CreateTray`, so the surface is destroyed
    /// as soon as that returns.
    ///
    /// `void* SDL_CreateSurfaceFrom(int, int, int, void*, int)`
    ///
    /// @param width  in pixels
    /// @param height in pixels
    /// @param format an `SDL_PixelFormat`
    /// @param pixels the first pixel; the caller keeps ownership
    /// @param pitch  bytes per row
    /// @return an `SDL_Surface*`, or NULL on failure
    public static final class CreateSurfaceFrom {

        private static final MethodHandle FD_SDL_CreateSurfaceFrom =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT));

        private final MemorySegment address;

        CreateSurfaceFrom(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_CreateSurfaceFrom");
        }

        public MemorySegment call(int width, int height, int format, MemorySegment pixels, int pitch) {
            try {
                return (MemorySegment)
                        FD_SDL_CreateSurfaceFrom.invokeExact(address, width, height, format, pixels, pitch);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_CreateSurfaceFrom", t);
            }
        }
    }

    /// Releases a surface. Borrowed pixels are untouched — they were never SDL's
    /// to free.
    ///
    /// `void SDL_DestroySurface(void*)`
    ///
    /// @param surface the surface to release
    public static final class DestroySurface {

        private static final MethodHandle FD_SDL_DestroySurface = Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        DestroySurface(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_DestroySurface");
        }

        public void call(MemorySegment surface) {
            try {
                FD_SDL_DestroySurface.invokeExact(address, surface);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_DestroySurface", t);
            }
        }
    }
}
