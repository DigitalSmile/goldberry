// The ray tracer's one shader library: primary rays from a pinhole camera,
// one shadow ray toward a directional light from each hit, and what each
// pixel found, written as one of three outputs. The third, the lighting, also
// traces ambient-occlusion rays around each hit and a reflection ray off it.
// Compiled by :gpu:compileRayShaders to SPIR-V alone: it runs on the ray
// tracer's own Vulkan device, never on SDL's.
//
// The push constants are RayConstants' 112 bytes, in its order.

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
    uint output;        // OUTPUT_RECORDS, OUTPUT_SHADOW_MASK or OUTPUT_LIGHTING
    uint occlusionRays; // how many ambient-occlusion rays a hit, for the lighting
    float occlusionRadius; // how far they look
    uint reflections;   // 1 to trace a reflection ray off each hit, for the lighting
};

[[vk::push_constant]] Constants constants;

[[vk::binding(0, 0)]] RaytracingAccelerationStructure scene;

// What the dispatch writes, by constants.output, a ray generation a pixel:
//
// OUTPUT_RECORDS: a HitRecord a pixel, 24 bytes, rows from the top.
//
// OUTPUT_SHADOW_MASK: a byte a pixel, rows from the top with no padding,
// 0xFF where the light reaches it or its ray hit nothing and 0 in shadow. The
// buffer is zeroed before the dispatch, and a lit pixel ORs its byte into
// the word it shares with three others.
//
// OUTPUT_LIGHTING: two planes, rows from the top. The first is 4 bytes a
// pixel: lit (0xFF or 0), how open the hit is to the sky (0 to 0xFF, the share
// of its occlusion rays that escape), how lit the surface its reflection ray
// hits is (0 to 0xFF), and 0xFF where the primary ray hit anything. The
// second, after the first's width * height * 4 bytes, is a float a pixel: the
// id of the instance the reflection ray hit, or -1. An id has 24 bits, which
// a float holds exactly.
[[vk::binding(1, 0)]] RWByteAddressBuffer output;

// Each mesh's triangles' normals in object space, the scene's distinct meshes
// one after another; and for each instance, in the order the top level holds
// them, where its mesh's normals start.
[[vk::binding(2, 0)]] StructuredBuffer<float4> faceNormals;
[[vk::binding(3, 0)]] StructuredBuffer<uint> firstTriangles;

static const uint OUTPUT_RECORDS = 0;
static const uint OUTPUT_SHADOW_MASK = 1;
static const uint OUTPUT_LIGHTING = 2;

// Hits' layout.
struct HitRecord {
    uint instanceId;   // the instance's id, or 0xFFFFFFFF where the ray hit nothing
    uint primitiveId;  // the triangle's index in its mesh
    float distance;    // along the unit ray
    float u;           // the weight of the triangle's second vertex
    float v;           // the weight of its third
    uint lit;          // 1 when the light reaches the hit, or no shadow was asked for
};

static const uint RECORD_BYTES = 24;
static const uint MISS = 0xFFFFFFFF;

struct Payload {
    uint instanceId;
    uint primitiveId;
    float distance;
    float2 barycentrics;
    float3 normal;     // the hit triangle's, in world space, unit length
};

struct ShadowPayload {
    uint lit;
};

// The ray through pixel's centre.
RayDesc primaryRay(uint2 pixel) {
    float2 ndc = (float2(pixel) + 0.5) / float2(constants.width, constants.height) * 2.0 - 1.0;
    RayDesc ray;
    ray.Origin = constants.origin.xyz;
    ray.Direction = normalize(constants.forward.xyz + ndc.x * constants.right.xyz - ndc.y * constants.up.xyz);
    ray.TMin = 0.0;
    ray.TMax = constants.tMax;
    return ray;
}

// The closest hit along ray, or a payload whose instanceId is MISS.
Payload closest(RayDesc ray) {
    Payload payload;
    payload.instanceId = MISS;
    payload.primitiveId = MISS;
    payload.distance = 0.0;
    payload.barycentrics = float2(0.0, 0.0);
    payload.normal = float3(0.0, 1.0, 0.0);
    TraceRay(scene, RAY_FLAG_FORCE_OPAQUE, 0xFF, 0, 1, 0, ray, payload);
    return payload;
}

// How far off its surface a secondary ray from a hit distance along its ray
// starts: an amount that grows with the distance, as the hit at's error
// does.
float bias(float distance) {
    return constants.shadowBias * max(1.0, distance);
}

// Whether nothing is hit from origin along direction, from tMin to tMax.
bool unblocked(float3 origin, float3 direction, float tMin, float tMax) {
    RayDesc ray;
    ray.Origin = origin;
    ray.Direction = direction;
    ray.TMin = tMin;
    ray.TMax = tMax;
    ShadowPayload payload;
    payload.lit = 0;
    TraceRay(scene,
             RAY_FLAG_FORCE_OPAQUE | RAY_FLAG_ACCEPT_FIRST_HIT_AND_END_SEARCH | RAY_FLAG_SKIP_CLOSEST_HIT_SHADER,
             0xFF, 0, 1, 1, ray, payload);
    return payload.lit != 0;
}

