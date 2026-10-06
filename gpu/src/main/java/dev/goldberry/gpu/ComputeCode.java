package dev.goldberry.gpu;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/// A compute shader's bytecode in one or more formats, and what it declares:
/// what a [ComputePipeline] is made from.
///
/// Compiled offline as the graphics shaders are, from `<name>.comp.hlsl`, and
/// loaded by [#load] from `<name>.comp.spv`, `.dxil` and `.msl`. The counts
/// are the shader's bindings, in SDL's order: samplers, then read-only
/// storage textures and buffers, in space 0; read-write storage textures and
/// buffers in space 1; uniform blocks in space 2. The workgroup size is the
/// shader's `numthreads`, and [ComputePass#dispatch] counts workgroups.
///
/// @param bytecode                 the code, by format; at least one
/// @param samplers                 how many textures it samples
/// @param readOnlyStorageTextures  how many storage textures it reads
/// @param readOnlyStorageBuffers   how many storage buffers it reads
/// @param readWriteStorageTextures how many storage textures it writes
/// @param readWriteStorageBuffers  how many storage buffers it writes
/// @param uniformBuffers           how many uniform blocks it reads
/// @param threadsX                 threads per workgroup along x
/// @param threadsY                 along y
/// @param threadsZ                 along z
public record ComputeCode(
        Map<ShaderFormat, ShaderCode.Bytecode> bytecode,
        int samplers,
        int readOnlyStorageTextures,
        int readOnlyStorageBuffers,
        int readWriteStorageTextures,
        int readWriteStorageBuffers,
        int uniformBuffers,
        int threadsX,
        int threadsY,
        int threadsZ) {

    /// Checks the counts and that there is code, and copies the map.
    public ComputeCode {
        if (bytecode.isEmpty()) {
            throw new IllegalArgumentException("a compute shader needs code in at least one format");
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
        bytecode = Collections.unmodifiableMap(new EnumMap<>(bytecode));
    }

    /// Starts a compute shader of `threadsX` by `threadsY` by `threadsZ`
    /// threads per workgroup, with no bindings until the builder says
    /// otherwise.
    public static Builder builder(int threadsX, int threadsY, int threadsZ) {
        return new Builder(threadsX, threadsY, threadsZ);
    }

    /// Reads `baseName` plus each format's [ShaderFormat#extension] through
    /// `resources`, keeping every one that exists, into `builder`.
    ///
    /// ```java
    /// var builder = ComputeCode.builder(64, 1, 1).readWriteStorageBuffers(1);
    /// var code = ComputeCode.load("/shaders/particles.comp", builder, MyApp.class::getResourceAsStream);
    /// ```
    ///
    /// @throws IllegalArgumentException when no format exists
    /// @throws UncheckedIOException     when one exists and cannot be read
    public static ComputeCode load(String baseName, Builder builder, ShaderCode.Resources resources) {
        var found = false;
        for (var format : ShaderFormat.values()) {
            var name = baseName + format.extension();
            try (var in = resources.open(name)) {
                if (in != null) {
                    builder.code(format, in.readAllBytes());
                    found = true;
                }
            } catch (IOException e) {
                throw new UncheckedIOException("cannot read " + name, e);
            }
        }
        if (!found) {
            throw new IllegalArgumentException(
                    "no compute shader " + baseName + " in any of " + List.of(ShaderFormat.values()) + "'s extensions");
        }
        return builder.build();
    }

    /// The formats it carries.
    public Set<ShaderFormat> formats() {
        return bytecode.keySet();
    }

    /// The format a device that takes `accepted` runs it in, by
    /// [ShaderCode#PREFERENCE], or empty when it carries none of them.
    public Optional<ShaderFormat> formatFor(Set<ShaderFormat> accepted) {
        return ShaderCode.PREFERENCE.stream()
                .filter(bytecode::containsKey)
                .filter(accepted::contains)
                .findFirst();
    }

    /// Builds a [ComputeCode] format by format.
    public static final class Builder {

        private final int threadsX;
        private final int threadsY;
        private final int threadsZ;
        private final Map<ShaderFormat, ShaderCode.Bytecode> bytecode = new EnumMap<>(ShaderFormat.class);
        private int samplers;
        private int readOnlyStorageTextures;
        private int readOnlyStorageBuffers;
        private int readWriteStorageTextures;
        private int readWriteStorageBuffers;
        private int uniformBuffers;

        private Builder(int threadsX, int threadsY, int threadsZ) {
            this.threadsX = threadsX;
            this.threadsY = threadsY;
            this.threadsZ = threadsZ;
        }

        /// How many textures it samples.
        public Builder samplers(int count) {
            this.samplers = count;
            return this;
        }

        /// How many storage textures it reads.
        public Builder readOnlyStorageTextures(int count) {
            this.readOnlyStorageTextures = count;
            return this;
        }

        /// How many storage buffers it reads.
        public Builder readOnlyStorageBuffers(int count) {
            this.readOnlyStorageBuffers = count;
            return this;
        }

        /// How many storage textures it writes.
        public Builder readWriteStorageTextures(int count) {
            this.readWriteStorageTextures = count;
            return this;
        }

        /// How many storage buffers it writes.
        public Builder readWriteStorageBuffers(int count) {
            this.readWriteStorageBuffers = count;
            return this;
        }

        /// How many uniform blocks it reads.
        public Builder uniformBuffers(int count) {
            this.uniformBuffers = count;
            return this;
        }

        /// Adds `code` in `format`, with the format's default entry point.
        public Builder code(ShaderFormat format, byte[] code) {
            return code(format, code, format.defaultEntryPoint());
        }

        /// Adds `code` in `format`, entered at `entryPoint`.
        public Builder code(ShaderFormat format, byte[] code, String entryPoint) {
            Objects.requireNonNull(format, "format");
            bytecode.put(format, new ShaderCode.Bytecode(code, entryPoint));
            return this;
        }

        /// The code.
        ///
        /// @throws IllegalArgumentException as [ComputeCode]'s constructor does
        public ComputeCode build() {
            return new ComputeCode(
                    bytecode,
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
    }
}
