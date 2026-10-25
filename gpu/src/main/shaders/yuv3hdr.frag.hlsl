// yuv3.frag's conversion of I420 and I010, then the picture's HDR or wide
// gamut mapped to SDR BT.709 (tonemap.hlsli).
//
// SDL_GPU's bindings: textures and samplers (t[n], space2) and (s[n], space2),
// uniforms (b[n], space3). The arithmetic is YuvConversion's and ToneMapping's,
// in Java, which the tests hold this shader to.

#include "yuv.hlsli"
#include "tonemap.hlsli"

COMBINED Texture2D<float> luma : register(t0, space2);
COMBINED SamplerState lumaSampler : register(s0, space2);
COMBINED Texture2D<float> blueDifference : register(t1, space2);
COMBINED SamplerState blueSampler : register(s1, space2);
COMBINED Texture2D<float> redDifference : register(t2, space2);
COMBINED SamplerState redSampler : register(s2, space2);

float4 main(float2 uv : TEXCOORD0) : SV_Target0
{
    float2 at = chromaCoordinates(uv);
    return toneMap(toRgb(luma.Sample(lumaSampler, uv), blueDifference.Sample(blueSampler, at),
                         redDifference.Sample(redSampler, at)));
}
