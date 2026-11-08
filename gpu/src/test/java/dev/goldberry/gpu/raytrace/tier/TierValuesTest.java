package dev.goldberry.gpu.raytrace.tier;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import dev.goldberry.gpu.raytrace.Camera;
import dev.goldberry.gpu.raytrace.LightingRequest;
import dev.goldberry.gpu.raytrace.TraceRequest;
import dev.goldberry.gpu.raytrace.TriangleMesh;
import dev.goldberry.gpu.raytrace.Vector3;
import dev.goldberry.gpu.render.BuiltInComputeShader;

/// What both tiers share, with no device: the constants their shaders read,
/// the face normals, the layouts they write, and that each tier's shaders say
/// the same as the Java that drives them.
@DisplayName("what the ray tracer's tiers share")
class TierValuesTest {

    @Nested
    @DisplayName("the push constants")
    class Constants {

        @Test
        @DisplayName("are the camera's frame, the light, the size, the distances and the output, in the shader's order")
        void layout() {
            var camera = Camera.lookingAt(new Vector3(0, 0, 5), Vector3.ZERO, 90);
            var request = TraceRequest.of(camera, 200, 100)
                    .withShadowsToward(new Vector3(0, 1, 0))
                    .withMaxDistance(50)
                    .withShadowBias(0.01f);
            try (var arena = Arena.ofConfined()) {
                var constants = arena.allocate(RayConstants.SIZE);
                RayConstants.write(constants, request, TraceOutput.SHADOW_MASK);
                assertEquals(5, constants.get(JAVA_FLOAT, RayConstants.ORIGIN + 8));
                assertEquals(1, constants.get(JAVA_FLOAT, RayConstants.ORIGIN + 12));
                assertEquals(-1, constants.get(JAVA_FLOAT, RayConstants.FORWARD + 8), 1e-6);
                assertEquals(2, constants.get(JAVA_FLOAT, RayConstants.RIGHT), 1e-6, "tan 45° × the aspect");
                assertEquals(1, constants.get(JAVA_FLOAT, RayConstants.UP + 4), 1e-6, "tan 45°");
                assertEquals(1, constants.get(JAVA_FLOAT, RayConstants.TOWARD_LIGHT + 4));
                assertEquals(1, constants.get(JAVA_FLOAT, RayConstants.TOWARD_LIGHT + 12), "shadows asked for");
                assertEquals(200, constants.get(JAVA_INT, RayConstants.WIDTH));
                assertEquals(100, constants.get(JAVA_INT, RayConstants.HEIGHT));
                assertEquals(50, constants.get(JAVA_FLOAT, RayConstants.MAX_DISTANCE));
                assertEquals(0.01f, constants.get(JAVA_FLOAT, RayConstants.SHADOW_BIAS));
                assertEquals(1, constants.get(JAVA_INT, RayConstants.OUTPUT), "the shadow mask");

                RayConstants.write(constants, TraceRequest.of(camera, 2, 2), TraceOutput.RECORDS);
                assertEquals(0, constants.get(JAVA_FLOAT, RayConstants.TOWARD_LIGHT + 12), "no shadows");
                assertEquals(0, constants.get(JAVA_INT, RayConstants.OUTPUT), "the records");
                assertThrows(
                        IllegalArgumentException.class,
                        () -> RayConstants.write(arena.allocate(16), request, TraceOutput.RECORDS));
                assertEquals(0, constants.get(JAVA_INT, RayConstants.OCCLUSION_RAYS), "none but for the lighting");
                assertEquals(0, constants.get(JAVA_INT, RayConstants.REFLECTIONS));
            }
        }

        @Test
        @DisplayName("carry the lighting's occlusion rays, their radius and its reflections after the output")
        void lighting() {
            var camera = Camera.lookingAt(new Vector3(0, 0, 5), Vector3.ZERO, 90);
            var rays = TraceRequest.of(camera, 8, 4).withShadowsToward(new Vector3(0, 1, 0));
            try (var arena = Arena.ofConfined()) {
                var constants = arena.allocate(RayConstants.SIZE);
                RayConstants.write(constants, LightingRequest.of(rays).withOcclusion(16, 0.75f));
                assertEquals(2, constants.get(JAVA_INT, RayConstants.OUTPUT), "the lighting");
                assertEquals(16, constants.get(JAVA_INT, RayConstants.OCCLUSION_RAYS));
                assertEquals(0.75f, constants.get(JAVA_FLOAT, RayConstants.OCCLUSION_RADIUS));
                assertEquals(1, constants.get(JAVA_INT, RayConstants.REFLECTIONS));
                assertEquals(1, constants.get(JAVA_FLOAT, RayConstants.TOWARD_LIGHT + 12), "shadows");
                RayConstants.write(constants, LightingRequest.of(rays).withReflections(false));
                assertEquals(0, constants.get(JAVA_INT, RayConstants.REFLECTIONS));
            }
        }
    }

