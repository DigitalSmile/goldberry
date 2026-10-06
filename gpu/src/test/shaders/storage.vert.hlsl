// A vertex read from a storage buffer by its id, red: what a compute pass
// wrote, drawn. Pairs with mesh.frag. Does not ship.
//
// SDL_GPU's bindings for a vertex shader: storage buffers are (t[n], space0)
// after any samplers and storage textures.

StructuredBuffer<float4> vertices : register(t0, space0);

struct Output
{
    float4 colour : TEXCOORD0;
    float4 position : SV_Position;
};

Output main(uint id : SV_VertexID)
{
    Output output;
    output.position = float4(vertices[id].xy, 0.5, 1.0);
    output.colour = float4(1.0, 0.0, 0.0, 1.0);
    return output;
}
