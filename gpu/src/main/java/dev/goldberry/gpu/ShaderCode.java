package dev.goldberry.gpu;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/// A shader's bytecode, in as many [ShaderFormat]s as it was compiled to, and
/// what it declares.
///
/// A device takes one family of formats, so a shader that should run on every
/// platform carries SPIR-V, DXIL and MSL, as the toolkit's own do
/// (`docs/gpu-plan.md`, D7). [GpuDevice#createShader] picks the one the device
/// takes and names the missing one when there is none.
///
/// SDL cannot read what a shader declares out of its bytecode, so the counts
/// are stated here. A wrong count is a validation error on a debug device and
/// undefined behaviour on a release one.
///
/// @param stage          the stage it runs in
/// @param bytecode       the code per format; at least one
/// @param samplers       how many textures it samples
/// @param uniformBuffers how many uniform blocks it reads
public record ShaderCode(ShaderStage stage, Map<ShaderFormat, Bytecode> bytecode, int samplers, int uniformBuffers) {

    /// The order formats are chosen in when a device takes more than one:
    /// precompiled Metal first, then what the toolkit ships.
    static final List<ShaderFormat> PREFERENCE =
            List.of(ShaderFormat.METALLIB, ShaderFormat.MSL, ShaderFormat.SPIRV, ShaderFormat.DXIL, ShaderFormat.DXBC);

    /// One format's code.
    ///
    /// @param code       the bytecode, or for MSL the source in UTF-8; copied
    /// @param entryPoint the function to run
    // The array is copied in and out, and equals and hashCode compare its
    // contents, which is what the check is about.
    @SuppressWarnings("ArrayRecordComponent")
    public record Bytecode(byte[] code, String entryPoint) {

        /// Checks there is code and an entry point, and copies the code.
        public Bytecode {
            Objects.requireNonNull(entryPoint, "entryPoint");
            if (code.length == 0) {
                throw new IllegalArgumentException("a shader needs code");
            }
            if (entryPoint.isBlank()) {
                throw new IllegalArgumentException("a shader needs an entry point");
            }
            code = code.clone();
        }

        /// The code; a copy.
        @Override
        public byte[] code() {
            return code.clone();
        }

        /// How many bytes of code.
        public int size() {
            return code.length;
        }

        /// The code itself, for this module, which does not change it.
        byte[] codeUnsafe() {
            return code;
        }

        @Override
        public boolean equals(@Nullable Object other) {
            return other instanceof Bytecode that
                    && Arrays.equals(code, that.code)
                    && entryPoint.equals(that.entryPoint);
        }

        @Override
        public int hashCode() {
            return 31 * Arrays.hashCode(code) + entryPoint.hashCode();
        }

        @Override
        public String toString() {
            return "Bytecode[" + code.length + " bytes, " + entryPoint + "]";
        }
    }

    /// Where [#load] reads a file from: typically `MyApp.class::getResourceAsStream`,
    /// which looks in the caller's own module, so its package need not be
    /// opened to this one.
    @FunctionalInterface
    public interface Resources {

        /// The stream of the resource `name`, or null when there is none.
        ///
        /// @throws IOException when it exists and cannot be opened
        @Nullable
        InputStream open(String name) throws IOException;
    }

    /// Checks the stage, the counts and that there is code, and copies the map.
    public ShaderCode {
        Objects.requireNonNull(stage, "stage");
        if (bytecode.isEmpty()) {
            throw new IllegalArgumentException("a shader needs code in at least one format");
        }
        if (samplers < 0 || uniformBuffers < 0) {
            throw new IllegalArgumentException("samplers " + samplers + ", uniform buffers " + uniformBuffers);
        }
        bytecode = Collections.unmodifiableMap(new EnumMap<>(bytecode));
    }

    /// Starts a shader for `stage`, with no samplers and no uniform blocks
    /// until the builder says otherwise.
    public static Builder builder(ShaderStage stage) {
        return new Builder(stage);
    }

    /// Reads `baseName` plus each format's [ShaderFormat#extension] through
    /// `resources`, and keeps every one that exists, with the format's
    /// [ShaderFormat#defaultEntryPoint].
    ///
    /// `ShaderCode.load(ShaderStage.VERTEX, "/shaders/cube.vert", 0, 1, MyApp.class::getResourceAsStream)`
    /// reads `cube.vert.spv`, `cube.vert.dxil`, `cube.vert.msl` and the others.
    ///
    /// @throws IllegalArgumentException when no format exists
    /// @throws UncheckedIOException     when one exists and cannot be read
    public static ShaderCode load(
            ShaderStage stage, String baseName, int samplers, int uniformBuffers, Resources resources) {
        var builder = builder(stage).samplers(samplers).uniformBuffers(uniformBuffers);
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
                    "no shader " + baseName + " in any of " + List.of(ShaderFormat.values()) + "'s extensions");
        }
        return builder.build();
    }

    /// The formats it carries.
    public Set<ShaderFormat> formats() {
        return bytecode.keySet();
    }

    /// The format a device that takes `accepted` runs it in, by [#PREFERENCE],
    /// or empty when it carries none of them.
    public Optional<ShaderFormat> formatFor(Set<ShaderFormat> accepted) {
        return PREFERENCE.stream()
                .filter(bytecode::containsKey)
                .filter(accepted::contains)
                .findFirst();
    }

    /// Builds a [ShaderCode] format by format.
    public static final class Builder {

        private final ShaderStage stage;
        private final Map<ShaderFormat, Bytecode> bytecode = new EnumMap<>(ShaderFormat.class);
        private int samplers;
        private int uniformBuffers;

        private Builder(ShaderStage stage) {
            this.stage = Objects.requireNonNull(stage, "stage");
        }

        /// How many textures it samples.
        public Builder samplers(int count) {
            this.samplers = count;
            return this;
        }

        /// How many uniform blocks it reads.
        public Builder uniformBuffers(int count) {
            this.uniformBuffers = count;
            return this;
        }

        /// Adds `code` in `format`, run from the format's default entry point.
        public Builder code(ShaderFormat format, byte[] code) {
            return code(format, code, format.defaultEntryPoint());
        }

        /// Adds `code` in `format`, run from `entryPoint`. A format added twice
        /// keeps the second.
        public Builder code(ShaderFormat format, byte[] code, String entryPoint) {
            bytecode.put(Objects.requireNonNull(format, "format"), new Bytecode(code, entryPoint));
            return this;
        }

        /// The shader code.
        ///
        /// @throws IllegalArgumentException when no format was added, or a
        ///                                  count is negative
        public ShaderCode build() {
            return new ShaderCode(stage, bytecode, samplers, uniformBuffers);
        }
    }
}
