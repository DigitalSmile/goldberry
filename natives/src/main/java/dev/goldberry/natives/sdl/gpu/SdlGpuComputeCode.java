package dev.goldberry.natives.sdl.gpu;

import java.util.Arrays;
import java.util.Objects;

import dev.goldberry.natives.sdl.gpu.enums.SdlGpuShaderFormat;

/// A compute shader's bytecode and what it declares: everything an
/// `SDL_GPUComputePipelineCreateInfo` carries.
///
/// SDL's bindings for a compute shader, in HLSL: samplers `t[n]`/`s[n]` in
/// space 0, then read-only storage textures and buffers after them in space
/// 0; read-write storage textures `u[n]` in space 1, then read-write storage
/// buffers after them; uniform buffers `b[n]` in space 2.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
///
/// @param format                   the bytecode's format
/// @param code                     the bytecode
/// @param entryPoint               the entry point's name
/// @param samplers                 how many textures it samples
/// @param readOnlyStorageTextures  how many storage textures it reads
/// @param readOnlyStorageBuffers   how many storage buffers it reads
/// @param readWriteStorageTextures how many storage textures it writes
/// @param readWriteStorageBuffers  how many storage buffers it writes
/// @param uniformBuffers           how many uniform blocks it reads
/// @param threadsX                 threads per workgroup along x, as the
///                                 shader's `numthreads` says
/// @param threadsY                 along y
/// @param threadsZ                 along z
public record SdlGpuComputeCode(
        SdlGpuShaderFormat format,
        byte[] code,
        String entryPoint,
        int samplers,
        int readOnlyStorageTextures,
        int readOnlyStorageBuffers,
        int readWriteStorageTextures,
        int readWriteStorageBuffers,
        int uniformBuffers,
        int threadsX,
        int threadsY,
        int threadsZ) {

    /// Checks the counts and that there is code, and copies it.
    public SdlGpuComputeCode {
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(entryPoint, "entryPoint");
        if (code.length == 0) {
            throw new IllegalArgumentException("a compute shader needs code");
        }
        if (samplers < 0
                || readOnlyStorageTextures < 0
                || readOnlyStorageBuffers < 0
                || readWriteStorageTextures < 0
                || readWriteStorageBuffers < 0
                || uniformBuffers < 0) {
            throw new IllegalArgumentException("a resource count is negative");
        }
        if (threadsX <= 0 || threadsY <= 0 || threadsZ <= 0) {
            throw new IllegalArgumentException(
                    "workgroup of " + threadsX + "x" + threadsY + "x" + threadsZ + " threads");
        }
        code = code.clone();
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
        return other instanceof SdlGpuComputeCode that
                && format == that.format
                && Arrays.equals(code, that.code)
                && entryPoint.equals(that.entryPoint)
                && samplers == that.samplers
                && readOnlyStorageTextures == that.readOnlyStorageTextures
                && readOnlyStorageBuffers == that.readOnlyStorageBuffers
                && readWriteStorageTextures == that.readWriteStorageTextures
                && readWriteStorageBuffers == that.readWriteStorageBuffers
                && uniformBuffers == that.uniformBuffers
                && threadsX == that.threadsX
                && threadsY == that.threadsY
                && threadsZ == that.threadsZ;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                format,
                Arrays.hashCode(code),
                entryPoint,
                samplers,
                readOnlyStorageTextures,
                readOnlyStorageBuffers,
                readWriteStorageTextures,
                readWriteStorageBuffers,
                uniformBuffers,
                threadsX,
                threadsY,
                threadsZ);
    }

    @Override
    public String toString() {
        return "SdlGpuComputeCode[" + format + ", " + code.length + " bytes, " + entryPoint + ", " + threadsX + "x"
                + threadsY + "x" + threadsZ + " threads]";
    }
}
