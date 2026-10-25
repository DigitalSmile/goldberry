// The ray-traced room's solids: a position and a normal per vertex, placed in
// the world by one matrix and seen through another. Compiled by
// :gpu:compileShaders into this module's resources, where ShaderCode.load
// finds it.
//
// SDL_GPU's bindings: a vertex shader's uniform buffers are (b[n], space1), and
// a vertex input's location is its TEXCOORD index.

cbuffer Transform : register(b0, space1)
{
    // The camera's projection and view, composed. Row-major, so the floats
    // pushed from Java are the rows in order.
    row_major float4x4 viewProjection;
    // The solid's place in the world: a turn, a uniform scale, a move.
    row_major float4x4 model;
};

struct Input
{
    [[vk::location(0)]] float3 position : TEXCOORD0;
    [[vk::location(1)]] float3 normal : TEXCOORD1;
};

struct Output
{
    float3 normal : TEXCOORD0;
    float3 world : TEXCOORD1;
    float4 position : SV_Position;
};

Output main(Input input)
{
    Output output;
    float4 world = mul(model, float4(input.position, 1.0));
    output.world = world.xyz;
    output.position = mul(viewProjection, world);
    // A scale that is not uniform would need the inverse transpose; the room
    // stretches its pillar, whose normals stay on the axes either way.
    output.normal = mul((float3x3) model, input.normal);
    return output;
}
