// A mesh's vertex: a position and a colour read from a vertex buffer, placed by
// one matrix. The public API's tests draw with it (GpuApiTest); it does not ship.
//
// SDL_GPU's bindings: a vertex shader's uniform buffers are (b[n], space1). A
// vertex input's location is its TEXCOORD index, which is what SDL's Direct3D
// 12 driver binds by; vk::location says the same to SPIR-V and so to MSL.

cbuffer Transform : register(b0, space1)
{
    // Row-major, so the sixteen floats pushed from Java are the matrix's rows
    // in order.
    row_major float4x4 transform;
};

struct Input
{
    [[vk::location(0)]] float3 position : TEXCOORD0;
    [[vk::location(1)]] float4 colour : TEXCOORD1;
};

struct Output
{
    float4 colour : TEXCOORD0;
    float4 position : SV_Position;
};

Output main(Input input)
{
    Output output;
    output.position = mul(transform, float4(input.position, 1.0));
    output.colour = input.colour;
    return output;
}
