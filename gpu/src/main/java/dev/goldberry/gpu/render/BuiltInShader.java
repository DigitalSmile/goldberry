package dev.goldberry.gpu.render;

import dev.goldberry.natives.sdl.gpu.enums.SdlGpuShaderStage;

/// The toolkit's shaders: one per file in `src/main/shaders`, compiled by
/// `:gpu:compileShaders` into SPIR-V, DXIL and MSL.
///
/// SDL cannot read what a shader declares out of its bytecode, so it is stated
/// here, beside the name, and a wrong count is a validation error on a debug
/// device. `ShaderManifestTest` checks every entry has its three files and every
/// source has an entry.
public enum BuiltInShader {
    /// Places a quad from one uniform block of eight floats: the destination in
    /// normalised device coordinates, the source in texture coordinates ([Quad]).
    QUAD_VERTEX("quad.vert", SdlGpuShaderStage.VERTEX, 0, 1),
    /// Samples one texture.
    TEXTURE_FRAGMENT("texture.frag", SdlGpuShaderStage.FRAGMENT, 1, 0),
    /// Fills with one premultiplied colour from one uniform block of four floats.
    SOLID_FRAGMENT("solid.frag", SdlGpuShaderStage.FRAGMENT, 0, 1),
    /// Y'CbCr to RGB from a luma and an interleaved chroma plane: NV12, P010
    /// ([YuvConversion]).
    YUV2_FRAGMENT("yuv2.frag", SdlGpuShaderStage.FRAGMENT, 2, 1),
    /// Y'CbCr to RGB from three planes: I420, I010 ([YuvConversion]).
    YUV3_FRAGMENT("yuv3.frag", SdlGpuShaderStage.FRAGMENT, 3, 1),
    /// [#YUV2_FRAGMENT], then HDR or wide-gamut R'G'B' to SDR BT.709 from a
    /// second uniform block ([ToneMapping]).
    YUV2_HDR_FRAGMENT("yuv2hdr.frag", SdlGpuShaderStage.FRAGMENT, 2, 2),
    /// [#YUV3_FRAGMENT], then HDR or wide-gamut R'G'B' to SDR BT.709 from a
    /// second uniform block ([ToneMapping]).
    YUV3_HDR_FRAGMENT("yuv3hdr.frag", SdlGpuShaderStage.FRAGMENT, 3, 2),
    /// FSR 1's EASU: a picture upscaled along its edges, from one uniform
    /// block of sixteen floats ([dev.goldberry.gpu.upscale.fsr.Fsr1#easu]).
    EASU_FRAGMENT("easu.frag", SdlGpuShaderStage.FRAGMENT, 1, 1),
    /// FSR 1's RCAS: a picture sharpened within its neighbours' range, from
    /// one uniform block of four floats ([dev.goldberry.gpu.upscale.fsr.Fsr1#rcas]).
    RCAS_FRAGMENT("rcas.frag", SdlGpuShaderStage.FRAGMENT, 1, 1);

    private final String fileName;
    private final SdlGpuShaderStage stage;
    private final int samplers;
    private final int uniformBuffers;

    BuiltInShader(String fileName, SdlGpuShaderStage stage, int samplers, int uniformBuffers) {
        this.fileName = fileName;
        this.stage = stage;
        this.samplers = samplers;
        this.uniformBuffers = uniformBuffers;
    }

    /// The name its source and bytecode share: `quad.vert` for
    /// `quad.vert.hlsl`, `quad.vert.spv`, `quad.vert.dxil` and `quad.vert.msl`.
    public String fileName() {
        return fileName;
    }

    /// The stage it runs in.
    public SdlGpuShaderStage stage() {
        return stage;
    }

    /// How many textures it samples.
    public int samplers() {
        return samplers;
    }

    /// How many uniform blocks it reads.
    public int uniformBuffers() {
        return uniformBuffers;
    }
}