// What pixel's primary ray hits, and its shadow ray where one is asked for.
HitRecord tracePixel(uint2 pixel) {
    RayDesc ray = primaryRay(pixel);
    Payload payload = closest(ray);

    HitRecord record;
    record.instanceId = payload.instanceId;
    record.primitiveId = payload.primitiveId;
    record.distance = payload.distance;
    record.u = payload.barycentrics.x;
    record.v = payload.barycentrics.y;
    record.lit = 1;

    if (payload.instanceId != MISS && constants.towardLight.w > 0.5) {
        float3 at = ray.Origin + ray.Direction * payload.distance;
        record.lit = unblocked(at, constants.towardLight.xyz, bias(payload.distance), constants.tMax) ? 1 : 0;
    }
    return record;
}

// A well-mixed 32-bit hash (Chris Wellons' lowbias32): what the occlusion rays'
// directions are drawn from, so a pixel's rays are the same on every run and
// a reference in Java draws the same ones.
uint mix(uint x) {
    x ^= x >> 16;
    x *= 0x7feb352du;
    x ^= x >> 15;
    x *= 0x846ca68bu;
    x ^= x >> 16;
    return x;
}

// The top 24 bits of a hash, as a float from 0 to 1, excluding 1.
float unit(uint hash) {
    return float(hash >> 8) * (1.0 / 16777216.0);
}

// The direction of occlusion ray k of a hit at pixel index, around the
// unit normal n: cosine-weighted over the hemisphere, in an orthonormal basis
// built from n without a branch (Duff et al., 2017).
float3 occlusionDirection(float3 n, uint index, uint k) {
    uint seed = index * 64u + k;
    float u1 = unit(mix(seed));
    float u2 = unit(mix(seed ^ 0x9e3779b9u));
    float r = sqrt(u1);
    float phi = 6.28318530718 * u2;
    float3 local = float3(r * cos(phi), r * sin(phi), sqrt(1.0 - u1));
    float s = n.z >= 0.0 ? 1.0 : -1.0;
    float a = -1.0 / (s + n.z);
    float b = n.x * n.y * a;
    float3 b1 = float3(1.0 + s * n.x * n.x * a, s * b, -s * n.x);
    float3 b2 = float3(b, s + n.y * n.y * a, -n.y);
    return normalize(b1 * local.x + b2 * local.y + n * local.z);
}

// A normal turned to face back along direction: a triangle seen from behind
// is lit and occluded from the side the ray sees.
float3 facing(float3 normal, float3 direction) {
    return dot(normal, direction) > 0.0 ? -normal : normal;
}

// Writes pixel index's lighting: its shadow, how open it is, and what its
// reflection sees.
void lightPixel(uint2 pixel, uint index) {
    RayDesc ray = primaryRay(pixel);
    Payload payload = closest(ray);
    uint lit = 0xFF;
    uint open = 0xFF;
    uint reflectedLight = 0;
    uint hit = 0;
    float reflectedId = -1.0;
    if (payload.instanceId != MISS) {
        hit = 0xFF;
        float3 at = ray.Origin + ray.Direction * payload.distance;
        float3 n = facing(payload.normal, ray.Direction);
        float tMin = bias(payload.distance);
        float3 light = constants.towardLight.xyz;
        lit = unblocked(at, light, tMin, constants.tMax) ? 0xFF : 0;
        if (constants.occlusionRays > 0) {
            uint escaped = 0;
            for (uint k = 0; k < constants.occlusionRays; k++) {
                float3 direction = occlusionDirection(n, index, k);
                if (unblocked(at, direction, tMin, constants.occlusionRadius)) {
                    escaped++;
                }
            }
            open = (escaped * 0xFF + constants.occlusionRays / 2) / constants.occlusionRays;
        }
        if (constants.reflections != 0) {
            RayDesc mirror;
            mirror.Origin = at;
            mirror.Direction = reflect(ray.Direction, n);
            mirror.TMin = tMin;
            mirror.TMax = constants.tMax;
            Payload seen = closest(mirror);
            if (seen.instanceId != MISS) {
                reflectedId = float(seen.instanceId);
                float3 seenAt = mirror.Origin + mirror.Direction * seen.distance;
                float3 seenNormal = facing(seen.normal, mirror.Direction);
                float lambert = saturate(dot(seenNormal, light));
                if (lambert > 0.0
                        && unblocked(seenAt, light, bias(payload.distance + seen.distance), constants.tMax)) {
                    reflectedLight = uint(lambert * 255.0 + 0.5);
                }
            }
        }
    }
    output.Store(index * 4, lit | (open << 8) | (reflectedLight << 16) | (hit << 24));
    output.Store(constants.width * constants.height * 4 + index * 4, asuint(reflectedId));
}

[shader("raygeneration")]
void RayGen() {
    uint2 pixel = DispatchRaysIndex().xy;
    uint index = pixel.y * constants.width + pixel.x;
    if (constants.output == OUTPUT_LIGHTING) {
        lightPixel(pixel, index);
        return;
    }
    HitRecord record = tracePixel(pixel);
    if (constants.output == OUTPUT_RECORDS) {
        output.Store<HitRecord>(index * RECORD_BYTES, record);
    } else if (record.lit != 0) {
        output.InterlockedOr(index & ~3u, 0xFFu << ((index & 3u) * 8));
    }
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
    // The object-space normal through the inverse transpose of the instance's
    // transform, so a scaled instance's normals stay perpendicular.
    float3 normal = faceNormals[firstTriangles[InstanceIndex()] + PrimitiveIndex()].xyz;
    payload.normal = normalize(mul(normal, (float3x3) WorldToObject3x4()));
}
