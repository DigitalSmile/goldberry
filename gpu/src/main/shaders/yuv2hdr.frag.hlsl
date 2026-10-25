// yuv2.frag's conversion of NV12 and P010, then the picture's HDR or wide
// gamut mapped to SDR BT.709 (tonemap.hlsli).
//
// SDL_GPU's bindings: textures and samplers (t[n], space2) and (s[n], space2),
// uniforms (b[n], space3). The arithmetic is YuvConversion's and ToneMapping's,
// in Java, which the tests hold this shader to.

#include "yuv.hlsli"
#include "tonemap.hlsli"

COMBINED Texture2D<float> luma : register(t0, space2);
COMBINED SamplerState lumaSampler : register(s0, space2);
COMBINED Texture2D<float2> chroma : register(t1, space2);
COMBINED SamplerState chromaSampler : register(s1, space2);

float4 main(float2 uv : TEXCOORD0) : SV_Target0
{
    float y = luma.Sample(lumaSampler, uv);
    float2 cbcr = chroma.Sample(chromaSampler, chromaCoordinates(uv));
    return toneMap(toRgb(y, cbcr.x, cbcr.y));
}
