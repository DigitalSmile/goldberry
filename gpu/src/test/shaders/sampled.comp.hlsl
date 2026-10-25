// Samples one texture at the centre of each of four columns across its middle
// row, and writes the four colours into the row of a storage texture: a
// compute pass's proof that a texture and its sampler are bound. Does not ship.
//
// SDL_GPU's bindings for a compute shader: sampled textures and their samplers
// are (t[n], space0) and (s[n], space0), before any read-only storage, and
// read-write storage textures are (u[n], space1).
#ifdef __spirv__
#define COMBINED [[vk::combinedImageSampler]]
#define RGBA8 [[vk::image_format("rgba8")]]
#else
#define COMBINED
#define RGBA8
#endif

COMBINED Texture2D<float4> image : register(t0, space0);
COMBINED SamplerState imageSampler : register(s0, space0);
RGBA8 RWTexture2D<unorm float4> colours : register(u0, space1);

[numthreads(4, 1, 1)]
void main(uint3 id : SV_DispatchThreadID)
{
    colours[id.xy] = image.SampleLevel(imageSampler, float2((id.x + 0.5) / 4.0, 0.5), 0);
}
