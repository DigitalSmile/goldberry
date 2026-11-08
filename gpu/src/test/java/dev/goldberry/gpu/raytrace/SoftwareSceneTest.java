package dev.goldberry.gpu.raytrace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.gpu.raytrace.bvh.BvhTraversal;
import dev.goldberry.gpu.raytrace.bvh.InstanceLevel;
import dev.goldberry.gpu.raytrace.bvh.MeshLevels;

/// The software tier's scene in Java, against [ReferenceTracer]: the walk the
/// shaders take finds what trying every triangle in double precision finds,
/// ray for ray, wherever the answer does not hang on a rounding. No device.
@DisplayName("the software tier's scene, walked in Java")
class SoftwareSceneTest {

    /// As [RayTracerTest]'s: a ray this close to an edge is not compared.
    private static final double EDGE = 2e-3;

    private static BvhTraversal walk(SceneGeometry geometry) {
        var meshes = MeshLevels.of(geometry);
        return new BvhTraversal(meshes, InstanceLevel.of(meshes, geometry));
    }

    /// Asserts the closest hits of `rays` match the reference's, and returns
    /// how many were compared and hit.
    private static int[] assertClosest(SceneGeometry geometry, BvhTraversal walk, Ray[] rays) {
        var reference = new ReferenceTracer(geometry);
        var compared = 0;
        var hits = 0;
        for (var i = 0; i < rays.length; i++) {
            var ray = rays[i];
            var answer =
                    reference.closest(ReferenceTracer.of(ray.origin()), ReferenceTracer.of(ray.direction()), 0, 1e6);
            if (answer.margin() < EDGE) {
                continue;
            }
            compared++;
            var found = walk.closest(ray.origin(), ray.direction(), 0, 1e6f);
            var at = "ray " + i;
            assertEquals(answer.hit().isPresent(), found.isPresent(), at + ": hit or miss");
            if (found.isEmpty()) {
                continue;
            }
            hits++;
            var expected = answer.hit().orElseThrow();
            var actual = found.get();
            assertEquals(expected.instanceId(), actual.instanceId(), at + ": instance");
            assertEquals(expected.primitiveId(), actual.primitiveId(), at + ": triangle");
            assertEquals(expected.distance(), actual.distance(), expected.distance() * 1e-4, at + ": distance");
            assertEquals(expected.u(), actual.u(), 1e-3, at + ": u");
            assertEquals(expected.v(), actual.v(), 1e-3, at + ": v");
            var normal = reference.normal(actual.instanceId(), actual.primitiveId());
            var dot = normal[0] * actual.normal().x()
                    + normal[1] * actual.normal().y()
                    + normal[2] * actual.normal().z();
            assertEquals(1, Math.abs(dot), 1e-4, at + ": the normal, either way it faces");
        }
        return new int[] {compared, hits};
    }

    @Test
    @DisplayName("finds what the reference finds through every pixel of the cubes on the ground")
    void cubesOnGround() {
        var geometry = TestScenes.cubesOnGround();
        var walk = walk(geometry);
        var rays = new ArrayList<Ray>();
        for (var y = 0; y < 64; y++) {
            for (var x = 0; x < 96; x++) {
                rays.add(TestScenes.camera().ray(x, y, 96, 64));
            }
        }
        var counts = assertClosest(geometry, walk, rays.toArray(Ray[]::new));
        assertTrue(counts[0] > 96 * 64 * 0.9, "compared only " + counts[0]);
        assertTrue(counts[1] > 96 * 64 / 2, "the camera looks at the scene: " + counts[1]);
    }

    @Test
    @DisplayName("finds what the reference finds in a crowd of turned, scaled and mirrored meshes, from everywhere")
    void crowd() {
        var random = new Random(42);
        var meshes = new TriangleMesh[] {TestScenes.cube(), soup(random, 300), soup(random, 40)};
        var instances = new ArrayList<Instance>();
        for (var i = 0; i < 60; i++) {
            var place = Transform.rotationX(random.nextDouble() * 6)
                    .then(Transform.scaling(
                            0.3f + random.nextFloat(),
                            (random.nextBoolean() ? -1 : 1) * (0.3f + random.nextFloat()),
                            0.3f + random.nextFloat()))
                    .then(Transform.rotationY(random.nextDouble() * 6))
                    .then(Transform.translation(new Vector3(
                            random.nextFloat() * 20 - 10, random.nextFloat() * 20 - 10, random.nextFloat() * 20 - 10)));
            instances.add(Instance.of(meshes[i % meshes.length], place, i));
        }
        var geometry = new SceneGeometry(instances);
        var walk = walk(geometry);
        var rays = new Ray[3000];
        for (var i = 0; i < rays.length; i++) {
            var origin = new Vector3(
                    random.nextFloat() * 40 - 20, random.nextFloat() * 40 - 20, random.nextFloat() * 40 - 20);
            var target =
                    new Vector3(random.nextFloat() * 10 - 5, random.nextFloat() * 10 - 5, random.nextFloat() * 10 - 5);
            rays[i] = new Ray(origin, target.minus(origin).normalized());
        }
        var counts = assertClosest(geometry, walk, rays);
        assertTrue(counts[0] > 2500, "compared only " + counts[0]);
        assertTrue(counts[1] > 500, "most rays at the middle hit something: " + counts[1]);
    }

