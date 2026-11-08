package dev.goldberry.gpu.raytrace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.gpu.raytrace.vulkan.AccelerationScene;

/// [RayTracerContract] on the hardware tier, and what is the hardware tier's
/// alone: its device's name, its compacted structures, and meshes it never
/// builds again.
///
/// Needs a Vulkan device with the ray-tracing extensions: this machine's GPU,
/// or lavapipe (`-Pgoldberry.raytracing.device=llvmpipe`). Skips without one,
/// unless `goldberry.raytracing.required` is set.
@DisplayName("the ray tracer in hardware, on a real device")
class HardwareRayTracerTest extends RayTracerContract {

    @Override
    RayTracer openTracer() {
        return RayTracingRequirement.open();
    }

    @Override
    RayTracer openAnother() {
        return RayTracer.open(RayTracingMode.HARDWARE).orElseThrow();
    }

    private AccelerationScene structures(RayScene scene) {
        return (AccelerationScene) scene.structuresFor(tracer);
    }

    @Test
    @DisplayName("names the device it traces on")
    void namesItsDevice() {
        assertTrue(RayTracer.availability().isAvailable());
        assertEquals(RayTracer.availability(), tracer.device());
        assertInstanceOf(RayTracing.Hardware.class, tracer.device());
        assertFalse(tracer.device().device().isBlank());
        assertTrue(tracer.toString().contains("hardware"), tracer.toString());
        // A run that names its device, as the lavapipe run does, gets that one.
        var named = System.getProperty("goldberry.raytracing.device");
        if (named != null && !named.isBlank()) {
            assertTrue(
                    tracer.device().device().toLowerCase(Locale.ROOT).contains(named.toLowerCase(Locale.ROOT)),
                    tracer.device() + " for " + named);
        }
    }

    @Test
    @DisplayName("keeps its meshes' structures as they were when its instances move")
    void movesWithoutBuildingMeshes() {
        var geometry = TestScenes.cubesOnGround();
        var scene = build(geometry);
        var before = structures(scene).bottomBytes();
        scene.update(new SceneGeometry(geometry.instances().stream()
                .map(instance -> instance.withTransform(
                        instance.transform().then(Transform.translation(new Vector3(0.5f, 0, 0)))))
                .toList()));
        assertEquals(before, structures(scene).bottomBytes(), "the meshes' structures stay");
    }

    @Test
    @DisplayName("compacts its meshes' structures, and counts what they hold")
    void compacts() {
        var scene = build(TestScenes.cubesOnGround());
        var structures = structures(scene);
        assertEquals(2, structures.bottomLevelCount(), "the ground and the shared cube");
        assertTrue(structures.bottomBytes() > 0);
        assertTrue(
                structures.bottomBytes() <= structures.bottomBytesBuilt(),
                structures.bottomBytes() + " compacted, " + structures.bottomBytesBuilt() + " built");
        assertTrue(scene.structureBytes() > structures.bottomBytes(), "the instances' structure counts too");
    }
}
