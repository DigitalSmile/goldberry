// Samples one texture: the UI quad, a GPU layer's result, a picture.
//
// SDL_GPU's bindings: a fragment shader's textures and samplers are
// (t[n], space2) and (s[n], space2), which Vulkan takes as combined image
// samplers in set 2, and SPIRV-Cross maps to [[texture(n)]] and [[sampler(n)]].

// DXC defines __spirv__ only when it writes SPIR-V; DXIL has no such attribute.
#ifdef __spirv__
#define COMBINED [[vk::combinedImageSampler]]
#else
#define COMBINED
#endif

COMBINED Texture2D<float4> image : register(t0, space2);
COMBINED SamplerState imageSampler : register(s0, space2);

float4 main(float2 uv : TEXCOORD0) : SV_Target0
{
    return image.Sample(imageSampler, uv);
}
