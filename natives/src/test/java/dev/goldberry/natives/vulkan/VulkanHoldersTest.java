package dev.goldberry.natives.vulkan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.MemorySegment;
import java.util.HashSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.goldberry.natives.metadata.ForeignSurface;
import dev.goldberry.natives.vulkan.calls.VkCalls;

/// The generated holders are this module's like any other: found and recorded
/// by the foreign surface, so a native image of `:media` or `:gpu` is told
/// every Vulkan shape without either listing them. Needs no Vulkan.
@DisplayName("the Vulkan holders")
class VulkanHoldersTest {

    @Test
    @DisplayName("are part of the foreign surface, vkGetInstanceProcAddr among them")
    void recorded() {
        var downcalls = new HashSet<>(ForeignSurface.downcalls());
        assertTrue(downcalls.containsAll(VkCalls.DESCRIPTORS), "every Vulkan descriptor is recorded");
        assertTrue(downcalls.contains(VkCalls.GetInstanceProcAddr.DESCRIPTOR));
    }

    @Test
    @DisplayName("bind each group of device functions it names, and no function twice")
    void groups() {
        var names = new HashSet<String>();
        var all = 0;
        for (var record :
                new Class<?>[] {VkDeviceFunctions.class, VkVideoFunctions.class, VkRayTracingFunctions.class}) {
            for (var component : record.getRecordComponents()) {
                names.add(component.getType().getName());
                all++;
            }
        }
        assertEquals(all, names.size(), "a function in two groups would be resolved twice");
        assertTrue(names.contains(VkCalls.CmdTraceRaysKHR.class.getName()));
        assertTrue(names.contains(VkCalls.CmdDecodeVideoKHR.class.getName()));
    }

    @Test
    @DisplayName("refuse a function the device has no address for, by name")
    void refusesNull() {
        var refused = assertThrows(VulkanException.class, () -> new VkCalls.CmdTraceRaysKHR(MemorySegment.NULL));
        assertTrue(refused.getMessage().contains("vkCmdTraceRaysKHR"), refused.getMessage());
    }

    @Test
    @DisplayName("resolve a group only where every function in it has an address")
    void resolvesAGroupWhole() {
        var resolved = VkRayTracingFunctions.resolve(name -> MemorySegment.ofAddress(0x1000));
        assertEquals(VkCalls.CmdTraceRaysKHR.NAME, "vkCmdTraceRaysKHR");
        assertTrue(resolved.cmdTraceRaysKHR() != null);
        assertThrows(
                VulkanException.class,
                () -> VkRayTracingFunctions.resolve(
                        name -> name.equals("vkCmdTraceRaysKHR") ? MemorySegment.NULL : MemorySegment.ofAddress(1)));
    }
}
