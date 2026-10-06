package dev.goldberry.natives.sdl.gpu;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import dev.goldberry.natives.sdl.gpu.enums.SdlGpuBlend;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuCompareOp;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuCullMode;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuFrontFace;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuPrimitiveType;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuSampleCount;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuShaderStage;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuTextureFormat;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuVertexFormat;
import dev.goldberry.natives.sdl.gpu.enums.SdlGpuVertexInputRate;

/// Everything a graphics pipeline is made from, checked before SDL sees it.
///
/// At most one colour target, filled polygons: what the composited window and
/// `canvas3d` draw with. A pipeline with no colour target writes depth alone:
/// a shadow map. A pipeline with more than one sample draws into a
/// multisampled target, which a pass resolves. Several colour targets,
/// stencil and depth bias join when something asks for them.
///
/// Read more: [The native boundary](https://goldberry.dev/docs/overview/architecture.html#the-native-boundary).
///
/// @param vertex           the vertex shader
/// @param fragment         the fragment shader
/// @param targetFormat     the colour target's format, or empty for a pipeline
///                         that writes depth alone
/// @param blend            how the output meets what the target holds
/// @param primitiveType    how vertices are assembled
/// @param cullMode         which triangles are discarded by their facing
/// @param frontFace        which winding faces front
/// @param vertexBuffers    the vertex buffers the pipeline reads, by slot; empty
///                         when the vertex shader makes its vertices from their
///                         ids, as the toolkit's quads do
/// @param vertexAttributes the vertex shader's inputs, each read from one of
///                         the buffers
/// @param depth            the depth test, or empty for none
/// @param sampleCount      how many samples the targets it draws into have
public record SdlGpuPipelineDescription(
        SdlGpuShader vertex,
        SdlGpuShader fragment,
        Optional<SdlGpuTextureFormat> targetFormat,
        SdlGpuBlend blend,
        SdlGpuPrimitiveType primitiveType,
        SdlGpuCullMode cullMode,
        SdlGpuFrontFace frontFace,
        List<VertexBuffer> vertexBuffers,
        List<VertexAttribute> vertexAttributes,
        Optional<DepthTest> depth,
        SdlGpuSampleCount sampleCount) {

    /// A description for single-sampled targets.
    public SdlGpuPipelineDescription(
            SdlGpuShader vertex,
            SdlGpuShader fragment,
            Optional<SdlGpuTextureFormat> targetFormat,
            SdlGpuBlend blend,
            SdlGpuPrimitiveType primitiveType,
            SdlGpuCullMode cullMode,
            SdlGpuFrontFace frontFace,
            List<VertexBuffer> vertexBuffers,
            List<VertexAttribute> vertexAttributes,
            Optional<DepthTest> depth) {
        this(
                vertex,
                fragment,
                targetFormat,
                blend,
                primitiveType,
                cullMode,
                frontFace,
                vertexBuffers,
                vertexAttributes,
                depth,
                SdlGpuSampleCount.ONE);
    }

    /// A vertex buffer the pipeline reads.
    ///
    /// @param slot  the slot it is bound to
    /// @param pitch the bytes from one element to the next
    /// @param rate  whether an element is a vertex or an instance
    public record VertexBuffer(int slot, int pitch, SdlGpuVertexInputRate rate) {

        /// Checks the slot and pitch.
        public VertexBuffer {
            if (slot < 0 || pitch <= 0) {
                throw new IllegalArgumentException("vertex buffer slot " + slot + ", pitch " + pitch);
            }
            Objects.requireNonNull(rate, "rate");
        }
    }

    /// One input of the vertex shader.
    ///
    /// @param location   the shader's input location
    /// @param bufferSlot the slot of the buffer it is read from
    /// @param format     how it is stored
    /// @param offset     its byte offset in the buffer's element
    public record VertexAttribute(int location, int bufferSlot, SdlGpuVertexFormat format, int offset) {

        /// Checks the numbers are not negative.
        public VertexAttribute {
            if (location < 0 || bufferSlot < 0 || offset < 0) {
                throw new IllegalArgumentException(
                        "vertex attribute location " + location + ", slot " + bufferSlot + ", offset " + offset);
            }
            Objects.requireNonNull(format, "format");
        }
    }

    /// A depth test.
    ///
    /// @param format  the depth target's format
    /// @param compare how a fragment's depth is compared with the target's
    /// @param write   whether a fragment that passes writes its depth
    public record DepthTest(SdlGpuTextureFormat format, SdlGpuCompareOp compare, boolean write) {

        /// Checks the format is a depth format.
        public DepthTest {
            if (!format.isDepth()) {
                throw new IllegalArgumentException(format + " is not a depth format");
            }
            Objects.requireNonNull(compare, "compare");
        }
    }

    /// Checks the description is whole and consistent, and copies the lists.
    ///
    /// @throws IllegalArgumentException when a shader is for the wrong stage or
    ///                                  of another device, the colour target is
    ///                                  a depth format, there is neither a
    ///                                  colour target nor a depth test, a slot
    ///                                  or a location is declared twice, or an
    ///                                  attribute reads a slot not declared or
    ///                                  past its element
    public SdlGpuPipelineDescription {
        Objects.requireNonNull(vertex, "vertex");
        Objects.requireNonNull(fragment, "fragment");
        Objects.requireNonNull(targetFormat, "targetFormat");
        Objects.requireNonNull(blend, "blend");
        Objects.requireNonNull(primitiveType, "primitiveType");
        Objects.requireNonNull(cullMode, "cullMode");
        Objects.requireNonNull(frontFace, "frontFace");
        Objects.requireNonNull(depth, "depth");
        Objects.requireNonNull(sampleCount, "sampleCount");
        vertexBuffers = List.copyOf(vertexBuffers);
        vertexAttributes = List.copyOf(vertexAttributes);
        if (vertex.stage() != SdlGpuShaderStage.VERTEX || fragment.stage() != SdlGpuShaderStage.FRAGMENT) {
            throw new IllegalArgumentException("a pipeline needs a vertex then a fragment shader, not " + vertex.stage()
                    + " and " + fragment.stage());
        }
        if (vertex.device() != fragment.device()) {
            throw new IllegalArgumentException("a pipeline's shaders must be one device's");
        }
        if (targetFormat.isPresent() && targetFormat.get().isDepth()) {
            throw new IllegalArgumentException(targetFormat.get() + " cannot be a colour target");
        }
        if (targetFormat.isEmpty() && depth.isEmpty()) {
            throw new IllegalArgumentException("a pipeline with no colour target needs a depth test");
        }
        var slots = new HashSet<Integer>();
        for (var buffer : vertexBuffers) {
            if (!slots.add(buffer.slot())) {
                throw new IllegalArgumentException("vertex buffer slot " + buffer.slot() + " declared twice");
            }
        }
        var locations = new HashSet<Integer>();
        for (var attribute : vertexAttributes) {
            if (!locations.add(attribute.location())) {
                throw new IllegalArgumentException("vertex location " + attribute.location() + " declared twice");
            }
            var buffer = vertexBuffers.stream()
                    .filter(candidate -> candidate.slot() == attribute.bufferSlot())
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("vertex location " + attribute.location()
                            + " reads slot " + attribute.bufferSlot() + ", which no buffer is declared for"));
            if (attribute.offset() + attribute.format().bytes() > buffer.pitch()) {
                throw new IllegalArgumentException("vertex location " + attribute.location() + " ends past its "
                        + buffer.pitch() + "-byte element");
            }
        }
    }

    /// The pipeline the toolkit's quads are drawn with: triangles made from the
    /// vertex id, no culling, no depth.
    public static SdlGpuPipelineDescription quads(
            SdlGpuShader vertex, SdlGpuShader fragment, SdlGpuTextureFormat targetFormat, SdlGpuBlend blend) {
        return new SdlGpuPipelineDescription(
                vertex,
                fragment,
                Optional.of(targetFormat),
                blend,
                SdlGpuPrimitiveType.TRIANGLE_LIST,
                SdlGpuCullMode.NONE,
                SdlGpuFrontFace.COUNTER_CLOCKWISE,
                List.of(),
                List.of(),
                Optional.empty());
    }

    /// Whether the pipeline writes depth alone, with no colour target.
    public boolean isDepthOnly() {
        return targetFormat.isEmpty();
    }

    /// The depth target's format, or empty when the pipeline tests no depth.
    public Optional<SdlGpuTextureFormat> depthFormat() {
        return depth.map(DepthTest::format);
    }
}
