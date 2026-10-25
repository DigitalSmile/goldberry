package dev.goldberry.gpu.raytrace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// Rays traced in hardware, against [ReferenceTracer]: the same instance, the
/// same triangle, the same distance and barycentrics, and the same shadow,
/// on every pixel whose answer does not hang on a rounding.
///
/// Needs a Vulkan device with the ray-tracing extensions: this machine's GPU,
/// or lavapipe (`-Pgoldberry.raytracing.device=llvmpipe`). Skips without one,
/// unless `goldberry.raytracing.required` is set.
@DisplayName("the ray tracer, on a real device")
class RayTracerTest {

    private static final int WIDTH = 96;
    private static final int HEIGHT = 64;

    /// Where single precision and the reference may disagree: a pixel this
    /// close to an edge, in barycentric units, is not compared.
    private static final double EDGE = 2e-3;

    private RayTracer tracer;
    private final List<AutoCloseable> closing = new ArrayList<>();

    @BeforeEach
    void open() {
        tracer = RayTracingRequirement.open();
    }

    @AfterEach
    void close() throws Exception {
        for (var resource : closing.reversed()) {
            resource.close();
        }
        // Null where there is no device: the open skipped the test, and JUnit
        // still runs this.
        if (tracer != null) {
            tracer.close();
        }
    }

    private RayScene build(SceneGeometry geometry) {
        var scene = tracer.build(geometry);
        closing.add(scene);
        return scene;
    }

    @Test
    @DisplayName("names the device it traces on")
    void namesItsDevice() {
        assertTrue(RayTracer.availability().isAvailable());
        assertEquals(RayTracer.availability(), tracer.device());
        assertFalse(tracer.device().device().isBlank());
        // A run that names its device, as the lavapipe run does, gets that one.
        var named = System.getProperty("goldberry.raytracing.device");
        if (named != null && !named.isBlank()) {
            assertTrue(
                    tracer.device().device().toLowerCase(Locale.ROOT).contains(named.toLowerCase(Locale.ROOT)),
                    tracer.device() + " for " + named);
        }
    }

    @Test
    @DisplayName("finds what the reference finds through every pixel")
    void primaryRays() {
        var geometry = TestScenes.cubesOnGround();
        var scene = build(geometry);
        var hits = tracer.trace(scene, TraceRequest.of(TestScenes.camera(), WIDTH, HEIGHT));
        var instances = assertMatchesReference(geometry, hits);
        assertEquals(
                Set.of(TestScenes.GROUND, TestScenes.CUBE, TestScenes.CUBE + 1, TestScenes.CUBE + 2),
                instances,
                "every instance is seen");
    }

    /// Asserts that `hits`, traced from [TestScenes#camera] at [#WIDTH] by
    /// [#HEIGHT], are what the reference finds in `geometry`, and returns the
    /// ids of the instances they hit.
    private static Set<Integer> assertMatchesReference(SceneGeometry geometry, Hits hits) {
        var reference = new ReferenceTracer(geometry);
        var compared = 0;
        var hitPixels = 0;
        var instances = new TreeSet<Integer>();
        for (var y = 0; y < HEIGHT; y++) {
            for (var x = 0; x < WIDTH; x++) {
                var ray = TestScenes.camera().ray(x, y, WIDTH, HEIGHT);
                var answer = reference.closest(
                        ReferenceTracer.of(ray.origin()), ReferenceTracer.of(ray.direction()), 0, 1e6);
                if (answer.margin() < EDGE) {
                    continue;
                }
                compared++;
                var at = "pixel " + x + "," + y;
                assertEquals(answer.hit().isPresent(), hits.isHit(x, y), at + ": hit or miss");
                if (answer.hit().isEmpty()) {
                    continue;
                }
                hitPixels++;
                var expected = answer.hit().get();
                var actual = hits.hit(x, y).orElseThrow();
                instances.add(actual.instanceId());
                assertEquals(expected.instanceId(), actual.instanceId(), at + ": instance");
                assertEquals(expected.primitiveId(), actual.primitiveId(), at + ": triangle");
                assertEquals(expected.distance(), actual.distance(), expected.distance() * 1e-4, at + ": distance");
                assertEquals(expected.u(), actual.u(), 1e-3, at + ": u");
                assertEquals(expected.v(), actual.v(), 1e-3, at + ": v");
                assertTrue(actual.lit(), at + ": no shadow was asked for");
            }
        }
        assertTrue(compared > WIDTH * HEIGHT * 0.9, "compared only " + compared + " pixels");
        assertTrue(hitPixels > WIDTH * HEIGHT / 2, "the camera looks at the scene: " + hitPixels + " hits");
        return instances;
    }

