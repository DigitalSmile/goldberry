// The software ray tracer's shadow mask, a thread a word of four pixels: 0xFF
// where the light reaches the pixel or its ray hit nothing, 0 in shadow, rows
// with no padding. Each word is written whole, so the buffer needs no zeroing
// and the pixels no atomics.

#include "raytrace.hlsli"

// The grid of threads the dispatch spans: threads a row, so a thread finds its
// word, and how many words the mask has.
cbuffer Words : register(b1, space2)
{
    uint wordsPerRow;
    uint words;
    uint2 padding;
};

RWByteAddressBuffer written : register(u0, space1);

[numthreads(64, 1, 1)]
void main(uint3 id : SV_DispatchThreadID)
{
    uint word = id.y * wordsPerRow + id.x;
    if (word >= words) {
        return;
    }
    uint pixels = width * height;
    uint packed = 0;
    for (uint b = 0; b < 4; b++) {
        uint index = word * 4 + b;
        if (index < pixels && tracePixel(uint2(index % width, index / width)).lit != 0) {
            packed |= 0xFFu << (b * 8);
        }
    }
    written.Store(word * 4, packed);
}
