package dev.goldberry.build.shaders;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Metal binding of every resource an HLSL shader declares, as the
 * {@code -fvk-bind-register} arguments that make DXC write it into the SPIR-V
 * SPIRV-Cross translates with {@code --msl-decoration-binding}.
 *
 * <p>SDL_GPU binds a Metal shader's resources by order, not by the descriptor
 * set a Vulkan shader puts them in. In a {@code [[buffer]]} slot the uniform
 * buffers come first, then the read-only storage buffers, then the read-write
 * ones; in a {@code [[texture]]} slot the sampled textures, then the read-only
 * storage textures, then the read-write ones; a sampler takes its texture's
 * index. SPIRV-Cross, left to itself, numbers the slots in descriptor-set order,
 * which is the Vulkan order SDL asks for: storage resources in set 0, read-write
 * ones in set 1, uniforms in set 2 for a compute shader. A compute shader with a
 * uniform and a storage buffer then reads the one where SDL bound the other.
 *
 * <p>The shader's registers already say which resource is which and in what
 * order, because SDL's D3D12 and Vulkan conventions require it: textures before
 * storage buffers in the {@code t} registers, read-write textures before
 * read-write buffers in the {@code u} registers. This reads that order and
 * writes each resource's Metal index as its SPIR-V binding, in a descriptor set
 * of its slot kind, so that SPIRV-Cross copies it through.
 *
 * <p>A {@code [[vk::combinedImageSampler]]} pair must share a set and a binding,
 * which it does: textures and samplers go in set {@value #TEXTURE_SET} and a
 * sampler takes its texture's index.
 */
public final class MetalBindings {

    /** The descriptor set textures and samplers are bound in, for the DXC run that feeds Metal. */
    static final int TEXTURE_SET = 0;

    /** The descriptor set uniform and storage buffers are bound in. */
    static final int BUFFER_SET = 1;

    private static final Pattern INCLUDE = Pattern.compile("^[ \\t]*#include[ \\t]+\"([^\"]+)\"[ \\t]*$", Pattern.MULTILINE);

    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);

    private static final Pattern LINE_COMMENT = Pattern.compile("//[^\\n]*");

    private static final Pattern REGISTER =
            Pattern.compile("register\\(\\s*([btsu])(\\d+)\\s*(?:,\\s*space(\\d+)\\s*)?\\)");

    private static final Pattern TYPE = Pattern.compile("\\b(cbuffer|tbuffer|ConstantBuffer|SamplerState|SamplerComparisonState"
            + "|RWTexture1D|RWTexture1DArray|RWTexture2D|RWTexture2DArray|RWTexture3D"
            + "|Texture1D|Texture1DArray|Texture2D|Texture2DArray|Texture2DMS|Texture2DMSArray|Texture3D"
            + "|TextureCube|TextureCubeArray|RWBuffer|Buffer"
            + "|RWStructuredBuffer|StructuredBuffer|RWByteAddressBuffer|ByteAddressBuffer"
            + "|AppendStructuredBuffer|ConsumeStructuredBuffer)\\b");

    /** What a resource is bound as in Metal: the slot kind decides which index sequence it joins. */
    enum Kind {
        UNIFORM,
        STORAGE_BUFFER,
        TEXTURE,
        SAMPLER
    }

    /**
     * One declared resource.
     *
     * @param kind     the slot kind its type puts it in
     * @param register the register letter: {@code b}, {@code t}, {@code s} or {@code u}
     * @param number   the register number
     * @param space    the register space, 0 when none is written
     */
    record Resource(Kind kind, char register, int number, int space) {

        String registerName() {
            return register + Integer.toString(number);
        }
    }

    /**
     * A resource with the Metal index it must take.
     *
     * @param resource what was declared
     * @param index    the {@code [[buffer]]}, {@code [[texture]]} or {@code [[sampler]]} index
     */
    record Binding(Resource resource, int index) {

        int set() {
            return resource.kind() == Kind.UNIFORM || resource.kind() == Kind.STORAGE_BUFFER ? BUFFER_SET : TEXTURE_SET;
        }
    }

    private MetalBindings() {}

    /**
     * The DXC arguments for {@code source}, with its {@code #include "…"} files
     * read from beside it.
     *
     * @param source an HLSL file
     * @return {@code -fvk-bind-register} and its four values, once per resource, in declaration order
     * @throws IllegalArgumentException when a declaration is not one this understands, or
     *                                  the registers are not in SDL's order
     * @throws UncheckedIOException     when the source or an include cannot be read
     */
    public static List<String> dxcArguments(Path source) {
        Objects.requireNonNull(source, "source");
        var directory = source.toAbsolutePath().getParent();
        Function<String, String> includes = name -> {
            try {
                return Files.readString(directory.resolve(name));
            } catch (IOException e) {
                throw new UncheckedIOException("cannot read " + name + ", included by " + source, e);
            }
        };
        try {
            return dxcArguments(Files.readString(source), includes);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + source, e);
        }
    }

    /**
     * The DXC arguments for {@code hlsl}, with {@code includes} answering each
     * {@code #include "name"} by its text.
     *
     * @param hlsl     a shader's source
     * @param includes the text of an included file, by the name the include wrote
     * @return {@code -fvk-bind-register} and its four values, once per resource, in declaration order
     */
    public static List<String> dxcArguments(String hlsl, Function<String, String> includes) {
        var arguments = new ArrayList<String>();
        for (var binding : bindings(resources(hlsl, includes))) {
            arguments.add("-fvk-bind-register");
            arguments.add(binding.resource().registerName());
            arguments.add(Integer.toString(binding.resource().space()));
            arguments.add(Integer.toString(binding.index()));
            arguments.add(Integer.toString(binding.set()));
        }
        return List.copyOf(arguments);
    }

    /** Every resource {@code hlsl} and its includes declare with a register, in order. */
    static List<Resource> resources(String hlsl, Function<String, String> includes) {
        var text = withoutComments(expandIncludes(hlsl, includes));
        var resources = new ArrayList<Resource>();
        var matcher = REGISTER.matcher(text);
        var from = 0;
        while (matcher.find()) {
            var declaration = text.substring(from, matcher.start());
            var type = TYPE.matcher(declaration);
            String name = null;
            while (type.find()) {
                name = type.group(1);
            }
            if (name == null) {
                throw new IllegalArgumentException("no resource type this understands before " + matcher.group()
                        + " in: " + declaration.strip());
            }
            var register = matcher.group(1).charAt(0);
            var kind = kindOf(name);
            requireRegister(kind, register, matcher.group());
            var space = matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3));
            resources.add(new Resource(kind, register, Integer.parseInt(matcher.group(2)), space));
            from = matcher.end();
        }
        return List.copyOf(resources);
    }

    /**
     * SDL's Metal index for each of {@code resources}: uniforms, then read-only
     * storage buffers, then read-write ones, for the buffer slots; sampled and
     * read-only storage textures, then read-write ones, for the texture slots.
     */
    static List<Binding> bindings(List<Resource> resources) {
        var uniforms = count(resources, Kind.UNIFORM, 'b');
        var texturesInT = count(resources, Kind.TEXTURE, 't');
        var texturesInU = count(resources, Kind.TEXTURE, 'u');
        var buffersInT = count(resources, Kind.STORAGE_BUFFER, 't');
        var bindings = new ArrayList<Binding>();
        for (var resource : resources) {
            var index = switch (resource.kind()) {
                case UNIFORM, SAMPLER -> resource.number();
                case TEXTURE -> resource.register() == 't' ? resource.number() : texturesInT + resource.number();
                case STORAGE_BUFFER -> resource.register() == 't'
                        ? uniforms + after(resource, texturesInT, "a texture")
                        : uniforms + buffersInT + after(resource, texturesInU, "a read-write texture");
            };
            bindings.add(new Binding(resource, index));
        }
        return List.copyOf(bindings);
    }

    /** {@code resource}'s number past the {@code textures} that share its register letter, which SDL puts first. */
    private static int after(Resource resource, int textures, String what) {
        if (resource.number() < textures) {
            throw new IllegalArgumentException("a storage buffer at " + resource.registerName() + " is numbered before "
                    + what + " in the same registers; SDL_GPU wants textures first, then buffers");
        }
        return resource.number() - textures;
    }

    private static int count(List<Resource> resources, Kind kind, char register) {
        return (int) resources.stream()
                .filter(resource -> resource.kind() == kind && resource.register() == register)
                .count();
    }

    private static Kind kindOf(String type) {
        return switch (type) {
            case "cbuffer", "tbuffer", "ConstantBuffer" -> Kind.UNIFORM;
            case "SamplerState", "SamplerComparisonState" -> Kind.SAMPLER;
            case "StructuredBuffer", "RWStructuredBuffer", "ByteAddressBuffer", "RWByteAddressBuffer",
                    "AppendStructuredBuffer", "ConsumeStructuredBuffer" -> Kind.STORAGE_BUFFER;
            // A typed Buffer<T> is a texel buffer, which Metal binds as a texture.
            default -> Kind.TEXTURE;
        };
    }

    private static void requireRegister(Kind kind, char register, String written) {
        var expected = switch (kind) {
            case UNIFORM -> "b";
            case SAMPLER -> "s";
            case TEXTURE, STORAGE_BUFFER -> "tu";
        };
        if (expected.indexOf(register) < 0) {
            throw new IllegalArgumentException(written + " is not a register a " + kind.name().toLowerCase(java.util.Locale.ROOT)
                    .replace('_', ' ') + " can be declared in");
        }
    }

    private static String expandIncludes(String hlsl, Function<String, String> includes) {
        var matcher = INCLUDE.matcher(hlsl);
        var out = new StringBuilder();
        while (matcher.find()) {
            var included = includes.apply(matcher.group(1));
            matcher.appendReplacement(out, Matcher.quoteReplacement(expandIncludes(included, includes)));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String withoutComments(String hlsl) {
        return LINE_COMMENT.matcher(BLOCK_COMMENT.matcher(hlsl).replaceAll(" ")).replaceAll(" ");
    }
}
