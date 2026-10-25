// FSR 1's EASU (Edge Adaptive Spatial Upsampling): a picture drawn small,
// upscaled to the target with a 12-tap Lanczos-2 kernel stretched along the
// edge each pixel is on, and clamped to its four nearest texels so it does not
// ring.
//
// AMD's FsrEasuF, in 32-bit floats; see fsr.hlsli for the licence. Drawn as a
// quad over the whole target (quad.vert); the pixel is SV_Position's. The
// constants are FsrEasuCon's, worked out in Java by Fsr1.easu.

#include "fsr.hlsli"

cbuffer Easu : register(b0, space3)
{
    // The scale from output to input pixels, then the offset to the input's
    // texel centres.
    float4 con0;
    // One input texel in texture coordinates; the first gather's offset.
    float4 con1;
    // The second and third gathers' offsets from the first.
    float4 con2;
    // The fourth gather's offset from the first; zw unused.
    float4 con3;
};

// One tap: its offset rotated into the edge's direction and stretched, then
// weighed by the approximated Lanczos-2.
void easuTap(inout float3 aC, inout float aW, float2 off, float2 dir, float2 len, float lob, float clp, float3 c)
{
    float2 v;
    v.x = (off.x * (dir.x)) + (off.y * dir.y);
    v.y = (off.x * (-dir.y)) + (off.y * dir.x);
    v *= len;
    float d2 = v.x * v.x + v.y * v.y;
    d2 = min(d2, clp);
    // (25/16 * (2/5 * x^2 - 1)^2 - (25/16 - 1)) * (lob * x^2 - 1)^2
    float wB = (2.0 / 5.0) * d2 + -1.0;
    float wA = lob * d2 + -1.0;
    wB *= wB;
    wA *= wA;
    wB = (25.0 / 16.0) * wB + -(25.0 / 16.0 - 1.0);
    float w = wB * wA;
    aC += c * w;
    aW += w;
}

// One of the four bilinear corners' share of the direction and of how much of
// an edge there is, from a plus of five lumas around it:
//    a
//  b c d
//    e
void easuSet(inout float2 dir, inout float len, float w, float lA, float lB, float lC, float lD, float lE)
{
    float dc = lD - lC;
    float cb = lC - lB;
    float lenX = max(abs(dc), abs(cb));
    lenX = fsrRcpLow(lenX);
    float dirX = lD - lB;
    dir.x += dirX * w;
    lenX = saturate(abs(dirX) * lenX);
    lenX *= lenX;
    len += lenX * w;

    float ec = lE - lC;
    float ca = lC - lA;
    float lenY = max(abs(ec), abs(ca));
    lenY = fsrRcpLow(lenY);
    float dirY = lE - lA;
    dir.y += dirY * w;
    lenY = saturate(abs(dirY) * lenY);
    lenY *= lenY;
    len += lenY * w;
}

