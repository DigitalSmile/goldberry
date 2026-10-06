// Reads a colour texture through one sampler and a depth map through a
// comparison sampler: two slots, each with a sampler of its own, as a card
// that receives a shadow reads its face and the shadow map. The depth map is
// compared against a reference in fragment uniform block 0, and the colour is
// kept where the reference is at or in front of the stored depth and black
// where it is behind. Does not ship.
//
// SDL_GPU's bindings: a fragment shader's textures and samplers are
// (t[n], space2) and (s[n], space2), and its uniform buffers (b[n], space3).
#ifdef __spirv__
#define COMBINED [[vk::combinedImageSampler]]
#else
#define COMBINED
#endif

COMBINED Texture2D<float4> colour : register(t0, space2);
COMBINED SamplerState colourSampler : register(s0, space2);
COMBINED Texture2D<float> shadow : register(t1, space2);
COMBINED SamplerComparisonState shadowSampler : register(s1, space2);

cbuffer Reference : register(b0, space3)
{
    float reference;
    float3 padding;
};

float4 main(float2 uv : TEXCOORD0) : SV_Target0
{
    float lit = shadow.SampleCmp(shadowSampler, uv, reference);
    return float4(colour.Sample(colourSampler, uv).rgb * lit, 1.0);
}
