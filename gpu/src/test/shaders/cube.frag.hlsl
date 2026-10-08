// Samples a cube texture along one direction, the direction a uniform: what a
// reflection reads its surroundings with. Does not ship.
//
// SDL_GPU's bindings: a fragment shader's textures and samplers are
// (t[n], space2) and (s[n], space2), and its uniform buffers (b[n], space3).
#ifdef __spirv__
#define COMBINED [[vk::combinedImageSampler]]
#else
#define COMBINED
#endif

COMBINED TextureCube<float4> cube : register(t0, space2);
COMBINED SamplerState cubeSampler : register(s0, space2);

cbuffer Direction : register(b0, space3)
{
    float3 direction;
    float padding;
};

float4 main(float2 uv : TEXCOORD0) : SV_Target0
{
    return cube.Sample(cubeSampler, direction);
}