float4 main(float2 uv : TEXCOORD0, float4 position : SV_Position) : SV_Target0
{
    float2 ip = floor(position.xy);
    // The pixel's centre in the input, and the texel f it is right of and
    // below:
    //    b c
    //  e f g h
    //  i j k l
    //    n o
    float2 pp = ip * con0.xy + con0.zw;
    float2 fp = floor(pp);
    pp -= fp;
    float2 p0 = fp * con1.xy + con1.zw;
    float2 p1 = p0 + con2.xy;
    float2 p2 = p0 + con2.zw;
    float2 p3 = p0 + con3.xy;

    // Each gather is a 2x2 footprint: x bottom left, y bottom right, z top
    // right, w top left.
    float4 bczzR = picture.GatherRed(pictureSampler, p0);
    float4 bczzG = picture.GatherGreen(pictureSampler, p0);
    float4 bczzB = picture.GatherBlue(pictureSampler, p0);
    float4 ijfeR = picture.GatherRed(pictureSampler, p1);
    float4 ijfeG = picture.GatherGreen(pictureSampler, p1);
    float4 ijfeB = picture.GatherBlue(pictureSampler, p1);
    float4 klhgR = picture.GatherRed(pictureSampler, p2);
    float4 klhgG = picture.GatherGreen(pictureSampler, p2);
    float4 klhgB = picture.GatherBlue(pictureSampler, p2);
    float4 zzonR = picture.GatherRed(pictureSampler, p3);
    float4 zzonG = picture.GatherGreen(pictureSampler, p3);
    float4 zzonB = picture.GatherBlue(pictureSampler, p3);

    float4 bczzL = bczzB * 0.5 + (bczzR * 0.5 + bczzG);
    float4 ijfeL = ijfeB * 0.5 + (ijfeR * 0.5 + ijfeG);
    float4 klhgL = klhgB * 0.5 + (klhgR * 0.5 + klhgG);
    float4 zzonL = zzonB * 0.5 + (zzonR * 0.5 + zzonG);
    float bL = bczzL.x;
    float cL = bczzL.y;
    float iL = ijfeL.x;
    float jL = ijfeL.y;
    float fL = ijfeL.z;
    float eL = ijfeL.w;
    float kL = klhgL.x;
    float lL = klhgL.y;
    float hL = klhgL.z;
    float gL = klhgL.w;
    float oL = zzonL.z;
    float nL = zzonL.w;

    // The direction and length of the edge, bilinearly from f, g, j and k.
    float2 dir = float2(0.0, 0.0);
    float len = 0.0;
    easuSet(dir, len, (1.0 - pp.x) * (1.0 - pp.y), bL, eL, fL, gL, jL);
    easuSet(dir, len, pp.x * (1.0 - pp.y), cL, fL, gL, hL, kL);
    easuSet(dir, len, (1.0 - pp.x) * pp.y, fL, iL, jL, kL, nL);
    easuSet(dir, len, pp.x * pp.y, gL, jL, kL, lL, oL);

    // Normalised, and no direction at all where there is next to none.
    float2 dir2 = dir * dir;
    float dirR = dir2.x + dir2.y;
    bool zro = dirR < (1.0 / 32768.0);
    dirR = fsrRsqLow(dirR);
    dirR = zro ? 1.0 : dirR;
    dir.x = zro ? 1.0 : dir.x;
    dir *= dirR;
    // From 0 to 2 to 0 to 1, shaped by a square.
    len = len * 0.5;
    len *= len;
    // The kernel is stretched from 1 along an axis to sqrt(2) on a diagonal,
    // and squeezed across an edge.
    float stretch = (dir.x * dir.x + dir.y * dir.y) * fsrRcpLow(max(abs(dir.x), abs(dir.y)));
    float2 len2 = float2(1.0 + (stretch - 1.0) * len, 1.0 + -0.5 * len);
    float lob = 0.5 + ((1.0 / 4.0 - 0.04) - 0.5) * len;
    float clp = fsrRcpLow(lob);

    float3 f = float3(ijfeR.z, ijfeG.z, ijfeB.z);
    float3 g = float3(klhgR.w, klhgG.w, klhgB.w);
    float3 j = float3(ijfeR.y, ijfeG.y, ijfeB.y);
    float3 k = float3(klhgR.x, klhgG.x, klhgB.x);
    float3 min4 = min(min(f, min(g, j)), k);
    float3 max4 = max(max(f, max(g, j)), k);

    float3 aC = float3(0.0, 0.0, 0.0);
    float aW = 0.0;
    easuTap(aC, aW, float2(0.0, -1.0) - pp, dir, len2, lob, clp, float3(bczzR.x, bczzG.x, bczzB.x));
    easuTap(aC, aW, float2(1.0, -1.0) - pp, dir, len2, lob, clp, float3(bczzR.y, bczzG.y, bczzB.y));
    easuTap(aC, aW, float2(-1.0, 1.0) - pp, dir, len2, lob, clp, float3(ijfeR.x, ijfeG.x, ijfeB.x));
    easuTap(aC, aW, float2(0.0, 1.0) - pp, dir, len2, lob, clp, j);
    easuTap(aC, aW, float2(0.0, 0.0) - pp, dir, len2, lob, clp, f);
    easuTap(aC, aW, float2(-1.0, 0.0) - pp, dir, len2, lob, clp, float3(ijfeR.w, ijfeG.w, ijfeB.w));
    easuTap(aC, aW, float2(1.0, 1.0) - pp, dir, len2, lob, clp, k);
    easuTap(aC, aW, float2(2.0, 1.0) - pp, dir, len2, lob, clp, float3(klhgR.y, klhgG.y, klhgB.y));
    easuTap(aC, aW, float2(2.0, 0.0) - pp, dir, len2, lob, clp, float3(klhgR.z, klhgG.z, klhgB.z));
    easuTap(aC, aW, float2(1.0, 0.0) - pp, dir, len2, lob, clp, g);
    easuTap(aC, aW, float2(1.0, 2.0) - pp, dir, len2, lob, clp, float3(zzonR.z, zzonG.z, zzonB.z));
    easuTap(aC, aW, float2(0.0, 2.0) - pp, dir, len2, lob, clp, float3(zzonR.w, zzonG.w, zzonB.w));

    float3 pix = min(max4, max(min4, aC * (1.0 / aW)));
    return float4(pix, 1.0);
}
