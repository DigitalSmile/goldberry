// The software ray tracer's lighting, a thread a pixel, straight into the two
// textures a renderer samples (LightingTargets): the shading, lit, open,
// reflected light and hit, in an RGBA8 texture, and the reflected id in an R32F
// one. The values are trace.comp's, which the hardware tier's are too.
#ifdef __spirv__
#define RGBA8 [[vk::image_format("rgba8")]]
#define R32F [[vk::image_format("r32f")]]
#else
#define RGBA8
#define R32F
#endif

#include "raytrace.hlsli"

RGBA8 RWTexture2D<unorm float4> shading : register(u0, space1);
R32F RWTexture2D<float> reflectedIds : register(u1, space1);

[numthreads(8, 8, 1)]
void main(uint3 id : SV_DispatchThreadID)
{
    if (id.x >= width || id.y >= height) {
        return;
    }
    Lit lighting = lightPixel(id.xy, id.y * width + id.x);
    uint word = lighting.shading;
    shading[id.xy] = float4(word & 0xFF, (word >> 8) & 0xFF, (word >> 16) & 0xFF, word >> 24) / 255.0;
    reflectedIds[id.xy] = lighting.reflectedId;
}
