package dev.goldberry.gpu.raytrace;

import java.util.Objects;

/// One picture's worth of lighting: from each primary hit a shadow ray toward
/// the light, rays around the hit for ambient occlusion, and a reflection ray
/// off it, which [RayTracer#traceLighting] answers with a [Lighting].
///
/// ```java
/// var request = LightingRequest.of(TraceRequest.of(camera, 640, 360).withShadowsToward(sun))
///         .withOcclusion(16, 0.75f);
/// ```
///
/// The occlusion rays are cosine-weighted over the hemisphere a hit faces,
/// from a fixed sequence a pixel, so a still scene lights the same on every
/// trace. Each looks [#occlusionRadius] far: what is nearer darkens the hit.
///
/// @param rays            the primary rays, and the light their shadow rays
///                        go toward
/// @param occlusionRays   how many occlusion rays a hit: 0 (no occlusion, every
///                        hit open) to [#MAX_OCCLUSION_RAYS]
/// @param occlusionRadius how far an occlusion ray looks
/// @param reflections     whether a reflection ray is traced off each hit
public record LightingRequest(TraceRequest rays, int occlusionRays, float occlusionRadius, boolean reflections) {

    /// The most occlusion rays a hit.
    public static final int MAX_OCCLUSION_RAYS = 64;

    /// How many occlusion rays a hit unless asked otherwise.
    public static final int DEFAULT_OCCLUSION_RAYS = 8;

    /// How far occlusion rays look unless asked otherwise.
    public static final float DEFAULT_OCCLUSION_RADIUS = 1f;

    /// @throws IllegalArgumentException when the rays have no light, the ray
    ///                                  count is outside 0 to
    ///                                  [#MAX_OCCLUSION_RAYS], or the radius is
    ///                                  not positive and finite
    public LightingRequest {
        Objects.requireNonNull(rays, "rays");
        if (rays.towardLight().isEmpty()) {
            throw new IllegalArgumentException("lighting needs a light: ask the rays withShadowsToward(direction)");
        }
        if (occlusionRays < 0 || occlusionRays > MAX_OCCLUSION_RAYS) {
            throw new IllegalArgumentException(
                    occlusionRays + " occlusion rays; a hit takes 0 to " + MAX_OCCLUSION_RAYS);
        }
        if (!(occlusionRadius > 0) || !Float.isFinite(occlusionRadius)) {
            throw new IllegalArgumentException("an occlusion radius is positive and finite, not " + occlusionRadius);
        }
    }

    /// Shadows, [#DEFAULT_OCCLUSION_RAYS] occlusion rays [#DEFAULT_OCCLUSION_RADIUS]
    /// long, and reflections, for `rays`.
    ///
    /// @throws IllegalArgumentException when `rays` has no light
    public static LightingRequest of(TraceRequest rays) {
        return new LightingRequest(rays, DEFAULT_OCCLUSION_RAYS, DEFAULT_OCCLUSION_RADIUS, true);
    }

    /// This, with `count` occlusion rays a hit looking `radius` far; 0 turns
    /// occlusion off.
    ///
    /// @throws IllegalArgumentException as the constructor
    public LightingRequest withOcclusion(int count, float radius) {
        return new LightingRequest(rays, count, radius, reflections);
    }

    /// This, with reflection rays or without.
    public LightingRequest withReflections(boolean value) {
        return new LightingRequest(rays, occlusionRays, occlusionRadius, value);
    }

    /// How many rays it traces at most: a primary, a shadow, the occlusion
    /// rays, and a reflection and its shadow, a pixel.
    public long maxRays() {
        return rays.pixels() * (2L + occlusionRays + (reflections ? 2 : 0));
    }
}