    @Test
    @DisplayName("says a shadow ray is blocked exactly where the reference does")
    void anyHit() {
        var geometry = TestScenes.cubesOnGround();
        var walk = walk(geometry);
        var reference = new ReferenceTracer(geometry);
        var sun = TestScenes.sun();
        var compared = 0;
        var blocked = 0;
        for (var y = 0; y < 64; y++) {
            for (var x = 0; x < 96; x++) {
                var ray = TestScenes.camera().ray(x, y, 96, 64);
                var primary = reference.closest(
                        ReferenceTracer.of(ray.origin()), ReferenceTracer.of(ray.direction()), 0, 1e6);
                if (primary.margin() < EDGE || primary.hit().isEmpty()) {
                    continue;
                }
                var t = primary.hit().get().distance();
                var point = ray.at(t);
                var bias = 1e-3f * Math.max(1, t);
                var shadow = reference.anyHit(ReferenceTracer.of(point), ReferenceTracer.of(sun), bias, 1e6);
                if (shadow.margin() < EDGE) {
                    continue;
                }
                compared++;
                var expected = shadow.hit().isPresent();
                assertEquals(expected, walk.anyHit(point, sun, bias, 1e6f), "pixel " + x + "," + y);
                if (expected) {
                    blocked++;
                }
            }
        }
        assertTrue(compared > 96 * 64 / 2, "compared only " + compared);
        assertTrue(blocked > 50, "the cubes cast shadows: " + blocked);
    }

    @Test
    @DisplayName("misses past the farthest distance, and hits nothing of a flattened instance")
    void limits() {
        var cube = TestScenes.cube();
        var geometry = SceneGeometry.of(
                Instance.of(cube, Transform.IDENTITY, 1),
                Instance.of(cube, Transform.scaling(1, 0, 1).then(Transform.translation(new Vector3(0, 0, -5))), 2));
        var walk = walk(geometry);
        var origin = new Vector3(0, 0, 5);
        var forward = new Vector3(0, 0, -1);
        assertEquals(1, walk.closest(origin, forward, 0, 100).orElseThrow().instanceId());
        assertEquals(4.5f, walk.closest(origin, forward, 0, 100).orElseThrow().distance(), 1e-5f);
        assertTrue(walk.closest(origin, forward, 0, 4).isEmpty(), "the cube is 4.5 away");
        var behind = walk.closest(new Vector3(0, 0, -2), forward, 0, 100);
        assertTrue(behind.isEmpty(), "nothing hits the flattened cube behind: " + behind);
        assertTrue(walk.anyHit(origin, forward, 0, 100));
        assertTrue(!walk.anyHit(origin, new Vector3(0, 1, 0), 0, 100), "straight up, past everything");
    }

    @Test
    @DisplayName("walks a ray along an axis, whose other components are zero, as one that is not")
    void axisAligned() {
        var geometry = SceneGeometry.of(Instance.of(TestScenes.ground(4), Transform.IDENTITY, 3));
        var walk = walk(geometry);
        var down = walk.closest(new Vector3(0.3f, 2, -0.7f), new Vector3(0, -1, 0), 0, 10)
                .orElseThrow();
        assertEquals(3, down.instanceId());
        assertEquals(2f, down.distance(), 1e-6f);
        assertTrue(
                walk.closest(new Vector3(0.3f, 2, -0.7f), new Vector3(1, 0, 0), 0, 10)
                        .isEmpty(),
                "level with it");
    }

    /// `count` random triangles in a unit-ish blob.
    private static TriangleMesh soup(Random random, int count) {
        var positions = new float[9 * count];
        var indices = new int[3 * count];
        for (var t = 0; t < count; t++) {
            var cx = random.nextFloat() * 2 - 1;
            var cy = random.nextFloat() * 2 - 1;
            var cz = random.nextFloat() * 2 - 1;
            for (var corner = 0; corner < 3; corner++) {
                positions[9 * t + 3 * corner] = cx + random.nextFloat() * 0.6f - 0.3f;
                positions[9 * t + 3 * corner + 1] = cy + random.nextFloat() * 0.6f - 0.3f;
                positions[9 * t + 3 * corner + 2] = cz + random.nextFloat() * 0.6f - 0.3f;
                indices[3 * t + corner] = 3 * t + corner;
            }
        }
        return TriangleMesh.of(positions, indices);
    }
}
