// One triangle covering the whole target, made from the vertex id alone, with
// a texture coordinate per pixel: what the texture model's tests sample a
// texture through. Does not ship.
//
// Clip space is x right, y up, -1 to 1; texture coordinates run 0 to 1 from
// the top left, as the toolkit's quad has them.

struct Output
{
    float2 uv : TEXCOORD0;
    float4 position : SV_Position;
};

Output main(uint id : SV_VertexID)
{
    float2 corner = float2(float((id << 1u) & 2u), float(id & 2u));
    Output output;
    output.position = float4(corner * 2.0 - 1.0, 0.0, 1.0);
    output.uv = float2(corner.x, 1.0 - corner.y);
    return output;
}