    @Test
    @DisplayName("shadows what the reference shadows, and lights the rest")
    void shadowRays() {
        var geometry = TestScenes.cubesOnGround();
        var scene = build(geometry);
        var request = TraceRequest.of(TestScenes.camera(), WIDTH, HEIGHT).withShadowsToward(TestScenes.sun());
        var hits = tracer.trace(scene, request);
        var reference = new ReferenceTracer(geometry);
        var sun = ReferenceTracer.of(TestScenes.sun());

        var compared = 0;
        var shadowed = 0;
        var lit = 0;
        for (var y = 0; y < HEIGHT; y++) {
            for (var x = 0; x < WIDTH; x++) {
                var ray = TestScenes.camera().ray(x, y, WIDTH, HEIGHT);
                var origin = ReferenceTracer.of(ray.origin());
                var direction = ReferenceTracer.of(ray.direction());
                var primary = reference.closest(origin, direction, 0, 1e6);
                if (primary.margin() < EDGE || primary.hit().isEmpty()) {
                    continue;
                }
                var t = primary.hit().get().distance();
                var point = new double[] {
                    origin[0] + direction[0] * t, origin[1] + direction[1] * t, origin[2] + direction[2] * t
                };
                var bias = request.shadowBias() * Math.max(1, t);
                var shadow = reference.anyHit(point, sun, bias, request.maxDistance());
                if (shadow.margin() < EDGE) {
                    continue;
                }
                compared++;
                var expectLit = shadow.hit().isEmpty();
                assertEquals(expectLit, hits.isLit(x, y), "pixel " + x + "," + y + ": in the light or not");
                if (expectLit) {
                    lit++;
                } else {
                    shadowed++;
                }
            }
        }
        assertTrue(compared > WIDTH * HEIGHT / 2, "compared only " + compared + " pixels");
        assertTrue(shadowed > 50, "the cubes cast shadows: " + shadowed);
        assertTrue(lit > shadowed, "most of the ground is in the sun: " + lit + " lit, " + shadowed + " not");

        assertTrue(hits.shadowMask().shadowedCount() >= shadowed, "the mask has a 0 for every shadowed pixel");
    }

    @Test
    @DisplayName("packs the shadow mask on the GPU as the records have it, at widths of whole words and not")
    void packedShadows() {
        var scene = build(TestScenes.cubesOnGround());
        for (var size : new int[][] {{WIDTH, HEIGHT}, {97, 63}, {1, 1}, {3, 5}, {6, 2}}) {
            var request = TraceRequest.of(TestScenes.camera(), size[0], size[1]).withShadowsToward(TestScenes.sun());
            var packed = tracer.traceShadows(scene, request);
            var fromRecords = tracer.trace(scene, request).shadowMask();
            assertEquals(size[0], packed.width());
            assertEquals(size[1], packed.height());
            assertEquals(fromRecords, packed, size[0] + "×" + size[1]);
        }
        var full = tracer.traceShadows(
                scene, TraceRequest.of(TestScenes.camera(), WIDTH, HEIGHT).withShadowsToward(TestScenes.sun()));
        assertTrue(full.shadowedCount() > 50, "the cubes cast shadows: " + full);
        for (var i = 0; i < full.pixels().capacity(); i++) {
            var value = full.pixels().get(i);
            assertTrue(value == ShadowMask.LIT || value == ShadowMask.SHADOWED, "pixel " + i + " is " + value);
        }
    }

