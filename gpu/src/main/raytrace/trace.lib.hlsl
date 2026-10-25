// The ray tracer's one shader library: primary rays from a pinhole camera,
// one shadow ray toward a directional light from each hit, and a hit record
// per pixel. Compiled by :gpu:compileRayShaders to SPIR-V alone: it runs on
// the ray tracer's own Vulkan device, never on SDL's.
//
// The push constants are RayConstants' 96 bytes, in its order.

struct Constants {
    float4 origin;      // xyz: the camera; w: unused
    float4 forward;     // xyz: the view direction, unit length
    float4 right;       // xyz: right, scaled by tan(fov / 2) * aspect
    float4 up;          // xyz: up, scaled by tan(fov / 2)
    float4 towardLight; // xyz: toward the light, unit length; w: 1 to trace shadows
    uint width;
    uint height;
    float tMax;
    float shadowBias;
};

[[vk::push_constant]] Constants constants;

[[vk::binding(0, 0)]] RaytracingAccelerationStructure scene;

// HitRecords' layout: 24 bytes a pixel, rows from the top.
struct HitRecord {
    uint instanceId;   // the instance's id, or 0xFFFFFFFF where the ray hit nothing
    uint primitiveId;  // the triangle's index in its mesh
    float distance;    // along the unit ray
    float u;           // the weight of the triangle's second vertex
    float v;           // the weight of its third
    uint lit;          // 1 when the light reaches the hit, or no shadow was asked for
};

[[vk::binding(1, 0)]] RWStructuredBuffer<HitRecord> hits;

static const uint MISS = 0xFFFFFFFF;

struct Payload {
    uint instanceId;
    uint primitiveId;
    float distance;
    float2 barycentrics;
};

struct ShadowPayload {
    uint lit;
};

[shader("raygeneration")]
void RayGen() {
    uint2 pixel = DispatchRaysIndex().xy;
    float2 ndc = (float2(pixel) + 0.5) / float2(constants.width, constants.height) * 2.0 - 1.0;

    RayDesc ray;
    ray.Origin = constants.origin.xyz;
    ray.Direction = normalize(constants.forward.xyz + ndc.x * constants.right.xyz - ndc.y * constants.up.xyz);
    ray.TMin = 0.0;
    ray.TMax = constants.tMax;

    Payload payload;
    payload.instanceId = MISS;
    payload.primitiveId = MISS;
    payload.distance = 0.0;
    payload.barycentrics = float2(0.0, 0.0);
    TraceRay(scene, RAY_FLAG_FORCE_OPAQUE, 0xFF, 0, 1, 0, ray, payload);

    HitRecord record;
    record.instanceId = payload.instanceId;
    record.primitiveId = payload.primitiveId;
    record.distance = payload.distance;
    record.u = payload.barycentrics.x;
    record.v = payload.barycentrics.y;
    record.lit = 1;

    if (payload.instanceId != MISS && constants.towardLight.w > 0.5) {
        RayDesc shadow;
        shadow.Origin = ray.Origin + ray.Direction * payload.distance;
        shadow.Direction = constants.towardLight.xyz;
        // Off the surface it starts on, by an amount that grows with the
        // distance, as the hit point's error does.
        shadow.TMin = constants.shadowBias * max(1.0, payload.distance);
        shadow.TMax = constants.tMax;

        ShadowPayload shadowPayload;
        shadowPayload.lit = 0;
        TraceRay(scene,
                 RAY_FLAG_FORCE_OPAQUE | RAY_FLAG_ACCEPT_FIRST_HIT_AND_END_SEARCH | RAY_FLAG_SKIP_CLOSEST_HIT_SHADER,
                 0xFF, 0, 1, 1, shadow, shadowPayload);
        record.lit = shadowPayload.lit;
    }

    hits[pixel.y * constants.width + pixel.x] = record;
}

[shader("miss")]
void Miss(inout Payload payload) {
    payload.instanceId = MISS;
}

[shader("miss")]
void ShadowMiss(inout ShadowPayload payload) {
    payload.lit = 1;
}

[shader("closesthit")]
void ClosestHit(inout Payload payload, in BuiltInTriangleIntersectionAttributes attributes) {
    payload.instanceId = InstanceID();
    payload.primitiveId = PrimitiveIndex();
    payload.distance = RayTCurrent();
    payload.barycentrics = attributes.barycentrics;
}
