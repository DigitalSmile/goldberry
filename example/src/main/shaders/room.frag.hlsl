// The ray-traced room's lighting. Each solid's colour is lit by the sun's
// angle and an ambient term; where the hardware ray tracer traced this frame,
// its shading darkens what is in shadow and what is occluded, and a shiny
// surface mixes in the colour its reflection ray saw. The tracer's picture is
// the canvas's size, so a pixel reads its own texel.
//
// SDL_GPU's bindings: textures and samplers (t[n], space2) and (s[n], space2),
// uniforms (b[n], space3).

// DXC defines __spirv__ only when it writes SPIR-V; DXIL has no such attribute.
#ifdef __spirv__
#define COMBINED [[vk::combinedImageSampler]]
#else
#define COMBINED
#endif

// Lighting's shading plane: lit, open, the reflection's light, hit.
COMBINED Texture2D<float4> shading : register(t0, space2);
COMBINED SamplerState shadingSampler : register(s0, space2);
// Lighting's reflected ids: the instance a pixel's reflection saw, or -1.
COMBINED Texture2D<float> reflected : register(t1, space2);
COMBINED SamplerState reflectedSampler : register(s1, space2);
// The floor's checks, mipmapped, read with the panel's anisotropy.
COMBINED Texture2D<float4> checks : register(t2, space2);
COMBINED SamplerState checksSampler : register(s2, space2);

static const uint MATERIALS = 6;

cbuffer Scene : register(b0, space3)
{
    // xyz: toward the sun, unit length; w: 1 when the textures hold this
    // frame's trace.
    float4 light;
    // 1 or 0 each: shadows, occlusion, reflections; w unused.
    float4 effects;
    // xy: the traced picture's last pixel; zw unused.
    float4 traced;
    // rgb: what a reflection that sees nothing shows.
    float4 sky;
    // rgb: each material's colour; a: how much of its reflection it shows.
    float4 materials[MATERIALS];
};

cbuffer Solid : register(b1, space3)
{
    // x: the solid's material; y: 1 for the checkered floor.
    float4 solid;
};

// The share of the light that comes from the sky; the rest is the sun.
static const float AMBIENT = 0.22;

float4 main(float3 normal : TEXCOORD0, float3 world : TEXCOORD1, float4 position : SV_Position) : SV_Target0
{
    uint material = min((uint) solid.x, MATERIALS - 1);
    float3 base = materials[material].rgb;
    if (solid.y > 0.5) {
        base *= checks.Sample(checksSampler, world.xz * 0.5).rgb;
    }
    float3 n = normalize(normal);
    float sun = saturate(dot(n, light.xyz));

    float shadow = 1.0;
    float open = 1.0;
    float3 colour;
    bool fromTrace = light.w > 0.5;
    int3 texel = int3(min(int2(position.xy), int2(traced.xy)), 0);
    float4 traceShading = shading.Load(texel);
    if (fromTrace && effects.x > 0.5) {
        shadow = traceShading.r;
    }
    if (fromTrace && effects.y > 0.5) {
        open = traceShading.g;
    }
    colour = base * (AMBIENT * open + (1.0 - AMBIENT) * sun * shadow);

    float shine = materials[material].a;
    if (fromTrace && effects.z > 0.5 && shine > 0.0) {
        float id = reflected.Load(texel);
        float3 seen = sky.rgb;
        if (id >= 0.0) {
            float3 other = materials[min((uint) id, MATERIALS - 1)].rgb;
            seen = other * (AMBIENT + (1.0 - AMBIENT) * traceShading.b);
        }
        colour = lerp(colour, seen, shine);
    }
    return float4(colour, 1.0);
}