    @Test
    @DisplayName("refuses a shadow mask without a light")
    void shadowsNeedALight() {
        var scene = build(TestScenes.cubesOnGround());
        assertThrows(
                IllegalArgumentException.class,
                () -> tracer.traceShadows(scene, TraceRequest.of(TestScenes.camera(), 8, 8)));
    }

    @Test
    @DisplayName("moves its instances without building their meshes again, and finds what the reference finds")
    void moves() {
        var geometry = TestScenes.cubesOnGround();
        var scene = build(geometry);
        var moved = new SceneGeometry(geometry.instances().stream()
                .map(instance -> instance.id() == TestScenes.GROUND
                        ? instance
                        : instance.withTransform(
                                        instance.transform().then(Transform.translation(new Vector3(0.7f, 0, 1.1f))))
                                .withId(instance.id() + 100))
                .toList());
        var bottomBefore = scene.structuresFor(tracer).bottomBytes();
        scene.update(moved);
        assertEquals(moved, scene.geometry());
        assertEquals(bottomBefore, scene.structuresFor(tracer).bottomBytes(), "the meshes' structures stay");
        var instances =
                assertMatchesReference(moved, tracer.trace(scene, TraceRequest.of(TestScenes.camera(), WIDTH, HEIGHT)));
        assertEquals(
                Set.of(TestScenes.GROUND, TestScenes.CUBE + 100, TestScenes.CUBE + 101, TestScenes.CUBE + 102),
                instances,
                "the new ids are reported");
    }

    @Test
    @DisplayName("takes fewer instances, then more than it has room for, on update")
    void changesItsInstanceCount() {
        var geometry = TestScenes.cubesOnGround();
        var scene = build(geometry);
        var ground = geometry.instances().getFirst();
        var cube = geometry.instances().get(1).mesh();

        var groundAlone = SceneGeometry.of(ground);
        scene.update(groundAlone);
        assertMatchesReference(groundAlone, tracer.trace(scene, TraceRequest.of(TestScenes.camera(), WIDTH, HEIGHT)));

        var crowd = new ArrayList<Instance>();
        crowd.add(ground);
        for (var i = 0; i < 40; i++) {
            var place = Transform.scaling(0.4f)
                    .then(Transform.translation(new Vector3(-3 + (i % 8) * 0.8f, 0.2f, -2 + (i / 8) * 0.9f)));
            crowd.add(Instance.of(cube, place, 1000 + i));
        }
        var crowded = new SceneGeometry(crowd);
        var bytes = scene.structureBytes();
        scene.update(crowded);
        assertTrue(scene.structureBytes() >= bytes, "the instances' structure grew: " + scene.structureBytes());
        var instances = assertMatchesReference(
                crowded, tracer.trace(scene, TraceRequest.of(TestScenes.camera(), WIDTH, HEIGHT)));
        assertTrue(instances.size() > 30, "most of the crowd is seen: " + instances);
    }

    @Test
    @DisplayName("refuses an update with a mesh it was not built with, and traces as it did")
    void refusesANewMesh() {
        var geometry = TestScenes.cubesOnGround();
        var scene = build(geometry);
        var request = TraceRequest.of(TestScenes.camera(), WIDTH, HEIGHT);
        var before = tracer.trace(scene, request);
        var stranger = SceneGeometry.of(Instance.of(TestScenes.cube(), Transform.IDENTITY, 5));
        assertThrows(IllegalArgumentException.class, () -> scene.update(stranger));
        assertEquals(geometry, scene.geometry());
        assertEquals(before.records(), tracer.trace(scene, request).records());
        scene.close();
        assertThrows(IllegalStateException.class, () -> scene.update(geometry));
    }

