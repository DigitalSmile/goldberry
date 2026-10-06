package dev.goldberry.natives.sdl.gpu;

import java.util.Arrays;
import java.util.Objects;

import dev.goldberry.natives.sdl.gpu.enums.SdlGpuShaderFormat;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuShaderStage;

/// A shader's bytecode in one format, and what it declares.
///
/// SDL cannot read a shader's resources out of its bytecode, so the counts are
/// the caller's to state; a count that is wrong is a validation error on a debug
/// device and undefined behaviour on a release one.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
///
/// @param stage          the stage it runs in
/// @param format         the bytecode's format
/// @param code           the bytecode (for MSL, the source in UTF-8); copied
/// @param entryPoint     the function to run: `main` for SPIR-V and DXIL,
///                       `main0` for MSL made by SPIRV-Cross
/// @param samplers       how many textures it samples
/// @param uniformBuffers how many uniform blocks it reads
/// @param storageTextures how many storage textures it reads
/// @param storageBuffers how many storage buffers it reads
public record SdlGpuShaderCode(
        SdlGpuShaderStage stage,
        SdlGpuShaderFormat format,
        byte[] code,
        String entryPoint,
        int samplers,
        int uniformBuffers,
        int storageTextures,
        int storageBuffers) {

    /// Checks the counts and that there is code, and copies it.
    public SdlGpuShaderCode {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(entryPoint, "entryPoint");
        if (code.length == 0) {
            throw new IllegalArgumentException("a shader needs code");
        }
        if (samplers < 0 || uniformBuffers < 0 || storageTextures < 0 || storageBuffers < 0) {
            throw new IllegalArgumentException("samplers " + samplers + ", uniform buffers " + uniformBuffers
                    + ", storage textures " + storageTextures + ", storage buffers " + storageBuffers);
        }
        code = code.clone();
    }

    /// Code that reads no storage textures or buffers: the toolkit's own.
    public SdlGpuShaderCode(
            SdlGpuShaderStage stage,
            SdlGpuShaderFormat format,
            byte[] code,
            String entryPoint,
            int samplers,
            int uniformBuffers) {
        this(stage, format, code, entryPoint, samplers, uniformBuffers, 0, 0);
    }

    /// The bytecode; a copy.
    @Override
    public byte[] code() {
        return code.clone();
    }

    /// How many bytes of bytecode.
    public int size() {
        return code.length;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SdlGpuShaderCode that
                && stage == that.stage
                && format == that.format
                && Arrays.equals(code, that.code)
                && entryPoint.equals(that.entryPoint)
                && samplers == that.samplers
                && uniformBuffers == that.uniformBuffers
                && storageTextures == that.storageTextures
                && storageBuffers == that.storageBuffers;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                stage,
                format,
                Arrays.hashCode(code),
                entryPoint,
                samplers,
                uniformBuffers,
                storageTextures,
                storageBuffers);
    }

    @Override
    public String toString() {
        return "SdlGpuShaderCode[" + stage + ", " + format + ", " + code.length + " bytes, " + entryPoint
                + ", samplers " + samplers + ", uniform buffers " + uniformBuffers + "]";
    }
}
