package dev.goldberry.natives.sdl.gpu.enums;

/// The texture formats the toolkit uses, with SDL's `SDL_GPUTextureFormat`
/// values.
///
/// Checked against the compiled library by the layout table, as every other
/// enumerator the bindings hard-code is: SDL's list is long, ordered, and has
/// been inserted into before.
///
/// A block-compressed format stores 4×4 texels in one block of 8 or 16 bytes,
/// so its sizes are counted in blocks: [#bytesPerBlock], [#blockWidth] and
/// [#blockHeight] answer for every format, a plain one's block being one
/// texel, and [#bytesPerPixel] refuses a compressed one.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
public enum SdlGpuTextureFormat {
    /// One 8-bit channel: a luma plane, or I420's chroma planes.
    R8_UNORM(2, 1),
    /// Two 8-bit channels: NV12's interleaved chroma plane.
    R8G8_UNORM(3, 2),
    /// Four 8-bit channels in memory order R, G, B, A.
    R8G8B8A8_UNORM(4, 4),
    /// One 16-bit channel: a 10-bit luma plane (P010, I010).
    R16_UNORM(5, 2),
    /// Two 16-bit channels: P010's interleaved chroma plane.
    R16G16_UNORM(6, 4),
    /// Four 8-bit channels in memory order B, G, R, A: what Blend2D paints, so
    /// the UI uploads without a conversion.
    B8G8R8A8_UNORM(12, 4),
    /// BC1: RGB and one bit of alpha in 8 bytes a 4×4 block.
    BC1_RGBA_UNORM(13, 4, 4, 8),
    /// BC3: RGBA in 16 bytes a 4×4 block, alpha interpolated.
    BC3_RGBA_UNORM(15, 4, 4, 16),
    /// BC5: two channels in 16 bytes a 4×4 block: a normal map's X and Y.
    BC5_RG_UNORM(17, 4, 4, 16),
    /// BC7: RGBA in 16 bytes a 4×4 block, the best of the BC formats.
    BC7_RGBA_UNORM(18, 4, 4, 16),
    /// Four 16-bit floats: a scene drawn in HDR, where bloom keeps values
    /// above one until the tonemap.
    R16G16B16A16_FLOAT(29, 8),
    /// One 32-bit float: what a sampled depth map is read back as.
    R32_FLOAT(30, 4),
    /// Three unsigned floats packed into 32 bits: a cheaper HDR target with no
    /// alpha.
    R11G11B10_UFLOAT(33, 4),
    /// [#R8G8B8A8_UNORM] holding sRGB-encoded colour, decoded to linear when
    /// sampled and encoded when written.
    R8G8B8A8_UNORM_SRGB(52, 4),
    /// [#B8G8R8A8_UNORM] holding sRGB-encoded colour.
    B8G8R8A8_UNORM_SRGB(53, 4),
    /// [#BC1_RGBA_UNORM] holding sRGB-encoded colour.
    BC1_RGBA_UNORM_SRGB(54, 4, 4, 8),
    /// [#BC3_RGBA_UNORM] holding sRGB-encoded colour.
    BC3_RGBA_UNORM_SRGB(56, 4, 4, 16),
    /// [#BC7_RGBA_UNORM] holding sRGB-encoded colour.
    BC7_RGBA_UNORM_SRGB(57, 4, 4, 16),
    /// A 16-bit depth target: enough for most scenes, and every device has it.
    D16_UNORM(58, 2),
    /// A 32-bit float depth target.
    D32_FLOAT(60, 4),
    /// ASTC: RGBA in 16 bytes a 4×4 block, which Apple's GPUs and most mobile
    /// ones decode.
    ASTC_4x4_UNORM(63, 4, 4, 16),
    /// [#ASTC_4x4_UNORM] holding sRGB-encoded colour.
    ASTC_4x4_UNORM_SRGB(77, 4, 4, 16);

    private final int value;
    private final int blockWidth;
    private final int blockHeight;
    private final int bytesPerBlock;

    SdlGpuTextureFormat(int value, int bytesPerPixel) {
        this(value, 1, 1, bytesPerPixel);
    }

    SdlGpuTextureFormat(int value, int blockWidth, int blockHeight, int bytesPerBlock) {
        this.value = value;
        this.blockWidth = blockWidth;
        this.blockHeight = blockHeight;
        this.bytesPerBlock = bytesPerBlock;
    }

    /// SDL's value.
    public int value() {
        return value;
    }

    /// How many bytes one pixel takes.
    ///
    /// @throws UnsupportedOperationException for a block-compressed format,
    ///                                       whose pixels have no size of their
    ///                                       own: ask [#bytesPerBlock]
    public int bytesPerPixel() {
        if (isCompressed()) {
            throw new UnsupportedOperationException(this + " is block-compressed: a block of " + blockWidth + "x"
                    + blockHeight + " texels takes " + bytesPerBlock + " bytes");
        }
        return bytesPerBlock;
    }

    /// How many bytes one block takes: one pixel's for a format that is not
    /// compressed.
    public int bytesPerBlock() {
        return bytesPerBlock;
    }

    /// How many texels wide a block is: 1 for a format that is not compressed.
    public int blockWidth() {
        return blockWidth;
    }

    /// How many texels tall a block is: 1 for a format that is not compressed.
    public int blockHeight() {
        return blockHeight;
    }

    /// Whether texels are stored in blocks of more than one, which the GPU
    /// decodes as it samples them.
    public boolean isCompressed() {
        return blockWidth > 1 || blockHeight > 1;
    }

    /// Whether the colour is sRGB-encoded: decoded to linear when sampled,
    /// filtered and blended in linear, and encoded again when written.
    public boolean isSrgb() {
        return name().endsWith("_SRGB");
    }

    /// Whether this is a depth format: one a render pass tests depth against,
    /// never a colour target.
    public boolean isDepth() {
        return this == D16_UNORM || this == D32_FLOAT;
    }

    /// Whether the channels are floats rather than normalised integers: a
    /// clear colour is taken as is, and a `Readback` hands back float bytes.
    public boolean isFloat() {
        return switch (this) {
            case R16G16B16A16_FLOAT, R32_FLOAT, R11G11B10_UFLOAT, D32_FLOAT -> true;
            default -> false;
        };
    }

    /// How many bytes one row of blocks `width` texels wide takes: the width
    /// rounded up to whole blocks.
    public long bytesPerRow(int width) {
        return (long) Math.ceilDiv(width, blockWidth) * bytesPerBlock;
    }

    /// How many bytes a `width` by `height` region takes, packed one row of
    /// blocks after another, both rounded up to whole blocks.
    public long byteSize(int width, int height) {
        return bytesPerRow(width) * Math.ceilDiv(height, blockHeight);
    }

    /// The name the C shim reports this constant under, for the layout probe.
    public String nativeName() {
        return "SDL_GPU_TEXTUREFORMAT_" + name();
    }
}
