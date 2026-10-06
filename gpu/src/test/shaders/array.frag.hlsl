// Samples one layer of a texture array, the layer chosen by a uniform: what a
// card-face cache draws every card with. Does not ship.
//
// SDL_GPU's bindings: a fragment shader's textures and samplers are
// (t[n], space2) and (s[n], space2), and its uniform buffers (b[n], space3).
#ifdef __spirv__
#define COMBINED [[vk::combinedImageSampler]]
#else
#define COMBINED
#endif

COMBINED Texture2DArray<float4> layers : register(t0, space2);
COMBINED SamplerState layerSampler : register(s0, space2);

cbuffer Layer : register(b0, space3)
{
    float layer;
    float3 padding;
};

float4 main(float2 uv : TEXCOORD0) : SV_Target0
{
    return layers.Sample(layerSampler, float3(uv, layer));
}
