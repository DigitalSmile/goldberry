// Samples one texture and writes its first channel to every colour channel: a
// depth map read as grey, a mip level read at a footprint. Does not ship.
//
// SDL_GPU's bindings: a fragment shader's textures and samplers are
// (t[n], space2) and (s[n], space2).
#ifdef __spirv__
#define COMBINED [[vk::combinedImageSampler]]
#else
#define COMBINED
#endif

COMBINED Texture2D<float4> image : register(t0, space2);
COMBINED SamplerState imageSampler : register(s0, space2);

float4 main(float2 uv : TEXCOORD0) : SV_Target0
{
    float value = image.Sample(imageSampler, uv).r;
    return float4(value, value, value, 1.0);
}
