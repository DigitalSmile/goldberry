package dev.goldberry.gpu.raytrace;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Supplier;

import dev.goldberry.gpu.GpuDevice;
import dev.goldberry.gpu.Readback;
import dev.goldberry.gpu.composite.CompositeHarness;
import dev.goldberry.gpu.raytrace.vulkan.AccelerationScene;
import dev.goldberry.render.model.PhysicalRect;

/// What ray tracing costs on this machine, in each tier there is: building a
/// scene of about 130,000 triangles, moving its instances, and tracing a
/// primary and a shadow ray a pixel through it at 1080p and at 4K, each trace
/// waited for: once with its 24-byte records copied out and the shadow mask
/// made from them, once with the mask packed on the GPU. Then a frame's
/// lighting, occlusion and reflections included, traced into two textures on
/// the window's device, as the showcase's room is.
///
/// The hardware tier runs where [RayTracer#availability()] finds a device, the
/// software tier on the window's device always. The scene is a 256 × 256
/// height field, one mesh, and 200 cubes of one shared mesh standing on it,
/// under a low sun. Run by `:gpu:rayTraceProbe`, which takes
/// `-Pgoldberry.raytracing.device=llvmpipe` for lavapipe's hardware tier, and
/// read by a person.
final class RayTraceProbe {

    private static final int GRID = 256;
    private static final int CUBES = 200;
    private static final int BUILDS = 5;
    private static final int TRACES = 30;

    /// How long a row runs for at most, past its third run: lavapipe's
    /// software traces take seconds each.
    private static final long BUDGET_NANOS = 15_000_000_000L;

    private RayTraceProbe() {}

    static void main() {
        var geometry = scene();
        System.out.printf(
                Locale.ROOT,
                "%,d instances, %,d triangles%n",
                geometry.instances().size(),
                geometry.triangleCount());
        try (var harness = CompositeHarness.open()) {
            var device = harness.device();
            var availability = RayTracer.availability();
            if (availability.isAvailable()) {
                try (var tracer = RayTracer.open(RayTracingMode.HARDWARE).orElseThrow()) {
                    measure(tracer, device, geometry);
                }
            } else {
                System.out.printf("%nno hardware ray tracing here: %s%n", availability);
            }
            try (var tracer = RayTracer.open(device, RayTracingMode.SOFTWARE).orElseThrow()) {
                measure(tracer, device, geometry);
            }
        }
    }

    /// Every row for `tracer`, its frames on `device`.
    private static void measure(RayTracer tracer, GpuDevice device, SceneGeometry geometry) {
        System.out.printf(Locale.ROOT, "%n%s%n%n", tracer);
        System.out.println("| Measurement | median | p95 | mean | runs |");
        System.out.println("|---|---|---|---|---|");
        row("build: every structure", BUILDS, () -> tracer.build(geometry), RayScene::close);
        try (var scene = tracer.build(geometry)) {
            if (scene.structuresFor(tracer) instanceof AccelerationScene structures) {
                System.out.printf(
                        Locale.ROOT,
                        "|   meshes' structures: %.1f MB built, %.1f MB compacted | | | | |%n",
                        structures.bottomBytesBuilt() / 1e6,
                        structures.bottomBytes() / 1e6);
            }
            System.out.printf(Locale.ROOT, "|   every structure: %.1f MB | | | | |%n", scene.structureBytes() / 1e6);
            var frames = new SceneGeometry[] {geometry, moved(geometry)};
            var frame = new int[1];
            row(
                    "update: " + geometry.instances().size() + " instances moved",
                    TRACES,
                    () -> {
                        scene.update(frames[++frame[0] % 2]);
                        return scene;
                    },
                    ignored -> {});
            scene.update(geometry);
            var camera = Camera.lookingAt(new Vector3(0, 40, 90), new Vector3(0, 0, 0), 50);
            var sun = new Vector3(-1, 0.8f, 0.2f);
            for (var size : new int[][] {{1920, 1080}, {3840, 2160}}) {
                var request = TraceRequest.of(camera, size[0], size[1]).withShadowsToward(sun);
                var label = size[0] + "×" + size[1];
                var last = new Hits[1];
                row(
                        "trace " + label + ", shadows, records copied out",
                        TRACES,
                        () -> {
                            last[0] = tracer.trace(scene, request);
                            return last[0];
                        },
                        ignored -> {});
                row("shadow mask " + label + " from the records", TRACES, () -> last[0].shadowMask(), ignored -> {});
                row(
                        "trace " + label + ", shadows, mask packed on the GPU",
                        TRACES,
                        () -> tracer.traceShadows(scene, request),
                        ignored -> {});
                System.out.printf(
                        Locale.ROOT,
                        "|   %s: %,d of %,d pixels hit, %.1f MB of records | | | | |%n",
                        label,
                        last[0].hitCount(),
                        request.pixels(),
                        request.pixels() * Hits.RECORD_BYTES / 1e6);
            }
            var lighting = LightingRequest.of(
                            TraceRequest.of(camera, 1920, 1080).withShadowsToward(sun))
                    .withOcclusion(12, 1.4f);
            try (var targets = LightingTargets.create(device, 1920, 1080)) {
                row(
                        "lighting 1920×1080 into textures, 12 occlusion rays and reflections",
                        TRACES,
                        () -> {
                            try (var into = device.beginFrame()) {
                                tracer.traceLighting(into, scene, lighting, targets);
                                // A pixel read back is what waits for the frame.
                                var done = into.readback(targets.shading(), new PhysicalRect(0, 0, 1, 1));
                                into.submit();
                                done.await();
                                return done;
                            }
                        },
                        Readback::close);
            }
        }
    }

