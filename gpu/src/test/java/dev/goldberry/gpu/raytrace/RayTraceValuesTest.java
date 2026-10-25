package dev.goldberry.gpu.raytrace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ReadOnlyBufferException;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

/// The ray-tracing API's values, which need no device: the geometry, the
/// camera's rays, the request, and the hits' layout.
@DisplayName("the ray-tracing values")
class RayTraceValuesTest {

    private static void assertNear(Vector3 expected, Vector3 actual) {
        assertEquals(expected.x(), actual.x(), 1e-5, "x of " + actual);
        assertEquals(expected.y(), actual.y(), 1e-5, "y of " + actual);
        assertEquals(expected.z(), actual.z(), 1e-5, "z of " + actual);
    }

    @Nested
    @DisplayName("a vector")
    class Vectors {

        @Test
        @DisplayName("adds, scales, crosses and normalises")
        void arithmetic() {
            var a = new Vector3(1, 2, 3);
            assertEquals(new Vector3(2, 4, 6), a.plus(a));
            assertEquals(Vector3.ZERO, a.minus(a));
            assertEquals(14, a.dot(a));
            assertEquals(new Vector3(0, 0, 1), new Vector3(1, 0, 0).cross(new Vector3(0, 1, 0)));
            assertEquals(1, new Vector3(3, 4, 0).normalized().length(), 1e-6);
        }

        @Test
        @DisplayName("refuses what is not finite, and a direction for zero")
        void refuses() {
            assertThrows(IllegalArgumentException.class, () -> new Vector3(Float.NaN, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> new Vector3(0, Float.POSITIVE_INFINITY, 0));
            assertThrows(IllegalArgumentException.class, Vector3.ZERO::normalized);
        }
    }

    @Nested
    @DisplayName("a transform")
    class Transforms {

        @Test
        @DisplayName("moves, scales and turns a point")
        void moves() {
            var p = new Vector3(1, 2, 3);
            assertEquals(p, Transform.IDENTITY.apply(p));
            assertEquals(
                    new Vector3(2, 2, 2),
                    Transform.translation(new Vector3(1, 0, -1)).apply(p));
            assertEquals(new Vector3(2, 6, 3), Transform.scaling(2, 3, 1).apply(p));
            assertNear(new Vector3(0, 0, -1), Transform.rotationY(Math.PI / 2).apply(new Vector3(1, 0, 0)));
            assertNear(new Vector3(0, 1, 0), Transform.rotationZ(Math.PI / 2).apply(new Vector3(1, 0, 0)));
            assertNear(new Vector3(0, 0, 1), Transform.rotationX(Math.PI / 2).apply(new Vector3(0, 1, 0)));
        }

        @Test
        @DisplayName("composes in the order written: the first, then the next")
        void composes() {
            var scaleThenMove = Transform.scaling(2).then(Transform.translation(new Vector3(1, 0, 0)));
            var moveThenScale = Transform.translation(new Vector3(1, 0, 0)).then(Transform.scaling(2));
            var p = new Vector3(1, 1, 1);
            assertEquals(new Vector3(3, 2, 2), scaleThenMove.apply(p));
            assertEquals(new Vector3(4, 2, 2), moveThenScale.apply(p));
            var turn = Transform.rotationY(0.3).then(Transform.translation(new Vector3(0, 1, 0)));
            assertNear(
                    turn.apply(Transform.scaling(0.5f).apply(p)),
                    Transform.scaling(0.5f).then(turn).apply(p));
        }

        @Test
        @DisplayName("is twelve elements row by row, and back")
        void rowMajor() {
            var elements = new float[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12};
            var transform = Transform.ofRowMajor(elements);
            assertEquals(4, transform.m03());
            assertEquals(5, transform.m10());
            assertEquals(List.of(1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f, 11f, 12f), boxed(transform.toRowMajor()));
            assertThrows(IllegalArgumentException.class, () -> Transform.ofRowMajor(1, 2, 3));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> Transform.ofRowMajor(1, 0, 0, Float.NaN, 0, 1, 0, 0, 0, 0, 1, 0));
        }

