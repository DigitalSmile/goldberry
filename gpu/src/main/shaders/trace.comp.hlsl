// The software ray tracer's records and lighting, a thread a pixel, into a
// byte buffer laid out as the hardware tier's (trace.lib.hlsl): a 24-byte
// record a pixel for OUTPUT_RECORDS, and for OUTPUT_LIGHTING two planes, the
// shading word a pixel and then the reflected id a pixel.

#include "raytrace.hlsli"

RWByteAddressBuffer written : register(u0, space1);

[numthreads(8, 8, 1)]
void main(uint3 id : SV_DispatchThreadID)
{
    if (id.x >= width || id.y >= height) {
        return;
    }
    uint index = id.y * width + id.x;
    if (output == OUTPUT_LIGHTING) {
        Lit lighting = lightPixel(id.xy, index);
        written.Store(index * 4, lighting.shading);
        written.Store(width * height * 4 + index * 4, asuint(lighting.reflectedId));
        return;
    }
    HitRecord record = tracePixel(id.xy);
    uint at = index * 24;
    written.Store3(at, uint3(record.instanceId, record.primitiveId, asuint(record.distance)));
    written.Store3(at + 12, uint3(asuint(record.u), asuint(record.v), record.lit));
}