    /// `geometry` with every cube raised a little: the same meshes, so an
    /// update rather than a build.
    private static SceneGeometry moved(SceneGeometry geometry) {
        return new SceneGeometry(geometry.instances().stream()
                .map(instance -> instance.id() == 0
                        ? instance
                        : instance.withTransform(
                                instance.transform().then(Transform.translation(new Vector3(0, 0.5f, 0)))))
                .toList());
    }

    /// Times `work` `runs` times after a first run, or fewer once
    /// [#BUDGET_NANOS] have passed after the third, handing each answer to
    /// `after` untimed.
    private static <T> void row(String name, int runs, Supplier<T> work, Consumer<T> after) {
        after.accept(work.get());
        var times = new ArrayList<Double>();
        var began = System.nanoTime();
        for (var i = 0; i < runs; i++) {
            var start = System.nanoTime();
            var made = work.get();
            times.add((System.nanoTime() - start) / 1e6);
            after.accept(made);
            if (i >= 2 && System.nanoTime() - began > BUDGET_NANOS) {
                break;
            }
        }
        System.out.printf(
                Locale.ROOT,
                "| %s | %.2f ms | %.2f ms | %.2f ms | %d |%n",
                name,
                percentile(times, 0.5),
                percentile(times, 0.95),
                times.stream().mapToDouble(Double::doubleValue).average().orElse(0),
                times.size());
    }

    private static double percentile(List<Double> times, double at) {
        var sorted = times.stream().mapToDouble(Double::doubleValue).toArray();
        Arrays.sort(sorted);
        return sorted[Math.min(sorted.length - 1, (int) Math.floor(at * (sorted.length - 1) + 0.5))];
    }

    /// The height field and the cubes on it.
    private static SceneGeometry scene() {
        var size = 160f;
        var positions = new float[GRID * GRID * 3];
        for (var z = 0; z < GRID; z++) {
            for (var x = 0; x < GRID; x++) {
                var at = (z * GRID + x) * 3;
                var px = (x / (float) (GRID - 1) - 0.5f) * size;
                var pz = (z / (float) (GRID - 1) - 0.5f) * size;
                positions[at] = px;
                positions[at + 1] = height(px, pz);
                positions[at + 2] = pz;
            }
        }
        var indices = new int[(GRID - 1) * (GRID - 1) * 6];
        var i = 0;
        for (var z = 0; z < GRID - 1; z++) {
            for (var x = 0; x < GRID - 1; x++) {
                var a = z * GRID + x;
                var b = a + 1;
                var c = a + GRID;
                var d = c + 1;
                indices[i++] = a;
                indices[i++] = c;
                indices[i++] = b;
                indices[i++] = b;
                indices[i++] = c;
                indices[i++] = d;
            }
        }
        var instances = new ArrayList<Instance>();
        instances.add(Instance.of(TriangleMesh.of(positions, indices), Transform.IDENTITY, 0));
        var cube = cube();
        var random = new Random(7);
        for (var n = 0; n < CUBES; n++) {
            var x = (random.nextFloat() - 0.5f) * size * 0.8f;
            var z = (random.nextFloat() - 0.5f) * size * 0.8f;
            var scale = 1 + random.nextFloat() * 3;
            var place = Transform.scaling(scale)
                    .then(Transform.rotationY(random.nextDouble() * Math.PI))
                    .then(Transform.translation(new Vector3(x, height(x, z) + scale / 2, z)));
            instances.add(Instance.of(cube, place, 1 + n));
        }
        return new SceneGeometry(instances);
    }

    private static float height(float x, float z) {
        return (float) (3 * Math.sin(x * 0.07) * Math.cos(z * 0.05) + 1.5 * Math.sin((x + z) * 0.13));
    }

    private static TriangleMesh cube() {
        var positions = new float[] {
            -0.5f, -0.5f, -0.5f, 0.5f, -0.5f, -0.5f, 0.5f, 0.5f, -0.5f, -0.5f, 0.5f, -0.5f, -0.5f, -0.5f, 0.5f, 0.5f,
            -0.5f, 0.5f, 0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f
        };
        var indices = new int[] {
            0, 2, 1, 0, 3, 2, 4, 5, 6, 4, 6, 7, 0, 1, 5, 0, 5, 4, 3, 7, 6, 3, 6, 2, 0, 4, 7, 0, 7, 3, 1, 2, 6, 1, 6, 5
        };
        return TriangleMesh.of(positions, indices);
    }
}
