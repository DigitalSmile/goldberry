// The showcase's cube (docs/gpu-plan.md, phase 5): a position, a normal and a
// colour per vertex, placed by one matrix and lit by one directional light, as
// an application's own shader would be. Compiled by :gpu:compileShaders into
// this module's resources, where ShaderCode.load finds it.
//
// SDL_GPU's bindings: a vertex shader's uniform buffers are (b[n], space1), and
// a vertex input's location is its TEXCOORD index.

cbuffer Transform : register(b0, space1)
{
    // Projection, view and model, composed: object space to clip space.
    // Row-major, so the floats pushed from Java are the rows in order.
    row_major float4x4 clip;
    // The model's rotation alone, which turns a normal into world space.
    row_major float4x4 model;
    // Towards the light, in world space; w is unused.
    float4 light;
};

struct Input
{
    [[vk::location(0)]] float3 position : TEXCOORD0;
    [[vk::location(1)]] float3 normal : TEXCOORD1;
    [[vk::location(2)]] float4 colour : TEXCOORD2;
};

struct Output
{
    float4 colour : TEXCOORD0;
    float4 position : SV_Position;
};

Output main(Input input)
{
    Output output;
    output.position = mul(clip, float4(input.position, 1.0));
    float3 normal = normalize(mul((float3x3) model, input.normal));
    // A quarter ambient, and the rest by how squarely the face meets the light.
    float lit = 0.25 + 0.75 * saturate(dot(normal, normalize(light.xyz)));
    output.colour = float4(input.colour.rgb * lit, input.colour.a);
    return output;
}
