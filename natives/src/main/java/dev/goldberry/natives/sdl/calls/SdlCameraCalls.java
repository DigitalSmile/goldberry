package dev.goldberry.natives.sdl.calls;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

import dev.goldberry.natives.Downcalls;

/// SDL's cameras, for `goldberry-camera`: the list, each camera's name,
/// position and modes, and an opened camera's permission, format and frames.
/// See [Downcalls] for why each handle is a `static final` constant.
public record SdlCameraCalls(
        GetCameras getCameras,
        GetCameraSupportedFormats getCameraSupportedFormats,
        GetCameraName getCameraName,
        GetCameraPosition getCameraPosition,
        OpenCamera openCamera,
        GetCameraPermissionState getCameraPermissionState,
        GetCameraFormat getCameraFormat,
        AcquireCameraFrame acquireCameraFrame,
        ReleaseCameraFrame releaseCameraFrame,
        CloseCamera closeCamera,
        GetCurrentCameraDriver getCurrentCameraDriver) {

    /// Binds every function above.
    ///
    /// @param lookup the loaded `libgoldberry`
    public static SdlCameraCalls bind(SymbolLookup lookup) {
        return new SdlCameraCalls(
                new GetCameras(lookup),
                new GetCameraSupportedFormats(lookup),
                new GetCameraName(lookup),
                new GetCameraPosition(lookup),
                new OpenCamera(lookup),
                new GetCameraPermissionState(lookup),
                new GetCameraFormat(lookup),
                new AcquireCameraFrame(lookup),
                new ReleaseCameraFrame(lookup),
                new CloseCamera(lookup),
                new GetCurrentCameraDriver(lookup));
    }

    /// `SDL_CameraID *SDL_GetCameras(int *count)`
    ///
    /// The cameras there are now, `count` ids and a zero after, in memory the
    /// caller releases with `SDL_free`. Null on failure.
    public static final class GetCameras {

        private static final MethodHandle FD_SDL_GetCameras = Downcalls.link(FunctionDescriptor.of(ADDRESS, ADDRESS));

        private final MemorySegment address;

        GetCameras(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_GetCameras");
        }

        public MemorySegment call(MemorySegment count) {
            try {
                return (MemorySegment) FD_SDL_GetCameras.invokeExact(address, count);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_GetCameras", t);
            }
        }
    }

    /// `SDL_CameraSpec **SDL_GetCameraSupportedFormats(SDL_CameraID instance_id, int *count)`
    ///
    /// `count` pointers to the modes the camera offers and a null after, in one
    /// allocation the caller releases with `SDL_free`. Null on failure.
    public static final class GetCameraSupportedFormats {

        private static final MethodHandle FD_SDL_GetCameraSupportedFormats =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, JAVA_INT, ADDRESS));

        private final MemorySegment address;

        GetCameraSupportedFormats(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_GetCameraSupportedFormats");
        }

        public MemorySegment call(int camera, MemorySegment count) {
            try {
                return (MemorySegment) FD_SDL_GetCameraSupportedFormats.invokeExact(address, camera, count);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_GetCameraSupportedFormats", t);
            }
        }
    }

    /// `const char *SDL_GetCameraName(SDL_CameraID instance_id)`
    ///
    /// SDL's string, or null for an id it does not know.
    public static final class GetCameraName {

        private static final MethodHandle FD_SDL_GetCameraName =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, JAVA_INT));

        private final MemorySegment address;

        GetCameraName(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_GetCameraName");
        }

        public MemorySegment call(int camera) {
            try {
                return (MemorySegment) FD_SDL_GetCameraName.invokeExact(address, camera);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_GetCameraName", t);
            }
        }
    }

    /// `SDL_CameraPosition SDL_GetCameraPosition(SDL_CameraID instance_id)`
    ///
    /// Where the camera faces.
    public static final class GetCameraPosition {

        private static final MethodHandle FD_SDL_GetCameraPosition =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, JAVA_INT));

        private final MemorySegment address;

        GetCameraPosition(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_GetCameraPosition");
        }

        public int call(int camera) {
            try {
                return (int) FD_SDL_GetCameraPosition.invokeExact(address, camera);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_GetCameraPosition", t);
            }
        }
    }

    /// `SDL_Camera *SDL_OpenCamera(SDL_CameraID instance_id, const SDL_CameraSpec *spec)`
    ///
    /// Opens the camera, converting to `spec` where it differs from the mode SDL
    /// picks; a null spec takes the camera's own. Null on failure.
    public static final class OpenCamera {

        private static final MethodHandle FD_SDL_OpenCamera =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, JAVA_INT, ADDRESS));

        private final MemorySegment address;

        OpenCamera(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_OpenCamera");
        }

        public MemorySegment call(int camera, MemorySegment spec) {
            try {
                return (MemorySegment) FD_SDL_OpenCamera.invokeExact(address, camera, spec);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_OpenCamera", t);
            }
        }
    }

    /// `SDL_CameraPermissionState SDL_GetCameraPermissionState(SDL_Camera *camera)`
    ///
    /// Whether the user has allowed the camera: -1, 0 or 1.
    public static final class GetCameraPermissionState {

        private static final MethodHandle FD_SDL_GetCameraPermissionState =
                Downcalls.link(FunctionDescriptor.of(JAVA_INT, ADDRESS));

        private final MemorySegment address;

        GetCameraPermissionState(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_GetCameraPermissionState");
        }

        public int call(MemorySegment camera) {
            try {
                return (int) FD_SDL_GetCameraPermissionState.invokeExact(address, camera);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_GetCameraPermissionState", t);
            }
        }
    }

    /// `bool SDL_GetCameraFormat(SDL_Camera *camera, SDL_CameraSpec *spec)`
    ///
    /// The format frames arrive in. False until the camera is approved.
    public static final class GetCameraFormat {

        private static final MethodHandle FD_SDL_GetCameraFormat =
                Downcalls.link(FunctionDescriptor.of(JAVA_BOOLEAN, ADDRESS, ADDRESS));

        private final MemorySegment address;

        GetCameraFormat(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_GetCameraFormat");
        }

        public boolean call(MemorySegment camera, MemorySegment spec) {
            try {
                return (boolean) FD_SDL_GetCameraFormat.invokeExact(address, camera, spec);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_GetCameraFormat", t);
            }
        }
    }

    /// `SDL_Surface *SDL_AcquireCameraFrame(SDL_Camera *camera, Uint64 *timestampNS)`
    ///
    /// The newest frame SDL holds, or null when there is none yet. The surface is
    /// SDL's until it is handed back with `SDL_ReleaseCameraFrame`.
    public static final class AcquireCameraFrame {

        private static final MethodHandle FD_SDL_AcquireCameraFrame =
                Downcalls.link(FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));

        private final MemorySegment address;

        AcquireCameraFrame(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_AcquireCameraFrame");
        }

        public MemorySegment call(MemorySegment camera, MemorySegment timestamp) {
            try {
                return (MemorySegment) FD_SDL_AcquireCameraFrame.invokeExact(address, camera, timestamp);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_AcquireCameraFrame", t);
            }
        }
    }

    /// `void SDL_ReleaseCameraFrame(SDL_Camera *camera, SDL_Surface *frame)`
    ///
    /// Gives a frame back to the driver.
    public static final class ReleaseCameraFrame {

        private static final MethodHandle FD_SDL_ReleaseCameraFrame =
                Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS, ADDRESS));

        private final MemorySegment address;

        ReleaseCameraFrame(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_ReleaseCameraFrame");
        }

        public void call(MemorySegment camera, MemorySegment frame) {
            try {
                FD_SDL_ReleaseCameraFrame.invokeExact(address, camera, frame);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_ReleaseCameraFrame", t);
            }
        }
    }

    /// `void SDL_CloseCamera(SDL_Camera *camera)`
    ///
    /// Closes the camera, releasing any frame still held.
    public static final class CloseCamera {

        private static final MethodHandle FD_SDL_CloseCamera = Downcalls.link(FunctionDescriptor.ofVoid(ADDRESS));

        private final MemorySegment address;

        CloseCamera(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_CloseCamera");
        }

        public void call(MemorySegment camera) {
            try {
                FD_SDL_CloseCamera.invokeExact(address, camera);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_CloseCamera", t);
            }
        }
    }

    /// `const char *SDL_GetCurrentCameraDriver(void)`
    ///
    /// The driver the camera subsystem started with, or null when it is not up.
    public static final class GetCurrentCameraDriver {

        private static final MethodHandle FD_SDL_GetCurrentCameraDriver =
                Downcalls.link(FunctionDescriptor.of(ADDRESS));

        private final MemorySegment address;

        GetCurrentCameraDriver(SymbolLookup lookup) {
            this.address = Downcalls.symbol(lookup, "SDL_GetCurrentCameraDriver");
        }

        public MemorySegment call() {
            try {
                return (MemorySegment) FD_SDL_GetCurrentCameraDriver.invokeExact(address);
            } catch (Throwable t) {
                throw Downcalls.failure("SDL_GetCurrentCameraDriver", t);
            }
        }
    }
}
