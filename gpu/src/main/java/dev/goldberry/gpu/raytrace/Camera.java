package dev.goldberry.gpu.raytrace;

import java.util.Objects;

/// A pinhole camera: where the primary rays start, and which way each pixel's
/// goes. Rows run from the top of the picture, as a texture's do.
///
/// The same camera an application rasterizes with gives rays through the same
/// pixel centres, so what is traced lines up with what is drawn.
///
/// @param position              where the rays start
/// @param target                what the middle of the picture looks at
/// @param up                    which way is up; need not be at right angles
///                              to the view
/// @param verticalFieldOfView   the angle from the top of the picture to the
///                              bottom, in degrees, more than 0 and less than
///                              180
public record Camera(Vector3 position, Vector3 target, Vector3 up, float verticalFieldOfView) {

    /// @throws IllegalArgumentException when `position` is `target`, `up` is
    ///                                  along the view, or the field of view is
    ///                                  out of range
    public Camera {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(up, "up");
        if (!(verticalFieldOfView > 0 && verticalFieldOfView < 180)) {
            throw new IllegalArgumentException(
                    "a vertical field of view is between 0 and 180 degrees, not " + verticalFieldOfView);
        }
        var forward = target.minus(position);
        if (forward.length() == 0) {
            throw new IllegalArgumentException("a camera at " + position + " cannot look at itself");
        }
        if (forward.normalized().cross(up).length() < 1e-6f) {
            throw new IllegalArgumentException("up " + up + " is along the view, so it says nothing");
        }
    }

    /// A camera at `position` looking at `target` with `+y` up.
    ///
    /// @throws IllegalArgumentException as the constructor does
    public static Camera lookingAt(Vector3 position, Vector3 target, float verticalFieldOfView) {
        return new Camera(position, target, Vector3.UP, verticalFieldOfView);
    }

    /// The view's frame for a `width` by `height` picture: the unit forward
    /// direction, and right and up scaled to reach the picture's edges.
    ///
    /// @throws IllegalArgumentException when either size is below one
    public Basis basis(int width, int height) {
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("a picture of " + width + "×" + height);
        }
        var forward = target.minus(position).normalized();
        var right = forward.cross(up).normalized();
        var trueUp = right.cross(forward);
        var halfHeight = (float) Math.tan(Math.toRadians(verticalFieldOfView) / 2);
        var halfWidth = halfHeight * width / height;
        return new Basis(position, forward, right.times(halfWidth), trueUp.times(halfHeight));
    }

    /// The ray through the centre of pixel `x`, `y` of a `width` by `height`
    /// picture, as the ray tracer makes it.
    ///
    /// @throws IllegalArgumentException when the pixel is outside the picture
    public Ray ray(int x, int y, int width, int height) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            throw new IllegalArgumentException("pixel " + x + "," + y + " of " + width + "×" + height);
        }
        return basis(width, height).ray((x + 0.5f) / width * 2 - 1, (y + 0.5f) / height * 2 - 1);
    }

    /// A camera's frame for one picture size.
    ///
    /// @param origin  where the rays start
    /// @param forward the view direction, unit length
    /// @param right   right, as long as the picture is half wide at unit
    ///                distance
    /// @param up      up, as long as the picture is half high at unit distance
    public record Basis(Vector3 origin, Vector3 forward, Vector3 right, Vector3 up) {

        /// The ray through `ndcX`, `ndcY`: -1 to 1 from the left and from the
        /// top.
        public Ray ray(float ndcX, float ndcY) {
            return new Ray(
                    origin,
                    forward.plus(right.times(ndcX)).minus(up.times(ndcY)).normalized());
        }
    }
}
