package io.github.digitalsmile.goldberry.gpu;

import io.github.digitalsmile.goldberry.natives.sdl.gpu.enums.SdlGpuTextureFormat;

/// How a texture's pixels are stored.
///
/// The colour formats are the ones the toolkit itself uploads, draws and reads
/// back: the UI in [#B8G8R8A8_UNORM], video planes in the one- and two-channel
/// formats. The depth formats are what a 3D scene tests against. Every device
/// SDL runs on takes all of them as sampled textures and colour targets, except
/// that a depth format is only ever a depth target.
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
    /// 16-bit depth.
    D16_UNORM,
    /// 32-bit float depth.
    D32_FLOAT;

    /// How many bytes one pixel takes.
    public int bytesPerPixel() {
        return sdl().bytesPerPixel();
    }

    /// Whether this is a depth format, which a render pass tests depth against
    /// and which is never a colour target, sampled, uploaded or read back.
    public boolean isDepth() {
        return sdl().isDepth();
    }

    SdlGpuTextureFormat sdl() {
        return switch (this) {
            case R8_UNORM -> SdlGpuTextureFormat.R8_UNORM;
            case R8G8_UNORM -> SdlGpuTextureFormat.R8G8_UNORM;
            case R8G8B8A8_UNORM -> SdlGpuTextureFormat.R8G8B8A8_UNORM;
            case R16_UNORM -> SdlGpuTextureFormat.R16_UNORM;
            case R16G16_UNORM -> SdlGpuTextureFormat.R16G16_UNORM;
            case B8G8R8A8_UNORM -> SdlGpuTextureFormat.B8G8R8A8_UNORM;
            case D16_UNORM -> SdlGpuTextureFormat.D16_UNORM;
            case D32_FLOAT -> SdlGpuTextureFormat.D32_FLOAT;
        };
    }
}