    @Nested
    @DisplayName("the face normals")
    class Normals {

        @Test
        @DisplayName("are each triangle's unit normal out of its counter-clockwise face, the meshes one after another")
        void normals() {
            var ground = TriangleMesh.of(new float[] {0, 0, 0, 1, 0, 0, 0, 0, 1}, new int[] {0, 2, 1});
            var wall = TriangleMesh.of(new float[] {0, 0, 0, 2, 0, 0, 0, 2, 0, 5, 5, 5}, new int[] {0, 1, 2, 3, 3, 3});
            var faces = FaceNormals.of(List.of(ground, wall));
            assertEquals(3, faces.triangles());
            assertArrayEquals(new int[] {0, 1}, faces.firstTriangles());
            assertArrayEquals(
                    new float[] {0, 1, 0, 0, 0, 0, 1, 0, 0, 1, 0, 0},
                    faces.normals(),
                    "up for the ground, +z for the wall, and y for a triangle of no area");
        }
    }

    @Nested
    @DisplayName("the outputs")
    class Outputs {

        @Test
        @DisplayName("are a record a pixel, each written whole")
        void records() {
            assertEquals(5 * 24, TraceOutput.RECORDS.bytes(5, 1));
            assertEquals(1920L * 1080 * 24, TraceOutput.RECORDS.bytes(1920, 1080));
            assertFalse(TraceOutput.RECORDS.zeroed());
        }

        @Test
        @DisplayName("are eight bytes a pixel of lighting, in two planes each written whole")
        void lighting() {
            assertEquals(5 * 8, TraceOutput.LIGHTING.bytes(5, 1));
            assertEquals(1920L * 1080 * 8, TraceOutput.LIGHTING.bytes(1920, 1080));
            assertFalse(TraceOutput.LIGHTING.zeroed());
            assertEquals(2, TraceOutput.LIGHTING.code());
        }

        @Test
        @DisplayName("pack the shadow mask a byte a pixel with no padding, rounded up to a word, zeroed first")
        void shadowMask() {
            assertEquals(4, TraceOutput.SHADOW_MASK.bytes(1, 1), "one pixel takes a word");
            assertEquals(16, TraceOutput.SHADOW_MASK.bytes(5, 3), "15 bytes, rounded up");
            assertEquals(1920L * 1080, TraceOutput.SHADOW_MASK.bytes(1920, 1080));
            assertTrue(TraceOutput.SHADOW_MASK.zeroed(), "the shaders write lit pixels alone");
        }

        @Test
        @DisplayName("are the codes both tiers' shaders test for")
        void codes() throws IOException {
            for (var file : List.of("src/main/raytrace/trace.lib.hlsl", "src/main/shaders/raytrace.hlsli")) {
                var source = Files.readString(Path.of(file));
                for (var output : TraceOutput.values()) {
                    assertTrue(
                            source.contains("static const uint OUTPUT_" + output.name() + " = " + output.code() + ";"),
                            output + " is " + output.code() + " in " + file + " too");
                }
            }
        }
    }

    @Nested
    @DisplayName("the software tier's shaders")
    class SoftwareShaders {

        @Test
        @DisplayName("read the constants' 112 bytes in their order, five float4s and eight 32-bit values")
        void constants() throws IOException {
            var source = Files.readString(Path.of("src/main/shaders/raytrace.hlsli"));
            var block = source.substring(
                    source.indexOf("cbuffer Rays"), source.indexOf("};", source.indexOf("cbuffer Rays")));
            var float4s = block.lines()
                    .filter(line -> line.trim().startsWith("float4 "))
                    .count();
            var scalars = block.lines()
                    .filter(line -> line.trim().matches("(uint|float) \\w+;.*"))
                    .count();
            assertEquals(5, float4s);
            assertEquals(RayConstants.SIZE, float4s * 16 + scalars * 4);
        }

        @ParameterizedTest
        @EnumSource(
                value = BuiltInComputeShader.class,
                names = {"RAY_TRACE", "RAY_SHADOWS", "RAY_LIGHTING"})
        @DisplayName("run in the workgroups their entries say")
        void workgroups(BuiltInComputeShader shader) throws IOException {
            var source = Files.readString(Path.of("src/main/shaders/" + shader.fileName() + ".hlsl"));
            var matcher =
                    Pattern.compile("\\[numthreads\\((\\d+), (\\d+), 1\\)\\]").matcher(source);
            assertTrue(matcher.find(), shader + " has a numthreads");
            assertEquals(shader.threadsX(), Integer.parseInt(matcher.group(1)));
            assertEquals(shader.threadsY(), Integer.parseInt(matcher.group(2)));
        }
    }
}
