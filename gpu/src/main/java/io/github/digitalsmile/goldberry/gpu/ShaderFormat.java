package io.github.digitalsmile.goldberry.gpu;

import java.util.EnumSet;
import java.util.Set;

import io.github.digitalsmile.goldberry.natives.sdl.gpu.enums.SdlGpuShaderFormat;

/// The bytecode formats a device takes. Each driver takes its own family:
/// Vulkan SPIR-V, Metal MSL or a metallib, Direct3D 12 DXIL or DXBC. A
/// [ShaderCode] that carries one per family runs everywhere.
///
/// The toolkit's own shaders are HLSL compiled into [#SPIRV], [#DXIL] and
/// [#MSL] (`docs/gpu-plan.md`, D7); an application's can be made the same way,
/// and the file extensions here are the ones that build writes.
public enum ShaderFormat {
    /// SPIR-V, for Vulkan.
    SPIRV(".spv", "main"),
    /// DXBC shader model 5.1, for Direct3D 12.
    DXBC(".dxbc", "main"),
    /// DXIL shader model 6.0, for Direct3D 12.
    DXIL(".dxil", "main"),
    /// Metal Shading Language source, compiled by the driver. Its entry point
    /// is `main0` when SPIRV-Cross made it, since `main` is reserved in MSL.
    MSL(".msl", "main0"),
    /// A precompiled Metal library.
    METALLIB(".metallib", "main0");

    private final String extension;
    private final String defaultEntryPoint;

    ShaderFormat(String extension, String defaultEntryPoint) {
        this.extension = extension;
        this.defaultEntryPoint = defaultEntryPoint;
    }

    /// The file extension this format is stored under, with its dot:
    /// [ShaderCode#load] looks for `name` plus it.
    public String extension() {
        return extension;
    }

    /// The function run when none is named: `main`, or `main0` for Metal,
    /// which is what DXC and SPIRV-Cross produce from an HLSL `main`.
    public String defaultEntryPoint() {
        return defaultEntryPoint;
    }

    SdlGpuShaderFormat sdl() {
        return switch (this) {
            case SPIRV -> SdlGpuShaderFormat.SPIRV;
            case DXBC -> SdlGpuShaderFormat.DXBC;
            case DXIL -> SdlGpuShaderFormat.DXIL;
            case MSL -> SdlGpuShaderFormat.MSL;
            case METALLIB -> SdlGpuShaderFormat.METALLIB;
        };
    }

    static ShaderFormat of(SdlGpuShaderFormat format) {
        return switch (format) {
            case SPIRV -> SPIRV;
            case DXBC -> DXBC;
            case DXIL -> DXIL;
            case MSL -> MSL;
            case METALLIB -> METALLIB;
        };
    }

    static Set<ShaderFormat> of(Set<SdlGpuShaderFormat> formats) {
        var mapped = EnumSet.noneOf(ShaderFormat.class);
        for (var format : formats) {
            mapped.add(of(format));
        }
        return mapped;
    }
}
