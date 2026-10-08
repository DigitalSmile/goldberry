// Samples a 3D texture at the pixel's texture coordinate and a depth from a
// uniform: what a colour-grading table is read with. Does not ship.
//
// SDL_GPU's bindings: a fragment shader's textures and samplers are
// (t[n], space2) and (s[n], space2), and its uniform buffers (b[n], space3).
#ifdef __spirv__
#define COMBINED [[vk::combinedImageSampler]]
#else
#define COMBINED
#endif

COMBINED Texture3D<float4> volume : register(t0, space2);
COMBINED SamplerState volumeSampler : register(s0, space2);

cbuffer Depth : register(b0, space3)
{
    float w;
    float3 padding;
};

float4 main(float2 uv : TEXCOORD0) : SV_Target0
{
    return volume.Sample(volumeSampler, float3(uv, w));
}
