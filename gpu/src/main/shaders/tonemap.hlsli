// HDR and wide-gamut R'G'B' to SDR BT.709, after yuv.hlsli's toRgb: what the
// two HDR shaders add. Included, not compiled on its own.
//
// The arithmetic is ToneMapping's, in Java, which the tests hold these shaders
// to: R'G'B' to light in nits by the picture's transfer function (PQ's EOTF,
// HLG's inverse OETF and BT.2100's OOTF, or BT.1886's gamma of 2.4 for SDR),
// into BT.709's primaries by a 3x3 matrix, below zero clipped, the largest
// channel tone-mapped from the picture's peak to 203 nits by BT.2390's EETF
// with the other two scaled alike, then divided by 203 and raised to 1/2.4.

cbuffer ToneMap : register(b1, space3)
{
    float4 curve;   // transfer (0 SDR, 1 PQ, 2 HLG), tone-map (0 or 1), source peak, unused
    float4 eetf;    // PQ of the source peak, the target in that scale, the knee start, unused
    float4 gamutR;  // the rows of the matrix into BT.709's primaries
    float4 gamutG;
    float4 gamutB;
    float4 hlg;     // the scene luminance weights in the picture's primaries, the peak
};

static const float PQ_M1 = 2610.0 / 16384.0;
static const float PQ_M2 = 2523.0 / 4096.0 * 128.0;
static const float PQ_C1 = 3424.0 / 4096.0;
static const float PQ_C2 = 2413.0 / 4096.0 * 32.0;
static const float PQ_C3 = 2392.0 / 4096.0 * 32.0;
static const float HLG_A = 0.17883277;
static const float HLG_B = 0.28466892;
static const float HLG_C = 0.55991073;
static const float REFERENCE_WHITE = 203.0;

float pqDecode(float encoded)
{
    float e = pow(saturate(encoded), 1.0 / PQ_M2);
    return 10000.0 * pow(max(e - PQ_C1, 0.0) / (PQ_C2 - PQ_C3 * e), 1.0 / PQ_M1);
}

float pqEncode(float nits)
{
    float y = pow(saturate(nits / 10000.0), PQ_M1);
    return pow((PQ_C1 + PQ_C2 * y) / (1.0 + PQ_C3 * y), PQ_M2);
}

float hlgInverseOetf(float encoded)
{
    float e = saturate(encoded);
    return e <= 0.5 ? e * e / 3.0 : (exp((e - HLG_C) / HLG_A) + HLG_B) / 12.0;
}

float3 toLight(float3 rgb)
{
    if (curve.x < 0.5) {
        return REFERENCE_WHITE * pow(saturate(rgb), 2.4);
    }
    if (curve.x < 1.5) {
        return float3(pqDecode(rgb.r), pqDecode(rgb.g), pqDecode(rgb.b));
    }
    float3 scene = float3(hlgInverseOetf(rgb.r), hlgInverseOetf(rgb.g), hlgInverseOetf(rgb.b));
    float ys = dot(hlg.xyz, scene);
    return hlg.w * (ys > 0.0 ? pow(ys, 0.2) : 0.0) * scene;
}

float eetfNits(float nits)
{
    float e1 = min(1.0, pqEncode(nits) / eetf.x);
    float e2 = e1;
    if (e1 >= eetf.z) {
        float t = (e1 - eetf.z) / (1.0 - eetf.z);
        float t2 = t * t;
        float t3 = t2 * t;
        e2 = (2.0 * t3 - 3.0 * t2 + 1.0) * eetf.z + (t3 - 2.0 * t2 + t) * (1.0 - eetf.z)
            + (-2.0 * t3 + 3.0 * t2) * eetf.y;
    }
    return pqDecode(e2 * eetf.x);
}

float4 toneMap(float4 encoded)
{
    float3 light = toLight(encoded.rgb);
    float3 mapped = max(float3(dot(gamutR.xyz, light), dot(gamutG.xyz, light), dot(gamutB.xyz, light)), 0.0);
    if (curve.y > 0.5) {
        float largest = max(mapped.r, max(mapped.g, mapped.b));
        if (largest > 0.0) {
            mapped *= eetfNits(largest) / largest;
        }
    }
    return float4(pow(saturate(mapped / REFERENCE_WHITE), 1.0 / 2.4), encoded.a);
}
