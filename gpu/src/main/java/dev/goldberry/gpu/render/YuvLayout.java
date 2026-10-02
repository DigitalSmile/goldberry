package dev.goldberry.gpu.render;

import java.util.List;

import dev.goldberry.natives.sdl.gpu.enums.SdlGpuTextureFormat;

/// How a picture's Y'CbCr planes are laid out: the four formats the media
/// engine hands over, each 4:2:0.
///
/// Each names the textures its planes are uploaded into, the shader that reads
/// them, and how a sampled texel becomes a code value normalised to the bit
/// depth: an `UNORM` texel is `stored / (2^bits - 1)` of its texture's width,
/// and what is stored differs.
public enum YuvLayout {
    /// 8-bit: a luma plane, then Cb and Cr interleaved at half size. Hardware
    /// decoders' 8-bit output.
    NV12(8, SdlGpuTextureFormat.R8_UNORM, SdlGpuTextureFormat.R8G8_UNORM, 1, 1.0),
    /// 8-bit: three planes, chroma at half size. Software decoders' output.
    I420(8, SdlGpuTextureFormat.R8_UNORM, SdlGpuTextureFormat.R8_UNORM, 2, 1.0),
    /// 10 bits in the high bits of 16: NV12's layout. Hardware decoders' 10-bit
    /// output. A code `c` is stored as `c << 6`.
    P010(10, SdlGpuTextureFormat.R16_UNORM, SdlGpuTextureFormat.R16G16_UNORM, 1, 65535.0 / 64 / 1023),
    /// 10 bits in the low bits of 16: I420's layout. dav1d's and VP9 profile
    /// 2's 10-bit output.
    I010(10, SdlGpuTextureFormat.R16_UNORM, SdlGpuTextureFormat.R16_UNORM, 2, 65535.0 / 1023);

    private final int bitDepth;
    private final SdlGpuTextureFormat lumaFormat;
    private final SdlGpuTextureFormat chromaFormat;
    private final int chromaPlanes;
    private final double sampleToCode;

    /// A layout of one luma plane and `chromaPlanes` chroma planes: 1 when Cb
    /// and Cr are interleaved, 2 when they are planes of their own.
    YuvLayout(
            int bitDepth,
            SdlGpuTextureFormat lumaFormat,
            SdlGpuTextureFormat chromaFormat,
            int chromaPlanes,
            double sampleToCode) {
        this.bitDepth = bitDepth;
        this.lumaFormat = lumaFormat;
        this.chromaFormat = chromaFormat;
        this.chromaPlanes = chromaPlanes;
        this.sampleToCode = sampleToCode;
    }

    /// Bits per code value.
    public int bitDepth() {
        return bitDepth;
    }

    /// The largest code value, `2^bitDepth - 1`.
    public int maxCode() {
        return (1 << bitDepth) - 1;
    }

    /// Each plane's texture format: the luma plane first, at full size, then
    /// the chroma planes at half size, rounded up.
    public List<SdlGpuTextureFormat> planeFormats() {
        return chromaPlanes == 1 ? List.of(lumaFormat, chromaFormat) : List.of(lumaFormat, chromaFormat, chromaFormat);
    }

    /// The fragment shader that reads the planes.
    public BuiltInShader shader() {
        return chromaPlanes == 1 ? BuiltInShader.YUV2_FRAGMENT : BuiltInShader.YUV3_FRAGMENT;
    }

    /// What a sampled texel is multiplied by to become a code value over
    /// [#maxCode]: 1 when the texture is as wide as the code.
    public double sampleToCode() {
        return sampleToCode;
    }

    /// The size of plane `index` of a `width` by `height` picture.
    public int planeWidth(int index, int width) {
        return index == 0 ? width : (width + 1) / 2;
    }

    /// The height of plane `index` of a `width` by `height` picture.
    public int planeHeight(int index, int height) {
        return index == 0 ? height : (height + 1) / 2;
    }
}
