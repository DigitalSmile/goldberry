package dev.goldberry.gpu.raytrace;

import java.util.Objects;
import java.util.Optional;

/// One picture's worth of rays: a primary ray through each pixel's centre
/// and, where one is asked for, a shadow ray from each hit toward a light.
///
/// @param camera       where the primary rays come from
/// @param width        the picture's width, in pixels
/// @param height       the picture's height, in pixels
/// @param towardLight  the unit direction toward a directional light, the sun,
///                     when each hit should be tested for shadow; empty for
///                     none, and every hit is then lit
/// @param maxDistance  how far a ray looks; a hit farther than this is a miss
/// @param shadowBias   how far off its surface a shadow ray starts, per unit of
///                     the primary ray's distance (and at least this much), so
///                     it does not hit the triangle it starts on
public record TraceRequest(
        Camera camera, int width, int height, Optional<Vector3> towardLight, float maxDistance, float shadowBias) {

    /// How far rays look unless asked otherwise.
    public static final float DEFAULT_MAX_DISTANCE = 1.0e6f;

    /// The shadow bias unless asked otherwise.
    public static final float DEFAULT_SHADOW_BIAS = 1.0e-3f;

    /// @throws IllegalArgumentException when a size is below one, the light's
    ///                                  direction is not of unit length, or a
    ///                                  distance is not positive and finite
    public TraceRequest {
        Objects.requireNonNull(camera, "camera");
        Objects.requireNonNull(towardLight, "towardLight");
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("a picture of " + width + "×" + height);
        }
        towardLight.ifPresent(direction -> {
            if (Math.abs(direction.length() - 1) > 1e-4f) {
                throw new IllegalArgumentException("the light's direction is of unit length, not " + direction);
            }
        });
        if (!(maxDistance > 0) || !Float.isFinite(maxDistance)) {
            throw new IllegalArgumentException("a maximum distance is positive and finite, not " + maxDistance);
        }
        if (!(shadowBias > 0) || !Float.isFinite(shadowBias)) {
            throw new IllegalArgumentException("a shadow bias is positive and finite, not " + shadowBias);
        }
    }

    /// Primary rays alone, through each pixel of a `width` by `height` picture.
    ///
    /// @throws IllegalArgumentException when a size is below one
    public static TraceRequest of(Camera camera, int width, int height) {
        return new TraceRequest(camera, width, height, Optional.empty(), DEFAULT_MAX_DISTANCE, DEFAULT_SHADOW_BIAS);
    }

    /// This, with a shadow ray from each hit toward `direction`, which need not
    /// be of unit length.
    ///
    /// @throws IllegalArgumentException when `direction` is zero
    public TraceRequest withShadowsToward(Vector3 direction) {
        return new TraceRequest(camera, width, height, Optional.of(direction.normalized()), maxDistance, shadowBias);
    }

    /// This, looking no farther than `distance`.
    ///
    /// @throws IllegalArgumentException when it is not positive and finite
    public TraceRequest withMaxDistance(float distance) {
        return new TraceRequest(camera, width, height, towardLight, distance, shadowBias);
    }

    /// This, with shadow rays starting `bias` off their surface.
    ///
    /// @throws IllegalArgumentException when it is not positive and finite
    public TraceRequest withShadowBias(float bias) {
        return new TraceRequest(camera, width, height, towardLight, maxDistance, bias);
    }

    /// How many primary rays it traces.
    public long pixels() {
        return (long) width * height;
    }
}
