package dev.goldberry.natives.vulkan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemoryLayout.PathElement;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The generated layouts against the C compiler's: `vulkan_layout.c`, compiled
/// against the same headers, wrote every struct's size and alignment and every
/// field's offset; this holds `VkStructs` to each one.
@DisplayName("the Vulkan layouts")
class VulkanLayoutTest {

    private static Properties probe() {
        var properties = new Properties();
        try (InputStream in = VulkanLayoutTest.class.getResourceAsStream(
                "/dev/goldberry/natives/vulkan/vulkan-layout-linux-x64.properties")) {
            properties.load(Objects.requireNonNull(in));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return properties;
    }

    @Test
    @DisplayName("every struct's size, alignment and field offsets are the C compiler's")
    void agreeWithTheCompiler() {
        var probe = probe();
        Map<String, MemoryLayout> byName = new HashMap<>();
        for (var layout : VkStructs.ALL) {
            byName.put(layout.name().orElseThrow(), layout);
        }
        var checked = 0;
        for (var key : probe.stringPropertyNames()) {
            var parts = key.split("\\.", -1);
            var value = Long.parseLong(probe.getProperty(key));
            switch (parts[0]) {
                case "struct" -> {
                    var layout = byName.get(parts[1]);
                    assertTrue(layout != null, "no layout for " + parts[1]);
                    if (parts[2].equals("sizeof")) {
                        assertEquals(value, layout.byteSize(), parts[1] + " size");
                    } else {
                        assertEquals(value, layout.byteAlignment(), parts[1] + " alignment");
                    }
                    checked++;
                }
                case "field" -> {
                    var layout = byName.get(parts[1]);
                    assertTrue(layout != null, "no layout for " + parts[1]);
                    assertEquals(value, layout.byteOffset(PathElement.groupElement(parts[2])), key);
                    checked++;
                }
                default -> {}
            }
        }
        assertTrue(checked > 1000, "the probe checked " + checked + " things");
    }

    @Test
    @DisplayName("the bitfields fill their words from the least significant bit")
    void bitfields() {
        assertEquals(0, VkStructs.StdVideoDecodeH264PictureInfoFlags.SHIFT_field_pic_flag);
        assertEquals(1 << 2, VkStructs.StdVideoDecodeH264PictureInfoFlags.MASK_IdrPicFlag);
        assertEquals(4, VkStructs.StdVideoDecodeH264PictureInfoFlags.LAYOUT.byteSize());
    }

    @Test
    @DisplayName("an instance of an acceleration structure packs its index and mask into one word")
    void instanceBitfields() {
        var instance = VkStructs.VkAccelerationStructureInstanceKHR.class;
        assertEquals(64, VkStructs.VkAccelerationStructureInstanceKHR.LAYOUT.byteSize(), instance.getSimpleName());
        assertEquals(48, VkStructs.VkAccelerationStructureInstanceKHR.WORD_instanceCustomIndex);
        assertEquals(48, VkStructs.VkAccelerationStructureInstanceKHR.WORD_mask);
        assertEquals(24, VkStructs.VkAccelerationStructureInstanceKHR.SHIFT_mask);
        assertEquals(0x00ff_ffff, VkStructs.VkAccelerationStructureInstanceKHR.MASK_instanceCustomIndex);
        assertEquals(52, VkStructs.VkAccelerationStructureInstanceKHR.WORD_flags);
        assertEquals(56, VkStructs.VkAccelerationStructureInstanceKHR.accelerationStructureReference);
    }
}
