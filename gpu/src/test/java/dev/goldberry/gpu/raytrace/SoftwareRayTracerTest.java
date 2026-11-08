package dev.goldberry.gpu.raytrace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import dev.goldberry.gpu.GpuDevice;
import dev.goldberry.gpu.composite.CompositeHarness;
import dev.goldberry.natives.sdl.gpu.GpuTestLauncher;

/// [RayTracerContract] on the software tier, on the window's kind of device,
/// and what the tiers share past it: a frame's lighting traced into textures,
/// the same as the lighting returned, in software and in hardware; and the
/// choice `AUTO` makes.
///
/// Runs in `gpuTest`, which has SDL's device, on lavapipe in CI.
@Tag(GpuTestLauncher.TAG)
@DisplayName("the ray tracer in software, on the GPU")
class SoftwareRayTracerTest extends RayTracerContract {

    private static CompositeHarness harness;
    private static GpuDevice device;
    private static int baseline;

    @BeforeAll
    static void openDevice() {
        harness = CompositeHarness.open();
        device = harness.device();
        // One trace first, so the device's staging buffer is in the baseline.
        try (var tracer = RayTracer.open(device, RayTracingMode.SOFTWARE).orElseThrow();
                var scene = tracer.build(TestScenes.cubesOnGround())) {
            tracer.trace(scene, TraceRequest.of(TestScenes.camera(), 2, 2));
        }
        baseline = harness.openResources();
    }

    @AfterAll
    static void closeDevice() {
        if (harness != null) {
            harness.close();
        }
    }

    @Override
    RayTracer openTracer() {
        return RayTracer.open(device, RayTracingMode.SOFTWARE).orElseThrow();
    }

    @Override
    RayTracer openAnother() {
        return openTracer();
    }

    @Override
    void afterClose() {
        assertEquals(baseline, harness.openResources(), "a test left a resource open");
    }

    @Test
    @DisplayName("names the window's device, the tier it traces with")
    void namesItsDevice() {
        var software = assertInstanceOf(RayTracing.Software.class, tracer.device());
        assertEquals(device.adapter().name(), software.device());
        assertEquals(software, RayTracer.availability(device, RayTracingMode.SOFTWARE));
        assertTrue(tracer.toString().contains("software"), tracer.toString());
    }

    @Test
    @DisplayName("traces a frame's lighting into its targets, the same lighting as it returns")
    void lightingIntoTargets() {
        assertIntoTargetsAsReturned(tracer);
    }

    @Test
    @DisplayName("and in hardware into the same targets, on this device, where there is a hardware tier")
    void hardwareIntoTargets() {
        assumeTrue(RayTracer.availability().isAvailable(), "no hardware ray tracing here");
        try (var hardware = RayTracer.open(RayTracingMode.HARDWARE).orElseThrow()) {
            assertIntoTargetsAsReturned(hardware);
        }
    }

    /// Asserts `traced`'s lighting of the test scene into targets, read back,
    /// is what it returns for the same request.
    private void assertIntoTargetsAsReturned(RayTracer traced) {
        try (var scene = traced.build(TestScenes.cubesOnGround());
                var targets = LightingTargets.create(device, 97, 63)) {
            var request = LightingRequest.of(
                            TraceRequest.of(TestScenes.camera(), 97, 63).withShadowsToward(TestScenes.sun()))
                    .withOcclusion(8, 1.5f);
            var returned = traced.traceLighting(scene, request);
            ByteBuffer shading;
            ByteBuffer ids;
            try (var frame = device.beginFrame()) {
                traced.traceLighting(frame, scene, request, targets);
                try (var shadingBack = frame.readback(targets.shading());
                        var idsBack = frame.readback(targets.reflectedIds())) {
                    frame.submit();
                    shading = shadingBack.await();
                    ids = idsBack.await().order(ByteOrder.LITTLE_ENDIAN);
                }
            }
            assertEquals(returned.shading(), shading, "the shading");
            assertEquals(returned.reflectedIds(), ids, "the reflected ids");
            assertTrue(returned.shadowedCount() > 50, "there is something to compare: " + returned);
        }
    }

    @Test
    @DisplayName("refuses targets of another size, or a frame of another device")
    void refusesTargets() {
        try (var scene = build(TestScenes.cubesOnGround());
                var targets = LightingTargets.create(device, 8, 8);
                var frame = device.beginFrame()) {
            var request = LightingRequest.of(
                    TraceRequest.of(TestScenes.camera(), 9, 8).withShadowsToward(TestScenes.sun()));
            assertThrows(IllegalArgumentException.class, () -> tracer.traceLighting(frame, scene, request, targets));
            var other = harness.otherDevice();
            try (var otherFrame = other.beginFrame();
                    var otherTargets = LightingTargets.create(other, 8, 8)) {
                var fits = LightingRequest.of(
                        TraceRequest.of(TestScenes.camera(), 8, 8).withShadowsToward(TestScenes.sun()));
                assertThrows(
                        IllegalArgumentException.class,
                        () -> tracer.traceLighting(otherFrame, scene, fits, otherTargets),
                        "rays traced on one device cannot write another's");
                assertThrows(
                        IllegalArgumentException.class,
                        () -> tracer.traceLighting(frame, scene, fits, otherTargets),
                        "the targets are another device's than the frame's");
            } finally {
                harness.closeOthers();
            }
        }
    }

    @Test
    @DisplayName("picks the tier AUTO says it would, and refuses hardware with a reason where there is none")
    void auto() {
        var answer = RayTracer.availability(device, RayTracingMode.AUTO);
        try (var auto = RayTracer.open(device, RayTracingMode.AUTO).orElseThrow()) {
            assertEquals(answer, auto.device());
        }
        assertFalse(RayTracer.availability(device, RayTracingMode.OFF).isAvailable());
        assertTrue(RayTracer.open(device, RayTracingMode.OFF).isEmpty());
        if (!RayTracer.availability().isAvailable()) {
            assertInstanceOf(RayTracing.Software.class, answer, "no hardware tier: software");
            assertThrows(UnsupportedOperationException.class, () -> RayTracer.open(device, RayTracingMode.HARDWARE));
        }
    }
}