    @Test
    @DisplayName("compacts its meshes' structures, and counts what they hold")
    void compacts() {
        var scene = build(TestScenes.cubesOnGround());
        var structures = scene.structuresFor(tracer);
        assertEquals(2, structures.bottomLevelCount(), "the ground and the shared cube");
        assertTrue(structures.bottomBytes() > 0);
        assertTrue(
                structures.bottomBytes() <= structures.bottomBytesBuilt(),
                structures.bottomBytes() + " compacted, " + structures.bottomBytesBuilt() + " built");
        assertTrue(scene.structureBytes() > structures.bottomBytes(), "the instances' structure counts too");
    }

    @Test
    @DisplayName("traces one scene again, at another size, and the hits buffer grows")
    void tracesAgain() {
        var scene = build(TestScenes.cubesOnGround());
        var small = tracer.trace(scene, TraceRequest.of(TestScenes.camera(), 16, 8));
        var large = tracer.trace(scene, TraceRequest.of(TestScenes.camera(), 160, 120));
        var again = tracer.trace(scene, TraceRequest.of(TestScenes.camera(), 16, 8));
        assertEquals(16, small.width());
        assertEquals(160 * 120 * Hits.RECORD_BYTES, large.records().remaining());
        assertEquals(small.records(), again.records(), "the same rays find the same hits");
    }

    @Test
    @DisplayName("misses what is beyond the maximum distance")
    void maxDistance() {
        var scene = build(TestScenes.cubesOnGround());
        var hits =
                tracer.trace(scene, TraceRequest.of(TestScenes.camera(), 32, 24).withMaxDistance(1));
        assertEquals(0, hits.hitCount(), "the camera is more than 1 from everything");
    }

    @Test
    @DisplayName("reports each instance's own id, from one shared mesh")
    void sharedMesh() {
        var cube = TestScenes.cube();
        var geometry = SceneGeometry.of(
                Instance.of(cube, Transform.translation(new Vector3(-1, 0, 0)), Instance.MAX_ID),
                Instance.of(cube, Transform.translation(new Vector3(1, 0, 0)), 0));
        assertEquals(1, geometry.meshes().size());
        var scene = build(geometry);
        var camera = Camera.lookingAt(new Vector3(0, 0, 5), Vector3.ZERO, 40);
        var hits = tracer.trace(scene, TraceRequest.of(camera, 64, 32));
        // At the cubes' front faces the picture is 6.6 wide: pixel 24 is at
        // x = -0.77, inside the left cube, and pixel 40 at 0.87, the right.
        var left = hits.hit(24, 16).orElseThrow();
        var right = hits.hit(40, 16).orElseThrow();
        assertEquals(Instance.MAX_ID, left.instanceId(), "24 bits of id survive");
        assertEquals(0, right.instanceId());
        assertFalse(hits.isHit(32, 16), "between the cubes");
        assertFalse(hits.isHit(24, 0), "above them");
    }

    @Test
    @DisplayName("refuses a scene another tracer built, or one that is closed")
    void refusesForeignAndClosedScenes() {
        var scene = tracer.build(TestScenes.cubesOnGround());
        var request = TraceRequest.of(TestScenes.camera(), 8, 8);
        try (var other = RayTracer.open(RayTracingMode.HARDWARE).orElseThrow()) {
            assertThrows(IllegalArgumentException.class, () -> other.trace(scene, request));
        }
        scene.close();
        assertTrue(scene.isClosed());
        assertThrows(IllegalStateException.class, () -> tracer.trace(scene, request));
        scene.close();
    }

    @Test
    @DisplayName("is its opening thread's alone")
    void confinedToItsThread() throws Exception {
        var scene = build(TestScenes.cubesOnGround());
        try (var executor = Executors.newSingleThreadExecutor()) {
            var failure = executor.submit(() -> {
                try {
                    tracer.trace(scene, TraceRequest.of(TestScenes.camera(), 8, 8));
                    return null;
                } catch (WrongThreadException e) {
                    return e;
                }
            });
            assertTrue(failure.get() instanceof WrongThreadException);
        }
    }

    @Test
    @DisplayName("is refused once closed")
    void closed() {
        tracer.close();
        assertTrue(tracer.isClosed());
        assertThrows(IllegalStateException.class, () -> tracer.build(TestScenes.cubesOnGround()));
        tracer.close();
    }
}
