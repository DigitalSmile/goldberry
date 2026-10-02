package dev.goldberry.natives;

import java.lang.foreign.MemorySegment;
import java.util.Objects;

import dev.goldberry.natives.sdl.SdlWindowHandle;

/// The one way code in this module outside `natives.sdl` reads a window's
/// pointer: the GPU wrappers, which claim a window for a device.
///
/// [SdlWindowHandle] keeps its pointer package-private, because its package is
/// exported to every application and a public accessor there would put a
/// `MemorySegment` in reach of all of them; raw foreign memory never leaves this
/// module. This package is not exported at all. The handle registers its
/// accessor here when its class is initialised, which it has been by the time
/// anyone holds one: the JDK's `SharedSecrets` arrangement, at the size of one
/// method.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
public final class WindowPointers {

    /// Reads a window's pointer.
    @FunctionalInterface
    public interface Access {

        /// The `SDL_Window*`.
        ///
        /// @throws IllegalStateException when the window has been destroyed
        MemorySegment pointer(SdlWindowHandle window);
    }

    private static volatile Access access;

    private WindowPointers() {}

    /// Called once, by [SdlWindowHandle]'s static initialiser.
    ///
    /// @throws IllegalStateException when called a second time
    public static void register(Access accessor) {
        Objects.requireNonNull(accessor, "accessor");
        if (access != null) {
            throw new IllegalStateException("window pointer access is already registered");
        }
        access = accessor;
    }

    /// The `SDL_Window*` behind `window`.
    ///
    /// @throws IllegalStateException when the window has been destroyed
    public static MemorySegment of(SdlWindowHandle window) {
        var accessor = access;
        if (accessor == null) {
            // Unreachable while a handle exists: making one initialised its class.
            throw new IllegalStateException("SdlWindowHandle has not registered its pointer access");
        }
        return accessor.pointer(window);
    }
}
