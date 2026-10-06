// Scales every float of one buffer into another: a compute pass's proof that a
// storage buffer is read, a uniform is read, and a storage buffer is written,
// which a draw then reads. Does not ship.
//
// SDL_GPU's bindings for a compute shader: read-only storage buffers are
// (t[n], space0) after any samplers and storage textures, read-write storage
// buffers (u[n], space1) after any read-write storage textures, and uniform
// buffers (b[n], space2).

StructuredBuffer<float> input : register(t0, space0);
RWStructuredBuffer<float> output : register(u0, space1);

cbuffer Scale : register(b0, space2)
{
    float scale;
    float3 padding;
};

[numthreads(64, 1, 1)]
void main(uint3 id : SV_DispatchThreadID)
{
    output[id.x] = input[id.x] * scale;
}
