package dev.goldberry.gpu.composite;

import org.jspecify.annotations.Nullable;

import dev.goldberry.gpu.CopyPass;
import dev.goldberry.gpu.GpuDevice;
import dev.goldberry.gpu.GpuTexture;
import dev.goldberry.gpu.render.mapped.InPlaceCopy;
import dev.goldberry.gpu.render.mapped.InPlaceProbe;
import dev.goldberry.natives.sdl.gpu.SdlGpuDevice;
import dev.goldberry.natives.sdl.gpu.SdlGpuTexture;

/// What the module needs of the public GPU API that the API does not make
/// public: a [GpuDevice] over the compositor's device, the SDL texture under a
/// [GpuTexture] a layer rendered into, and, for `…gpu.raster`'s texture painted
/// in place, the SDL device, whether it paints in place, and a copy pass to
/// record into with what says its frame has run.
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
    public static SdlGpuTexture texture(GpuTexture texture) {
        return access().unwrap(texture);
    }

    /// The SDL device under `device`, checked open and on its thread.
    public static SdlGpuDevice sdl(GpuDevice device) {
        return access().unwrap(device);
    }

    /// Whether rasters are painted in place on `device`: what the compositor
    /// decided when it made the device, or the device's own check, made the
    /// first time this is asked of a device the compositor did not make.
    public static InPlaceProbe.Decision inPlace(GpuDevice device) {
        return access().decision(device);
    }

    /// Says what the compositor decided for `device`, before anything asks.
    static void inPlace(GpuDevice device, InPlaceProbe.Decision decision) {
        access().decide(device, decision);
    }

    /// `pass`'s SDL pass, `destination`'s SDL texture checked to be on the
    /// pass's device, and what says the pass's frame has run.
    public static InPlaceCopy inPlaceCopy(CopyPass pass, GpuTexture destination) {
        return access().copy(pass, destination);
    }

    /// Wraps `device`; see [#device].
    protected abstract GpuDevice wrap(SdlGpuDevice device);

    /// Unwraps `texture`; see [#texture].
    protected abstract SdlGpuTexture unwrap(GpuTexture texture);

    /// Unwraps `device`; see [#sdl].
    protected abstract SdlGpuDevice unwrap(GpuDevice device);

    /// `device`'s decision; see [#inPlace(GpuDevice)].
    protected abstract InPlaceProbe.Decision decision(GpuDevice device);

    /// Sets `device`'s decision; see [#inPlace(GpuDevice, InPlaceProbe.Decision)].
    protected abstract void decide(GpuDevice device, InPlaceProbe.Decision decision);

    /// The copy; see [#inPlaceCopy].
    protected abstract InPlaceCopy copy(CopyPass pass, GpuTexture destination);

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
