package dev.goldberry.gpu.raytrace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.gpu.GpuException;
import dev.goldberry.gpu.GpuFrame;
import dev.goldberry.gpu.raytrace.tier.Tier;
import dev.goldberry.gpu.raytrace.tier.TracedScene;
import dev.goldberry.natives.vulkan.VulkanException;

/// A tier's Vulkan failures, as the tracer words them: a [GpuException] naming
/// what failed and on which device, the tier's exception its cause. Needs no
/// device: the tier is a fake that refuses.
@DisplayName("a ray tracer whose tier refuses")
class RayTracerFailureTest {

    private static final String DEVICE = "a refusing device";

    @Test
    @DisplayName("words a refused build as the scene not being built, on its device")
    void refusedBuild() {
        var tier = new RefusingTier(true);
        try (var tracer = new RayTracer(tier)) {
            var failure = assertThrows(GpuException.class, () -> tracer.build(TestScenes.cubesOnGround()));

            assertEquals("the scene could not be built on " + DEVICE, failure.getMessage());
            assertSame(tier.refusal, failure.getCause());
        }
    }

    @Test
    @DisplayName("words a refused update as the scene not being updated, on its device")
    void refusedUpdate() {
        var tier = new RefusingTier(false);
        try (var tracer = new RayTracer(tier);
                var scene = tracer.build(TestScenes.cubesOnGround())) {
            var failure = assertThrows(GpuException.class, () -> scene.update(TestScenes.cubesOnGround()));

            assertTrue(failure.getMessage().endsWith(" could not be updated on " + DEVICE), failure.getMessage());
            assertTrue(failure.getMessage().startsWith("RayScene["), failure.getMessage());
            assertSame(tier.refusal, failure.getCause());
        }
    }

    /// A tier that builds a scene with nothing in it, unless told to refuse,
    /// and refuses everything else with [#refusal].
    private static final class RefusingTier implements Tier {

        final VulkanException refusal = new VulkanException("the driver refused");
        private final boolean refuseBuild;

        RefusingTier(boolean refuseBuild) {
            this.refuseBuild = refuseBuild;
        }

        @Override
        public RayTracing.Available answer() {
            return new RayTracing.Software(DEVICE);
        }

        @Override
        public TracedScene build(SceneGeometry geometry) {
            if (refuseBuild) {
                throw refusal;
            }
            return new EmptyScene(geometry);
        }

        @Override
        public void update(TracedScene scene, SceneGeometry geometry) {
            throw refusal;
        }

        @Override
        public ByteBuffer trace(TracedScene scene, TraceRequest request) {
            throw refusal;
        }

        @Override
        public ByteBuffer traceShadows(TracedScene scene, TraceRequest request) {
            throw refusal;
        }

        @Override
        public ByteBuffer traceLighting(TracedScene scene, LightingRequest request) {
            throw refusal;
        }

        @Override
        public void traceLighting(GpuFrame frame, TracedScene scene, LightingRequest request, LightingTargets into) {
            throw refusal;
        }

        @Override
        public void close() {}
    }

    /// A built scene that holds no GPU memory.
    private static final class EmptyScene implements TracedScene {

        private final SceneGeometry geometry;
        private boolean closed;

        EmptyScene(SceneGeometry geometry) {
            this.geometry = geometry;
        }

        @Override
        public SceneGeometry geometry() {
            return geometry;
        }

        @Override
        public long structureBytes() {
            return 0;
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
