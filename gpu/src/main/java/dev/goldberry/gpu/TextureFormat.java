package dev.goldberry.gpu;

import dev.goldberry.natives.sdl.gpu.enums.SdlGpuTextureFormat;
import dev.goldberry.render.model.PhysicalSize;

/// How a texture's pixels are stored.
///
/// The `UNORM` colour formats are the ones the toolkit itself uploads, draws
/// and reads back: the UI in [#B8G8R8A8_UNORM], video planes in the one- and
/// two-channel formats. The float colour formats are for a scene drawn in HDR,
/// where a pass keeps values above one for a later one to tonemap. The depth
/// formats are what a 3D scene tests against, and a depth texture made with
/// [TextureUsage#SAMPLER] is read by a later pass as a shadow map. Every device
/// SDL runs on takes all of these as sampled textures, and the colour formats
/// as colour targets; [GpuDevice#supports] answers for a pairing in doubt.
///
/// The `_SRGB` formats hold sRGB-encoded colour, as images are stored: a shader
/// reads linear values, filtering and mipmap generation average in linear, and
/// a render pass encodes what it writes ([#isSrgb]).
///
/// The block-compressed formats (`BC`, `ASTC`) store a block of 4×4 texels in
/// 8 or 16 bytes, which the GPU decodes as it samples ([#isCompressed]). They
/// are only sampled and uploaded to: a compressed texture is never rendered
/// into, used as storage, mipmapped by the GPU, or read back. Its sizes are
/// counted in blocks ([#blockSize], [#bytesPerBlock], [#bytesPerRow]). The BC
/// formats are what desktop GPUs decode and ASTC what Apple's and mobile ones
/// do; ask [GpuDevice#supports] before making one.
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
    /// [#R8G8B8A8_UNORM] holding sRGB-encoded colour: a colour image, sampled
    /// as linear, and a target whose writes are encoded.
    R8G8B8A8_UNORM_SRGB,
    /// [#B8G8R8A8_UNORM] holding sRGB-encoded colour.
    B8G8R8A8_UNORM_SRGB,
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
    /// BC1: colour and one bit of alpha, 8 bytes a 4×4 block.
    BC1_RGBA_UNORM,
    /// [#BC1_RGBA_UNORM] holding sRGB-encoded colour.
    BC1_RGBA_UNORM_SRGB,
    /// BC3: colour and smooth alpha, 16 bytes a 4×4 block.
    BC3_RGBA_UNORM,
    /// [#BC3_RGBA_UNORM] holding sRGB-encoded colour.
    BC3_RGBA_UNORM_SRGB,
    /// BC5: two channels, 16 bytes a 4×4 block: a normal map's X and Y.
    BC5_RG_UNORM,
    /// BC7: colour and alpha at the best quality of the BC formats, 16 bytes a
    /// 4×4 block.
    BC7_RGBA_UNORM,
    /// [#BC7_RGBA_UNORM] holding sRGB-encoded colour.
    BC7_RGBA_UNORM_SRGB,
    /// ASTC 4×4: colour and alpha, 16 bytes a 4×4 block, which Apple's GPUs
    /// and most mobile ones decode and most desktop ones do not.
    ASTC_4x4_UNORM,
    /// [#ASTC_4x4_UNORM] holding sRGB-encoded colour.
    ASTC_4x4_UNORM_SRGB,
    /// 16-bit depth.
    D16_UNORM,
    /// 32-bit float depth.
    D32_FLOAT;

    /// How many bytes one pixel takes.
    ///
    /// @throws UnsupportedOperationException for a block-compressed format,
    ///                                       whose pixels have no size of their
    ///                                       own: ask [#bytesPerBlock]
    public int bytesPerPixel() {
        return sdl().bytesPerPixel();
    }

    /// How many bytes one block takes: one pixel's, for a format that is not
    /// compressed.
    public int bytesPerBlock() {
        return sdl().bytesPerBlock();
    }

    /// The size of a block in texels: 4×4 for a compressed format, and 1×1 for
    /// the rest.
    public PhysicalSize blockSize() {
        var sdl = sdl();
        return new PhysicalSize(sdl.blockWidth(), sdl.blockHeight());
    }

    /// How many bytes one row of blocks `width` texels wide takes, the width
    /// rounded up to whole blocks: a tightly packed row of an upload.
    ///
    /// @throws IllegalArgumentException when `width` is not positive
    public long bytesPerRow(int width) {
        if (width <= 0) {
            throw new IllegalArgumentException("a row of " + width + " texels");
        }
        return sdl().bytesPerRow(width);
    }

    /// How many bytes a `width` by `height` image takes, tightly packed: rows
    /// of whole blocks, as many as cover the height.
    ///
    /// @throws IllegalArgumentException when either is not positive
    public long byteSize(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("an image " + width + "x" + height);
        }
        return sdl().byteSize(width, height);
    }

    /// Whether texels are stored in blocks of more than one, which the GPU
    /// decodes as it samples: sampled and uploaded to, and nothing else.
    public boolean isCompressed() {
        return sdl().isCompressed();
    }

    /// Whether the colour is sRGB-encoded: decoded to linear when sampled,
    /// filtered and blended in linear, and encoded when written.
    public boolean isSrgb() {
        return sdl().isSrgb();
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
            case R8G8B8A8_UNORM_SRGB -> SdlGpuTextureFormat.R8G8B8A8_UNORM_SRGB;
            case B8G8R8A8_UNORM_SRGB -> SdlGpuTextureFormat.B8G8R8A8_UNORM_SRGB;
            case R16G16B16A16_FLOAT -> SdlGpuTextureFormat.R16G16B16A16_FLOAT;
            case R32_FLOAT -> SdlGpuTextureFormat.R32_FLOAT;
            case R11G11B10_UFLOAT -> SdlGpuTextureFormat.R11G11B10_UFLOAT;
            case BC1_RGBA_UNORM -> SdlGpuTextureFormat.BC1_RGBA_UNORM;
            case BC1_RGBA_UNORM_SRGB -> SdlGpuTextureFormat.BC1_RGBA_UNORM_SRGB;
            case BC3_RGBA_UNORM -> SdlGpuTextureFormat.BC3_RGBA_UNORM;
            case BC3_RGBA_UNORM_SRGB -> SdlGpuTextureFormat.BC3_RGBA_UNORM_SRGB;
            case BC5_RG_UNORM -> SdlGpuTextureFormat.BC5_RG_UNORM;
            case BC7_RGBA_UNORM -> SdlGpuTextureFormat.BC7_RGBA_UNORM;
            case BC7_RGBA_UNORM_SRGB -> SdlGpuTextureFormat.BC7_RGBA_UNORM_SRGB;
            case ASTC_4x4_UNORM -> SdlGpuTextureFormat.ASTC_4x4_UNORM;
            case ASTC_4x4_UNORM_SRGB -> SdlGpuTextureFormat.ASTC_4x4_UNORM_SRGB;
            case D16_UNORM -> SdlGpuTextureFormat.D16_UNORM;
            case D32_FLOAT -> SdlGpuTextureFormat.D32_FLOAT;
        };
    }
}
