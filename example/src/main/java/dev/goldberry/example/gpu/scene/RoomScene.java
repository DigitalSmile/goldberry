package dev.goldberry.example.gpu.scene;

import java.util.ArrayList;
import java.util.List;

import dev.goldberry.example.gpu.Matrices;
import dev.goldberry.gpu.raytrace.Camera;
import dev.goldberry.gpu.raytrace.Instance;
import dev.goldberry.gpu.raytrace.SceneGeometry;
import dev.goldberry.gpu.raytrace.Transform;
import dev.goldberry.gpu.raytrace.Vector3;

/// The room: a checkered floor, a red block, a mirrored cube that turns, a
/// tall pillar, a ball, and a small cube circling them, under a low sun, seen
/// from one camera. Where each is at a moment, and what it is made of.
///
/// Each solid's instance id is its material's index, so what a reflection ray
/// hits names the colour it sees.
final class RoomScene {

    /// Instance ids, which are also material indices.
    static final int FLOOR = 0;

    static final int BLOCK = 1;
    static final int MIRROR = 2;
    static final int PILLAR = 3;
    static final int ORBIT = 4;
    static final int BALL = 5;

    /// Each material's colour, then how much of what its reflection sees it
    /// shows: Nord's snow for the floor, aurora and frost for the rest.
    static final float[][] MATERIALS = {
        {0.85f, 0.87f, 0.91f, 0.15f},
        {0.749f, 0.380f, 0.416f, 0.05f},
        {0.80f, 0.82f, 0.86f, 0.70f},
        {0.506f, 0.631f, 0.757f, 0.10f},
        {0.922f, 0.796f, 0.545f, 0.05f},
        {0.639f, 0.745f, 0.549f, 0.35f}
    };

    /// What a reflection that sees nothing shows: Nord's polar night, lifted.
    static final float[] SKY = {0.30f, 0.34f, 0.42f};

    /// Where the camera is, and what it looks at.
    static final float[] EYE = {5.2f, 3.4f, 6.6f};

    static final float[] TARGET = {0f, 0.7f, 0f};

    /// The camera's vertical field of view, in degrees.
    static final float FIELD_OF_VIEW = 40;

    /// Toward the sun: low on the left, so shadows fall to the right and back.
    static final Vector3 SUN = new Vector3(-0.55f, 0.75f, 0.38f).normalized();

    /// One solid where it is: its id, its mesh, and its place as a row-major
    /// 4x4.
    static final class Placed {

        private final int id;
        private final Mesh mesh;
        private final float[] model;

        Placed(int id, Mesh mesh, float[] model) {
            this.id = id;
            this.mesh = mesh;
            this.model = model.clone();
        }

        int id() {
            return id;
        }

        Mesh mesh() {
            return mesh;
        }

        /// Its place: a copy.
        float[] model() {
            return model.clone();
        }
    }

    private final Mesh cube = Mesh.cube();
    private final Mesh floor = Mesh.floor(14);
    private final Mesh sphere = Mesh.sphere(24, 14);

    /// The distinct meshes, which the renderer uploads once each.
    List<Mesh> meshes() {
        return List.of(floor, cube, sphere);
    }

    /// Every solid at `seconds` into the canvas's life.
    List<Placed> at(double seconds) {
        var placed = new ArrayList<Placed>();
        placed.add(new Placed(FLOOR, floor, Matrices.translate(0, 0, 0)));
        placed.add(new Placed(
                BLOCK,
                cube,
                Matrices.multiply(
                        Matrices.translate(-1.3f, 0.55f, 0.4f),
                        Matrices.multiply(Matrices.rotateY(0.4), Matrices.scale(1.1f)))));
        placed.add(new Placed(
                MIRROR,
                cube,
                Matrices.multiply(
                        Matrices.translate(0.9f, 0.7f, -0.2f),
                        Matrices.multiply(Matrices.rotateY(0.3 + 0.35 * seconds), Matrices.scale(1.4f)))));
        placed.add(new Placed(
                PILLAR, cube, Matrices.multiply(Matrices.translate(-0.6f, 1.3f, -1.9f), stretchedY(0.6f, 2.6f))));
        placed.add(new Placed(
                BALL, sphere, Matrices.multiply(Matrices.translate(1.7f, 0.5f, 1.6f), Matrices.scale(1.0f))));
        var angle = 0.8 * seconds;
        placed.add(new Placed(
                ORBIT,
                cube,
                Matrices.multiply(
                        Matrices.translate((float) (2.6 * Math.cos(angle)), 0.25f, (float) (2.6 * Math.sin(angle))),
                        Matrices.multiply(Matrices.rotateY(-angle * 2), Matrices.scale(0.5f)))));
        return placed;
    }

    /// `placed` as the ray tracer takes it.
    static SceneGeometry geometry(List<Placed> placed) {
        var instances = new ArrayList<Instance>();
        for (var solid : placed) {
            var m = solid.model();
            instances.add(Instance.of(
                    solid.mesh().traced(),
                    Transform.ofRowMajor(m[0], m[1], m[2], m[3], m[4], m[5], m[6], m[7], m[8], m[9], m[10], m[11]),
                    solid.id()));
        }
        return new SceneGeometry(instances);
    }

    /// The camera, as the ray tracer takes it.
    static Camera camera() {
        return Camera.lookingAt(
                new Vector3(EYE[0], EYE[1], EYE[2]), new Vector3(TARGET[0], TARGET[1], TARGET[2]), FIELD_OF_VIEW);
    }

    /// Projection and view, composed, for a picture of `aspect`: the same
    /// rays [#camera()] traces, pixel for pixel.
    static float[] viewProjection(double aspect) {
        return Matrices.multiply(
                Matrices.perspective(Math.toRadians(FIELD_OF_VIEW), aspect, 0.1, 60), Matrices.lookAt(EYE, TARGET));
    }

    private static float[] stretchedY(float width, float height) {
        return new float[] {width, 0, 0, 0, 0, height, 0, 0, 0, 0, width, 0, 0, 0, 0, 1};
    }
}
