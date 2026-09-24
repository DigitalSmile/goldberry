package io.github.digitalsmile.goldberry.gpu;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/// What a [GraphicsPipeline] is made from: its two shaders, the colour target it
/// draws into, how it blends, assembles and culls, the vertex buffers it reads,
/// and its depth test. Built with [#builder]; everything but the shaders and the
/// target format has a default.
///
/// One colour target, one sample, filled polygons. A pipeline with no vertex
/// buffers makes its vertices from the vertex id, as the toolkit's quads do.
///
/// @param vertexShader   a [ShaderStage#VERTEX] shader
/// @param fragmentShader a [ShaderStage#FRAGMENT] shader of the same device
/// @param targetFormat   the colour target's format
/// @param blend          how the output meets the target
/// @param primitiveType  how vertices are assembled
/// @param cullMode       which triangles are discarded by their facing
/// @param frontFace      which winding faces front
/// @param vertexBuffers  the vertex buffers read, by slot
/// @param depthTest      the depth test, or empty for none
public record PipelineSpec(
        Shader vertexShader,
        Shader fragmentShader,
        TextureFormat targetFormat,
        BlendMode blend,
        PrimitiveType primitiveType,
        CullMode cullMode,
        FrontFace frontFace,
        List<VertexBufferLayout> vertexBuffers,
        Optional<DepthTest> depthTest) {

    /// Checks the pieces fit together, and copies the buffer list.
    ///
    /// @throws IllegalArgumentException when a shader is for the wrong stage,
    ///                                  the two are different devices', the
    ///                                  target format is a depth format, or a
    ///                                  slot or a location is declared twice
    public PipelineSpec {
        Objects.requireNonNull(vertexShader, "vertexShader");
        Objects.requireNonNull(fragmentShader, "fragmentShader");
        Objects.requireNonNull(targetFormat, "targetFormat");
        Objects.requireNonNull(blend, "blend");
        Objects.requireNonNull(primitiveType, "primitiveType");
        Objects.requireNonNull(cullMode, "cullMode");
        Objects.requireNonNull(frontFace, "frontFace");
        Objects.requireNonNull(depthTest, "depthTest");
        vertexBuffers = List.copyOf(vertexBuffers);
        if (vertexShader.stage() != ShaderStage.VERTEX) {
            throw new IllegalArgumentException(vertexShader + " is not a vertex shader");
        }
        if (fragmentShader.stage() != ShaderStage.FRAGMENT) {
            throw new IllegalArgumentException(fragmentShader + " is not a fragment shader");
        }
        if (vertexShader.device() != fragmentShader.device()) {
            throw new IllegalArgumentException("a pipeline's shaders must be one device's");
        }
        if (targetFormat.isDepth()) {
            throw new IllegalArgumentException(targetFormat + " cannot be a colour target");
        }
        var slots = new HashSet<Integer>();
        var locations = new HashSet<Integer>();
        for (var buffer : vertexBuffers) {
            if (!slots.add(buffer.slot())) {
                throw new IllegalArgumentException("vertex slot " + buffer.slot() + " declared twice");
            }
            for (var attribute : buffer.attributes()) {
                if (!locations.add(attribute.location())) {
                    throw new IllegalArgumentException("vertex location " + attribute.location() + " declared twice");
                }
            }
        }
    }

    /// Starts a pipeline of `vertexShader` and `fragmentShader` drawing into
    /// `targetFormat`: replacing what is there, triangle lists, no culling,
    /// counter-clockwise front faces, no vertex buffers, no depth test.
    public static Builder builder(Shader vertexShader, Shader fragmentShader, TextureFormat targetFormat) {
        return new Builder(vertexShader, fragmentShader, targetFormat);
    }

    /// Builds a [PipelineSpec].
    public static final class Builder {

        private final Shader vertexShader;
        private final Shader fragmentShader;
        private final TextureFormat targetFormat;
        private BlendMode blend = BlendMode.REPLACE;
        private PrimitiveType primitiveType = PrimitiveType.TRIANGLE_LIST;
        private CullMode cullMode = CullMode.NONE;
        private FrontFace frontFace = FrontFace.COUNTER_CLOCKWISE;
        private final List<VertexBufferLayout> vertexBuffers = new ArrayList<>();
        private @Nullable DepthTest depthTest;

        private Builder(Shader vertexShader, Shader fragmentShader, TextureFormat targetFormat) {
            this.vertexShader = vertexShader;
            this.fragmentShader = fragmentShader;
            this.targetFormat = targetFormat;
        }

        /// How the output meets the target.
        public Builder blend(BlendMode mode) {
            this.blend = Objects.requireNonNull(mode, "mode");
            return this;
        }

        /// How vertices are assembled.
        public Builder primitiveType(PrimitiveType type) {
            this.primitiveType = Objects.requireNonNull(type, "type");
            return this;
        }

        /// Which triangles are discarded, and which winding faces front.
        public Builder cull(CullMode mode, FrontFace front) {
            this.cullMode = Objects.requireNonNull(mode, "mode");
            this.frontFace = Objects.requireNonNull(front, "front");
            return this;
        }

        /// Adds a vertex buffer the pipeline reads.
        public Builder vertexBuffer(VertexBufferLayout layout) {
            vertexBuffers.add(Objects.requireNonNull(layout, "layout"));
            return this;
        }

        /// Tests depth as `test` says.
        public Builder depthTest(DepthTest test) {
            this.depthTest = Objects.requireNonNull(test, "test");
            return this;
        }

        /// The spec.
        ///
        /// @throws IllegalArgumentException as [PipelineSpec]'s constructor does
        public PipelineSpec build() {
            return new PipelineSpec(
                    vertexShader,
                    fragmentShader,
                    targetFormat,
                    blend,
                    primitiveType,
                    cullMode,
                    frontFace,
                    vertexBuffers,
                    Optional.ofNullable(depthTest));
        }
    }
}
