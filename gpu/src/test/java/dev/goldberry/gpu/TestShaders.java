package dev.goldberry.gpu;

/// The tests' own shaders, compiled from `src/test/shaders` by
/// `:gpu:compileShaders` into test resources: a mesh with vertex buffers, which
/// no shader the toolkit ships reads.
final class TestShaders {

    static final String DIRECTORY = "/dev/goldberry/gpu/testshaders/";

    /// Bytes from one mesh vertex to the next: a `float3` position, then a
    /// `float4` colour.
    static final int MESH_STRIDE = 28;

    private TestShaders() {}

    /// `mesh.vert`: a position and a colour from vertex slot 0, locations 0 and
    /// 1, placed by one row-major `float4x4` in uniform block 0.
    static ShaderCode meshVertex() {
        return ShaderCode.load(
                ShaderStage.VERTEX, DIRECTORY + "mesh.vert", 0, 1, TestShaders.class::getResourceAsStream);
    }

    /// `mesh.frag`: the colour, as it is.
    static ShaderCode meshFragment() {
        return ShaderCode.load(
                ShaderStage.FRAGMENT, DIRECTORY + "mesh.frag", 0, 0, TestShaders.class::getResourceAsStream);
    }

    /// `fullscreen.vert`: one triangle over the whole target from the vertex
    /// id, with a texture coordinate per pixel. Three vertices.
    static ShaderCode fullscreenVertex() {
        return ShaderCode.load(
                ShaderStage.VERTEX, DIRECTORY + "fullscreen.vert", 0, 0, TestShaders.class::getResourceAsStream);
    }

    /// `sample.frag`: one texture's first channel as grey.
    static ShaderCode sampleFragment() {
        return ShaderCode.load(
                ShaderStage.FRAGMENT, DIRECTORY + "sample.frag", 1, 0, TestShaders.class::getResourceAsStream);
    }

    /// `array.frag`: one layer of a texture array, the layer a float in
    /// fragment uniform block 0.
    static ShaderCode arrayFragment() {
        return ShaderCode.load(
                ShaderStage.FRAGMENT, DIRECTORY + "array.frag", 1, 1, TestShaders.class::getResourceAsStream);
    }

    /// `shadowed.frag`: a colour texture in sampler slot 0, read through a
    /// plain sampler, darkened by a depth map in slot 1, read through a
    /// comparison sampler against the reference in fragment uniform block 0.
    static ShaderCode shadowedFragment() {
        return ShaderCode.load(
                ShaderStage.FRAGMENT, DIRECTORY + "shadowed.frag", 2, 1, TestShaders.class::getResourceAsStream);
    }

    /// `depthonly.frag`: writes nothing, for a pipeline with no colour target.
    static ShaderCode depthOnlyFragment() {
        return ShaderCode.load(
                ShaderStage.FRAGMENT, DIRECTORY + "depthonly.frag", 0, 0, TestShaders.class::getResourceAsStream);
    }

    /// `scale.comp`: `output[i] = input[i] * scale`, over workgroups of 64,
    /// reading one storage buffer, writing one, with the scale in uniform
    /// block 0.
    static ComputeCode scaleCompute() {
        return ComputeCode.load(
                DIRECTORY + "scale.comp",
                ComputeCode.builder(64, 1, 1)
                        .readOnlyStorageBuffers(1)
                        .readWriteStorageBuffers(1)
                        .uniformBuffers(1),
                TestShaders.class::getResourceAsStream);
    }

    /// `storage.vert`: a red vertex at `vertices[id].xy`, read from storage
    /// buffer slot 0. Pairs with [#meshFragment].
    static ShaderCode storageVertex() {
        return ShaderCode.load(
                DIRECTORY + "storage.vert",
                ShaderCode.builder(ShaderStage.VERTEX).storageBuffers(1),
                TestShaders.class::getResourceAsStream);
    }

    /// The vertex layout both describe.
    static VertexBufferLayout meshLayout() {
        return VertexBufferLayout.perVertex(
                0,
                MESH_STRIDE,
                VertexAttribute.of(0, VertexFormat.FLOAT3, 0),
                VertexAttribute.of(1, VertexFormat.FLOAT4, 12));
    }
}
