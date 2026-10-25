package dev.goldberry.gpu.raytrace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
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
        tracer.close();
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
                    tracer.device()
                            .device()
                            .toLowerCase(java.util.Locale.ROOT)
                            .contains(named.toLowerCase(java.util.Locale.ROOT)),
                    tracer.device() + " for " + named);
        }
    }

    @Test
    @DisplayName("finds what the reference finds through every pixel")
    void primaryRays() {
        var geometry = TestScenes.cubesOnGround();
        var scene = build(geometry);
        var request = TraceRequest.of(TestScenes.camera(), WIDTH, HEIGHT);
        var hits = tracer.trace(scene, request);
        var reference = new ReferenceTracer(geometry);

        var compared = 0;
        var hitPixels = 0;
        var instances = new java.util.TreeSet<Integer>();
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
        assertEquals(
                java.util.Set.of(TestScenes.GROUND, TestScenes.CUBE, TestScenes.CUBE + 1, TestScenes.CUBE + 2),
                instances,
                "every instance is seen");
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

        var mask = hits.shadowMask();
        var zeros = 0;
        for (var i = 0; i < mask.capacity(); i++) {
            zeros += mask.get(i) == 0 ? 1 : 0;
        }
        assertTrue(zeros >= shadowed, "the mask has a 0 for every shadowed pixel");
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
