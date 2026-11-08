// The software ray tracer's walk of a scene, which its three compute shaders
// share: the scene's buffers and the rays' constants, a two-level walk of the
// bounding volume hierarchy built in Java (dev.goldberry.gpu.raytrace.bvh), and
// the same rays, shadows, occlusion and reflections trace.lib.hlsl traces in
// hardware. BvhTraversal is this walk in Java, step for step.
//
// SDL_GPU's bindings for a compute shader: read-only storage buffers are
// (t[n], space0), read-write storage textures then buffers (u[n], space1), and
// uniform buffers (b[n], space2). The shader that includes this declares what
// it writes.

// The rays: RayConstants' 112 bytes, in its order, the hardware tier's push
// constants.
cbuffer Rays : register(b0, space2)
{
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

// The top level's nodes, two float4s each: the low corner and a link, the high
// corner and a leaf's count (Nodes). A leaf's items are instances.
StructuredBuffer<float4> topNodes : register(t0, space0);

// Four float4s an instance (InstanceLevel): the three rows of the transform
// from the world into its mesh's space, then its mesh's root node, its first
// normal, its id in the low 24 bits and its mask in the high 8, and 0.
StructuredBuffer<float4> instances : register(t1, space0);

// Every mesh's nodes, one tree after another; a leaf's items are triangles.
StructuredBuffer<float4> bottomNodes : register(t2, space0);

// Three float4s a triangle (MeshLevels): its first corner and its index in its
// mesh, then its two edges from the first corner.
StructuredBuffer<float4> triangles : register(t3, space0);

// Each mesh's triangles' normals in its own space, the meshes one after
// another.
StructuredBuffer<float4> faceNormals : register(t4, space0);

static const uint OUTPUT_RECORDS = 0;
static const uint OUTPUT_SHADOW_MASK = 1;
static const uint OUTPUT_LIGHTING = 2;

static const uint MISS = 0xFFFFFFFF;
static const uint LEAF = 0x80000000;
static const uint MAX_ID = 0xFFFFFF;
static const uint RAY_MASK = 0xFF;

// BvhBuilder.MAX_DEPTH: no walk needs more stack than a tree is deep.
static const uint STACK = 48;

// BvhTraversal.TINY and EDGE_TOLERANCE.
static const float TINY = 1e-20;
static const float EDGE_TOLERANCE = 1e-6;
static const float NONE = 3.402823466e+38;

struct Ray {
    float3 origin;
    float3 direction;
    float tMin;
    float tMax;
};

struct Hit {
    uint instanceId;   // the instance's id, or MISS
    uint primitiveId;  // the triangle's index in its mesh
    float distance;    // along the unit ray
    float2 barycentrics;
    float3 normal;     // the hit triangle's, in world space, unit length
};

// The inverse of a direction, a zero component made TINY first so that every
// product with it is finite.
float3 safeInverse(float3 d) {
    float3 nonZero = float3(
        abs(d.x) < TINY ? (d.x < 0 ? -TINY : TINY) : d.x,
        abs(d.y) < TINY ? (d.y < 0 ? -TINY : TINY) : d.y,
        abs(d.z) < TINY ? (d.z < 0 ? -TINY : TINY) : d.z);
    return 1.0 / nonZero;
}

// Where a ray enters the box low to high, clipped to tMin and tMax, or NONE.
float enter(float3 low, float3 high, float3 o, float3 inverse, float tMin, float tMax) {
    float3 t1 = (low - o) * inverse;
    float3 t2 = (high - o) * inverse;
    float3 nearSide = min(t1, t2);
    float3 farSide = max(t1, t2);
    float tNear = max(max(max(nearSide.x, nearSide.y), nearSide.z), tMin);
    float tFar = min(min(min(farSide.x, farSide.y), farSide.z), tMax);
    return tNear <= tFar ? tNear : NONE;
}

float enterTop(uint node, float3 o, float3 inverse, float tMin, float tMax) {
    return enter(topNodes[2 * node].xyz, topNodes[2 * node + 1].xyz, o, inverse, tMin, tMax);
}

float enterBottom(uint node, float3 o, float3 inverse, float tMin, float tMax) {
    return enter(bottomNodes[2 * node].xyz, bottomNodes[2 * node + 1].xyz, o, inverse, tMin, tMax);
}

// What a walk has found so far.
struct Found {
    bool hasHit;       // whether anything was hit
    float best;        // the closest distance so far, or the ray's tMax
    uint instance;     // the instance hit, its place in the top level
    uint primitive;    // the triangle hit, its index in its mesh
    float2 barycentrics;
};

// Möller–Trumbore, both faces: whether triangle index is hit closer than the
// best so far, which it then becomes.
bool hitTriangle(uint index, float3 from, float3 along, float tMin, uint instance, inout Found found) {
    float4 first = triangles[3 * index];
    float3 e1 = triangles[3 * index + 1].xyz;
    float3 e2 = triangles[3 * index + 2].xyz;
    float3 p = cross(along, e2);
    float det = dot(e1, p);
    if (det == 0.0) {
        return false;
    }
    float inverseDet = 1.0 / det;
    float3 s = from - first.xyz;
    float u = dot(s, p) * inverseDet;
    if (u < -EDGE_TOLERANCE || u > 1.0 + EDGE_TOLERANCE) {
        return false;
    }
    float3 q = cross(s, e1);
    float v = dot(along, q) * inverseDet;
    if (v < -EDGE_TOLERANCE || u + v > 1.0 + EDGE_TOLERANCE) {
        return false;
    }
    float t = dot(e2, q) * inverseDet;
    if (!(t > tMin && t < found.best)) {
        return false;
    }
    found.best = t;
    found.hasHit = true;
    found.instance = instance;
    found.primitive = asuint(first.w);
    found.barycentrics = float2(u, v);
    return true;
}

// Walks the mesh whose root is root with the ray in its space; whether the
// walk is over, which for an any-hit walk is once anything is hit.
bool walkMesh(uint root, float3 from, float3 along, float tMin, bool anyHit, uint instance, inout Found found) {
    float3 inverse = safeInverse(along);
    if (enterBottom(root, from, inverse, tMin, found.best) == NONE) {
        return false;
    }
    uint stack[STACK];
    float entries[STACK];
    uint depth = 0;
    uint node = root;
    [loop]
    while (true) {
        float4 low = bottomNodes[2 * node];
        float4 high = bottomNodes[2 * node + 1];
        uint link = asuint(low.w);
        uint tail = asuint(high.w);
        uint next = MISS;
        if ((tail & LEAF) != 0) {
            uint count = tail & ~LEAF;
            for (uint k = 0; k < count; k++) {
                if (hitTriangle(link + k, from, along, tMin, instance, found) && anyHit) {
                    return true;
                }
            }
        } else {
            uint left = node + 1;
            float tLeft = enterBottom(left, from, inverse, tMin, found.best);
            float tRight = enterBottom(link, from, inverse, tMin, found.best);
            if (tLeft != NONE && tRight != NONE) {
                bool leftFirst = tLeft <= tRight;
                stack[depth] = leftFirst ? link : left;
                entries[depth] = leftFirst ? tRight : tLeft;
                depth++;
                next = leftFirst ? left : link;
            } else if (tLeft != NONE) {
                next = left;
            } else if (tRight != NONE) {
                next = link;
            }
        }
        while (next == MISS && depth > 0) {
            depth--;
            if (entries[depth] < found.best) {
                next = stack[depth];
            }
        }
        if (next == MISS) {
            return false;
        }
        node = next;
    }
    return false;
}

// Tries instance index of the top level; whether the walk is over.
bool walkInstance(uint index, Ray ray, bool anyHit, inout Found found) {
    float4 row0 = instances[4 * index];
    float4 row1 = instances[4 * index + 1];
    float4 row2 = instances[4 * index + 2];
    uint4 links = asuint(instances[4 * index + 3]);
    if (((links.z >> 24) & RAY_MASK) == 0) {
        return false;
    }
    // The ray in the mesh's space. Its direction is not normalised, so a
    // distance along it is a distance along the world's ray.
    float3 from = float3(
        dot(row0.xyz, ray.origin) + row0.w,
        dot(row1.xyz, ray.origin) + row1.w,
        dot(row2.xyz, ray.origin) + row2.w);
    float3 along = float3(dot(row0.xyz, ray.direction), dot(row1.xyz, ray.direction), dot(row2.xyz, ray.direction));
    return walkMesh(links.x, from, along, ray.tMin, anyHit, index, found) && anyHit;
}

// Walks the scene for ray: the closest hit, or for anyHit the first.
Found walkScene(Ray ray, bool anyHit) {
    Found found;
    found.hasHit = false;
    found.best = ray.tMax;
    found.instance = 0;
    found.primitive = 0;
    found.barycentrics = float2(0.0, 0.0);
    float3 inverse = safeInverse(ray.direction);
    if (enterTop(0, ray.origin, inverse, ray.tMin, found.best) == NONE) {
        return found;
    }
    uint stack[STACK];
    float entries[STACK];
    uint depth = 0;
    uint node = 0;
    [loop]
    while (true) {
        float4 low = topNodes[2 * node];
        float4 high = topNodes[2 * node + 1];
        uint link = asuint(low.w);
        uint tail = asuint(high.w);
        uint next = MISS;
        if ((tail & LEAF) != 0) {
            uint count = tail & ~LEAF;
            for (uint k = 0; k < count; k++) {
                if (walkInstance(link + k, ray, anyHit, found)) {
                    return found;
                }
            }
        } else {
            uint left = node + 1;
            float tLeft = enterTop(left, ray.origin, inverse, ray.tMin, found.best);
            float tRight = enterTop(link, ray.origin, inverse, ray.tMin, found.best);
            if (tLeft != NONE && tRight != NONE) {
                bool leftFirst = tLeft <= tRight;
                stack[depth] = leftFirst ? link : left;
                entries[depth] = leftFirst ? tRight : tLeft;
                depth++;
                next = leftFirst ? left : link;
            } else if (tLeft != NONE) {
                next = left;
            } else if (tRight != NONE) {
                next = link;
            }
        }
        while (next == MISS && depth > 0) {
            depth--;
            if (entries[depth] < found.best) {
                next = stack[depth];
            }
        }
        if (next == MISS) {
            return found;
        }
        node = next;
    }
    return found;
}

// The ray through pixel's centre: trace.lib.hlsl's primaryRay.
Ray primaryRay(uint2 pixel) {
    float2 ndc = (float2(pixel) + 0.5) / float2(width, height) * 2.0 - 1.0;
    Ray ray;
    ray.origin = origin.xyz;
    ray.direction = normalize(forward.xyz + ndc.x * right.xyz - ndc.y * up.xyz);
    ray.tMin = 0.0;
    ray.tMax = tMax;
    return ray;
}

// The closest hit along ray, or a hit whose instanceId is MISS.
Hit closest(Ray ray) {
    Found found = walkScene(ray, false);
    Hit hit;
    hit.instanceId = MISS;
    hit.primitiveId = MISS;
    hit.distance = 0.0;
    hit.barycentrics = float2(0.0, 0.0);
    hit.normal = float3(0.0, 1.0, 0.0);
    if (!found.hasHit) {
        return hit;
    }
    float4 row0 = instances[4 * found.instance];
    float4 row1 = instances[4 * found.instance + 1];
    float4 row2 = instances[4 * found.instance + 2];
    uint4 links = asuint(instances[4 * found.instance + 3]);
    hit.instanceId = links.z & MAX_ID;
    hit.primitiveId = found.primitive;
    hit.distance = found.best;
    hit.barycentrics = found.barycentrics;
    // The mesh's normal through the transpose of the world-to-object rows: the
    // inverse transpose of the instance's transform, as the hardware tier's
    // mul(normal, (float3x3) WorldToObject3x4()) is.
    float3 n = faceNormals[links.y + found.primitive].xyz;
    hit.normal = normalize(n.x * row0.xyz + n.y * row1.xyz + n.z * row2.xyz);
    return hit;
}

// How far off its surface a secondary ray from a hit distance along its ray
// starts: trace.lib.hlsl's bias.
float bias(float distance) {
    return shadowBias * max(1.0, distance);
}

// Whether nothing is hit from from along direction, from tMin to tFar.
bool unblocked(float3 from, float3 direction, float tMin, float tFar) {
    Ray ray;
    ray.origin = from;
    ray.direction = direction;
    ray.tMin = tMin;
    ray.tMax = tFar;
    return !walkScene(ray, true).hasHit;
}

// trace.lib.hlsl's HitRecord: what pixel's primary ray hits, and its shadow ray
// where one is asked for.
struct HitRecord {
    uint instanceId;
    uint primitiveId;
    float distance;
    float u;
    float v;
    uint lit;
};

HitRecord tracePixel(uint2 pixel) {
    Ray ray = primaryRay(pixel);
    Hit hit = closest(ray);
    HitRecord record;
    record.instanceId = hit.instanceId;
    record.primitiveId = hit.primitiveId;
    record.distance = hit.distance;
    record.u = hit.barycentrics.x;
    record.v = hit.barycentrics.y;
    record.lit = 1;
    if (hit.instanceId != MISS && towardLight.w > 0.5) {
        float3 at = ray.origin + ray.direction * hit.distance;
        record.lit = unblocked(at, towardLight.xyz, bias(hit.distance), tMax) ? 1 : 0;
    }
    return record;
}

// trace.lib.hlsl's hash, unit, occlusion directions and facing normal: the same
// rays from the same sequence, so both tiers light a pixel alike.
uint mix(uint x) {
    x ^= x >> 16;
    x *= 0x7feb352du;
    x ^= x >> 15;
    x *= 0x846ca68bu;
    x ^= x >> 16;
    return x;
}

float unit(uint hash) {
    return float(hash >> 8) * (1.0 / 16777216.0);
}

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

float3 facing(float3 normal, float3 direction) {
    return dot(normal, direction) > 0.0 ? -normal : normal;
}

// A pixel's lighting, as trace.lib.hlsl's lightPixel writes it: the packed
// shading word (lit, open, reflected light, hit) and the reflected id.
struct Lit {
    uint shading;
    float reflectedId;
};

Lit lightPixel(uint2 pixel, uint index) {
    Ray ray = primaryRay(pixel);
    Hit hit = closest(ray);
    uint lit = 0xFF;
    uint open = 0xFF;
    uint reflectedLight = 0;
    uint hitAnything = 0;
    float reflectedId = -1.0;
    if (hit.instanceId != MISS) {
        hitAnything = 0xFF;
        float3 at = ray.origin + ray.direction * hit.distance;
        float3 n = facing(hit.normal, ray.direction);
        float tMin = bias(hit.distance);
        float3 light = towardLight.xyz;
        lit = unblocked(at, light, tMin, tMax) ? 0xFF : 0;
        if (occlusionRays > 0) {
            uint escaped = 0;
            for (uint k = 0; k < occlusionRays; k++) {
                float3 direction = occlusionDirection(n, index, k);
                if (unblocked(at, direction, tMin, occlusionRadius)) {
                    escaped++;
                }
            }
            open = (escaped * 0xFF + occlusionRays / 2) / occlusionRays;
        }
        if (reflections != 0) {
            Ray mirror;
            mirror.origin = at;
            mirror.direction = reflect(ray.direction, n);
            mirror.tMin = tMin;
            mirror.tMax = tMax;
            Hit seen = closest(mirror);
            if (seen.instanceId != MISS) {
                reflectedId = float(seen.instanceId);
                float3 seenAt = mirror.origin + mirror.direction * seen.distance;
                float3 seenNormal = facing(seen.normal, mirror.direction);
                float lambert = saturate(dot(seenNormal, light));
                if (lambert > 0.0 && unblocked(seenAt, light, bias(hit.distance + seen.distance), tMax)) {
                    reflectedLight = uint(lambert * 255.0 + 0.5);
                }
            }
        }
    }
    Lit result;
    result.shading = lit | (open << 8) | (reflectedLight << 16) | (hitAnything << 24);
    result.reflectedId = reflectedId;
    return result;
}
