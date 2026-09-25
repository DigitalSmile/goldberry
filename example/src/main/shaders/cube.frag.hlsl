// The lit colour cube.vert passes on, as it is: opaque, so premultiplied too.

float4 main(float4 colour : TEXCOORD0) : SV_Target0
{
    return colour;
}
