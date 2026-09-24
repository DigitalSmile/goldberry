package io.github.digitalsmile.goldberry.gpu.render;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;

import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuDevice;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuShader;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuShaderCode;
import io.github.digitalsmile.goldberry.natives.sdl.gpu.SdlGpuShaderFormat;

/// Loads a [BuiltInShader] in the format a device takes.
///
/// The bytecode is a resource of this module, under
/// `io/github/digitalsmile/goldberry/gpu/shaders/`, declared to native-image by
/// glob. The formats are tried in the order of [#PREFERENCE]: MSL, SPIR-V, DXIL.
/// A device takes one family, so the order only matters for a device that
/// takes two, and none of SDL's three does.
public final class ShaderLibrary {

    /// Where the bytecode is, relative to the module's root.
    static final String DIRECTORY = "/io/github/digitalsmile/goldberry/gpu/shaders/";

    /// The formats the toolkit ships, in the order they are tried.
    static final List<SdlGpuShaderFormat> PREFERENCE =
            List.of(SdlGpuShaderFormat.MSL, SdlGpuShaderFormat.SPIRV, SdlGpuShaderFormat.DXIL);

    private ShaderLibrary() {}

    /// The format `shader` is loaded in on a device that takes `formats`, or
    /// empty when the toolkit ships none of them.
    static Optional<SdlGpuShaderFormat> formatFor(java.util.Set<SdlGpuShaderFormat> formats) {
        return PREFERENCE.stream().filter(formats::contains).findFirst();
    }

    /// The file extension each shipped format is stored under.
    static String extension(SdlGpuShaderFormat format) {
        return switch (format) {
            case MSL -> ".msl";
            case SPIRV -> ".spv";
            case DXIL -> ".dxil";
            case DXBC, METALLIB -> throw new IllegalArgumentException("the toolkit ships no " + format + " shaders");
        };
    }

    /// The entry point in each shipped format: SPIRV-Cross renames `main` to
    /// `main0` in MSL, since `main` is reserved there.
    static String entryPoint(SdlGpuShaderFormat format) {
        return format == SdlGpuShaderFormat.MSL ? "main0" : "main";
    }

    /// The bytecode of `shader` in `format`.
    ///
    /// @throws IllegalStateException when the resource is missing: a build that
    ///                               left out `src/main/resources`
    public static SdlGpuShaderCode code(BuiltInShader shader, SdlGpuShaderFormat format) {
        var name = DIRECTORY + shader.fileName() + extension(format);
        try (var in = ShaderLibrary.class.getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException("no shader resource " + name);
            }
            return new SdlGpuShaderCode(
                    shader.stage(),
                    format,
                    in.readAllBytes(),
                    entryPoint(format),
                    shader.samplers(),
                    shader.uniformBuffers());
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + name, e);
        }
    }

    /// Creates `shader` on `device`, in the format it takes.
    ///
    /// @throws IllegalArgumentException when the device takes none of the
    ///                                  formats the toolkit ships
    public static SdlGpuShader create(SdlGpuDevice device, BuiltInShader shader) {
        var format = formatFor(device.shaderFormats())
                .orElseThrow(() -> new IllegalArgumentException(
                        device + " takes " + device.shaderFormats() + ", and the toolkit ships " + PREFERENCE));
        return device.createShader(code(shader, format));
    }
}
