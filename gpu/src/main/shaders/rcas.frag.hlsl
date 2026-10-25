// FSR 1's RCAS (Robust Contrast Adaptive Sharpening): each pixel sharpened
// against the plus of four around it, by as much as it can be without leaving
// their range, so it does not ring or clip.
//
// AMD's FsrRcasF, in 32-bit floats and without its optional denoise; see
// fsr.hlsli for the licence. Drawn as a quad over the whole target
// (quad.vert), one pixel of the picture per pixel of the target. The constants
// are FsrRcasCon's, worked out in Java by Fsr1.rcas.
//
// One change: the two limiters divide by the ring's extremes, which are 0 on
// a ring all black or 1 on one all white. AMD's divide then makes 0 times
// infinity, a NaN that GPUs' max() resolve differently, so the divisors are
// kept off zero. The numerator is zero exactly when they would be, so the
// result is the same 0 the limiter means.

#include "fsr.hlsli"

cbuffer Rcas : register(b0, space3)
{
    // x: how much it sharpens, 0 to 1. yz: the picture's last pixel, for the
    // ring at its edges. w unused.
    float4 con;
};

// What sharpening may take from, or add to, a pixel at most.
static const float RCAS_LIMIT = 0.25 - (1.0 / 16.0);

float3 rcasLoad(int2 p)
{
    return picture.Load(int3(clamp(p, int2(0, 0), int2(con.yz)), 0)).rgb;
}

float4 main(float2 uv : TEXCOORD0, float4 position : SV_Position) : SV_Target0
{
    //    b
    //  d e f
    //    h
    int2 sp = int2(floor(position.xy));
    float3 b = rcasLoad(sp + int2(0, -1));
    float3 d = rcasLoad(sp + int2(-1, 0));
    float3 e = rcasLoad(sp);
    float3 f = rcasLoad(sp + int2(1, 0));
    float3 h = rcasLoad(sp + int2(0, 1));

    float3 mn4 = min(min(b, min(d, f)), h);
    float3 mx4 = max(max(b, max(d, f)), h);
    float2 peakC = float2(1.0, -1.0 * 4.0);
    float3 hitMin = min(mn4, e) / max(4.0 * mx4, 1.0 / 1024.0);
    float3 hitMax = (peakC.x - max(mx4, e)) / min(4.0 * mn4 + peakC.y, -1.0 / 1024.0);
    float3 lobeRgb = max(-hitMin, hitMax);
    float lobe = max(-RCAS_LIMIT, min(max(lobeRgb.r, max(lobeRgb.g, lobeRgb.b)), 0.0)) * con.x;

    float rcpL = fsrRcpMedium(4.0 * lobe + 1.0);
    float3 pix = (lobe * b + lobe * d + lobe * h + lobe * f + e) * rcpL;
    return float4(pix, 1.0);
}
