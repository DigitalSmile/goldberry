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

    /// The vertex layout both describe.
    static VertexBufferLayout meshLayout() {
        return VertexBufferLayout.perVertex(
                0,
                MESH_STRIDE,
                VertexAttribute.of(0, VertexFormat.FLOAT3, 0),
                VertexAttribute.of(1, VertexFormat.FLOAT4, 12));
    }
}
