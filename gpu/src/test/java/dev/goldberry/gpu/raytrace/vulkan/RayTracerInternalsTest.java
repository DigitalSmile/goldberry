package dev.goldberry.gpu.raytrace.vulkan;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import dev.goldberry.gpu.raytrace.Camera;
import dev.goldberry.gpu.raytrace.Instance;
import dev.goldberry.gpu.raytrace.SceneGeometry;
import dev.goldberry.gpu.raytrace.TraceRequest;
import dev.goldberry.gpu.raytrace.Transform;
import dev.goldberry.gpu.raytrace.TriangleMesh;
import dev.goldberry.gpu.raytrace.Vector3;
import dev.goldberry.natives.vulkan.VkConstants;
import dev.goldberry.natives.vulkan.VkStructs;
import dev.goldberry.natives.vulkan.VulkanException;
import dev.goldberry.natives.vulkan.VulkanInstance;

/// What the ray tracer computes before it calls Vulkan, which needs no
/// device: the shader binding table's layout, the push constants, an
/// instance's record, the choice of device and memory, and the shader
/// library as it ships.
@DisplayName("the ray tracer's internals")
class RayTracerInternalsTest {

    @Nested
    @DisplayName("the shader binding table")
    class Table {

        @Test
        @DisplayName("lays out NVIDIA's 32-byte handles on 64-byte regions")
        void nvidia() {
            var table = ShaderBindingTable.of(32, 32, 64, 2, 1);
            assertEquals(new ShaderBindingTable.Region(0, 64, 64, 1), table.raygen());
            assertEquals(new ShaderBindingTable.Region(64, 32, 64, 2), table.miss());
            assertEquals(new ShaderBindingTable.Region(128, 32, 64, 1), table.hit());
            assertEquals(192, table.size());
            assertArrayEquals(new long[] {0, 64, 96, 128}, table.groupOffsets());
        }

        @Test
        @DisplayName("rounds a handle up to its alignment, and each region up to the base")
        void rounding() {
            var table = ShaderBindingTable.of(20, 16, 64, 3, 2);
            assertEquals(32, table.miss().stride());
            assertEquals(64, table.raygen().stride(), "the ray-generation record's stride is the region's");
            assertEquals(128, table.miss().size(), "3 × 32 = 96, rounded up to 128");
            assertEquals(64 + 128, table.hit().offset());
            for (var offset : table.groupOffsets()) {
                assertEquals(0, offset % 16, "every handle on its alignment: " + offset);
            }
            for (var region : List.of(table.raygen(), table.miss(), table.hit())) {
                assertEquals(0, region.offset() % 64, "every region on the base: " + region);
            }
        }

