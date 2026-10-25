package dev.goldberry.gpu.raytrace.vulkan;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_LONG_UNALIGNED;

import java.lang.foreign.MemorySegment;

import dev.goldberry.gpu.raytrace.Instance;
import dev.goldberry.natives.vulkan.Structs;
import dev.goldberry.natives.vulkan.VkConstants;
import dev.goldberry.natives.vulkan.VkStructs;

/// An [Instance] as a top-level acceleration structure holds it: a
/// `VkAccelerationStructureInstanceKHR`, its transform row by row, its id and
/// mask packed into one word, and the bottom-level structure it places by
/// device address. Both faces of every triangle are hit.
final class InstanceRecords {

    /// The size of one record.
    static final long SIZE = VkStructs.VkAccelerationStructureInstanceKHR.LAYOUT.byteSize();

    private InstanceRecords() {}

    /// Writes record `index` of `records` for `instance`, placing the
    /// bottom-level structure at `bottomLevel`.
    static void write(MemorySegment records, int index, Instance instance, long bottomLevel) {
        var record = records.asSlice(index * SIZE, SIZE);
        record.fill((byte) 0);
        var elements = instance.transform().toRowMajor();
        for (var i = 0; i < elements.length; i++) {
            record.set(
                    JAVA_FLOAT_UNALIGNED,
                    VkStructs.VkAccelerationStructureInstanceKHR.transform + (long) i * Float.BYTES,
                    elements[i]);
        }
        var word = VkStructs.VkAccelerationStructureInstanceKHR.WORD_instanceCustomIndex;
        record.set(JAVA_INT_UNALIGNED, word, 0);
        Structs.bits(
                record,
                word,
                VkStructs.VkAccelerationStructureInstanceKHR.SHIFT_instanceCustomIndex,
                VkStructs.VkAccelerationStructureInstanceKHR.MASK_instanceCustomIndex,
                instance.id());
        Structs.bits(
                record,
                VkStructs.VkAccelerationStructureInstanceKHR.WORD_mask,
                VkStructs.VkAccelerationStructureInstanceKHR.SHIFT_mask,
                VkStructs.VkAccelerationStructureInstanceKHR.MASK_mask,
                instance.mask());
        Structs.bits(
                record,
                VkStructs.VkAccelerationStructureInstanceKHR.WORD_instanceShaderBindingTableRecordOffset,
                VkStructs.VkAccelerationStructureInstanceKHR.SHIFT_instanceShaderBindingTableRecordOffset,
                VkStructs.VkAccelerationStructureInstanceKHR.MASK_instanceShaderBindingTableRecordOffset,
                0);
        Structs.bits(
                record,
                VkStructs.VkAccelerationStructureInstanceKHR.WORD_flags,
                VkStructs.VkAccelerationStructureInstanceKHR.SHIFT_flags,
                VkStructs.VkAccelerationStructureInstanceKHR.MASK_flags,
                VkConstants.VK_GEOMETRY_INSTANCE_TRIANGLE_FACING_CULL_DISABLE_BIT_KHR);
        record.set(
                JAVA_LONG_UNALIGNED,
                VkStructs.VkAccelerationStructureInstanceKHR.accelerationStructureReference,
                bottomLevel);
    }
}
