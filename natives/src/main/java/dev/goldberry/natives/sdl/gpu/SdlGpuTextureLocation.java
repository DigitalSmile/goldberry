package dev.goldberry.natives.sdl.gpu;

import java.util.Objects;

/// A texel of one mip level of one layer of a texture: the corner a copy from
/// texture to texture starts at, at either end. For a 3D texture the layer is
/// the depth slice.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
///
/// @param texture the texture
/// @param level   the mip level
/// @param layer   the layer, cube face or depth slice
/// @param x       the texel's column in the level
/// @param y       the texel's row in the level
public record SdlGpuTextureLocation(SdlGpuTexture texture, int level, int layer, int x, int y) {

    /// Checks the numbers are not negative.
    public SdlGpuTextureLocation {
        Objects.requireNonNull(texture, "texture");
        if (level < 0 || layer < 0 || x < 0 || y < 0) {
            throw new IllegalArgumentException(
                    "level " + level + ", layer " + layer + ", texel " + x + "," + y + " of " + texture);
        }
    }

    /// The top left texel of level 0 of layer 0 of `texture`.
    public static SdlGpuTextureLocation of(SdlGpuTexture texture) {
        return new SdlGpuTextureLocation(texture, 0, 0, 0, 0);
    }
}
