// A mesh's vertex moved by the first texel of a storage texture, read with no
// sampler: a height field displacing a terrain, at its smallest. Pairs with
// mesh.frag. Does not ship.
//
// SDL_GPU's bindings for a vertex shader: storage textures are (t[n], space0)
// after any samplers, and read as sampled images, which is how SDL's Vulkan
// driver declares them.

Texture2D<float4> displacement : register(t0, space0);

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
    output.position = float4(input.position.xy + displacement.Load(int3(0, 0, 0)).xy, input.position.z, 1.0);
    output.colour = input.colour;
    return output;
}
