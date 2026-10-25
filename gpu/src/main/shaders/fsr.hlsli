// What FSR 1's two passes share: the binding of the picture they read, and the
// approximations AMD's ffx_a.h makes of a reciprocal and a reciprocal square
// root from the bits of a float.
//
// Ported from AMD FidelityFX Super Resolution 1.0 (ffx_a.h, ffx_fsr1.h),
// Copyright (c) 2021 Advanced Micro Devices, Inc., MIT licence; the notice is
// in THIRD-PARTY-NOTICES.md. In 32-bit floats only. The Java reference the
// tests hold these shaders to, Fsr1Reference, makes the same approximations.
//
// SDL_GPU's bindings: textures and samplers (t[n], space2) and (s[n], space2),
// uniforms (b[n], space3).

// DXC defines __spirv__ only when it writes SPIR-V; DXIL has no such attribute.
#ifdef __spirv__
#define COMBINED [[vk::combinedImageSampler]]
#else
#define COMBINED
#endif

COMBINED Texture2D<float4> picture : register(t0, space2);
COMBINED SamplerState pictureSampler : register(s0, space2);

// ffx_a.h's APrxLoRcpF1: 1/a to a few bits, by subtracting the bits from a
// constant.
float fsrRcpLow(float a)
{
    return asfloat(0x7ef07ebbu - asuint(a));
}

// ffx_a.h's APrxLoRsqF1: 1/sqrt(a) to a few bits.
float fsrRsqLow(float a)
{
    return asfloat(0x5f347d74u - (asuint(a) >> 1u));
}

// ffx_a.h's APrxMedRcpF1: the low approximation, refined by one Newton step.
float fsrRcpMedium(float a)
{
    float b = asfloat(0x7ef19fffu - asuint(a));
    return b * (-b * a + 2.0);
}
