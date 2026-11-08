package dev.goldberry.gpu.raytrace;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import dev.goldberry.gpu.GpuDevice;
import dev.goldberry.gpu.GpuTexture;
import dev.goldberry.gpu.TextureFormat;
import dev.goldberry.gpu.TextureSpec;
import dev.goldberry.gpu.TextureUsage;

/// The two textures a frame's lighting is traced into, by
/// [RayTracer#traceLighting(dev.goldberry.gpu.GpuFrame, RayScene, LightingRequest, LightingTargets)]:
/// [Lighting]'s two planes, kept on the GPU for a renderer's shader to sample.
///
/// Both are made to be sampled and written by compute ([#USAGES]), which
/// either tier needs: the software tier writes them as storage textures, and
/// the hardware tier uploads into them. [#create] makes a pair; a renderer
/// that resizes makes a new pair and closes the old.
///
/// @param shading      [Lighting#shading()]'s plane, an
///                     [TextureFormat#R8G8B8A8_UNORM] texture
/// @param reflectedIds [Lighting#reflectedIds()]'s plane, an
///                     [TextureFormat#R32_FLOAT] texture of the same size and
///                     device
public record LightingTargets(GpuTexture shading, GpuTexture reflectedIds) implements AutoCloseable {

    /// What both textures are made for.
    public static final Set<TextureUsage> USAGES =
            Set.copyOf(EnumSet.of(TextureUsage.SAMPLER, TextureUsage.COMPUTE_STORAGE_WRITE));

    /// @throws IllegalArgumentException when a texture is of another format,
    ///                                  not made for [#USAGES], or the two
    ///                                  differ in size or device
    public LightingTargets {
        Objects.requireNonNull(shading, "shading");
        Objects.requireNonNull(reflectedIds, "reflectedIds");
        require(shading, TextureFormat.R8G8B8A8_UNORM);
        require(reflectedIds, TextureFormat.R32_FLOAT);
        if (shading.width() != reflectedIds.width() || shading.height() != reflectedIds.height()) {
            throw new IllegalArgumentException("the shading is " + shading.width() + "×" + shading.height()
                    + ", and the reflected ids " + reflectedIds.width() + "×" + reflectedIds.height());
        }
        if (shading.device() != reflectedIds.device()) {
            throw new IllegalArgumentException(shading + " and " + reflectedIds + " are two devices'");
        }
    }

    private static void require(GpuTexture texture, TextureFormat format) {
        if (texture.format() != format) {
            throw new IllegalArgumentException(texture + " is " + texture.format() + ", not " + format);
        }
        if (!texture.usages().containsAll(USAGES)) {
            throw new IllegalArgumentException(texture + " is made for " + texture.usages() + ", not " + USAGES);
        }
    }

    /// A pair of `width` by `height` textures on `device`.
    ///
    /// @throws IllegalArgumentException when a size is below one
    /// @throws dev.goldberry.gpu.GpuException when the driver refuses
    public static LightingTargets create(GpuDevice device, int width, int height) {
        Objects.requireNonNull(device, "device");
        var shading = device.createTexture(new TextureSpec(TextureFormat.R8G8B8A8_UNORM, width, height, USAGES));
        try {
            var ids = device.createTexture(new TextureSpec(TextureFormat.R32_FLOAT, width, height, USAGES));
            shading.setDebugName("ray tracing: shading");
            ids.setDebugName("ray tracing: reflected ids");
            return new LightingTargets(shading, ids);
        } catch (RuntimeException e) {
            shading.close();
            throw e;
        }
    }

    /// Their width, in pixels.
    public int width() {
        return shading.width();
    }

    /// Their height, in pixels.
    public int height() {
        return shading.height();
    }

    /// Closes both. Idempotent.
    @Override
    public void close() {
        shading.close();
        reflectedIds.close();
    }
}
