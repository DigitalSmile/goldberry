// Y'CbCr to RGB for the three-plane formats: I420 (8-bit) and I010 (10-bit in
// the low bits of 16), a luma plane and two chroma planes at half size
// (docs/gpu-plan.md, phase 6).
//
// SDL_GPU's bindings: textures and samplers (t[n], space2) and (s[n], space2),
// uniforms (b[n], space3). The conversion is YuvConversion's, in Java, which
// the tests hold this shader to.

#include "yuv.hlsli"

COMBINED Texture2D<float> luma : register(t0, space2);
COMBINED SamplerState lumaSampler : register(s0, space2);
COMBINED Texture2D<float> blueDifference : register(t1, space2);
COMBINED SamplerState blueSampler : register(s1, space2);
COMBINED Texture2D<float> redDifference : register(t2, space2);
COMBINED SamplerState redSampler : register(s2, space2);

float4 main(float2 uv : TEXCOORD0) : SV_Target0
{
    float2 at = chromaCoordinates(uv);
    return toRgb(luma.Sample(lumaSampler, uv), blueDifference.Sample(blueSampler, at),
                 redDifference.Sample(redSampler, at));
}
