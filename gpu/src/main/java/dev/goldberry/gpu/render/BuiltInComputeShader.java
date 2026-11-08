package dev.goldberry.gpu.render;

import dev.goldberry.gpu.ComputeCode;

/// The toolkit's compute shaders: one per `.comp.hlsl` file in
/// `src/main/shaders`, compiled by `:gpu:compileShaders` into SPIR-V, DXIL and
/// MSL beside [BuiltInShader]'s, and loaded as an application's compute
/// shaders are, through [ComputeCode#load].
///
/// What each declares is stated here, as [BuiltInShader] states a graphics
/// shader's, since SDL cannot read it from the bytecode. `ShaderManifestTest`
/// checks every entry has its three files and every source has an entry.
public enum BuiltInComputeShader {
    /// The software ray tracer's records or lighting into a byte buffer, a
    /// thread a pixel: the scene's five buffers, the rays' constants.
    RAY_TRACE("trace.comp", 5, 0, 1, 1, 8, 8),
    /// The software ray tracer's shadow mask, a thread a word of four pixels:
    /// the scene's five buffers, the rays' constants, then the dispatch's grid.
    RAY_SHADOWS("shadows.comp", 5, 0, 1, 2, 64, 1),
    /// The software ray tracer's lighting into two storage textures, a thread a
    /// pixel.
    RAY_LIGHTING("lighting.comp", 5, 2, 0, 1, 8, 8);

    private final String fileName;
    private final int readOnlyStorageBuffers;
    private final int readWriteStorageTextures;
    private final int readWriteStorageBuffers;
    private final int uniformBuffers;
    private final int threadsX;
    private final int threadsY;

    BuiltInComputeShader(
            String fileName,
            int readOnlyStorageBuffers,
            int readWriteStorageTextures,
            int readWriteStorageBuffers,
            int uniformBuffers,
            int threadsX,
            int threadsY) {
        this.fileName = fileName;
        this.readOnlyStorageBuffers = readOnlyStorageBuffers;
        this.readWriteStorageTextures = readWriteStorageTextures;
        this.readWriteStorageBuffers = readWriteStorageBuffers;
        this.uniformBuffers = uniformBuffers;
        this.threadsX = threadsX;
        this.threadsY = threadsY;
    }

    /// The name its source and bytecode share: `trace.comp` for
    /// `trace.comp.hlsl`, `trace.comp.spv`, `trace.comp.dxil` and
    /// `trace.comp.msl`.
    public String fileName() {
        return fileName;
    }

    /// Threads per workgroup along x, its `numthreads`.
    public int threadsX() {
        return threadsX;
    }

    /// Threads per workgroup along y.
    public int threadsY() {
        return threadsY;
    }

    /// Its code in every format it ships in, with what it declares.
    ///
    /// @throws IllegalArgumentException when no format of it is in the jar
    public ComputeCode code() {
        var builder = ComputeCode.builder(threadsX, threadsY, 1)
                .readOnlyStorageBuffers(readOnlyStorageBuffers)
                .readWriteStorageTextures(readWriteStorageTextures)
                .readWriteStorageBuffers(readWriteStorageBuffers)
                .uniformBuffers(uniformBuffers);
        return ComputeCode.load(
                ShaderLibrary.DIRECTORY + fileName, builder, BuiltInComputeShader.class::getResourceAsStream);
    }
}
