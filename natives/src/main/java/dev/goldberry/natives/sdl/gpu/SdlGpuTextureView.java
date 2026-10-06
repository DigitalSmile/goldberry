package dev.goldberry.natives.sdl.gpu;

import java.util.Objects;

/// One mip level of one layer of a texture, as a render pass's colour target or
/// a blit's destination.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
///
/// @param texture the texture
/// @param level   the mip level, from 0
/// @param layer   the layer, from 0
public record SdlGpuTextureView(SdlGpuTexture texture, int level, int layer) implements SdlGpuTarget {

    /// Checks the level and layer exist.
    ///
    /// @throws IllegalArgumentException when either is out of range
    public SdlGpuTextureView {
        Objects.requireNonNull(texture, "texture");
        texture.requireSubresource(level, layer);
    }

    /// The width of the level, in texels.
    @Override
    public int width() {
        return texture.levelWidth(level);
    }

    /// The height of the level, in texels.
    @Override
    public int height() {
        return texture.levelHeight(level);
    }
}
