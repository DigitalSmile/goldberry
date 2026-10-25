// Writes four words from a uniform into a buffer: an indirect command the GPU
// wrote, which a draw or a dispatch then reads its counts from. What a culling
// pass does, without the culling. Does not ship.
//
// SDL_GPU's bindings for a compute shader: read-write storage buffers are
// (u[n], space1), and uniform buffers (b[n], space2).

RWStructuredBuffer<uint> commands : register(u0, space1);

cbuffer Command : register(b0, space2)
{
    uint4 words;
};

[numthreads(1, 1, 1)]
void main(uint3 id : SV_DispatchThreadID)
{
    commands[0] = words.x;
    commands[1] = words.y;
    commands[2] = words.z;
    commands[3] = words.w;
}
