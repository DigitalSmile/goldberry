// Fills with one colour, premultiplied: a test layer, and a hole's colour.
//
// SDL_GPU's bindings: a fragment shader's uniform buffers are (b[n], space3),
// SPIR-V set 3, and [[buffer(n)]] in MSL.

cbuffer Colour : register(b0, space3)
{
    float4 colour;
};

float4 main(float2 uv : TEXCOORD0) : SV_Target0
{
    return colour;
}
