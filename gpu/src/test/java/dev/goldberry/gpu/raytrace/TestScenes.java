package dev.goldberry.gpu.raytrace;

/// The scenes the ray-tracing tests trace: a ground quad, unit cubes on it,
/// a camera above looking down at them, and a low sun that throws their
/// shadows across the ground.
final class TestScenes {

    /// The ground's instance id.
    static final int GROUND = 1;

    /// The first cube's id; the others count up from it.
    static final int CUBE = 10;

    private TestScenes() {}

    /// A unit cube centred on the origin: 8 vertices, 12 triangles.
    static TriangleMesh cube() {
        var positions = new float[] {
            -0.5f, -0.5f, -0.5f, 0.5f, -0.5f, -0.5f, 0.5f, 0.5f, -0.5f, -0.5f, 0.5f, -0.5f, -0.5f, -0.5f, 0.5f, 0.5f,
            -0.5f, 0.5f, 0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f
        };
        var indices = new int[] {
            0, 2, 1, 0, 3, 2, // back
            4, 5, 6, 4, 6, 7, // front
            0, 1, 5, 0, 5, 4, // bottom
            3, 7, 6, 3, 6, 2, // top
            0, 4, 7, 0, 7, 3, // left
            1, 2, 6, 1, 6, 5 // right
        };
        return TriangleMesh.of(positions, indices);
    }

    /// A square of side `size` in the `y = 0` plane, centred on the origin.
    static TriangleMesh ground(float size) {
        var h = size / 2;
        return TriangleMesh.of(new float[] {-h, 0, -h, h, 0, -h, h, 0, h, -h, 0, h}, new int[] {0, 2, 1, 0, 3, 2});
    }

    /// The ground and three cubes of one shared mesh: one resting on it, one
    /// turned, one floating and scaled.
    static SceneGeometry cubesOnGround() {
        var cube = cube();
        return SceneGeometry.of(
                Instance.of(ground(20), Transform.IDENTITY, GROUND),
                Instance.of(cube, Transform.translation(new Vector3(0, 0.5f, 0)), CUBE),
                Instance.of(
                        cube,
                        Transform.rotationY(Math.toRadians(30)).then(Transform.translation(new Vector3(2, 0.5f, -1))),
                        CUBE + 1),
                Instance.of(
                        cube,
                        Transform.scaling(0.6f).then(Transform.translation(new Vector3(-2, 1.5f, 0.5f))),
                        CUBE + 2));
    }

    /// Looking down at the cubes from above and in front.
    static Camera camera() {
        return Camera.lookingAt(new Vector3(0, 6, 8), new Vector3(0, 0.5f, 0), 50);
    }

    /// Toward a sun low in the west, so shadows fall east across the ground.
    static Vector3 sun() {
        return new Vector3(-1, 1.2f, 0.3f).normalized();
    }
}
