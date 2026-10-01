package dev.goldberry.gpu.composite;

import org.jspecify.annotations.Nullable;

import dev.goldberry.gpu.GpuDevice;
import dev.goldberry.gpu.GpuTexture;
import dev.goldberry.natives.sdl.gpu.SdlGpuDevice;
import dev.goldberry.natives.sdl.gpu.SdlGpuTexture;

/// What this package needs of the public GPU API that the API does not make
/// public: a [GpuDevice] over the compositor's device, and the SDL texture
/// under a [GpuTexture] a layer rendered into (ADR-0481).
///
/// The two are package-private in `…gpu`, because no application should hold
/// either, and this package is another package. So `…gpu` registers an
/// implementation here when [GpuDevice] is initialised -- the JDK's own
/// pattern for the same problem -- and this package reaches it through the
/// static methods below. Nothing outside the module can: this package is not
/// exported.
public abstract class ApiAccess {

    private static volatile @Nullable ApiAccess registered;

    /// For `…gpu`'s own subclass. Nothing else makes one.
    protected ApiAccess() {}

    /// Called once, by [GpuDevice]'s static initialiser.
    ///
    /// @throws IllegalStateException when called a second time
    public static void register(ApiAccess access) {
        if (registered != null) {
            throw new IllegalStateException("the GPU API's access is registered once");
        }
        registered = access;
    }

    /// The public face of `device`, confined to the calling thread. The caller
    /// keeps closing `device`.
    static GpuDevice device(SdlGpuDevice device) {
        return access().wrap(device);
    }

    /// The SDL texture under `texture`, which must be open and on the device
    /// it was made on.
    static SdlGpuTexture texture(GpuTexture texture) {
        return access().unwrap(texture);
    }

    /// Wraps `device`; see [#device].
    protected abstract GpuDevice wrap(SdlGpuDevice device);

    /// Unwraps `texture`; see [#texture].
    protected abstract SdlGpuTexture unwrap(GpuTexture texture);

    private static ApiAccess access() {
        var current = registered;
        if (current == null) {
            // Initialising the class is what registers it.
            try {
                Class.forName(GpuDevice.class.getName(), true, GpuDevice.class.getClassLoader());
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("the GPU API is not in this module", e);
            }
            current = registered;
            if (current == null) {
                throw new IllegalStateException("GpuDevice registered no access to the GPU API");
            }
        }
        return current;
    }
}
