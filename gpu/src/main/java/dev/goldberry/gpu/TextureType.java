package dev.goldberry.gpu;

import dev.goldberry.natives.sdl.gpu.enums.SdlGpuTextureType;

/// What shape a texture is: what a shader declares it as, and what its layers
/// and depth mean. [TextureSpec]'s factories choose it.
public enum TextureType {
    /// One 2D image of one layer: `Texture2D` in HLSL.
    TWO_D,
    /// Layers of 2D images of one size, sampled by index: `Texture2DArray`.
    /// Rendered into and uploaded to a layer at a time.
    TWO_D_ARRAY,
    /// Six square faces, sampled by direction: `TextureCube`. Its layers are
    /// its faces, in [CubeFace]'s order, each rendered into and uploaded to on
    /// its own through [GpuTexture#face].
    CUBE,
    /// A volume of `depth` slices, filtered across all three axes:
    /// `Texture3D`. Uploaded to a slice at a time through [GpuTexture#slice],
    /// and sampled; never rendered into.
    THREE_D;

    SdlGpuTextureType sdl() {
        return switch (this) {
            case TWO_D -> SdlGpuTextureType.TWO_D;
            case TWO_D_ARRAY -> SdlGpuTextureType.TWO_D_ARRAY;
            case CUBE -> SdlGpuTextureType.CUBE;
            case THREE_D -> SdlGpuTextureType.THREE_D;
        };
    }
}
