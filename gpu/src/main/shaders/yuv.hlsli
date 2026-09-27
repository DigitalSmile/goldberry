// What the two Y'CbCr shaders share: the uniform block and the arithmetic.
// Included, not compiled on its own: :gpu:compileShaders compiles *.hlsl only.
//
// A sample becomes a code value normalised to its bit depth (x of the
// transforms: 1 for 8-bit, 65535/64/1023 for 10 bits in the high bits of 16,
// 65535/1023 for 10 in the low bits), loses its range's offset (y) and is
// scaled by its gain (z). Then the matrix: R = Y + a Cr, G = Y + b Cb + c Cr,
// B = Y + d Cb. Where a chroma sample sits is chromaTransform.w, a
// texture-coordinate offset left of a centred sample: 0 for centred chroma,
// which is how swscale's conversion for CPU present sites it, and a quarter of
// a chroma texel for MPEG-2's left siting (YuvConversion.Siting).

// DXC defines __spirv__ only when it writes SPIR-V; DXIL has no such attribute.
#ifdef __spirv__
#define COMBINED [[vk::combinedImageSampler]]
#else
#define COMBINED
#endif

cbuffer Yuv : register(b0, space3)
{
    float4 lumaTransform;   // scale to code, offset, gain, unused
    float4 chromaTransform; // scale to code, offset, gain, horizontal siting offset
    float4 coefficients;    // Cr to R, Cb to G, Cr to G, Cb to B
};

float2 chromaCoordinates(float2 uv)
{
    return float2(uv.x + chromaTransform.w, uv.y);
}

float4 toRgb(float y, float cb, float cr)
{
    float luma = (y * lumaTransform.x - lumaTransform.y) * lumaTransform.z;
    float blue = (cb * chromaTransform.x - chromaTransform.y) * chromaTransform.z;
    float red = (cr * chromaTransform.x - chromaTransform.y) * chromaTransform.z;
    float3 rgb = float3(
        luma + coefficients.x * red,
        luma + coefficients.y * blue + coefficients.z * red,
        luma + coefficients.w * blue);
    return float4(saturate(rgb), 1.0);
}