        @Test
        @DisplayName("refuses sizes and counts that are not positive, and records past the end")
        void refuses() {
            assertThrows(IllegalArgumentException.class, () -> ShaderBindingTable.of(0, 32, 64, 1, 1));
            assertThrows(IllegalArgumentException.class, () -> ShaderBindingTable.of(32, 32, 64, 0, 1));
            var table = ShaderBindingTable.of(32, 32, 64, 2, 1);
            assertThrows(IndexOutOfBoundsException.class, () -> table.miss().record(2));
        }
    }

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
            }
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
        @DisplayName("pack the shadow mask a byte a pixel with no padding, rounded up to a word, zeroed first")
        void shadowMask() {
            assertEquals(4, TraceOutput.SHADOW_MASK.bytes(1, 1), "one pixel takes a word");
            assertEquals(16, TraceOutput.SHADOW_MASK.bytes(5, 3), "15 bytes, rounded up");
            assertEquals(1920L * 1080, TraceOutput.SHADOW_MASK.bytes(1920, 1080));
            assertTrue(TraceOutput.SHADOW_MASK.zeroed(), "the shaders write lit pixels alone");
        }

        @Test
        @DisplayName("are the codes the shader tests for")
        void codes() throws IOException {
            var source = Files.readString(Path.of("src/main/raytrace/trace.lib.hlsl"));
            for (var output : TraceOutput.values()) {
                assertTrue(
                        source.contains("static const uint OUTPUT_" + output.name() + " = " + output.code() + ";"),
                        output + " is " + output.code() + " in the shader too");
            }
        }
    }

    @Nested
    @DisplayName("an update's meshes")
    class UpdatedMeshes {

        @Test
        @DisplayName("are found among the built ones by identity, in any order and number")
        void found() {
            var a = TriangleMesh.of(new float[] {0, 0, 0, 1, 0, 0, 0, 1, 0}, new int[] {0, 1, 2});
            var b = TriangleMesh.of(new float[] {0, 0, 0, 1, 0, 0, 0, 1, 0}, new int[] {0, 1, 2});
            var geometry = new SceneGeometry(List.of(
                    Instance.of(b, Transform.IDENTITY, 1),
                    Instance.of(a, Transform.IDENTITY, 2),
                    Instance.of(b, Transform.translation(new Vector3(1, 0, 0)), 3)));
            assertArrayEquals(new int[] {1, 0, 1}, AccelerationScene.meshIndices(List.of(a, b), geometry));
        }

        @Test
        @DisplayName("refuse a mesh the scene was not built with, even one of equal triangles")
        void refused() {
            var a = TriangleMesh.of(new float[] {0, 0, 0, 1, 0, 0, 0, 1, 0}, new int[] {0, 1, 2});
            var twin = TriangleMesh.of(new float[] {0, 0, 0, 1, 0, 0, 0, 1, 0}, new int[] {0, 1, 2});
            var geometry =
                    SceneGeometry.of(Instance.of(a, Transform.IDENTITY, 1), Instance.of(twin, Transform.IDENTITY, 9));
            var refusal = assertThrows(
                    IllegalArgumentException.class, () -> AccelerationScene.meshIndices(List.of(a), geometry));
            assertTrue(refusal.getMessage().contains("instance 9"), refusal.getMessage());
        }
    }

    @Nested
    @DisplayName("an instance's record")
    class Records {

        @Test
        @DisplayName("holds the transform row by row, the id and mask in one word, and the structure's address")
        void packs() {
            var mesh = TriangleMesh.of(new float[] {0, 0, 0, 1, 0, 0, 0, 1, 0}, new int[] {0, 1, 2});
            var transform = Transform.translation(new Vector3(1, 2, 3));
            try (var arena = Arena.ofConfined()) {
                var records = arena.allocate(InstanceRecords.SIZE * 2, 16);
                InstanceRecords.write(records, 1, new Instance(mesh, transform, 0xAB_CDEF, 0x0F), 0x1234_5678_9ABCL);
                var record = records.asSlice(InstanceRecords.SIZE, InstanceRecords.SIZE);
                assertEquals(64, InstanceRecords.SIZE);
                assertEquals(1, record.get(JAVA_FLOAT, 0));
                assertEquals(1, record.get(JAVA_FLOAT, 12), "m03, the move along x");
                assertEquals(2, record.get(JAVA_FLOAT, 28), "m13");
                assertEquals(3, record.get(JAVA_FLOAT, 44), "m23");
                assertEquals(0x0FAB_CDEF, record.get(JAVA_INT, 48), "id in the low 24 bits, mask in the top 8");
                assertEquals(
                        VkConstants.VK_GEOMETRY_INSTANCE_TRIANGLE_FACING_CULL_DISABLE_BIT_KHR << 24,
                        record.get(JAVA_INT, 52),
                        "both faces hit; the first hit group");
                assertEquals(0x1234_5678_9ABCL, record.get(JAVA_LONG, 56));
                assertEquals(0, records.get(JAVA_LONG, 56), "record 0 untouched");
                assertEquals(
                        VkStructs.VkAccelerationStructureInstanceKHR.accelerationStructureReference,
                        56,
                        "the generated layout agrees");
            }
        }
    }

    @Nested
    @DisplayName("the device")
    class Devices {

        private static RayDevice.Candidate candidate(int index, String name, int type) {
            return new RayDevice.Candidate(index, name, type, true, true, 0);
        }

        private final List<RayDevice.Candidate> machine = List.of(
                candidate(0, "llvmpipe (LLVM 21.1.8, 256 bits)", VkConstants.VK_PHYSICAL_DEVICE_TYPE_CPU),
                candidate(1, "NVIDIA GeForce GTX 1660 Ti", VkConstants.VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU),
                new RayDevice.Candidate(
                        2, "Old GPU", VkConstants.VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU, false, false, 0));

        @Test
        @DisplayName("is the discrete GPU before the CPU, unless one is named")
        void chooses() {
            assertEquals(
                    "NVIDIA GeForce GTX 1660 Ti",
                    RayDevice.choose(machine, null).orElseThrow().name());
            assertEquals(
                    "NVIDIA GeForce GTX 1660 Ti",
                    RayDevice.choose(machine, " ").orElseThrow().name());
            assertEquals(
                    "llvmpipe (LLVM 21.1.8, 256 bits)",
                    RayDevice.choose(machine, "LLVMPIPE").orElseThrow().name());
            assertEquals(Optional.empty(), RayDevice.choose(machine, "old gpu"), "named, and unusable");
        }

        @Test
        @DisplayName("says why none was chosen, device by device")
        void refusals() {
            assertEquals("no Vulkan device", RayDevice.refusal(List.of(), null));
            assertTrue(RayDevice.refusal(machine, "old").startsWith("Old GPU has no VK_KHR_acceleration_structure"));
            assertTrue(RayDevice.refusal(machine, "amd").contains("named like 'amd'"));
            var noQueue = new RayDevice.Candidate(0, "Odd", 1, true, true, -1);
            assertEquals("Odd has no queue that computes", noQueue.refusal());
            var noFeatures = new RayDevice.Candidate(0, "Half", 1, true, false, 0);
            assertEquals("Half has the extensions but not their features", noFeatures.refusal());
        }

        @Test
        @DisplayName(
                "takes the first memory type with every flag, or any it may live in when that is only a preference")
        void memoryTypes() {
            var local = VkConstants.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT;
            var host =
                    VkConstants.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VkConstants.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT;
            var types = List.of(
                    new VulkanInstance.MemoryType(0, local),
                    new VulkanInstance.MemoryType(1, host),
                    new VulkanInstance.MemoryType(2, local | host));
            assertEquals(0, RayDevice.chooseMemoryType(types, 0b111, local, true));
            assertEquals(1, RayDevice.chooseMemoryType(types, 0b111, host, true));
            assertEquals(2, RayDevice.chooseMemoryType(types, 0b100, host, true), "only type 2 is allowed");
            assertEquals(1, RayDevice.chooseMemoryType(types, 0b010, local, false), "a preference gives way");
            assertThrows(VulkanException.class, () -> RayDevice.chooseMemoryType(types, 0b001, host, true));
        }

        @Test
        @DisplayName("reads back through cached memory where there is some, and coherent memory where not")
        void readbackMemory() {
            var host = DeviceBuffer.HOST;
            var cached = host | VkConstants.VK_MEMORY_PROPERTY_HOST_CACHED_BIT;
            var local = VkConstants.VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT;
            var discrete = List.of(
                    new VulkanInstance.MemoryType(0, local),
                    new VulkanInstance.MemoryType(1, host),
                    new VulkanInstance.MemoryType(2, cached));
            assertEquals(2, RayDevice.chooseReadbackMemoryType(discrete, 0b111), "cached first");
            assertEquals(
                    1, RayDevice.chooseReadbackMemoryType(discrete, 0b011), "coherent where cached is not allowed");
            assertThrows(VulkanException.class, () -> RayDevice.chooseReadbackMemoryType(discrete, 0b001));
        }

        @Test
        @DisplayName("rounds sizes up to an alignment")
        void align() {
            assertEquals(64, DeviceBuffer.align(1, 64));
            assertEquals(64, DeviceBuffer.align(64, 64));
            assertEquals(96, DeviceBuffer.align(65, 32));
            assertEquals(7, DeviceBuffer.align(7, 1));
            assertEquals(0, DeviceBuffer.align(0, 256));
        }
    }

    @Nested
    @DisplayName("the shader library")
    class Library {

        @Test
        @DisplayName("is SPIR-V with the four entry points the pipeline names")
        void entryPoints() {
            var code = RayShaders.trace();
            var text = new String(code, StandardCharsets.ISO_8859_1);
            for (var entry :
                    List.of(RayShaders.RAYGEN, RayShaders.MISS, RayShaders.SHADOW_MISS, RayShaders.CLOSEST_HIT)) {
                assertTrue(text.contains(entry + "\0"), entry + " is an entry point of trace.spv");
            }
            assertThrows(IllegalStateException.class, () -> RayShaders.load("missing.spv"));
        }

        @Test
        @DisplayName("was compiled from trace.lib.hlsl as it is now")
        void fresh() throws IOException, NoSuchAlgorithmException {
            var manifest = new Properties();
            try (var in = RayShaders.class.getResourceAsStream(RayShaders.DIRECTORY + "shaders.properties")) {
                assertTrue(in != null, "shaders.properties is missing: run ./gradlew :gpu:compileRayShaders");
                manifest.load(in);
            }
            var sources = Path.of("src/main/raytrace");
            try (var files = Files.list(sources)) {
                var libraries =
                        files.filter(f -> f.toString().endsWith(".lib.hlsl")).toList();
                assertEquals(1, libraries.size(), "one library: " + libraries);
                for (var source : libraries) {
                    var name = source.getFileName().toString().replace(".lib.hlsl", "");
                    var digest = HexFormat.of()
                            .formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source)));
                    assertEquals(
                            manifest.getProperty(name + ".sha256"),
                            digest,
                            source.getFileName()
                                    + " changed since it was compiled: run ./gradlew :gpu:compileRayShaders");
                }
            }
            assertTrue(manifest.getProperty("tools", "").contains("dxcompiler"), "the compiler is recorded");
        }

        @Test
        @DisplayName("writes its hits in the layout the API reads, 24 bytes a pixel")
        void recordLayout() throws IOException {
            var source = Files.readString(Path.of("src/main/raytrace/trace.lib.hlsl"));
            var record = source.substring(
                    source.indexOf("struct HitRecord"), source.indexOf("};", source.indexOf("struct HitRecord")));
            var fields = record.lines()
                    .filter(line -> line.trim().matches("(uint|float) \\w+;.*"))
                    .toList();
            assertEquals(6, fields.size(), "six 32-bit fields: " + fields);
            assertEquals(dev.goldberry.gpu.raytrace.Hits.RECORD_BYTES, fields.size() * 4);
            var constants = source.substring(
                    source.indexOf("struct Constants"), source.indexOf("};", source.indexOf("struct Constants")));
            var float4s = constants
                    .lines()
                    .filter(line -> line.trim().startsWith("float4 "))
                    .count();
            var scalars = constants
                    .lines()
                    .filter(line -> line.trim().matches("(uint|float) \\w+;.*"))
                    .count();
            assertEquals(RayConstants.SIZE, float4s * 16 + scalars * 4);
        }
    }
}