        private static List<Float> boxed(float[] values) {
            var boxed = new java.util.ArrayList<Float>();
            for (var value : values) {
                boxed.add(value);
            }
            return boxed;
        }
    }

    @Nested
    @DisplayName("a mesh")
    class Meshes {

        @Test
        @DisplayName("counts its vertices and triangles, and names each corner")
        void counts() {
            var cube = TestScenes.cube();
            assertEquals(8, cube.vertexCount());
            assertEquals(12, cube.triangleCount());
            assertEquals(new Vector3(-0.5f, -0.5f, -0.5f), cube.corner(0, 0));
            assertEquals(new Vector3(0.5f, 0.5f, -0.5f), cube.corner(0, 1));
            assertThrows(IndexOutOfBoundsException.class, () -> cube.corner(12, 0));
            assertThrows(IndexOutOfBoundsException.class, () -> cube.corner(0, 3));
        }

        @Test
        @DisplayName("copies what it is made from, and lends read-only views")
        void immutable() {
            var positions = new float[] {0, 0, 0, 1, 0, 0, 0, 1, 0};
            var indices = new int[] {0, 1, 2};
            var mesh = TriangleMesh.of(positions, indices);
            positions[0] = 9;
            indices[0] = 2;
            assertEquals(Vector3.ZERO, mesh.corner(0, 0));
            assertThrows(ReadOnlyBufferException.class, () -> mesh.positions().put(0, 1));
            assertThrows(ReadOnlyBufferException.class, () -> mesh.indices().put(0, 1));
        }

