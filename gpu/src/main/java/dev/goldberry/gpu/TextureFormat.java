package dev.goldberry.gpu;

import dev.goldberry.natives.sdl.gpu.enums.SdlGpuTextureFormat;

/// How a texture's pixels are stored.
///
/// The `UNORM` colour formats are the ones the toolkit itself uploads, draws
/// and reads back: the UI in [#B8G8R8A8_UNORM], video planes in the one- and
/// two-channel formats. The float colour formats are for a scene drawn in HDR,
/// where a pass keeps values above one for a later one to tonemap. The depth
/// formats are what a 3D scene tests against, and a depth texture made with
/// [TextureUsage#SAMPLER] is read by a later pass as a shadow map. Every device
/// SDL runs on takes all of them as sampled textures, and the colour formats
/// as colour targets; [GpuDevice#supports] answers for a pairing in doubt.
public enum TextureFormat {
    /// One 8-bit channel: a luma plane, a mask.
    R8_UNORM,
    /// Two 8-bit channels: NV12's interleaved chroma.
    R8G8_UNORM,
    /// Four 8-bit channels, in memory order red, green, blue, alpha.
    R8G8B8A8_UNORM,
    /// One 16-bit channel: a 10-bit luma plane.
    R16_UNORM,
    /// Two 16-bit channels: P010's interleaved chroma.
    R16G16_UNORM,
    /// Four 8-bit channels, in memory order blue, green, red, alpha: what the
    /// UI is painted in (`PixelFormat.BGRA32_PREMULTIPLIED`), so it uploads
    /// without a conversion.
    B8G8R8A8_UNORM,
    /// Four 16-bit floats: an HDR colour target. Read back as half floats,
    /// which `Float.float16ToFloat` decodes.
    R16G16B16A16_FLOAT,
    /// One 32-bit float: a sampled depth map read back, or a single-channel
    /// HDR quantity.
    R32_FLOAT,
    /// Three unsigned floats packed into 32 bits, no alpha: a cheaper HDR
    /// colour target. Not every device renders into it; ask
    /// [GpuDevice#supports].
    R11G11B10_UFLOAT,
    /// 16-bit depth.
    D16_UNORM,
    /// 32-bit float depth.
    D32_FLOAT;

    /// How many bytes one pixel takes.
    public int bytesPerPixel() {
        return sdl().bytesPerPixel();
    }

    /// Whether this is a depth format, which a render pass tests depth against
    /// and which is never a colour target or uploaded. Sampled and read back
    /// when made with [TextureUsage#SAMPLER].
    public boolean isDepth() {
        return sdl().isDepth();
    }

    /// Whether the channels are floats rather than normalised integers: a
    /// [Load.Clear] is taken as it is, past 1 and below 0, and [Readback#await]
    /// hands back float bytes.
    public boolean isFloat() {
        return sdl().isFloat();
    }

    SdlGpuTextureFormat sdl() {
        return switch (this) {
            case R8_UNORM -> SdlGpuTextureFormat.R8_UNORM;
            case R8G8_UNORM -> SdlGpuTextureFormat.R8G8_UNORM;
            case R8G8B8A8_UNORM -> SdlGpuTextureFormat.R8G8B8A8_UNORM;
            case R16_UNORM -> SdlGpuTextureFormat.R16_UNORM;
            case R16G16_UNORM -> SdlGpuTextureFormat.R16G16_UNORM;
            case B8G8R8A8_UNORM -> SdlGpuTextureFormat.B8G8R8A8_UNORM;
            case R16G16B16A16_FLOAT -> SdlGpuTextureFormat.R16G16B16A16_FLOAT;
            case R32_FLOAT -> SdlGpuTextureFormat.R32_FLOAT;
            case R11G11B10_UFLOAT -> SdlGpuTextureFormat.R11G11B10_UFLOAT;
            case D16_UNORM -> SdlGpuTextureFormat.D16_UNORM;
            case D32_FLOAT -> SdlGpuTextureFormat.D32_FLOAT;
        };
    }
}
