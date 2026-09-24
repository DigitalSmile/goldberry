// A quad placed by one uniform block: two triangles, six vertices, made from the
// vertex id alone, so no vertex buffer is needed (docs/gpu-plan.md, phase 2).
//
// SDL_GPU's bindings: a vertex shader's uniform buffers are (b[n], space1),
// which DXC maps to SPIR-V set 1 and SPIRV-Cross to [[buffer(n)]].

cbuffer Quad : register(b0, space1)
{
    // Left, top, right and bottom of the destination, in normalised device
    // coordinates: x right, y up, -1 to 1.
    float4 destination;
    // Left, top, right and bottom of the source, in texture coordinates:
    // 0 to 1 from the top left.
    float4 source;
};

struct Output
{
    float2 uv : TEXCOORD0;
    float4 position : SV_Position;
};

Output main(uint id : SV_VertexID)
{
    // Corners 0 1 2 3 are top-left, top-right, bottom-left, bottom-right; the
    // six vertices are the triangles 0 1 2 and 2 1 3.
    static const uint corners[6] = { 0u, 1u, 2u, 2u, 1u, 3u };
    uint corner = corners[id];
    float2 t = float2(corner & 1u, corner >> 1u);

    Output output;
    output.position = float4(lerp(destination.xy, destination.zw, t), 0.0, 1.0);
    output.uv = lerp(source.xy, source.zw, t);
    return output;
}
