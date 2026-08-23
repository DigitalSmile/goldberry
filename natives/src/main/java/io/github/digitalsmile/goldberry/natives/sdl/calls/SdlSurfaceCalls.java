package io.github.digitalsmile.goldberry.natives.sdl.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import io.github.digitalsmile.goldberry.natives.Downcalls;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

/// SDL's window surface — the pixels a frame is painted into.
///
/// One holder per function: its handle, its address, and a `call` whose
/// parameters are the C prototype’s. See [Downcalls] for why the handle is a
/// `static final` constant and why these live in a package of their own.
public record SdlSurfaceCalls(
        GetWindowSurface getWindowSurface,
        UpdateWindowSurfaceRects updateWindowSurfaceRects,
        DestroyWindowSurface destroyWindowSurface) {

    /// Binds every function above.
    ///
    /// @param lookup the loaded `libgoldberry`
    public static SdlSurfaceCalls bind(SymbolLookup lookup) {
        return new SdlSurfaceCalls(
                new GetWindowSurface(lookup),
                new UpdateWindowSurfaceRects(lookup),
                new DestroyWindowSurface(lookup));
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
                return (boolean) FD_SDL_UpdateWindowSurfaceRects.invokeExact(
                        address, window, rects, count);
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
}