        @Test
        @DisplayName("refuses partial vertices and triangles, and indices outside")
        void refuses() {
            assertThrows(IllegalArgumentException.class, () -> TriangleMesh.of(new float[0], new int[] {0, 0, 0}));
            assertThrows(
                    IllegalArgumentException.class, () -> TriangleMesh.of(new float[] {0, 0}, new int[] {0, 0, 0}));
            assertThrows(
                    IllegalArgumentException.class, () -> TriangleMesh.of(new float[] {0, 0, 0}, new int[] {0, 0}));
            assertThrows(
                    IllegalArgumentException.class, () -> TriangleMesh.of(new float[] {0, 0, 0}, new int[] {0, 0, 1}));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> TriangleMesh.of(new float[] {0, Float.NaN, 0}, new int[] {0, 0, 0}));
        }
    }

    @Nested
    @DisplayName("an instance and a scene")
    class Scenes {

        @Test
        @DisplayName("keep an id in 24 bits and a mask in 8")
        void ranges() {
            var cube = TestScenes.cube();
            assertEquals(
                    Instance.VISIBLE,
                    Instance.of(cube, Transform.IDENTITY, Instance.MAX_ID).mask());
            assertThrows(IllegalArgumentException.class, () -> Instance.of(cube, Transform.IDENTITY, -1));
            assertThrows(IllegalArgumentException.class, () -> Instance.of(cube, Transform.IDENTITY, 1 << 24));
            assertThrows(IllegalArgumentException.class, () -> new Instance(cube, Transform.IDENTITY, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> new Instance(cube, Transform.IDENTITY, 0, 256));
        }

        @Test
        @DisplayName("builds each mesh once however many instances place it")
        void sharesMeshes() {
            var scene = TestScenes.cubesOnGround();
            assertEquals(2, scene.meshes().size());
            assertEquals(
                    List.of(0, 1, 1, 1),
                    java.util.Arrays.stream(scene.meshIndices()).boxed().toList());
            assertEquals(2 + 3 * 12, scene.triangleCount());
            assertThrows(IllegalArgumentException.class, SceneGeometry::of);
        }

        @Test
        @DisplayName("tells two equal meshes apart: identity, not contents")
        void identity() {
            var a = TestScenes.cube();
            var b = TestScenes.cube();
            var scene = SceneGeometry.of(Instance.of(a, Transform.IDENTITY, 0), Instance.of(b, Transform.IDENTITY, 1));
            assertEquals(2, scene.meshes().size());
        }
    }

    @Nested
    @DisplayName("a camera")
    class Cameras {

        @Test
        @DisplayName("sends the middle pixel's ray at its target, and the corners' out to the field of view")
        void rays() {
            var camera = Camera.lookingAt(new Vector3(0, 0, 5), Vector3.ZERO, 90);
            var middle = camera.ray(50, 50, 101, 101);
            assertNear(new Vector3(0, 0, -1), middle.direction());
            assertEquals(new Vector3(0, 0, 5), middle.origin());

            // 90° vertically and square: the edges are 45° off the axis, the
            // top-left corner pixel's centre just inside them.
            var basis = camera.basis(100, 100);
            assertNear(new Vector3(0, 1, 0), basis.up());
            assertNear(new Vector3(1, 0, 0), basis.right());
            var topLeft = basis.ray(-1, -1).direction();
            assertTrue(topLeft.x() < 0 && topLeft.y() > 0, "rows run from the top: " + topLeft);
            assertEquals(Math.toDegrees(Math.atan(Math.sqrt(2))), Math.toDegrees(Math.acos(-topLeft.z())), 1e-3);
        }

        @Test
        @DisplayName("widens with the picture's aspect, keeping the vertical field")
        void aspect() {
            var camera = Camera.lookingAt(new Vector3(0, 0, 5), Vector3.ZERO, 60);
            var wide = camera.basis(200, 100);
            assertEquals(2 * wide.up().length(), wide.right().length(), 1e-5);
            assertEquals((float) Math.tan(Math.toRadians(30)), wide.up().length(), 1e-6);
        }

        @Test
        @DisplayName("refuses a degenerate view")
        void refuses() {
            assertThrows(IllegalArgumentException.class, () -> Camera.lookingAt(Vector3.ZERO, Vector3.ZERO, 60));
            assertThrows(
                    IllegalArgumentException.class, () -> Camera.lookingAt(Vector3.ZERO, new Vector3(0, 5, 0), 60));
            assertThrows(
                    IllegalArgumentException.class, () -> Camera.lookingAt(Vector3.ZERO, new Vector3(0, 0, 1), 180));
            var camera = TestScenes.camera();
            assertThrows(IllegalArgumentException.class, () -> camera.ray(10, 0, 10, 10));
            assertThrows(IllegalArgumentException.class, () -> camera.basis(0, 10));
            assertThrows(IllegalArgumentException.class, () -> new Ray(Vector3.ZERO, new Vector3(0, 0, 2)));
        }
    }

    @Nested
    @DisplayName("a request")
    class Requests {

        @Test
        @DisplayName("traces primary rays alone until shadows are asked for, toward a unit direction")
        void shadows() {
            var request = TraceRequest.of(TestScenes.camera(), 4, 3);
            assertEquals(Optional.empty(), request.towardLight());
            assertEquals(12, request.pixels());
            var lit = request.withShadowsToward(new Vector3(0, 10, 0));
            assertEquals(Optional.of(Vector3.UP), lit.towardLight());
            assertEquals(5, lit.withMaxDistance(5).maxDistance());
            assertEquals(0.01f, lit.withShadowBias(0.01f).shadowBias());
        }

        @Test
        @DisplayName("refuses an empty picture, a light of no length, and distances that are not positive")
        void refuses() {
            var camera = TestScenes.camera();
            assertThrows(IllegalArgumentException.class, () -> TraceRequest.of(camera, 0, 1));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> TraceRequest.of(camera, 1, 1).withShadowsToward(Vector3.ZERO));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> TraceRequest.of(camera, 1, 1).withMaxDistance(0));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> TraceRequest.of(camera, 1, 1).withMaxDistance(Float.POSITIVE_INFINITY));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> TraceRequest.of(camera, 1, 1).withShadowBias(-1));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new TraceRequest(
                            camera, 1, 1, Optional.of(new Vector3(0, 2, 0)), 1, TraceRequest.DEFAULT_SHADOW_BIAS));
        }
    }

    @Nested
    @DisplayName("the hits")
    class HitRecords {

        /// Two pixels: a lit hit on instance 7, then a miss.
        private static ByteBuffer twoPixels() {
            var records = ByteBuffer.allocate(2 * Hits.RECORD_BYTES).order(ByteOrder.LITTLE_ENDIAN);
            records.putInt(7)
                    .putInt(3)
                    .putFloat(2.5f)
                    .putFloat(0.25f)
                    .putFloat(0.5f)
                    .putInt(1);
            records.putInt(Hits.MISS)
                    .putInt(Hits.MISS)
                    .putFloat(0)
                    .putFloat(0)
                    .putFloat(0)
                    .putInt(1);
            return records.flip();
        }

        @Test
        @DisplayName("read each pixel's record in the documented layout")
        void layout() {
            var hits = Hits.of(2, 1, twoPixels());
            var hit = hits.hit(0, 0).orElseThrow();
            assertEquals(new Hit(7, 3, 2.5f, 0.25f, 0.5f, true), hit);
            assertEquals(0.25f, hit.w());
            assertTrue(hits.isHit(0, 0));
            assertFalse(hits.isHit(1, 0));
            assertEquals(Optional.empty(), hits.hit(1, 0));
            assertEquals(1, hits.hitCount());
            assertEquals(7, hits.instanceId(0, 0));
            assertEquals(3, hits.primitiveId(0, 0));
            assertEquals(2.5f, hits.distance(0, 0));
            assertThrows(IndexOutOfBoundsException.class, () -> hits.hit(2, 0));
            assertThrows(ReadOnlyBufferException.class, () -> hits.records().put(0, (byte) 1));
        }

        @Test
        @DisplayName("make an R8 mask and an R32 distance picture for upload")
        void pictures() {
            var records = twoPixels();
            records.putInt(20, 0);
            var hits = Hits.of(2, 1, records);
            var mask = hits.shadowMask();
            assertEquals(2, mask.capacity());
            assertEquals(0, mask.get(0), "in shadow");
            assertEquals((byte) 0xFF, mask.get(1), "a miss is lit");
            var distances = hits.distances(100).order(ByteOrder.LITTLE_ENDIAN);
            assertEquals(2.5f, distances.getFloat(0));
            assertEquals(100, distances.getFloat(4));
        }

        @Test
        @DisplayName("refuse records that are not the picture's size")
        void refuses() {
            assertThrows(IllegalArgumentException.class, () -> Hits.of(3, 1, twoPixels()));
            assertThrows(IllegalArgumentException.class, () -> Hits.of(0, 1, ByteBuffer.allocate(0)));
        }
    }

    @Nested
    @DisplayName("the mode")
    class Modes {

        @Test
        @DisplayName("parses off, auto and hardware, in any case")
        void parses() {
            assertEquals(RayTracingMode.OFF, RayTracingMode.parse("OFF"));
            assertEquals(RayTracingMode.AUTO, RayTracingMode.parse(" auto "));
            assertEquals(RayTracingMode.HARDWARE, RayTracingMode.parse("Hardware"));
            assertThrows(IllegalArgumentException.class, () -> RayTracingMode.parse("software"));
        }

        @Test
        @ResourceLock(Resources.SYSTEM_PROPERTIES)
        @DisplayName("is overridden by goldberry.raytracing, and off opens nothing")
        void property() {
            var previous = System.getProperty(RayTracingMode.PROPERTY);
            try {
                System.setProperty(RayTracingMode.PROPERTY, "off");
                assertEquals(RayTracingMode.OFF, RayTracingMode.effective(RayTracingMode.HARDWARE));
                assertEquals(Optional.empty(), RayTracer.open(RayTracingMode.HARDWARE));
                assertEquals(new RayTracing.Unavailable(RayTracingMode.PROPERTY + " is off"), RayTracer.availability());
                assertFalse(RayTracer.availability().isAvailable());
                System.clearProperty(RayTracingMode.PROPERTY);
                assertEquals(RayTracingMode.AUTO, RayTracingMode.effective(RayTracingMode.AUTO));
            } finally {
                if (previous == null) {
                    System.clearProperty(RayTracingMode.PROPERTY);
                } else {
                    System.setProperty(RayTracingMode.PROPERTY, previous);
                }
            }
        }
    }
}
