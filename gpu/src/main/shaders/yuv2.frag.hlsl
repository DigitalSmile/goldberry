// Y'CbCr to RGB for the two-plane formats: NV12 (8-bit) and P010 (10-bit in
// the high bits of 16), a luma plane and an interleaved chroma plane at half
// size (docs/gpu-plan.md, phase 6).
//
// SDL_GPU's bindings: textures and samplers (t[n], space2) and (s[n], space2),
// uniforms (b[n], space3). The conversion is YuvConversion's, in Java, which
// the tests hold this shader to.

#include "yuv.hlsli"

COMBINED Texture2D<float> luma : register(t0, space2);
COMBINED SamplerState lumaSampler : register(s0, space2);
COMBINED Texture2D<float2> chroma : register(t1, space2);
COMBINED SamplerState chromaSampler : register(s1, space2);

float4 main(float2 uv : TEXCOORD0) : SV_Target0
{
    float y = luma.Sample(lumaSampler, uv);
    float2 cbcr = chroma.Sample(chromaSampler, chromaCoordinates(uv));
    return toRgb(y, cbcr.x, cbcr.y);
}
